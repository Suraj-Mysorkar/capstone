package com.bank.digital.lending.service;

import com.bank.digital.lending.model.dto.*;
import com.bank.digital.lending.model.entity.Customer;
import com.bank.digital.lending.model.entity.LoanApplication;
import com.bank.digital.lending.model.entity.LoanAuditLog;
import com.bank.digital.lending.model.entity.LoanDocument;
import com.bank.digital.lending.model.entity.LoanScheme;
import com.bank.digital.lending.model.enums.DocType;
import com.bank.digital.lending.model.enums.LoanStatus;
import com.bank.digital.lending.orchestration.LoanDurableOrchestrator;
import com.bank.digital.lending.repository.CustomerRepository;
import com.bank.digital.lending.repository.LoanApplicationRepository;
import com.bank.digital.lending.repository.LoanAuditLogRepository;
import com.bank.digital.lending.repository.LoanDocumentRepository;
import com.bank.digital.lending.repository.LoanSchemeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LoanApplicationService {

    private static final Logger log = LoggerFactory.getLogger(LoanApplicationService.class);

    @Value("${azure.callback-base-url:}")
    private String callbackBaseUrl;

    private final LoanApplicationRepository applicationRepository;
    private final LoanSchemeRepository schemeRepository;
    private final LoanAuditLogRepository auditLogRepository;
    private final LoanDocumentRepository loanDocumentRepository;
    private final CustomerRepository customerRepository;
    private final EMICalculatorProxyService emiCalculatorProxy;
    private final DocumentStorageProxyService documentStorageProxy;
    private final LoanDurableOrchestrator durableOrchestrator;
    private final AzureEventBusPublisherService eventBusPublisher;

    public LoanApplicationService(LoanApplicationRepository applicationRepository,
                                  LoanSchemeRepository schemeRepository,
                                  LoanAuditLogRepository auditLogRepository,
                                  LoanDocumentRepository loanDocumentRepository,
                                  CustomerRepository customerRepository,
                                  EMICalculatorProxyService emiCalculatorProxy,
                                  DocumentStorageProxyService documentStorageProxy,
                                  LoanDurableOrchestrator durableOrchestrator,
                                  AzureEventBusPublisherService eventBusPublisher) {
        this.applicationRepository = applicationRepository;
        this.schemeRepository = schemeRepository;
        this.auditLogRepository = auditLogRepository;
        this.loanDocumentRepository = loanDocumentRepository;
        this.customerRepository = customerRepository;
        this.emiCalculatorProxy = emiCalculatorProxy;
        this.documentStorageProxy = documentStorageProxy;
        this.durableOrchestrator = durableOrchestrator;
        this.eventBusPublisher = eventBusPublisher;
    }

    @Transactional
    public LoanApplicationResponse applyForLoan(LoanApplicationRequest request) {
        LoanScheme scheme = schemeRepository.findById(request.schemeId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid Scheme ID: " + request.schemeId()));

        String applicationId = "APP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // 1. Calculate EMI via Azure Function Proxy
        BigDecimal emi = emiCalculatorProxy.computeMonthlyEMI(
                request.loanAmount(),
                scheme.getBaseInterestRate(),
                request.tenureMonths()
        );

        // 2. Persist Customer into Customers table
        Customer customer = null;
        if (request.customerEmail() != null && !request.customerEmail().isBlank()) {
            customer = customerRepository.findByEmail(request.customerEmail()).orElse(null);
        }
        if (customer == null && request.customerId() != null && !request.customerId().isBlank()) {
            try {
                String rawId = request.customerId().replace("CUST-", "");
                customer = customerRepository.findById(Long.parseLong(rawId)).orElse(null);
            } catch (Exception ignored) {}
        }
        if (customer == null) {
            customer = new Customer(
                    request.customerName(),
                    request.customerEmail(),
                    request.customerPhone(),
                    request.monthlyIncome(),
                    request.employmentType() != null ? request.employmentType().name() : "SALARIED"
            );
            customer = customerRepository.save(customer);
        } else {
            customer.setFullName(request.customerName());
            customer.setMobileNumber(request.customerPhone());
            customer.setIncomeDetails(request.monthlyIncome());
            if (request.employmentType() != null) {
                customer.setEmploymentDetails(request.employmentType().name());
            }
            customer = customerRepository.save(customer);
        }

        String assignedCustomerId = "CUST-" + customer.getCustomerId();

        // 3. Initialize Application Entity
        LoanApplication app = new LoanApplication();
        app.setApplicationId(applicationId);
        app.setCustomerId(assignedCustomerId);
        app.setCustomerName(customer.getFullName());
        app.setCustomerEmail(customer.getEmail());
        app.setCustomerPhone(customer.getMobileNumber());
        app.setMonthlyIncome(request.monthlyIncome());
        app.setExistingLiabilities(request.existingLiabilities() != null ? request.existingLiabilities() : BigDecimal.ZERO);
        app.setEmploymentType(request.employmentType());
        app.setScheme(scheme);
        app.setLoanType(scheme.getLoanType());
        app.setLoanAmount(request.loanAmount());
        app.setTenureMonths(request.tenureMonths());
        app.setInterestRate(scheme.getBaseInterestRate());
        app.setCalculatedEMI(emi);
        app.setStatus(LoanStatus.SUBMITTED);

        boolean hasDocs = request.documentIds() != null && !request.documentIds().isEmpty();
        app.setDocumentProvided(hasDocs);

        LoanApplication savedApp = applicationRepository.save(app);
        recordAuditLog(applicationId, null, LoanStatus.SUBMITTED, "APPLICANT", "Initial loan application submitted.");

        // 3. Link uploaded documents
        if (hasDocs) {
            documentStorageProxy.linkDocumentsToApplication(request.documentIds(), applicationId);
        }

        // 4. Publish initial Application Submitted Event
        eventBusPublisher.publishLoanSubmittedEvent(savedApp);

        // 5. Trigger Azure Durable Function Orchestrator
        String callbackUrl = callbackUrlFor(applicationId);
        durableOrchestrator.runOrchestrationWorkflow(savedApp, callbackUrl);

        // 6. Persist State Changes & Record Audit Logs
        LoanApplication updatedApp = applicationRepository.save(savedApp);
        recordAuditLog(applicationId, LoanStatus.SUBMITTED, updatedApp.getStatus(),
                "DURABLE_ORCHESTRATOR", updatedApp.getDecisionRemarks());

        // 7. Publish Status Event to Azure Service Bus (Approved, Rejected, Manual Review, Doc Pending)
        if (updatedApp.getStatus() == LoanStatus.APPROVED || updatedApp.getStatus() == LoanStatus.REJECTED) {
            eventBusPublisher.publishLoanCompletedEvent(updatedApp);
        } else if (updatedApp.getStatus() == LoanStatus.MANUAL_REVIEW_REQUIRED) {
            eventBusPublisher.publishLoanStatusEvent(updatedApp, "LOAN_MANUAL_REVIEW_REQUIRED", updatedApp.getDecisionRemarks());
        } else if (updatedApp.getStatus() == LoanStatus.DOCUMENT_REVIEW_PENDING) {
            eventBusPublisher.publishLoanStatusEvent(updatedApp, "LOAN_DOCUMENT_REVIEW_PENDING", updatedApp.getDecisionRemarks());
        }

        return mapToResponse(updatedApp);
    }

    @Transactional
    public LoanApplicationResponse processManagerDecision(String applicationId, ManagerDecisionRequest request) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Loan application not found with ID: " + applicationId));

        if (app.getStatus() != LoanStatus.MANUAL_REVIEW_REQUIRED && app.getStatus() != LoanStatus.DOCUMENT_REVIEW_PENDING) {
            throw new IllegalStateException("Application is not in a reviewable status. Current status: " + app.getStatus());
        }

        if (request.decision() == com.bank.digital.lending.model.enums.ApprovalDecision.APPROVE && !app.isDocumentProvided()) {
            throw new IllegalStateException("Cannot approve application: Mandatory KYC/Income verification documents must be provided first.");
        }

        LoanStatus previousStatus = app.getStatus();

        // Resume Durable Orchestration with Manager Decision
        durableOrchestrator.processManagerApprovalEvent(app, request.decision(), request.remarks(), request.managerId());

        LoanApplication finalizedApp = applicationRepository.save(app);
        recordAuditLog(applicationId, previousStatus, finalizedApp.getStatus(),
                "MANAGER:" + request.managerId(), request.remarks());

        // Publish Completion Event to Azure Service Bus
        eventBusPublisher.publishLoanCompletedEvent(finalizedApp);

        return mapToResponse(finalizedApp);
    }

    @Transactional(readOnly = true)
    public Optional<LoanApplicationResponse> getApplicationById(String applicationId) {
        return applicationRepository.findById(applicationId).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Optional<LoanStatusResponse> getApplicationStatus(String applicationId) {
        return applicationRepository.findById(applicationId).map(app -> {
            String stageDescription = switch (app.getStatus()) {
                case SUBMITTED -> "Application submitted and queued for processing.";
                case VALIDATING -> "Validating applicant demographic and eligibility details.";
                case CREDIT_ASSESSMENT -> "Performing automated risk assessment and credit scoring.";
                case MANUAL_REVIEW_REQUIRED -> "Under review by Senior Underwriting Manager.";
                case DOCUMENT_REVIEW_PENDING -> "Document review pending; awaiting manager decision.";
                case APPROVED -> "Loan approved! Ready for sanction letter generation and disbursement.";
                case REJECTED -> "Loan application rejected based on underwriting criteria.";
            };

            return new LoanStatusResponse(
                    app.getApplicationId(),
                    app.getCustomerName(),
                    app.getStatus(),
                    stageDescription,
                    app.getRiskScore(),
                    app.getDecisionRemarks(),
                    app.getUpdatedAt()
            );
        });
    }

    @Transactional(readOnly = true)
    public List<LoanApplicationResponse> listApplications(LoanStatus status) {
        List<LoanApplication> apps = (status != null) ?
                applicationRepository.findByStatus(status) :
                applicationRepository.findAllByOrderByCreatedAtDesc();

        return apps.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LoanAuditLogResponse> getAuditLogs(String applicationId) {
        return auditLogRepository.findByApplicationIdOrderByTimestampAsc(applicationId)
                .stream()
                .map(log -> new LoanAuditLogResponse(
                        log.getLogId(),
                        log.getApplicationId(),
                        log.getPreviousStatus(),
                        log.getNewStatus(),
                        log.getChangedBy(),
                        log.getComments(),
                        log.getTimestamp()
                ))
                .toList();
    }

    private void recordAuditLog(String applicationId, LoanStatus prev, LoanStatus next, String changedBy, String comments) {
        LoanAuditLog log = new LoanAuditLog(applicationId, prev, next, changedBy, comments);
        auditLogRepository.save(log);
    }

    private String callbackUrlFor(String applicationId) {
        String path = "/api/v1/loans/applications/" + applicationId + "/manager-callback";
        return callbackBaseUrl == null || callbackBaseUrl.isBlank()
                ? path
                : callbackBaseUrl.replaceAll("/+$", "") + path;
    }

    private LoanApplicationResponse mapToResponse(LoanApplication app) {
        List<DocumentUploadResponse> docs = documentStorageProxy.getDocumentsByApplicationId(app.getApplicationId());

        return new LoanApplicationResponse(
                app.getApplicationId(),
                app.getCustomerId(),
                app.getCustomerName(),
                app.getCustomerEmail(),
                app.getCustomerPhone(),
                app.getMonthlyIncome(),
                app.getExistingLiabilities(),
                app.getEmploymentType(),
                app.getScheme().getSchemeId(),
                app.getScheme().getSchemeName(),
                app.getLoanType(),
                app.getLoanAmount(),
                app.getTenureMonths(),
                app.getInterestRate(),
                app.getCalculatedEMI(),
                app.getStatus(),
                app.getRiskScore(),
                app.getDtiRatio(),
                app.getOrchestrationInstanceId(),
                app.getAssignedManager(),
                app.getDecisionRemarks(),
                app.getCreatedAt(),
                app.getUpdatedAt(),
                docs
        );
    }
    
    /**
     * Handles document uploaded callback (Level 1: Document Verification).
     *
     * Persists a row in LOAN_DOCUMENTS for each uploaded document (blob URL, type, size, etc.)
     * and transitions the loan application status:
     *  - Low Risk (Score ≤ 30):  DOCUMENT_REVIEW_PENDING → APPROVED (Auto-Approved by Credit Engine)
     *  - Medium Risk (31–69):    DOCUMENT_REVIEW_PENDING → MANUAL_REVIEW_REQUIRED (Level 2: Underwriter review)
     *  - High Risk (≥ 70):       Already REJECTED — document upload has no effect
     */
    @Transactional
    public LoanApplicationResponse handleDocumentUploaded(String applicationId, DocumentUploadedRequest request) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Loan application not found with ID: " + applicationId));

        // ── 1. Persist document metadata in LOAN_DOCUMENTS table ──────────────────────────
        String docId = "DOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DocType docType = request.resolvedDocType();
        String blobUrl   = request.blobUrl()   != null ? request.blobUrl()   : "";
        String blobPath  = request.blobPath()  != null ? request.blobPath()  : blobUrl;
        String fileName  = request.documentName() != null ? request.documentName() : docType.name() + ".pdf";
        String mime      = request.contentType() != null ? request.contentType() : "application/pdf";
        long   fileSize  = request.fileSizeBytes() != null ? request.fileSizeBytes() : 0L;
        String customerId = request.customerId() != null ? request.customerId() : app.getCustomerId();

        // Only save if we have a real blob URL (i.e., the upload actually went to Azure)
        if (!blobUrl.isBlank()) {
            LoanDocument loanDoc = new LoanDocument(
                    docId,
                    applicationId,
                    customerId,
                    docType,
                    fileName,
                    mime,
                    blobPath.isBlank() ? blobUrl : blobPath,
                    fileSize
            );
            loanDocumentRepository.save(loanDoc);
            log.info("[LOAN-SERVICE] ✅ LOAN_DOCUMENTS persisted: docId={}, appId={}, type={}, blobUrl={}",
                    docId, applicationId, docType, blobUrl);
        }

        // ── 2. Link legacy doc IDs (backward compat for pre-document-service uploads) ─────
        if (request.documentIds() != null && !request.documentIds().isEmpty()) {
            documentStorageProxy.linkDocumentsToApplication(request.documentIds(), applicationId);
        }
        app.setDocumentProvided(true);

        LoanStatus previousStatus = app.getStatus();

        // ── 3. Advance workflow based on risk score ────────────────────────────────────────
        if (app.getStatus() == LoanStatus.DOCUMENT_REVIEW_PENDING) {
            int score = app.getRiskScore() != null ? app.getRiskScore() : 50;
            String dti  = app.getDtiRatio() != null ? app.getDtiRatio().toString() : "—";

            if (score <= 30) {
                // ── BRANCH A: Low Risk → Level 1 complete → Auto-Approved ────────────────────────
                app.setStatus(LoanStatus.APPROVED);
                app.setDecisionRemarks(
                        "✅ Document Review Passed (Level 1 of 1). "
                        + "Auto-Approved by Credit Engine: KYC and income verification documents received and verified. "
                        + "Low risk profile confirmed (Score: " + score + "/100, DTI: " + dti + "%). "
                        + "No further review required.");
                recordAuditLog(applicationId, previousStatus, LoanStatus.APPROVED,
                        "SYSTEM_CREDIT_ENGINE",
                        "Level 1 (Document Review) passed. Low-risk auto-approval triggered.");
                eventBusPublisher.publishLoanCompletedEvent(app);

            } else {
                // ── BRANCH B: Medium Risk → Level 1 complete → Escalate to Level 2 ───────────────
                app.setStatus(LoanStatus.MANUAL_REVIEW_REQUIRED);
                app.setDecisionRemarks(
                        "✅ Document Review Passed (Level 1 of 2). "
                        + "KYC and income verification documents received and verified (Score: " + score + "/100, DTI: " + dti + "%). "
                        + "Now awaiting Level 2: Operations Manager must review loan amount, tenure, and overall risk profile before final decision.");
                recordAuditLog(applicationId, previousStatus, LoanStatus.MANUAL_REVIEW_REQUIRED,
                        "DOC_VERIFICATION_SERVICE",
                        "Level 1 (Document Review) passed. Escalated to Level 2 (Underwriter Review).");
                String callbackUrl = callbackUrlFor(applicationId);
                durableOrchestrator.triggerManagerReviewWorkflow(app, callbackUrl);
                eventBusPublisher.publishLoanStatusEvent(app, "LOAN_MANUAL_REVIEW_REQUIRED", app.getDecisionRemarks());
            }
        } else {
            // Additional documents on an already-processed application
            recordAuditLog(applicationId, app.getStatus(), app.getStatus(),
                    "DOC_UPLOAD_SERVICE", "Supplementary documents uploaded and linked.");
        }

        LoanApplication saved = applicationRepository.save(app);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<com.bank.digital.lending.model.dto.CustomerResponse> listCustomers() {
        return customerRepository.findAll().stream()
                .map(c -> new com.bank.digital.lending.model.dto.CustomerResponse(
                        c.getCustomerId(),
                        "CUST-" + c.getCustomerId(),
                        c.getFullName(),
                        c.getEmail(),
                        c.getMobileNumber(),
                        c.getAddress(),
                        c.getEmploymentDetails(),
                        c.getIncomeDetails(),
                        c.getOnboardingStatus(),
                        c.getCreatedAt()
                ))
                .toList();
    }
}

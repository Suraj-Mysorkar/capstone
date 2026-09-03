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
    private final NotificationService notificationService;

    public LoanApplicationService(LoanApplicationRepository applicationRepository,
                                  LoanSchemeRepository schemeRepository,
                                  LoanAuditLogRepository auditLogRepository,
                                  LoanDocumentRepository loanDocumentRepository,
                                  CustomerRepository customerRepository,
                                  EMICalculatorProxyService emiCalculatorProxy,
                                  DocumentStorageProxyService documentStorageProxy,
                                  LoanDurableOrchestrator durableOrchestrator,
                                  AzureEventBusPublisherService eventBusPublisher,
                                  NotificationService notificationService) {
        this.applicationRepository = applicationRepository;
        this.schemeRepository = schemeRepository;
        this.auditLogRepository = auditLogRepository;
        this.loanDocumentRepository = loanDocumentRepository;
        this.customerRepository = customerRepository;
        this.emiCalculatorProxy = emiCalculatorProxy;
        this.documentStorageProxy = documentStorageProxy;
        this.durableOrchestrator = durableOrchestrator;
        this.eventBusPublisher = eventBusPublisher;
        this.notificationService = notificationService;
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
            customer = customerRepository.findByEmail(request.customerEmail().trim()).orElse(null);
        }
        if (customer == null && request.customerId() != null && !request.customerId().isBlank()
                && !request.customerId().equalsIgnoreCase("CUST-1") && !request.customerId().equalsIgnoreCase("CUST-null")) {
            try {
                String rawId = request.customerId().replace("CUST-", "").trim();
                customer = customerRepository.findById(Long.parseLong(rawId)).orElse(null);
            } catch (Exception ignored) {}
        }
        if (customer == null) {
            Customer newCust = new Customer(
                    request.customerName() != null ? request.customerName() : "Applicant",
                    request.customerEmail() != null ? request.customerEmail().trim() : "customer@bank.com",
                    (request.customerPhone() != null && !request.customerPhone().isBlank()) ? request.customerPhone().trim() : "N/A",
                    request.monthlyIncome(),
                    request.employmentType() != null ? request.employmentType().name() : "SALARIED"
            );
            newCust.setLoginId("u" + Long.toString(System.currentTimeMillis() % 1_000_000_000L, 36));
            newCust.setLoginPassword("Password@123");
            try {
                customer = customerRepository.save(newCust);
            } catch (Exception ex) {
                log.warn("[LOAN-SERVICE] Customer save warning: {}. Retrying with fresh instance.", ex.getMessage());
                newCust.setLoginId("u" + (int)(Math.random() * 900000 + 100000));
                customer = customerRepository.save(newCust);
            }
        } else {
            if (request.customerName() != null && !request.customerName().isBlank()) {
                customer.setFullName(request.customerName().trim());
            }
            if (request.customerPhone() != null && !request.customerPhone().isBlank()) {
                customer.setMobileNumber(request.customerPhone().trim());
            }
            if (request.monthlyIncome() != null) {
                customer.setIncomeDetails(request.monthlyIncome());
            }
            if (request.employmentType() != null) {
                customer.setEmploymentDetails(request.employmentType().name());
            }
            try {
                customer = customerRepository.save(customer);
            } catch (Exception ignored) {}
        }

        String assignedCustomerId = (request.customerId() != null && !request.customerId().isBlank()
                && !request.customerId().equalsIgnoreCase("CUST-1") && !request.customerId().equalsIgnoreCase("CUST-null"))
                ? request.customerId().trim()
                : (customer != null && customer.getCustomerId() != null
                        ? "CUST-" + customer.getCustomerId()
                        : "CUST-" + (1000 + (System.currentTimeMillis() % 9000)));

        // 3. Initialize Application Entity
        LoanApplication app = new LoanApplication();
        app.setApplicationId(applicationId);
        app.setCustomerId(assignedCustomerId);
        app.setCustomerName(request.customerName() != null ? request.customerName() : customer.getFullName());
        app.setCustomerEmail(request.customerEmail() != null ? request.customerEmail() : customer.getEmail());
        app.setCustomerPhone(request.customerPhone() != null ? request.customerPhone() : customer.getMobileNumber());
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

        // Load-balanced manager assignment across available managers pool
        ManagerInfo chosenManager = selectLeastLoadedManager();
        app.setAssignedManager(chosenManager.loginId());

        boolean hasDocs = request.documentIds() != null && !request.documentIds().isEmpty();
        app.setDocumentProvided(hasDocs);

        LoanApplication savedApp = applicationRepository.save(app);
        recordAuditLog(applicationId, null, LoanStatus.SUBMITTED, "APPLICANT",
                "Initial loan application submitted. Assigned to " + chosenManager.name() + " (" + chosenManager.loginId() + ").");

        // Dispatch Real-time Notification to Assigned Employee
        notificationService.sendNotification(new NotificationDTO(
                chosenManager.loginId(),
                "New Case Assigned: " + app.getCustomerName(),
                "New loan application " + applicationId + " submitted by " + app.getCustomerName() + " (" + app.getCustomerId() + ") for ₹" + app.getLoanAmount() + ".",
                "NEW_CASE_ASSIGNED",
                app.getCustomerId(),
                app.getCustomerName(),
                applicationId
        ));

        // Dispatch instant confirmation email with manager contact details to Customer
        dispatchLoanApplicationReceivedEmail(savedApp, chosenManager);

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

        if (app.getStatus() != LoanStatus.MANUAL_REVIEW_REQUIRED && app.getStatus() != LoanStatus.DOCUMENT_REVIEW_PENDING && app.getStatus() != LoanStatus.DOCUMENTS_SUBMITTED) {
            throw new IllegalStateException(
                    "Cannot process decision for application in status: " + app.getStatus() +
                            ". Decisions can only be made on applications in MANUAL_REVIEW_REQUIRED, DOCUMENT_REVIEW_PENDING, or DOCUMENTS_SUBMITTED status.");
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

        // Dispatch Real-time Notification
        notificationService.sendNotification(new NotificationDTO(
                "markj",
                "Decision Processed: " + applicationId,
                "Application " + applicationId + " for " + finalizedApp.getCustomerName() + " was marked " + request.decision() + " by " + request.managerId() + ".",
                "DECISION_RECORDED",
                finalizedApp.getCustomerId(),
                finalizedApp.getCustomerName(),
                applicationId
        ));

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
                case DOCUMENT_REVIEW_PENDING -> "Document review pending; awaiting document submission or manager review.";
                case DOCUMENTS_SUBMITTED -> "All required verification documents submitted. Awaiting manager review.";
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

    public record ManagerInfo(String loginId, String name, String email, String phone) {}

    public static final List<ManagerInfo> MANAGERS_POOL = List.of(
            new ManagerInfo("mgr.arjun", "Arjun Rao", "arjun.rao@bank.example.com", "+91 98765 43211"),
            new ManagerInfo("mgr.meera", "Meera Iyer", "meera.iyer@bank.example.com", "+91 98765 43212"),
            new ManagerInfo("mgr.karan", "Karan Malhotra", "karan.malhotra@bank.example.com", "+91 98765 43213"),
            new ManagerInfo("mgr.divya", "Divya Nair", "divya.nair@bank.example.com", "+91 98765 43214"),
            new ManagerInfo("markj", "Mark Johnson", "mark.johnson@bank.com", "+1 (555) 019-2834")
    );

    public static ManagerInfo getManagerInfo(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return MANAGERS_POOL.get(0);
        }
        return MANAGERS_POOL.stream()
                .filter(m -> m.loginId().equalsIgnoreCase(loginId.trim()))
                .findFirst()
                .orElse(new ManagerInfo(loginId, loginId, loginId + "@bank.com", "+1 (555) 019-2834"));
    }

    private ManagerInfo selectLeastLoadedManager() {
        try {
            java.util.Map<String, Long> activeCounts = new java.util.HashMap<>();
            for (ManagerInfo m : MANAGERS_POOL) {
                activeCounts.put(m.loginId().toLowerCase(), 0L);
            }
            List<LoanApplication> activeApps = applicationRepository.findByStatusIn(List.of(
                    LoanStatus.SUBMITTED,
                    LoanStatus.DOCUMENT_REVIEW_PENDING,
                    LoanStatus.MANUAL_REVIEW_REQUIRED,
                    LoanStatus.CREDIT_ASSESSMENT
            ));
            for (LoanApplication a : activeApps) {
                if (a.getAssignedManager() != null) {
                    String mgr = a.getAssignedManager().toLowerCase();
                    activeCounts.put(mgr, activeCounts.getOrDefault(mgr, 0L) + 1);
                }
            }
            return MANAGERS_POOL.stream()
                    .min(java.util.Comparator.comparingLong(m -> activeCounts.getOrDefault(m.loginId().toLowerCase(), 0L)))
                    .orElse(MANAGERS_POOL.get(0));
        } catch (Exception ex) {
            log.warn("Manager load balancing exception: {}. Defaulting to first manager.", ex.getMessage());
            return MANAGERS_POOL.get(0);
        }
    }

    private LoanApplicationResponse mapToResponse(LoanApplication app) {
        List<DocumentUploadResponse> docs = documentStorageProxy.getDocumentsByApplicationId(app.getApplicationId());
        ManagerInfo mgr = getManagerInfo(app.getAssignedManager());
        List<String> requestedDocs = List.of(
                "Identity Proof (Aadhaar Card / Passport / Voter ID)",
                "Income Verification (Salary Slips for Last 3 Months or Latest Form 16)",
                "Bank Account Statement (Operational Account Statement for Last 6 Months)"
        );

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
                mgr.name(),
                mgr.email(),
                mgr.phone(),
                app.getDecisionRemarks(),
                app.getCreatedAt(),
                app.getUpdatedAt(),
                docs,
                requestedDocs
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

        // ── 3. Check if ALL required documents have been uploaded ────────────────
        List<LoanDocument> appDocs = loanDocumentRepository.findByApplicationId(applicationId);
        if ((appDocs == null || appDocs.isEmpty()) && customerId != null) {
            appDocs = loanDocumentRepository.findByCustomerId(customerId);
        }
        int totalUploaded = appDocs != null ? appDocs.size() : 1;
        int requiredDocCount = 3; // Mandatory 3 documents: Identity Proof, Income Verification, Bank Statement

        int score = app.getRiskScore() != null ? app.getRiskScore() : 50;
        String dti  = app.getDtiRatio() != null ? app.getDtiRatio().toString() : "—";
        String manager = app.getAssignedManager() != null ? app.getAssignedManager() : "markj";

        if (totalUploaded >= requiredDocCount) {
            app.setDocumentProvided(true);
            app.setStatus(LoanStatus.DOCUMENTS_SUBMITTED);
            app.setDecisionRemarks(
                    "📄 All required verification documents (" + totalUploaded + "/" + requiredDocCount + ") submitted by applicant (" + fileName + ") (Risk Score: " + score + "/100, DTI: " + dti + "%). "
                    + "Awaiting Credit Manager (" + manager + ") document verification and review.");
            recordAuditLog(applicationId, previousStatus, LoanStatus.DOCUMENTS_SUBMITTED,
                    "DOC_UPLOAD_SERVICE",
                    "All required documents received (" + totalUploaded + "/" + requiredDocCount + "). Application advanced to DOCUMENTS_SUBMITTED queue.");
            eventBusPublisher.publishLoanStatusEvent(app, "LOAN_DOCUMENTS_SUBMITTED", app.getDecisionRemarks());
        } else {
            // Under mandatory requirement: keep status as DOCUMENT_REVIEW_PENDING
            app.setStatus(LoanStatus.DOCUMENT_REVIEW_PENDING);
            app.setDecisionRemarks(
                    "⏳ Incomplete documents: Applicant submitted " + totalUploaded + " of " + requiredDocCount + " required documents (" + fileName + ", " + docType.name() + "). "
                    + "Application remains in DOCUMENT_REVIEW_PENDING until all required documents are received.");
            recordAuditLog(applicationId, previousStatus, LoanStatus.DOCUMENT_REVIEW_PENDING,
                    "DOC_UPLOAD_SERVICE",
                    "Document uploaded (" + fileName + "). Status remains DOCUMENT_REVIEW_PENDING (" + totalUploaded + "/" + requiredDocCount + " received).");
            eventBusPublisher.publishLoanStatusEvent(app, "LOAN_DOCUMENT_REVIEW_PENDING", app.getDecisionRemarks());
        }

        LoanApplication saved = applicationRepository.save(app);

        // Dispatch Real-time Notification for Document Upload to Employee manager
        String targetManager = saved.getAssignedManager() != null ? saved.getAssignedManager() : "markj";
        notificationService.sendNotification(new NotificationDTO(
                targetManager,
                "Documents Submitted: " + saved.getCustomerName(),
                "Customer " + saved.getCustomerName() + " (" + saved.getCustomerId() + ") has submitted " + fileName + " (" + docType.name() + "). Ready for Document Review.",
                "DOCUMENT_UPLOADED",
                saved.getCustomerId(),
                saved.getCustomerName(),
                applicationId
        ));

        return mapToResponse(saved);
    }

    /**
     * Handles document review callback (when Manager Approves or Rejects a document).
     * If all documents are verified:
     *   - Low Risk (Score ≤ 30): DOCUMENT_REVIEW_PENDING / DOCUMENTS_SUBMITTED → APPROVED (Auto-Approved by Credit Engine)
     *   - Medium Risk (31–69):   DOCUMENT_REVIEW_PENDING / DOCUMENTS_SUBMITTED → MANUAL_REVIEW_REQUIRED (Underwriter Review)
     * If document is rejected:
     *   - Transitions to DOCUMENT_REVIEW_PENDING (Action Required: Re-upload needed).
     */
    @Transactional
    public LoanApplicationResponse handleDocumentReviewed(String applicationId,
                                                          com.bank.digital.lending.model.dto.DocumentReviewedRequest request) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Loan application not found with ID: " + applicationId));

        String status = request.status() != null ? request.status().toUpperCase() : "VERIFIED";
        String docId = request.documentId() != null ? request.documentId() : "DOC";
        String docType = request.documentType() != null ? request.documentType() : "Document";
        String remarks = request.remarks() != null ? request.remarks() : "";
        String reviewer = request.verifiedBy() != null ? request.verifiedBy() : (app.getAssignedManager() != null ? app.getAssignedManager() : "Operations Manager");

        LoanStatus previousStatus = app.getStatus();

        if ("VERIFIED".equalsIgnoreCase(status) || "APPROVED".equalsIgnoreCase(status)) {
            // Level 1: Document Review Passed!
            int score = app.getRiskScore() != null ? app.getRiskScore() : 30;
            if (score <= 30) {
                // Low Risk -> Auto Approved after Document Verification
                app.setStatus(LoanStatus.APPROVED);
                app.setDecisionRemarks("✅ Level 1 (Document Review) passed: Document " + docId + " (" + docType + ") verified by " + reviewer + ". Low-risk application auto-approved.");
                recordAuditLog(applicationId, previousStatus, LoanStatus.APPROVED, "SYSTEM_CREDIT_ENGINE", app.getDecisionRemarks());
                eventBusPublisher.publishLoanStatusEvent(app, "LOAN_APPROVED", app.getDecisionRemarks());
            } else {
                // Moderate Risk -> Route to Manager Underwriter Review
                app.setStatus(LoanStatus.MANUAL_REVIEW_REQUIRED);
                app.setDecisionRemarks("✅ Level 1 (Document Review) passed: Document " + docId + " (" + docType + ") verified by " + reviewer + ". Application routed to Underwriter (" + app.getAssignedManager() + ") for final loan decision.");
                recordAuditLog(applicationId, previousStatus, LoanStatus.MANUAL_REVIEW_REQUIRED, "DOC_REVIEW_SERVICE", app.getDecisionRemarks());
                eventBusPublisher.publishLoanStatusEvent(app, "LOAN_MANUAL_REVIEW_REQUIRED", app.getDecisionRemarks());
            }
        } else if ("REJECTED".equalsIgnoreCase(status) || "ACTION_REQUIRED".equalsIgnoreCase(status)) {
            // Document Rejected -> Move back to DOCUMENT_REVIEW_PENDING (Action Required)
            app.setStatus(LoanStatus.DOCUMENT_REVIEW_PENDING);
            app.setDecisionRemarks("❌ Document Action Required: " + docType + " (" + docId + ") was rejected by " + reviewer + ". Reason: " + remarks + ". Awaiting new document upload from applicant.");
            recordAuditLog(applicationId, previousStatus, LoanStatus.DOCUMENT_REVIEW_PENDING, "DOC_REVIEW_SERVICE", app.getDecisionRemarks());
            eventBusPublisher.publishLoanStatusEvent(app, "LOAN_DOCUMENT_REVIEW_PENDING", app.getDecisionRemarks());
        }

        LoanApplication saved = applicationRepository.save(app);

        // Notify manager & customer
        String targetManager = saved.getAssignedManager() != null ? saved.getAssignedManager() : "markj";
        notificationService.sendNotification(new NotificationDTO(
                targetManager,
                "Document Review: " + docType + " (" + status + ")",
                "Application " + applicationId + " document " + docId + " review was marked " + status + " by " + reviewer + ".",
                "DOCUMENT_REVIEW",
                saved.getCustomerId(),
                saved.getCustomerName(),
                applicationId
        ));

        return mapToResponse(saved);
    }

    /**
     * Creates or updates (upsert keyed by email) a row in the shared Customers
     * table, without a loan application. Lets the customer self-service portal
     * make a freshly-registered customer visible to the loan officer console.
     */
    @Transactional
    public com.bank.digital.lending.model.dto.CustomerResponse registerOrUpdateCustomer(
            com.bank.digital.lending.model.dto.CustomerRegistrationRequest request) {

        Customer customer = customerRepository.findByEmail(request.email()).orElse(null);
        if (customer == null) {
            customer = new Customer(
                    request.fullName(),
                    request.email(),
                    (request.mobileNumber() != null && !request.mobileNumber().isBlank())
                            ? request.mobileNumber() : "N/A",
                    request.incomeDetails(),
                    (request.employmentDetails() != null && !request.employmentDetails().isBlank())
                            ? request.employmentDetails() : "SALARIED"
            );
        } else {
            if (request.fullName() != null && !request.fullName().isBlank()) {
                customer.setFullName(request.fullName());
            }
            if (request.mobileNumber() != null && !request.mobileNumber().isBlank()) {
                customer.setMobileNumber(request.mobileNumber());
            }
            if (request.incomeDetails() != null) {
                customer.setIncomeDetails(request.incomeDetails());
            }
            if (request.employmentDetails() != null && !request.employmentDetails().isBlank()) {
                customer.setEmploymentDetails(request.employmentDetails());
            }
        }
        if (request.address() != null && !request.address().isBlank()) {
            customer.setAddress(request.address());
        }
        if (request.onboardingStatus() != null && !request.onboardingStatus().isBlank()) {
            customer.setOnboardingStatus(request.onboardingStatus());
        }
        // The shared Customers.loginid column is narrow (varchar(20)); the entity
        // default copies the full email in and overflows it. Portal customers
        // authenticate via customer-service, not here, so use a short token.
        if (customer.getCustomerId() == null) {
            customer.setLoginId("u" + Long.toString(System.currentTimeMillis() % 1_000_000_000L, 36));
        }

        Customer saved = customerRepository.save(customer);
        log.info("[LOAN-SERVICE] Customer upserted from portal registration: id={}, email={}, externalRef={}",
                saved.getCustomerId(), saved.getEmail(), request.externalRef());

        // Dispatch welcome registration email to customer
        if (saved.getEmail() != null && !saved.getEmail().isBlank()) {
            String welcomeSubject = "Welcome to Digital Banking - Account Registered";
            String welcomeHtml = String.format(
                    "<html><body style=\"font-family: Arial, sans-serif; font-size: 14px; color: #333333; line-height: 1.6;\">"
                    + "<p>Dear %s,</p>"
                    + "<p>Welcome to Digital Banking! Your customer account (Customer ID: <strong>CUST-%d</strong>) has been successfully created.</p>"
                    + "<p>You can now apply for flexible loans, calculate EMIs, and upload documents directly through the customer portal.</p>"
                    + "<p>Kind regards,<br><strong>Digital Banking Lending Team</strong></p>"
                    + "</body></html>",
                    saved.getFullName() != null ? saved.getFullName() : "Customer",
                    saved.getCustomerId()
            );
            dispatchEmailViaLogicApp(saved.getEmail(), welcomeSubject, welcomeHtml);
        }

        return new com.bank.digital.lending.model.dto.CustomerResponse(
                saved.getCustomerId(),
                "CUST-" + saved.getCustomerId(),
                saved.getFullName(),
                saved.getEmail(),
                saved.getMobileNumber(),
                saved.getAddress(),
                saved.getEmploymentDetails(),
                saved.getIncomeDetails(),
                saved.getOnboardingStatus(),
                saved.getCreatedAt()
        );
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

    /**
     * Dispatches an email to the customer with the list of mandatory verification documents required for loan processing.
     */
    @Transactional
    public DocumentRequestEmailResponse sendDocumentRequestEmail(String applicationId, DocumentRequestEmailRequest request) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Loan application not found with ID: " + applicationId));

        String customerEmail = app.getCustomerEmail();
        if (customerEmail == null || customerEmail.isBlank()) {
            throw new IllegalStateException("Customer email is missing for application " + applicationId);
        }

        List<String> docList = (request != null && request.requiredDocumentTypes() != null && !request.requiredDocumentTypes().isEmpty())
                ? request.requiredDocumentTypes()
                : List.of(
                    "Government Photo Identity Proof (PAN Card - Mandatory)",
                    "Address Proof (Aadhaar Card / Passport / Recent Utility Bill)",
                    "Income Verification (Salary Slips for Last 3 Months or Latest Form 16 / ITR)",
                    "Banking Statement (Operational Bank Account Statement for Last 6 Months)"
                );

        StringBuilder docHtmlList = new StringBuilder();
        for (String doc : docList) {
            docHtmlList.append("<li style=\"margin-bottom: 8px; font-weight: 600; color: #1e293b;\">").append(doc).append("</li>");
        }

        String notes = (request != null && request.customNotes() != null && !request.customNotes().isBlank())
                ? "<p style=\"background: #f1f5f9; padding: 12px; border-radius: 6px;\"><strong>Underwriter Notes:</strong> " + request.customNotes() + "</p>"
                : "";

        String subject = "Action Required: Verification Documents Needed for Loan Application " + app.getApplicationId();
        String body = String.format(
                "<html><body style=\"font-family: Arial, sans-serif; font-size: 14px; color: #333333; line-height: 1.6;\">"
                + "<p>Dear %s,</p>"
                + "<p>Thank you for choosing Digital Lending. We are currently processing your <strong>%s</strong> application (ID: <strong>%s</strong>) for <strong>₹%s</strong>.</p>"
                + "<p>To proceed with underwriting verification and loan sanctioning, please upload the following mandatory verification documents:</p>"
                + "<ul style=\"background: #f8fafc; padding: 16px 32px; border-left: 4px solid #00d2ff; border-radius: 6px;\">%s</ul>"
                + "%s"
                + "<p>Please log in to the Digital Banking portal to upload these documents or reply to this email.</p>"
                + "<p>If you have any questions, please contact our Customer Support team.</p>"
                + "<p>Kind regards,<br><strong>Digital Lending Underwriting & Operations Team</strong></p>"
                + "</body></html>",
                app.getCustomerName() != null ? app.getCustomerName() : "Valued Customer",
                app.getLoanType() != null ? app.getLoanType().name() : "Loan",
                app.getApplicationId(),
                app.getLoanAmount() != null ? app.getLoanAmount().toString() : "0",
                docHtmlList.toString(),
                notes
        );

        // 1. Dispatch Email via Azure Logic Apps Webhook
        boolean emailSent = dispatchEmailViaLogicApp(customerEmail, subject, body);

        // 2. Publish status event to Azure Service Bus
        eventBusPublisher.publishLoanStatusEvent(app, "LOAN_DOCUMENT_REVIEW_PENDING",
                "Document requirement checklist email sent to applicant " + customerEmail);

        // 3. Dispatch Live Notification to Employee markj
        notificationService.sendNotification(new NotificationDTO(
                "markj",
                "Document Request Sent: " + app.getCustomerName(),
                "Document requirement checklist email successfully sent to " + app.getCustomerName() + " (" + customerEmail + ") for application " + applicationId + ".",
                "DOCUMENT_REQUEST_SENT",
                app.getCustomerId(),
                app.getCustomerName(),
                applicationId
        ));

        // 4. Record Audit Log
        recordAuditLog(applicationId, app.getStatus(), app.getStatus(),
                "MANAGER:markj", "Sent required documents checklist email to " + customerEmail);

        return new DocumentRequestEmailResponse(
                applicationId,
                app.getCustomerId(),
                app.getCustomerName(),
                customerEmail,
                docList,
                "Verification document request email sent successfully to " + customerEmail,
                emailSent
        );
    }

    private void dispatchLoanApplicationReceivedEmail(LoanApplication app, ManagerInfo mgr) {
        if (app.getCustomerEmail() == null || app.getCustomerEmail().isBlank()) return;
        String subject = "Loan Application Received (ID: " + app.getApplicationId() + ") - Assigned Manager: " + mgr.name();
        String body = String.format(
                "<html><body style=\"font-family: Arial, sans-serif; font-size: 14px; color: #333333; line-height: 1.6;\">"
                + "<p>Dear %s,</p>"
                + "<p>Thank you for submitting your loan application with Digital Banking. We are pleased to confirm that your application has been received and registered under Customer ID: <strong>%s</strong>.</p>"
                + "<h3>Application Details</h3>"
                + "<table style=\"border-collapse: collapse; width: 100%%; max-width: 600px; margin-bottom: 16px;\">"
                + "<tr><td style=\"padding: 8px; border: 1px solid #ddd; font-weight: bold;\">Application ID</td><td style=\"padding: 8px; border: 1px solid #ddd;\">%s</td></tr>"
                + "<tr><td style=\"padding: 8px; border: 1px solid #ddd; font-weight: bold;\">Loan Type</td><td style=\"padding: 8px; border: 1px solid #ddd;\">%s</td></tr>"
                + "<tr><td style=\"padding: 8px; border: 1px solid #ddd; font-weight: bold;\">Requested Amount</td><td style=\"padding: 8px; border: 1px solid #ddd;\">₹%s</td></tr>"
                + "<tr><td style=\"padding: 8px; border: 1px solid #ddd; font-weight: bold;\">Tenure</td><td style=\"padding: 8px; border: 1px solid #ddd;\">%s Months</td></tr>"
                + "</table>"
                + "<h3>Dedicated Loan Relationship Manager</h3>"
                + "<p>A dedicated Credit &amp; Relationship Manager has been assigned to assist and review your loan application:</p>"
                + "<table style=\"border-collapse: collapse; width: 100%%; max-width: 600px; margin-bottom: 16px; background: #f0fdf4;\">"
                + "<tr><td style=\"padding: 8px; border: 1px solid #bbf7d0; font-weight: bold;\">Manager Name</td><td style=\"padding: 8px; border: 1px solid #bbf7d0;\">%s (%s)</td></tr>"
                + "<tr><td style=\"padding: 8px; border: 1px solid #bbf7d0; font-weight: bold;\">Contact Mobile</td><td style=\"padding: 8px; border: 1px solid #bbf7d0;\">%s</td></tr>"
                + "<tr><td style=\"padding: 8px; border: 1px solid #bbf7d0; font-weight: bold;\">Official Email</td><td style=\"padding: 8px; border: 1px solid #bbf7d0;\">%s</td></tr>"
                + "</table>"
                + "<p>You can securely log in to the Customer Portal at any time to track your progress and upload any requested verification documents.</p>"
                + "<p>Kind regards,<br><strong>Digital Banking Lending Team</strong></p>"
                + "</body></html>",
                app.getCustomerName() != null ? app.getCustomerName() : "Applicant",
                app.getCustomerId() != null ? app.getCustomerId() : "N/A",
                app.getApplicationId(),
                app.getLoanType() != null ? app.getLoanType().name() : "Loan",
                app.getLoanAmount() != null ? app.getLoanAmount().toString() : "0",
                app.getTenureMonths() != null ? app.getTenureMonths().toString() : "24",
                mgr.name(),
                mgr.loginId(),
                mgr.phone(),
                mgr.email()
        );
        dispatchEmailViaLogicApp(app.getCustomerEmail(), subject, body);
    }

    private boolean dispatchEmailViaLogicApp(String to, String subject, String htmlBody) {
        String logicAppUrl = "https://prod-17.southindia.logic.azure.com:443/workflows/a4b29c1d5e814824900b41a17fa24844/triggers/When_a_HTTP_request_is_received/paths/invoke?api-version=2016-10-01&sp=%2Ftriggers%2FWhen_a_HTTP_request_is_received%2Frun&sv=1.0&sig=F--JabvW3Uwr-JsZU76HgaWWTcekahkC6HBwTEImtys";
        try {
            String payload = String.format("{\"emailTo\":\"%s\",\"emailSubject\":\"%s\",\"emailBody\":\"%s\"}",
                    to.replace("\"", "\\\""),
                    subject.replace("\"", "\\\""),
                    htmlBody.replace("\"", "\\\"").replace("\n", "").replace("\r", ""));

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(logicAppUrl))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload, java.nio.charset.StandardCharsets.UTF_8))
                    .build();

            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            log.info("[LOAN-SERVICE] Logic App email dispatch response code: {}", resp.statusCode());
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (Exception ex) {
            log.warn("[LOAN-SERVICE] Could not deliver email directly via Logic App: {}. Email logged for verification.", ex.getMessage());
            return false;
        }
    }
}

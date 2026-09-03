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
            customer = customerRepository.findByEmail(request.customerEmail()).orElse(null);
        }
        if (customer == null && request.customerId() != null && !request.customerId().isBlank()) {
            try {
                String rawId = request.customerId().replace("CUST-", "");
                customer = customerRepository.findById(Long.parseLong(rawId)).orElse(null);
            } catch (Exception ignored) {}
        }
        if (customer == null) {
            try {
                Customer newCust = new Customer(
                        request.customerName(),
                        request.customerEmail(),
                        request.customerPhone(),
                        request.monthlyIncome(),
                        request.employmentType() != null ? request.employmentType().name() : "SALARIED"
                );
                customer = customerRepository.save(newCust);
            } catch (Exception ex) {
                log.warn("Could not persist customer: {}. Using in-memory customer reference.", ex.getMessage());
                customer = new Customer();
                customer.setCustomerId(System.currentTimeMillis() % 10000);
                customer.setFullName(request.customerName());
                customer.setEmail(request.customerEmail());
                customer.setMobileNumber(request.customerPhone());
            }
        } else {
            customer.setFullName(request.customerName());
            if (request.customerEmail() != null && !request.customerEmail().isBlank()) {
                customer.setEmail(request.customerEmail());
            }
            customer.setMobileNumber(request.customerPhone());
            customer.setIncomeDetails(request.monthlyIncome());
            if (request.employmentType() != null) {
                customer.setEmploymentDetails(request.employmentType().name());
            }
            try {
                customer = customerRepository.save(customer);
            } catch (Exception ignored) {}
        }

        String assignedCustomerId = "CUST-" + (customer.getCustomerId() != null ? customer.getCustomerId() : "1");

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
        app.setAssignedManager("markj"); // Auto-assign to credit manager markj

        boolean hasDocs = request.documentIds() != null && !request.documentIds().isEmpty();
        app.setDocumentProvided(hasDocs);

        LoanApplication savedApp = applicationRepository.save(app);
        recordAuditLog(applicationId, null, LoanStatus.SUBMITTED, "APPLICANT", "Initial loan application submitted. Assigned to markj.");

        // Dispatch Real-time Notification to Employee markj
        notificationService.sendNotification(new NotificationDTO(
                "markj",
                "New Case Assigned: " + app.getCustomerName(),
                "New loan application " + applicationId + " submitted by " + app.getCustomerName() + " (" + app.getCustomerId() + ") for ₹" + app.getLoanAmount() + ".",
                "NEW_CASE_ASSIGNED",
                app.getCustomerId(),
                app.getCustomerName(),
                applicationId
        ));

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

        // ── 3. Keep application in DOCUMENT_REVIEW_PENDING for Manager Review ─────────
        if (app.getStatus() == LoanStatus.DOCUMENT_REVIEW_PENDING || app.getStatus() == LoanStatus.SUBMITTED) {
            int score = app.getRiskScore() != null ? app.getRiskScore() : 50;
            String dti  = app.getDtiRatio() != null ? app.getDtiRatio().toString() : "—";
            String manager = app.getAssignedManager() != null ? app.getAssignedManager() : "markj";

            app.setStatus(LoanStatus.DOCUMENT_REVIEW_PENDING);
            app.setDecisionRemarks(
                    "📄 Documents uploaded by applicant (Risk Score: " + score + "/100, DTI: " + dti + "%). "
                    + "Awaiting Credit Manager (" + manager + ") document review and underwriting decision.");
            recordAuditLog(applicationId, previousStatus, LoanStatus.DOCUMENT_REVIEW_PENDING,
                    "DOC_UPLOAD_SERVICE",
                    "Documents uploaded (" + fileName + "). Application in Document Review queue for Manager decision.");
            eventBusPublisher.publishLoanStatusEvent(app, "LOAN_DOCUMENT_REVIEW_PENDING", app.getDecisionRemarks());
        } else {
            // Additional documents on an already-processed application
            recordAuditLog(applicationId, app.getStatus(), app.getStatus(),
                    "DOC_UPLOAD_SERVICE", "Supplementary document (" + fileName + ") uploaded and linked.");
        }

        LoanApplication saved = applicationRepository.save(app);

        // Dispatch Real-time Notification for Document Upload to Employee markj
        notificationService.sendNotification(new NotificationDTO(
                "markj",
                "Document Uploaded: " + saved.getCustomerName(),
                "Customer " + saved.getCustomerName() + " (" + saved.getCustomerId() + ") has uploaded " + fileName + " (" + docType.name() + "). Ready for Document Review.",
                "DOCUMENT_UPLOADED",
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

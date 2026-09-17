# Digital Lending & Customer Onboarding Platform
## Architecture & Technical Design Document (HLD & LLD)

---

## 1. Executive Summary

The **Azure-Powered Digital Lending & Customer Onboarding Platform** is an enterprise-grade, cloud-native banking solution built to modernize legacy retail lending operations. It replaces manual, paper-intensive loan underwriting with an automated, event-driven, microservices-based platform hosted entirely on **Microsoft Azure**.

### Key Business & Technical Highlights
- **End-to-End Digital Customer Journey**: Instant self-registration, KYC ingestion, product selection, EMI simulation, multi-document proof uploads, and real-time application lifecycle tracking.
- **Automated Credit Engine & Stateful Orchestration**: Powered by **Azure Durable Functions** to perform real-time Debt-to-Income (DTI) computation, multi-factor risk scoring, and rule-based workflow routing.
- **Strict Document Verification & Rejection Lifecycle**: Robust document workflow where individual proofs (Identity, Income, Bank Statements) are audited, verified, or rejected by Credit Managers. Rejections automatically revert application states (`DOCUMENT_REVIEW_PENDING`) and trigger customer re-upload notifications.
- **Comprehensive Immutable Audit Trail**: Full state history captured via **Hibernate Envers** and dedicated audit tables, tracking timestamps, state deltas, actor IDs, and underwriting remarks.
- **Zero-Trust Security**: Centralized API Gateway (**Azure API Management**) enforcing JWT authentication, rate limiting, and role-based header injection (`ROLE_CUSTOMER`, `ROLE_MANAGER`).

---

## 2. High Level Design (HLD)

### 2.1 High-Level Architecture Diagram

```mermaid
flowchart TB
    subgraph Clients["Presentation Layer (Client Channels)"]
        CP["🌐 Customer Portal<br/>(React + Vite on Azure Static Web Apps)"]
        MP["👔 Credit Manager Console<br/>(React + Tailwind on Azure Static Web Apps)"]
    end

    subgraph APIM_Layer["API Gateway & Ingress Layer"]
        APIM["🛡️ Azure API Management (APIM)<br/>team6-api-management.azure-api.net<br/>• JWT Validation • Rate Limiting • CORS • Header Injection (X-User-Role, X-User-Id)"]
    end

    subgraph Microservices["Microservices Layer (Azure App Services - Java 17 / Spring Boot 3)"]
        CS["👤 Customer Service<br/>(Auth, KYC, Customer Profiles)"]
        LS["💳 Loan Application Service<br/>(Workflow, State Machine, EMI, Decisions)"]
        DS["📁 Document Management Service<br/>(Uploads, SAS URLs, Metadata, Review Lifecycle)"]
        NS["🔔 Notification Service<br/>(Email Dispatch, SSE Notifications)"]
        RS["📊 Reporting & Operations Service<br/>(Analytics, KPI Dashboards, Portfolio Metrics)"]
    end

    subgraph Serverless["Serverless & Integration Layer"]
        DF["⚡ Azure Durable Functions<br/>(Loan Risk Assessment & Orchestration Workflow)"]
        LA["📬 Azure Logic Apps<br/>(Automated Email Checklist & Review Alert Dispatch)"]
        SB["📨 Azure Service Bus<br/>• Topics: loan-status-topic, document-events-topic<br/>• Queues: notification-queue, document-events-queue"]
    end

    subgraph Storage_Layer["Data & Persistence Layer"]
        SQL[("🗄️ Azure SQL Database<br/>smzen-capstone-db<br/>• Customers • Loans • Documents • Envers Audit Tables")]
        BLOB[("📦 Azure Blob Storage<br/>(Secure Document Vault, Private Containers, SAS Streaming)")]
        KV["🔐 Azure Key Vault<br/>team6-arpit-kv<br/>(DB Credentials, JWT Signing Keys, Connection Strings)"]
    end

    Clients -->|HTTPS / REST| APIM
    APIM -->|Reverse Proxy / Authorized Routes| CS
    APIM -->|Reverse Proxy / Authorized Routes| LS
    APIM -->|Reverse Proxy / Authorized Routes| DS
    APIM -->|Reverse Proxy / Authorized Routes| NS
    APIM -->|Reverse Proxy / Authorized Routes| RS

    LS -->|Trigger Orchestration| DF
    DF -->|Async Callback / Decision| LS
    DS -->|Stream Blobs / SAS Upload| BLOB
    DS -->|Document Reviewed / Uploaded Callback| LS
    LS -->|Publish Status Events| SB
    DS -->|Publish Doc Events| SB
    SB -->|Consume Notification Messages| NS
    LS -->|Invoke Webhook| LA
    DS -->|Invoke Webhook| LA

    CS -->|JDBC Connection| SQL
    LS -->|JPA / Hibernate Envers| SQL
    DS -->|JPA / Metadata| SQL
    RS -->|Read-Only Aggregates| SQL
    Microservices -.->|Fetch Secrets| KV
```

---

### 2.2 Microservices Domain Decomposition

| Service Name | Responsibility | Tech Stack | Azure Host |
| :--- | :--- | :--- | :--- |
| **Customer Service** | Customer identity, registration, password hashing (BCrypt), JWT token issuance, customer profile lookup. | Java 17, Spring Boot 3, Spring Security | Azure App Service (`team6-customer-service`) |
| **Loan Application Service** | Core lending engine: scheme catalog, EMI calculation, state transition machine, manager assignment, audit trail recording. | Java 17, Spring Boot 3, Hibernate Envers | Azure App Service (`team6-loan-service`) |
| **Document Service** | Document metadata, Azure Blob integration, secure upload/download streaming, verification status updates. | Java 17, Spring Boot 3, Azure Storage SDK | Azure App Service (`team6-document-service`) |
| **Notification Service** | Real-time Server-Sent Events (SSE) notification stream, manager alert feeds, customer notification history. | Java 17, Spring Boot 3, Spring WebFlux | Azure App Service (`team6-notification-service`) |
| **Reporting Service** | Operations dashboard, executive metrics, loan approval ratios, average turnaround time (TAT), underwriting KPIs. | Java 17, Spring Boot 3, JPA Projections | Azure App Service (`team6-report-service`) |

---

## 3. Azure Cloud Components Architecture

The architecture leverages managed Platform-as-a-Service (PaaS) and Serverless components on Microsoft Azure:

```mermaid
graph LR
    subgraph Azure_Cloud["Microsoft Azure Environment (Resource Group: SurajM-RG | Region: South India)"]
        subgraph Ingress["Edge & Delivery"]
            SWA_C["Azure Static Web Apps<br/>(Customer Portal)"]
            SWA_M["Azure Static Web Apps<br/>(Manager Portal)"]
            APIM_GW["Azure API Management<br/>(team6-api-management)"]
        end

        subgraph Compute["App Service Plan (Linux)"]
            APP_L["App Service: Loan Service"]
            APP_D["App Service: Document Service"]
            APP_C["App Service: Customer Service"]
            APP_N["App Service: Notification Service"]
            APP_R["App Service: Report Service"]
        end

        subgraph Serverless_Integration["Event & Workflow Automation"]
            FN_DUR["Azure Durable Functions<br/>(Underwriting Engine)"]
            LOGIC_APP["Azure Logic Apps<br/>(Email Dispatcher)"]
            ASB["Azure Service Bus<br/>(Topics & Queues)"]
        end

        subgraph Data_Security["Persistence & Security"]
            AZ_SQL["Azure SQL Database<br/>(smzen-capstone-db)"]
            AZ_BLOB["Azure Blob Storage<br/>(Document Vault)"]
            AZ_KV["Azure Key Vault<br/>(team6-arpit-kv)"]
            AZ_MON["Azure Monitor & App Insights<br/>(Distributed Telemetry)"]
        end
    end

    SWA_C --> APIM_GW
    SWA_M --> APIM_GW
    APIM_GW --> Compute
    Compute --> Serverless_Integration
    Compute --> Data_Security
    Serverless_Integration --> Data_Security
```

### Detailed Azure Service Inventory

| Azure Service | Resource Name / SKU | Purpose in System |
| :--- | :--- | :--- |
| **Azure API Management** | `team6-api-management` (Developer SKU) | Gateway entrypoint for all frontend traffic; enforces subscription keys, validates JWTs, injects `X-User-Role` / `X-User-Id` headers. |
| **Azure Static Web Apps** | `victorious-grass-0e6891400` (Customer)<br/>`lively-grass-0d6cbb800` (Manager) | Hosts high-performance single-page React applications with global CDN distribution and SSL termination. |
| **Azure App Service** | Linux App Service Plan (South India) | Hosts containerized Spring Boot microservices with auto-scaling and health check monitoring. |
| **Azure SQL Database** | `smzen-capstone-db` on `smzen-capstone.database.windows.net` | Enterprise relational storage with Transparent Data Encryption (TDE), automated backups, and Envers audit tables. |
| **Azure Blob Storage** | Azure Storage Account (Standard LRS) | Encrypted object storage for identity proofs, income proofs, and bank statements with SAS URL generation. |
| **Azure Service Bus** | Standard Tier Service Bus Namespace | Decoupled asynchronous messaging for loan status changes (`loan-status-topic`) and document processing events (`document-events-topic`). |
| **Azure Durable Functions** | Flex/Consumption Plan Function App | Stateful workflow orchestrator executing multi-step underwriting and credit risk calculations. |
| **Azure Logic Apps** | Consumption Logic App Workflow | Event-driven email dispatch engine sending dynamic HTML checklists and manager notifications via Office 365 / SMTP. |
| **Azure Key Vault** | `team6-arpit-kv` | Centralized hardware security module storing DB passwords, JWT secret keys, and Service Bus connection strings. |
| **Azure Monitor / App Insights** | Shared Log Analytics Workspace | Real-time metrics, HTTP dependency tracking, live transaction search, and distributed tracing. |

---

## 4. Low Level Design (LLD)

### 4.1 Database Schema & Data Models

```mermaid
erDiagram
    CUSTOMERS ||--o{ LOAN_APPLICATIONS : "applies for"
    LOAN_SCHEMES ||--o{ LOAN_APPLICATIONS : "governs"
    LOAN_APPLICATIONS ||--o{ LOAN_DOCUMENTS : "contains"
    LOAN_APPLICATIONS ||--o{ LOAN_AUDIT_LOG : "generates audit history"
    LOAN_DOCUMENTS ||--o{ LOAN_DOCUMENTS_AUD : "tracks revisions"

    CUSTOMERS {
        uniqueidentifier customer_id PK
        varchar first_name
        varchar last_name
        varchar email UK
        varchar mobile_number
        varchar pan_number
        varchar aadhaar_number
        varchar employment_details
        decimal income_details
        varchar login_id UK
        varchar login_password
        datetime created_at
    }

    LOAN_SCHEMES {
        varchar scheme_id PK
        varchar scheme_name
        varchar loan_type
        decimal min_amount
        decimal max_amount
        decimal base_interest_rate
        int min_tenure_months
        int max_tenure_months
        varchar processing_fee_pct
        bit is_active
    }

    LOAN_APPLICATIONS {
        varchar application_id PK
        varchar customer_id FK
        varchar customer_name
        varchar customer_email
        varchar customer_phone
        decimal monthly_income
        decimal existing_liabilities
        varchar employment_type
        varchar scheme_id FK
        varchar loan_type
        decimal loan_amount
        int tenure_months
        decimal interest_rate
        decimal calculated_emi
        varchar status
        int risk_score
        decimal dti_ratio
        varchar orchestration_instance_id
        varchar assigned_manager
        varchar decision_remarks
        bit document_provided
        datetime created_at
        datetime updated_at
    }

    LOAN_DOCUMENTS {
        varchar document_id PK
        varchar application_id FK
        varchar customer_id
        varchar doc_type
        varchar document_name
        varchar blob_path
        varchar content_type
        bigint file_size_bytes
        varchar verification_status
        varchar reviewed_by
        varchar review_remarks
        datetime uploaded_at
        datetime updated_at
    }

    LOAN_AUDIT_LOG {
        bigint audit_id PK
        varchar application_id FK
        varchar previous_status
        varchar new_status
        varchar action_by
        varchar comments
        datetime timestamp
    }

    LOAN_DOCUMENTS_AUD {
        varchar document_id PK
        int rev PK
        smallint revtype
        varchar verification_status
        varchar reviewed_by
        varchar review_remarks
    }
```

---

### 4.2 REST API Interface Specifications

#### 1. Customer Authentication & Profile
- `POST /customers/api/customers/auth/register` — Self-service customer onboarding.
- `POST /customers/api/customers/auth/login` — Issues JWT Bearer token with claims (`userId`, `role=ROLE_CUSTOMER`).
- `POST /auth/internal/login` — Issues internal staff token (`role=ROLE_MANAGER`).

#### 2. Loan Management (`loan-service`)
- `GET /loan-applications/api/v1/loans/schemes` — Active loan schemes and interest rates.
- `POST /loan-applications/api/v1/loans/calculate-emi` — Serverless EMI calculation and amortization schedule.
- `POST /loan-applications/api/v1/loans/apply` — Application submission triggering the Durable Function Orchestrator.
- `GET /loan-applications/api/v1/loans/applications/{id}` — Full application details with customer, scheme, and document state.
- `GET /loan-applications/api/v1/loans/applications/{id}/status` — Lightweight status tracking for customer timeline.
- `GET /loan-applications/api/v1/loans/applications/{id}/audit-logs` — Complete chronological audit trail.
- `POST /loan-applications/api/v1/loans/applications/{id}/request-documents` — Manager requests required document checklist.
- `POST /loan-applications/api/v1/loans/applications/{id}/document-uploaded` — Customer notifies upload completion.
- `POST /loan-applications/api/v1/loans/applications/{id}/document-reviewed` — Review decision (`APPROVED` / `REJECTED`).
- `POST /loan-applications/api/v1/loans/applications/{id}/decision` — Final Underwriter decision (`APPROVED` / `REJECTED`).

#### 3. Document Management (`document-service`)
- `POST /documents/api/v1/documents/upload` — Multipart document upload to Azure Blob Storage.
- `GET /documents/api/v1/documents/customer/{customerId}` — Retrieve customer's uploaded document catalog.
- `GET /documents/api/v1/documents/{id}/download` — Stream document blob securely via SAS URL.
- `PUT /documents/api/v1/documents/{id}/status` — Manager verifies/rejects document; triggers event bus & loan-service callback.
- `DELETE /documents/api/v1/documents/{id}` — Removes document blob and deletes metadata.

---

### 4.3 Security & Zero-Trust Authentication Architecture

```mermaid
sequenceDiagram
    autonumber
    actor Client as Customer / Credit Manager
    participant APIM as Azure API Management
    participant Auth as Customer Service (Auth)
    participant LoanSvc as Loan Service
    participant SpringSec as Spring Security Filter Chain

    Client->>APIM: POST /auth/customer/login (credentials)
    APIM->>Auth: Forward Auth Request
    Auth-->>Client: Returns signed JWT Bearer Token

    Note over Client,APIM: Subsequent API Request with Bearer Token
    Client->>APIM: POST /loan-applications/api/v1/loans/apply<br/>[Header: Authorization: Bearer <JWT>]
    APIM->>APIM: Validate JWT signature, issuer & expiry
    APIM->>APIM: Extract claims & inject internal headers:<br/>X-User-Role: ROLE_CUSTOMER<br/>X-User-Id: cust_12345
    APIM->>LoanSvc: Forward request with injected headers

    LoanSvc->>SpringSec: RequestHeaderAuthenticationFilter
    SpringSec->>SpringSec: Inspect X-User-Role & X-User-Id
    SpringSec->>SpringSec: Build PreAuthenticatedAuthenticationToken with Granted Authorities
    SpringSec->>LoanSvc: @PreAuthorize verification
    LoanSvc-->>Client: 201 Created (LoanApplicationResponse)
```

---

## 5. End-to-End Loan Processing Business Logic & State Machine

### 5.1 Loan State Machine Lifecycle

```mermaid
stateDiagram-v2
    [*] --> SUBMITTED : Customer Submits Loan Application
    
    SUBMITTED --> VALIDATING : Orchestrator Initiated
    VALIDATING --> CREDIT_ASSESSMENT : Demographic Checks Passed
    
    CREDIT_ASSESSMENT --> DOCUMENT_REVIEW_PENDING : Low Risk (Score <= 30) - KYC Proofs Required
    CREDIT_ASSESSMENT --> MANUAL_REVIEW_REQUIRED : Moderate Risk (Score 31-69)
    CREDIT_ASSESSMENT --> REJECTED : High Risk (Score >= 70) / High DTI (> 50%)

    DOCUMENT_REVIEW_PENDING --> DOCUMENTS_SUBMITTED : Customer Uploads All 3 Required Documents
    
    state DOCUMENTS_SUBMITTED {
        [*] --> DocReview
        DocReview : Credit Manager Reviews Proofs
    }

    DOCUMENTS_SUBMITTED --> DOCUMENT_REVIEW_PENDING : Manager Rejects >= 1 Document (Action Required)
    DOCUMENT_REVIEW_PENDING --> DOCUMENTS_SUBMITTED : Customer Re-uploads Rejected Document
    
    DOCUMENTS_SUBMITTED --> APPROVED : Manager Approves All 3 Documents (Low Risk Profile)
    DOCUMENTS_SUBMITTED --> MANUAL_REVIEW_REQUIRED : Manager Approves All Documents (Moderate Risk Profile)

    MANUAL_REVIEW_REQUIRED --> APPROVED : Senior Underwriter Approves Loan
    MANUAL_REVIEW_REQUIRED --> REJECTED : Senior Underwriter Rejects Loan

    APPROVED --> [*]
    REJECTED --> [*]
```

---

### 5.2 Step-by-Step Business Logic Workflow

```mermaid
sequenceDiagram
    autonumber
    actor Customer
    actor Manager as Credit Manager (mgr3)
    participant Portal as Web Portals (SWA)
    participant APIM as API Management
    participant LoanSvc as Loan Service
    participant DocSvc as Document Service
    participant Blob as Azure Blob Storage
    participant Durable as Azure Durable Functions
    participant LogicApp as Azure Logic App
    participant Audit as Azure SQL (Audit Trail)

    %% Step 1: Registration & Application
    Customer->>Portal: 1. Register & Submit Loan Application (₹500,000, 36 Mo)
    Portal->>APIM: POST /loans/apply
    APIM->>LoanSvc: applyForLoan()
    LoanSvc->>Audit: Record: (INITIAL) -> SUBMITTED [by APPLICANT]
    LoanSvc->>Durable: 2. Trigger Credit Assessment Orchestration
    Durable->>Durable: Calculate DTI (28.65%) & Risk Score (28/100)
    Durable-->>LoanSvc: Outcome: Low Risk -> Route to DOCUMENT_REVIEW_PENDING
    LoanSvc->>Audit: Record: SUBMITTED -> DOCUMENT_REVIEW_PENDING [by DURABLE_ORCHESTRATOR]
    LoanSvc->>LogicApp: Dispatch Loan Application Received Email to Customer

    %% Step 2: Document Request
    Manager->>Portal: 3. View Application & Send Document Request
    Portal->>APIM: POST /applications/{id}/request-documents
    APIM->>LoanSvc: sendDocumentRequestEmail()
    LoanSvc->>LogicApp: Send Document Checklist Email (Identity, Income, Bank Statement)
    LoanSvc->>Audit: Record: DOCUMENT_REVIEW_PENDING -> DOCUMENT_REVIEW_PENDING [by MANAGER]

    %% Step 3: Customer Document Submission
    Customer->>Portal: 4. Upload Identity Proof, Income Proof, Bank Statement
    Portal->>DocSvc: POST /documents/upload (Multipart PDF)
    DocSvc->>Blob: Stream bytes into Azure Blob Storage
    DocSvc-->>Portal: Return docId (69, 70, 71) & Blob URL
    Portal->>LoanSvc: POST /applications/{id}/document-uploaded
    LoanSvc->>Audit: Record uploads & advance: DOCUMENT_REVIEW_PENDING -> DOCUMENTS_SUBMITTED

    %% Step 4: Manager Reviews Documents (Approve 2, Reject 1)
    Manager->>Portal: 5. Review Documents:
    Manager->>Portal: - Approve Identity Proof (69)
    Manager->>Portal: - Approve Income Proof (70)
    Manager->>Portal: - Reject Bank Statement (71) (Reason: "Statement > 6 months old")
    Portal->>DocSvc: PUT /documents/{id}/status (REJECTED)
    DocSvc->>LoanSvc: POST /applications/{id}/document-reviewed (Status: REJECTED)
    LoanSvc->>Audit: Record: DOCUMENTS_SUBMITTED -> DOCUMENT_REVIEW_PENDING [by mgr3]
    Note over LoanSvc,Customer: Status immediately reverts to DOCUMENT_REVIEW_PENDING.<br/>Customer alerted to re-upload.

    %% Step 5: Customer Re-uploads
    Customer->>Portal: 6. Customer re-uploads fresh Bank Statement (72)
    Portal->>DocSvc: POST /documents/upload (Updated Bank Statement)
    Portal->>LoanSvc: POST /applications/{id}/document-uploaded
    LoanSvc->>Audit: Record: DOCUMENT_REVIEW_PENDING -> DOCUMENTS_SUBMITTED [by APPLICANT]

    %% Step 6: Final Approval
    Manager->>Portal: 7. Manager reviews and approves updated Bank Statement (72)
    Portal->>DocSvc: PUT /documents/72/status (APPROVED)
    DocSvc->>LoanSvc: POST /applications/{id}/document-reviewed (Status: APPROVED)
    LoanSvc->>LoanSvc: All 3 required documents approved with 0 rejections!
    LoanSvc->>Audit: Record: DOCUMENTS_SUBMITTED -> APPROVED [by mgr3]
    LoanSvc->>LogicApp: Dispatch Loan Sanction & Approval Email to Customer
    LoanSvc-->>Portal: Final Application State: APPROVED
```

---

## 6. Live Production Deployment & Resource Directory

| Resource Type | Endpoint / Portal URL | Status |
| :--- | :--- | :--- |
| **Customer Web Portal** | `https://victorious-grass-0e6891400.5.azurestaticapps.net` | Live & Operational |
| **Credit Manager Console** | `https://lively-grass-0d6cbb800.7.azurestaticapps.net` | Live & Operational |
| **API Management Gateway** | `https://team6-api-management.azure-api.net` | Live & Operational |
| **Loan Application Service** | `https://team6-loan-service.azurewebsites.net` | Live (`RuntimeSuccessful`) |
| **Document Management Service** | `https://team6-document-service.azurewebsites.net` | Live (`RuntimeSuccessful`) |
| **Customer Auth Service** | `https://team6-customer-service.azurewebsites.net` | Live (`RuntimeSuccessful`) |
| **Notification Service** | `https://team6-notification-service.azurewebsites.net` | Live (`RuntimeSuccessful`) |
| **Reporting & KPI Service** | `https://team6-report-service.azurewebsites.net` | Live (`RuntimeSuccessful`) |
| **Azure SQL Database** | `smzen-capstone.database.windows.net` (`smzen-capstone-db`) | Active (Envers Enabled) |
| **Azure Key Vault** | `team6-arpit-kv.vault.azure.net` | Active (Access Policies Configured) |

---

## 7. Demo Walkthrough Script for Architecture Review

### Step-by-Step Live Demo Runbook

1. **Step 1: Customer Self-Registration & Login**
   - Open Customer Portal & register new user (`cust_<timestamp>@e2etest.com`).
   - Customer is instantly authenticated and redirected to the lending dashboard.
2. **Step 2: Loan Scheme Selection & Application Submission**
   - Select Business Loan (`SCHEME-BL-01`) for ₹500,000, 36 months tenure.
   - Click **Submit Loan Application**.
   - **Show Evaluators**: Application instantly created with status `DOCUMENT_REVIEW_PENDING` (Low Risk Score 28/100, DTI 28.65%) and assigned to Credit Manager.
3. **Step 3: Manager Views Application & Sends Document Checklist**
   - Log in to Credit Manager Console (`mgr3`).
   - Open the new application and click **Send Document Request Email**.
   - Show the dispatched checklist email containing mandatory KYC, Income, and Bank Statement requirements.
4. **Step 4: Customer Uploads 3 Required Documents**
   - Switch to Customer Portal -> Document Upload section.
   - Upload:
     1. Identity Proof (`id_proof.pdf`)
     2. Income Proof (`income_proof.pdf`)
     3. Bank Statement (`bank_statement.pdf`)
   - Notice application status automatically advances from `DOCUMENT_REVIEW_PENDING` $\to$ `DOCUMENTS_SUBMITTED`.
5. **Step 5: Manager Audits Documents — Approve 2 & Reject 1**
   - Manager reviews Identity Proof $\to$ **Approve**.
   - Manager reviews Income Proof $\to$ **Approve**.
   - Manager reviews Bank Statement $\to$ **Reject** with remark: *"Bank statement is over 6 months old. Please submit a recent statement."*
   - **Highlight to Evaluators**: Loan application status immediately goes back to `DOCUMENT_REVIEW_PENDING` and audit log captures the rejection reason.
6. **Step 6: Customer Re-uploads Fresh Bank Statement**
   - Customer sees rejection notice in their dashboard and uploads the updated bank statement.
   - Application status transitions back to `DOCUMENTS_SUBMITTED`.
7. **Step 7: Manager Approves Final Document $\to$ Loan Sanction**
   - Manager opens updated bank statement $\to$ clicks **Approve**.
   - System detects all 3 documents verified with 0 rejections $\to$ application moves to **`APPROVED`**!
8. **Step 8: Review Complete Audit Trail**
   - Open Audit Trail tab in Manager Console or via API.
   - Point out every state delta, timestamp, changed-by actor, and underwriting remark.

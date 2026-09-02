# Digital Lending Core: Loan Service Testing & Verification Guide

Complete guide for running, testing, and verifying the **Loan Service** microservice built with **Java 17**, **Spring Boot 3**, **H2 Database**, **Azure Durable Functions (Stateful Orchestration)**, **Azure Logic Apps (Manager Approvals)**, and **Azure Service Bus (Event Publishing)**.

---

## 1. Quick Start & Service Execution

### 1.1 Prerequisites
- **Java 17+** (or Java 21 / 25)
- **Apache Maven 3.8+**

### 1.2 Starting the Service
Run the following command from the `loan-service` directory:

```powershell
# Set JAVA_HOME (if using Adoptium JDK)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"

# Start the Spring Boot application
mvn spring-boot:run
```

Once started, the service will be active on **`http://localhost:8080`**.

---

## 2. Interactive Tooling & Consoles

### 2.1 Swagger UI (Interactive API Testing)
Open your browser and navigate to:
👉 **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**
- Test all REST endpoints with pre-populated schema models and immediate response visualization.

### 2.2 H2 In-Memory Database Web Console
Navigate to:
👉 **[http://localhost:8080/h2-console](http://localhost:8080/h2-console)**
- **JDBC URL**: `jdbc:h2:mem:loandb`
- **User Name**: `sa`
- **Password**: *(leave blank)*

Click **Connect** to inspect tables: `LOAN_SCHEMES`, `LOAN_APPLICATIONS`, `LOAN_DOCUMENTS`, and `LOAN_AUDIT_LOGS`.

---

## 3. End-to-End Test Scenarios

### Scenario 1: Automated Low-Risk Approval ($\text{Risk Score} \le 30$)
> **Context**: High-income applicant with low debt requests a standard loan within bounds.
> **Expected Behavior**: Instantly evaluated by Credit Engine and marked as `APPROVED`. Emits `LoanApplicationCompletedEvent` to Azure Service Bus.

#### Step 1.1: Submit Loan Application
```bash
curl -X POST http://localhost:8080/api/v1/loans/apply \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-1001",
    "customerName": "Alice Johnson",
    "customerEmail": "alice.johnson@example.com",
    "customerPhone": "+14155552671",
    "monthlyIncome": 180000.00,
    "existingLiabilities": 3000.00,
    "employmentType": "SALARIED",
    "schemeId": "SCHEME-PL-01",
    "loanAmount": 150000.00,
    "tenureMonths": 24,
    "documentIds": []
  }'
```

#### Step 1.2: Check Terminal Status
```bash
# Replace APP-ID with the returned applicationId
curl http://localhost:8080/api/v1/loans/applications/{APP-ID}/status
```
**Expected Response**:
```json
{
  "applicationId": "APP-XXXXXXX",
  "customerName": "Alice Johnson",
  "status": "APPROVED",
  "stageDescription": "Loan approved! Ready for sanction letter generation and disbursement.",
  "riskScore": 15,
  "decisionRemarks": "Auto-Approved by Credit Engine (Risk Score: 15/100, DTI: 5.48%)"
}
```

---

### Scenario 2: Automated High-Risk Rejection ($\text{Risk Score} \ge 70$)
> **Context**: Applicant with existing liabilities exceeding monthly income requesting a large loan (DTI $> 100\%$).
> **Expected Behavior**: Immediately rejected with policy violation remarks. Emits `LoanApplicationCompletedEvent` with `REJECTED` status.

#### Step 2.1: Submit High-Risk Application
```bash
curl -X POST http://localhost:8080/api/v1/loans/apply \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-2002",
    "customerName": "Bob Overleveraged",
    "customerEmail": "bob.debt@example.com",
    "customerPhone": "+14155559988",
    "monthlyIncome": 30000.00,
    "existingLiabilities": 25000.00,
    "employmentType": "STUDENT",
    "schemeId": "SCHEME-PL-01",
    "loanAmount": 800000.00,
    "tenureMonths": 60,
    "documentIds": []
  }'
```

#### Step 2.2: Verify Auto-Rejection
```bash
curl http://localhost:8080/api/v1/loans/applications/{APP-ID}
```
**Expected Response**:
```json
{
  "status": "REJECTED",
  "riskScore": 95,
  "decisionRemarks": "Auto-Rejected by Credit Engine: High debt burden or leverage (Risk Score: 95/100, DTI: 141.77%)"
}
```

---

### Scenario 3: Human Intervention (Logic App Approval Webhook) ($\text{Risk Score } 31 - 69$)
> **Context**: Moderate DTI applicant ($45\%$). System flags application for Senior Underwriter review.
> **Expected Behavior**: Enters `MANUAL_REVIEW_REQUIRED`, triggers Logic App approval card, waits for Operations Manager decision, and finalizes via webhook.

#### Step 3.1: Submit Medium-Risk Application
```bash
curl -X POST http://localhost:8080/api/v1/loans/apply \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-3003",
    "customerName": "Elena Rostova",
    "customerEmail": "elena.rostova@example.com",
    "customerPhone": "+14155557766",
    "monthlyIncome": 75000.00,
    "existingLiabilities": 15000.00,
    "employmentType": "SELF_EMPLOYED",
    "schemeId": "SCHEME-PL-01",
    "loanAmount": 400000.00,
    "tenureMonths": 24,
    "documentIds": []
  }'
```
**Expected Response**: Status will be **`MANUAL_REVIEW_REQUIRED`** with Risk Score around `45/100`.

#### Step 3.2: Operations Manager Submits Review Decision (Webhook Callback)
Simulate the Azure Logic App actionable card response:
```bash
curl -X POST http://localhost:8080/api/v1/loans/applications/{APP-ID}/manager-callback \
  -H "Content-Type: application/json" \
  -d '{
    "decision": "APPROVE",
    "remarks": "Reviewed 3 years tax filings and audited balance sheet. Approved with standard terms.",
    "managerId": "senior.underwriter.sarah@bank.com"
  }'
```

#### Step 3.3: Verify Final State & Audit Trail
```bash
curl http://localhost:8080/api/v1/loans/applications/{APP-ID}
```
**Expected Output**:
- `status`: **`APPROVED`**
- `assignedManager`: `"senior.underwriter.sarah@bank.com"`
- `decisionRemarks`: `"Approved by Operations Manager: Reviewed 3 years tax filings and audited balance sheet. Approved with standard terms."`

---

## 4. Individual API Reference

### 4.1 Discover Loan Schemes Catalog
```bash
curl http://localhost:8080/api/v1/loans/schemes
```

### 4.2 Calculate Loan EMI (Azure Function Proxy)
```bash
curl -X POST http://localhost:8080/api/v1/loans/calculate-emi \
  -H "Content-Type: application/json" \
  -d '{
    "loanAmount": 500000.00,
    "tenureMonths": 36,
    "interestRate": 9.50
  }'
```

### 4.3 Upload KYC / Income Document
```bash
# Upload a file using multipart form-data
curl -X POST http://localhost:8080/api/v1/loans/documents/upload \
  -F "customerId=CUST-1001" \
  -F "docType=IDENTITY_PROOF" \
  -F "file=@README.md"
```

### 4.4 List Applications by Status
```bash
# List all applications requiring human review
curl "http://localhost:8080/api/v1/loans/applications?status=MANUAL_REVIEW_REQUIRED"

# List all approved loans
curl "http://localhost:8080/api/v1/loans/applications?status=APPROVED"
```

---

## 5. Console Log Verification

When running the application, observe the structured logs generated for every step:

```
[MOCK AZURE FUNCTION CALL] Executing Azure Function at endpoint: https://bank-lending-functions.azurewebsites.net/api/v1/calculate-emi
[MOCK AZURE FUNCTION CALL] Result: Monthly EMI = 16,022.42, Total Interest = 76,807.12

[MOCK AZURE DURABLE FUNCTION] >>> STARTING DURABLE ORCHESTRATION INSTANCE <<<
[MOCK AZURE DURABLE FUNCTION] Instance ID: ORCH-APP-9B4A1C8D
[MOCK AZURE DURABLE FUNCTION] [Step 1/3] Executing Activity: 'ValidateApplicationActivity'... PASSED
[MOCK AZURE DURABLE FUNCTION] [Step 2/3] Executing Activity: 'ComputeCreditRiskScoreActivity'... Score: 18/100
[MOCK AZURE DURABLE FUNCTION] [Step 3/3] Decision Gateway: Direct Auto-Approval. Status -> APPROVED

[MOCK AZURE SERVICE BUS] >>> DISPATCHING MESSAGE TO AZURE EVENT BUS <<<
[MOCK AZURE SERVICE BUS] Target Topic: 'loan-events-topic' | Event Type: 'LOAN_APPLICATION_COMPLETED'
[MOCK AZURE SERVICE BUS] Application: APP-9B4A1C8D | Final Status: APPROVED
```

---

## 6. Running Automated Tests

Run the comprehensive unit and integration test suite:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"
mvn test
```

All tests validate:
1. **Mathematical Amortization Accuracy** (`EMICalculatorProxyServiceTest`)
2. **Credit Risk Scoring Logic & DTI Calculations** (`CreditRiskScoringServiceTest`)
3. **Orchestrator Lifecycle & Event Publishing** (`LoanApplicationServiceTest`)
4. **End-to-End HTTP Endpoints & Webhook Flow** (`LoanApplicationIntegrationTest`)

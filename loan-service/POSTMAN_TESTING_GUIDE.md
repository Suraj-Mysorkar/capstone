# Loan Service Postman Demo Guide

This guide covers the complete REST API test flow for the Digital Lending Loan Service, structured sequentially for a live demonstration.

## 1. Start The Application

From the `loan-service` directory:

```powershell
mvn spring-boot:run
```

The default server URL is:

```text
http://localhost:8080
```

The service uses an in-memory H2 database. Seeded loan schemes are recreated when the application restarts.

## 2. Postman Environment

Create a Postman environment named `Loan Service - Local` with these variables:

| Variable | Initial value | Description |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | Service base URL |
| `applicationId` | empty | Set from a successful Apply Loan response |
| `documentId` | empty | Set from a successful Upload Document response |
| `schemeId` | `SCHEME-PL-01` | Default personal-loan scheme |

Use `{{baseUrl}}` in every request URL.

---

## 3. Live Demo Flow (APIs In Order)

Follow these steps sequentially to demonstrate the end-to-end loan origination process.

### Step 1: List Active Loan Schemes

Retrieve the catalog of available loan products in the system.

**Request**
```http
GET {{baseUrl}}/api/v1/loans/schemes
```
**cURL**
```bash
curl -X GET http://localhost:8080/api/v1/loans/schemes
```
**Expected:** `200 OK` with an array of active schemes (Personal, Home, Vehicle, Education).

### Step 2: Calculate EMI

Calculate the estimated monthly installment for a Personal Loan (`SCHEME-PL-01`).

**Request**
```http
POST {{baseUrl}}/api/v1/loans/calculate-emi
Content-Type: application/json
```
**Body**
```json
{
  "loanAmount": 300000.00,
  "tenureMonths": 36,
  "schemeId": "SCHEME-PL-01"
}
```
**cURL**
```bash
curl -X POST http://localhost:8080/api/v1/loans/calculate-emi \
  -H "Content-Type: application/json" \
  -d '{
    "loanAmount": 300000.00,
    "tenureMonths": 36,
    "schemeId": "SCHEME-PL-01"
  }'
```
**Expected:** `200 OK` with `monthlyEMI` and an amortization schedule.

### Step 3: Upload A Document

Upload an Identity Proof document for the customer before applying.

**Request** (In Postman, select **Body > form-data**)
```http
POST {{baseUrl}}/api/v1/loans/documents/upload
Content-Type: multipart/form-data
```
**cURL**
```bash
curl -X POST http://localhost:8080/api/v1/loans/documents/upload \
  -F "customerId=CUST-1001" \
  -F "docType=IDENTITY_PROOF" \
  -F "file=@/path/to/your/file.pdf"
```
**Expected:** `201 Created` with a generated `documentId`. *Save this ID to the Postman `documentId` variable to use in the next step.*

### Step 4: Submit a Low-Risk Application (Auto Approval)

Submit a loan application with a high monthly income and low liabilities.

**Request**
```http
POST {{baseUrl}}/api/v1/loans/apply
Content-Type: application/json
```
**Body**
```json
{
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
}
```
*(Optionally, add your `documentId` from Step 3 into the `documentIds` array)*

**cURL**
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
**Expected:** `201 Created` with `status: APPROVED`. The low risk score results in an auto-approval. *Save the returned `applicationId` to the Postman variable.*

### Step 5: Query Application Details

Check the full details of the newly approved application.

**Request**
```http
GET {{baseUrl}}/api/v1/loans/applications/{{applicationId}}
```
**cURL**
```bash
curl -X GET http://localhost:8080/api/v1/loans/applications/<applicationId>
```
**Expected:** `200 OK` with applicant details, loan details, risk score, documents, and auto-approval remarks.

### Step 6: Submit a Medium-Risk Application (Manual Review)

A self-employed customer submits an application. The system flags this for manual review by an underwriter.

**Request**
```http
POST {{baseUrl}}/api/v1/loans/apply
Content-Type: application/json
```
**Body**
```json
{
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
}
```
**cURL**
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
**Expected:** `201 Created` with `status: MANUAL_REVIEW_REQUIRED` and an orchestration ID. *Save this new `applicationId` to the Postman variable.*

### Step 7: Manager Approves Manual Review

An operations manager reviews the application and approves it via the webhook callback.

**Request**
```http
POST {{baseUrl}}/api/v1/loans/applications/{{applicationId}}/manager-callback
Content-Type: application/json
```
**Body**
```json
{
  "decision": "APPROVE",
  "remarks": "Reviewed tax filings and bank statements. Income verified.",
  "managerId": "senior.underwriter@bank.com"
}
```
**cURL**
```bash
curl -X POST http://localhost:8080/api/v1/loans/applications/<applicationId>/manager-callback \
  -H "Content-Type: application/json" \
  -d '{
    "decision": "APPROVE",
    "remarks": "Reviewed tax filings and bank statements. Income verified.",
    "managerId": "senior.underwriter@bank.com"
  }'
```
**Expected:** `200 OK` showing the status is now `APPROVED`.

### Step 8: View Application Audit Trail

Check the history of the medium-risk application to see the state transitions (Submitted -> Manual Review -> Approved).

**Request**
```http
GET {{baseUrl}}/api/v1/loans/applications/{{applicationId}}/audit-logs
```
**cURL**
```bash
curl -X GET http://localhost:8080/api/v1/loans/applications/<applicationId>/audit-logs
```
**Expected:** `200 OK` with status transitions, actor, comments, and timestamps.

### Step 9: Submit a High-Risk Application (Auto Rejection)

A student with high liabilities requests a large loan.

**Request**
```http
POST {{baseUrl}}/api/v1/loans/apply
Content-Type: application/json
```
**Body**
```json
{
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
}
```
**cURL**
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
**Expected:** `201 Created` with `status: REJECTED` and high `riskScore`. The system automatically rejects the high-risk profile.

### Step 10: List All Applications

View all recent applications.

**Request**
```http
GET {{baseUrl}}/api/v1/loans/applications
```
**cURL**
```bash
curl -X GET http://localhost:8080/api/v1/loans/applications
```
**Expected:** `200 OK` with applications ordered newest first.

### Step 11: Filter Applications By Status

Filter applications by their approval status.

**Request**
```http
GET {{baseUrl}}/api/v1/loans/applications?status=APPROVED
```
**cURL**
```bash
curl -X GET "http://localhost:8080/api/v1/loans/applications?status=APPROVED"
```
**Expected:** `200 OK` showing only the applications we just approved in the demo. (You can also filter by `REJECTED` or `MANUAL_REVIEW_REQUIRED`).

---

## 4. Additional Reference APIs

### 4.1 Get One Loan Scheme
```http
GET {{baseUrl}}/api/v1/loans/schemes/{{schemeId}}
```
**cURL**
```bash
curl -X GET http://localhost:8080/api/v1/loans/schemes/SCHEME-PL-01
```
**Expected:** `200 OK` for a valid scheme, `404 Not Found` for `SCHEME-UNKNOWN`.

### 4.2 Get Document Metadata
```http
GET {{baseUrl}}/api/v1/loans/documents/{{documentId}}
```
**cURL**
```bash
curl -X GET http://localhost:8080/api/v1/loans/documents/<documentId>
```
**Expected:** `200 OK` for an existing document, `404 Not Found` for `DOC-NOTFOUND`.

### 4.3 Get Lightweight Application Status
```http
GET {{baseUrl}}/api/v1/loans/applications/{{applicationId}}/status
```
**cURL**
```bash
curl -X GET http://localhost:8080/api/v1/loans/applications/<applicationId>/status
```
**Expected:** `200 OK` with `status`, `stageDescription`, `riskScore`, `decisionRemarks`, and `lastUpdated`.

### 4.4 Manager Rejects Manual Review
Send to the `manager-callback` endpoint (Step 7) with:
```json
{
  "decision": "REJECT",
  "remarks": "Insufficient supporting income documentation.",
  "managerId": "senior.underwriter@bank.com"
}
```
**Expected:** `200 OK` and `status: REJECTED`.

---

## 5. Negative And Validation Tests

### 5.1 Invalid Application Email
Use the apply endpoint with `"customerEmail": "not-an-email"`.
**Expected:** `400 Bad Request`.

### 5.2 Missing Required Fields
Send `{}` to `/api/v1/loans/apply`.
**Expected:** `400 Bad Request` with validation messages.

### 5.3 Unknown Scheme
Use a valid application body but set `"schemeId": "SCHEME-UNKNOWN"`.
**Expected:** `404 Not Found`.

### 5.4 Amount Outside Scheme Limits
Use `SCHEME-PL-01` with `"loanAmount": 5000000.00`.
**Expected:** `201 Created` with `status: REJECTED` and remarks containing `validation failed`.

### 5.5 Invalid Manager Callback
Send a body with only `{"decision": "APPROVE"}`.
**Expected:** `400 Bad Request` because `remarks` and `managerId` are required.

### 5.6 Repeat Manager Callback
Send a second callback for an already approved or rejected application.
**Expected:** `409 Conflict` because only applications in `MANUAL_REVIEW_REQUIRED` can receive a manager decision.

---

## 6. Useful URLs

| Resource | URL |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| H2 Console | `http://localhost:8080/h2-console` |
| H2 JDBC URL (H2 Console) | `jdbc:h2:mem:loandb` |
| H2 JDBC URL (external client) | `jdbc:h2:tcp://localhost:9092/mem:loandb` |

For H2 Console use username `sa` and leave the password blank. The web console runs on port `8080`; external database clients must use the H2 TCP port `9092` started by `H2ServerConfig`.

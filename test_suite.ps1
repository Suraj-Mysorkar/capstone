<#
.SYNOPSIS
    Digital Lending & Banking Capstone - Full End-to-End API Test Suite
.DESCRIPTION
    Automated test suite covering all Azure microservices:
    - Customer Service (Registration, Email Login, UserID Login, Manager Login, Profile)
    - Loan Service (Schemes Catalog, Application Submission, Application Details, Manager Review)
    - Document Service (Document Types Catalog)
    - Report Service (OpenAPI Spec, Operations Summary, Executive Metrics)
#>

$ErrorActionPreference = "Continue"

$CUSTOMER_SVC_URL = "https://team6-arpit-customer-service.azurewebsites.net"
$LOAN_SVC_URL     = "https://team6-loan-service.azurewebsites.net"
$DOC_SVC_URL      = "https://team6-document-service.azurewebsites.net"
$REPORT_SVC_URL   = "https://team6-report-service-g2gdfshahugvgxf7.southindia-01.azurewebsites.net"

$TOTAL_TESTS = 0
$PASSED_TESTS = 0
$FAILED_TESTS = 0

function Report-Result {
    param([string]$TestName, [bool]$Success, [string]$Details = "")
    $script:TOTAL_TESTS++
    if ($Success) {
        $script:PASSED_TESTS++
        Write-Host "  [PASS] $TestName" -ForegroundColor Green
        if ($Details) { Write-Host "         $Details" -ForegroundColor DarkGray }
    } else {
        $script:FAILED_TESTS++
        Write-Host "  [FAIL] $TestName" -ForegroundColor Red
        if ($Details) { Write-Host "         Error: $Details" -ForegroundColor Yellow }
    }
}

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " DIGITAL LENDING CAPSTONE - END-TO-END API TEST SUITE" -ForegroundColor Cyan
Write-Host "========================================================`n" -ForegroundColor Cyan

# -------------------------------------------------------------
# SUITE 1: MASTER DATA & SCHEMES (LOAN & DOCUMENT SERVICES)
# -------------------------------------------------------------
Write-Host "--- 1. Testing Master Data (Loan & Document Services) ---" -ForegroundColor White
try {
    $schemes = Invoke-RestMethod -Uri "$LOAN_SVC_URL/api/v1/loans/schemes" -Method Get -TimeoutSec 20
    $schemeCount = ($schemes | Measure-Object).Count
    Report-Result -TestName "GET /api/v1/loans/schemes (Master Schemes Catalog)" `
                  -Success ($schemeCount -ge 5) `
                  -Details "Retrieved $schemeCount active schemes (e.g. $($schemes[0].schemeName))"
} catch {
    Report-Result -TestName "GET /api/v1/loans/schemes (Master Schemes Catalog)" -Success $false -Details $_.Exception.Message
}

try {
    $docTypes = Invoke-RestMethod -Uri "$DOC_SVC_URL/api/v1/documents/types" -Method Get -TimeoutSec 20
    $dtCount = ($docTypes | Measure-Object).Count
    Report-Result -TestName "GET /api/v1/documents/types (Master Document Types Catalog)" `
                  -Success ($dtCount -ge 3) `
                  -Details "Retrieved $dtCount supported document types"
} catch {
    Report-Result -TestName "GET /api/v1/documents/types (Master Document Types Catalog)" -Success $false -Details $_.Exception.Message
}

# -------------------------------------------------------------
# SUITE 2: CUSTOMER SERVICE - REGISTRATION & AUTHENTICATION
# -------------------------------------------------------------
Write-Host "`n--- 2. Testing Customer Service (Auth & Profiles) ---" -ForegroundColor White

$randNum = Get-Random -Minimum 10000 -Maximum 99999
$testEmail = "suite_cust_${randNum}@banktest.com"
$testPassword = "Password@123"
$regPayload = @{
    firstName = "TestFirstName"
    lastName  = "TestLastName"
    email     = $testEmail
    password  = $testPassword
    phoneNumber = "+91 98765 43210"
    addressLine1 = "100 MG Road"
    city = "Bengaluru"
    state = "Karnataka"
    postalCode = "560001"
    countryCode = "IN"
} | ConvertTo-Json

$custAuthRes = $null
try {
    $custAuthRes = Invoke-RestMethod -Uri "$CUSTOMER_SVC_URL/api/customers/auth/register" `
                                    -Method Post -ContentType "application/json" -Body $regPayload -TimeoutSec 20
    $hasIds = ($custAuthRes.userId -gt 0) -and (![string]::IsNullOrEmpty($custAuthRes.customerId))
    Report-Result -TestName "POST /api/customers/auth/register (Customer Registration)" `
                  -Success $hasIds `
                  -Details "Created User_ID: $($custAuthRes.userId) | Customer UUID: $($custAuthRes.customerId)"
} catch {
    Report-Result -TestName "POST /api/customers/auth/register (Customer Registration)" -Success $false -Details $_.Exception.Message
}

# Customer Login with Email
$custToken = ""
try {
    $loginBody = @{ username = $testEmail; password = $testPassword } | ConvertTo-Json
    $loginRes = Invoke-RestMethod -Uri "$CUSTOMER_SVC_URL/api/customers/auth/login" `
                                  -Method Post -ContentType "application/json" -Body $loginBody -TimeoutSec 20
    $custToken = $loginRes.token
    Report-Result -TestName "POST /api/customers/auth/login (Customer Email Login)" `
                  -Success (![string]::IsNullOrEmpty($custToken)) `
                  -Details "Authenticated token received for role: $($loginRes.role)"
} catch {
    Report-Result -TestName "POST /api/customers/auth/login (Customer Email Login)" -Success $false -Details $_.Exception.Message
}

# Customer Login with numeric User_ID
if ($custAuthRes -and $custAuthRes.userId) {
    try {
        $userIdLoginBody = @{ username = [string]$custAuthRes.userId; password = $testPassword } | ConvertTo-Json
        $userIdLoginRes = Invoke-RestMethod -Uri "$CUSTOMER_SVC_URL/api/customers/auth/login" `
                                            -Method Post -ContentType "application/json" -Body $userIdLoginBody -TimeoutSec 20
        Report-Result -TestName "POST /api/customers/auth/login (Login using numeric User_ID: $($custAuthRes.userId))" `
                      -Success ($userIdLoginRes.userId -eq $custAuthRes.userId) `
                      -Details "Successfully authenticated user using numeric User ID!"
    } catch {
        Report-Result -TestName "POST /api/customers/auth/login (Login using numeric User_ID)" -Success $false -Details $_.Exception.Message
    }
}

# Manager Login (seeded manager mgr1 / manager1@bank.com / Password@123)
$mgrToken = ""
try {
    $mgrLoginBody = @{ username = "manager1@bank.com"; password = "Password@123" } | ConvertTo-Json
    $mgrLoginRes = Invoke-RestMethod -Uri "$CUSTOMER_SVC_URL/api/customers/auth/login" `
                                     -Method Post -ContentType "application/json" -Body $mgrLoginBody -TimeoutSec 20
    $mgrToken = $mgrLoginRes.token
    $isManager = ($mgrLoginRes.role -eq "manager")
    Report-Result -TestName "POST /api/customers/auth/login (Manager Login: manager1@bank.com / Password@123)" `
                  -Success ($isManager -and ![string]::IsNullOrEmpty($mgrToken)) `
                  -Details "Manager Verified! Name: $($mgrLoginRes.name) | Role: $($mgrLoginRes.role)"
} catch {
    Report-Result -TestName "POST /api/customers/auth/login (Manager Login)" -Success $false -Details $_.Exception.Message
}

# -------------------------------------------------------------
# SUITE 3: LOAN SERVICE - SUBMISSION & APPLICATION LIFECYCLE
# -------------------------------------------------------------
Write-Host "`n--- 3. Testing Loan Service (Application Lifecycle) ---" -ForegroundColor White

$createdAppId = ""
$customerIdToUse = if ($custAuthRes -and $custAuthRes.customerId) { $custAuthRes.customerId } else { "131fbd51-f9b3-47e0-b40a-08ebb01226dc" }

try {
    $applyBody = @{
        customerId = $customerIdToUse
        customerName = "Suite Test Customer"
        customerEmail = $testEmail
        customerPhone = "9876543210"
        monthlyIncome = 85000
        existingLiabilities = 4000
        employmentType = "SALARIED"
        schemeId = "SCHEME-PL-01"
        loanAmount = 250000
        tenureMonths = 24
        documentIds = @()
    } | ConvertTo-Json

    $applyRes = Invoke-RestMethod -Uri "$LOAN_SVC_URL/api/v1/loans/apply" `
                                  -Method Post -ContentType "application/json" -Body $applyBody -TimeoutSec 20
    $createdAppId = $applyRes.applicationId
    $hasAppId = (![string]::IsNullOrEmpty($createdAppId))
    Report-Result -TestName "POST /api/v1/loans/apply (Submit Loan Application)" `
                  -Success $hasAppId `
                  -Details "Application created: $createdAppId | Assigned Manager: $($applyRes.assignedManagerName) ($($applyRes.assignedManager)) | Status: $($applyRes.status)"
} catch {
    Report-Result -TestName "POST /api/v1/loans/apply (Submit Loan Application)" -Success $false -Details $_.Exception.Message
}

if ($createdAppId) {
    # Query application by ID
    try {
        $getApp = Invoke-RestMethod -Uri "$LOAN_SVC_URL/api/v1/loans/applications/$createdAppId" -Method Get -TimeoutSec 20
        Report-Result -TestName "GET /api/v1/loans/applications/$createdAppId (Query Application by ID)" `
                      -Success ($getApp.applicationId -eq $createdAppId) `
                      -Details "Verified Status: $($getApp.status) | EMI: ₹$($getApp.calculatedEMI)"
    } catch {
        Report-Result -TestName "GET /api/v1/loans/applications/$createdAppId" -Success $false -Details $_.Exception.Message
    }

    # Query customer applications by email
    try {
        $custApps = Invoke-RestMethod -Uri "$LOAN_SVC_URL/api/v1/loans/applications?customerEmail=$testEmail" -Method Get -TimeoutSec 20
        $count = ($custApps | Measure-Object).Count
        Report-Result -TestName "GET /api/v1/loans/applications?customerEmail=$testEmail" `
                      -Success ($count -ge 1) `
                      -Details "Found $count application(s) for customer"
    } catch {
        Report-Result -TestName "GET /api/v1/loans/applications?customerEmail=..." -Success $false -Details $_.Exception.Message
    }
}

# -------------------------------------------------------------
# SUITE 4: REPORT SERVICE - OPENAPI & ANALYTICS
# -------------------------------------------------------------
Write-Host "`n--- 4. Testing Report Service (Analytics & Docs) ---" -ForegroundColor White

try {
    $apiDocs = Invoke-RestMethod -Uri "$REPORT_SVC_URL/v3/api-docs" -Method Get -TimeoutSec 25
    $hasOpenApi = (![string]::IsNullOrEmpty($apiDocs.openapi))
    Report-Result -TestName "GET /v3/api-docs (OpenAPI Specification)" `
                  -Success $hasOpenApi `
                  -Details "OpenAPI v$($apiDocs.openapi) specification available"
} catch {
    Report-Result -TestName "GET /v3/api-docs (OpenAPI Specification)" -Success $false -Details $_.Exception.Message
}

try {
    $headers = @{
        "X-User-Id"   = "1"
        "X-User-Role" = "ROLE_EMPLOYEE"
    }
    $summary = Invoke-RestMethod -Uri "$REPORT_SVC_URL/api/v1/reports/operations/summary" `
                                 -Headers $headers -Method Get -TimeoutSec 20
    $hasSummary = ($null -ne $summary)
    Report-Result -TestName "GET /api/v1/reports/operations/summary (APIM Role-Authorized Summary)" `
                  -Success $hasSummary `
                  -Details "Summary metrics retrieved: Total Apps: $($summary.totalApplications)"
} catch {
    Report-Result -TestName "GET /api/v1/reports/operations/summary" -Success $false -Details $_.Exception.Message
}

try {
    $headers = @{
        "X-User-Id"   = "1"
        "X-User-Role" = "ROLE_EMPLOYEE"
    }
    $metrics = Invoke-RestMethod -Uri "$REPORT_SVC_URL/api/v1/reports/executives/metrics" `
                                 -Headers $headers -Method Get -TimeoutSec 20
    $metricsCount = ($metrics | Measure-Object).Count
    Report-Result -TestName "GET /api/v1/reports/executives/metrics (Executive Trends)" `
                  -Success ($metricsCount -ge 1) `
                  -Details "Monthly trend data points: $metricsCount (Month: $($metrics[0].month), New Customers: $($metrics[0].newCustomers))"
} catch {
    Report-Result -TestName "GET /api/v1/reports/executives/metrics" -Success $false -Details $_.Exception.Message
}

# -------------------------------------------------------------
# SUITE 5: NOTIFICATION SERVICE & ALERTS (MANAGER & CUSTOMER)
# -------------------------------------------------------------
Write-Host "`n--- 5. Testing Notification Service & Alerts ---" -ForegroundColor White

$NOTIF_SVC_URL = "https://team6-notification-service-function-app-f3afbxhbfnbbaner.southindia-01.azurewebsites.net"

# 5.1 Manager In-App Notifications Queue
try {
    $mgrNotifs = Invoke-RestMethod -Uri "$LOAN_SVC_URL/api/v1/notifications?username=mgr1" -Method Get -TimeoutSec 20
    $notifCount = ($mgrNotifs | Measure-Object).Count
    Report-Result -TestName "GET /api/v1/notifications?username=mgr1 (Manager In-App Alerts)" `
                  -Success ($notifCount -ge 1) `
                  -Details "Retrieved $notifCount alert(s) for manager queue (Latest: $($mgrNotifs[0].title) - $($mgrNotifs[0].message))"
} catch {
    Report-Result -TestName "GET /api/v1/notifications?username=mgr1" -Success $false -Details $_.Exception.Message
}

# 5.2 Azure Logic App Email Alert Dispatch
try {
    $emailPayload = @{
        eventType = "LOAN_APPLIED"
        data = @{
            customerName  = "Suite Test Customer"
            email         = "itsarpitgupta@gmail.com"
            applicationId = $(if ($createdAppId) { $createdAppId } else { "APP-62900096" })
            amount        = "250000"
        }
    } | ConvertTo-Json

    $notifRes = Invoke-RestMethod -Uri "$NOTIF_SVC_URL/api/notify" `
                                  -Method Post -ContentType "application/json" -Body $emailPayload -TimeoutSec 25
    $isDispatched = ($notifRes.status -eq "SUCCESS")
    Report-Result -TestName "POST /api/notify (Logic App Email Alert Dispatch)" `
                  -Success $isDispatched `
                  -Details "Notification dispatched: Status = $($notifRes.status) | $($notifRes.message) (Email sent to itsarpitgupta@gmail.com)"
} catch {
    Report-Result -TestName "POST /api/notify (Logic App Email Alert Dispatch)" -Success $false -Details $_.Exception.Message
}

# -------------------------------------------------------------
# FINAL SCORECARD
# -------------------------------------------------------------
Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " TEST RESULTS SCORECARD: $PASSED_TESTS / $TOTAL_TESTS PASSED" -ForegroundColor $(if ($FAILED_TESTS -eq 0) { "Green" } else { "Yellow" })
Write-Host "========================================================`n" -ForegroundColor Cyan

if ($FAILED_TESTS -eq 0) {
    Write-Host "🎉 ALL API TESTS PASSED 100% SUCCESSFULLY!`n" -ForegroundColor Green
} else {
    Write-Host "⚠️ Some tests encountered issues. Review log above for details.`n" -ForegroundColor Yellow
}

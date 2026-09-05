<#
.SYNOPSIS
    Digital Lending & Banking Capstone - Full End-to-End API Test Suite via Azure API Management (APIM)
.DESCRIPTION
    Automated test suite executing all microservice calls exclusively through Azure API Management Gateway (team6-api-management):
    - Master Data: Loan Schemes & Document Types Catalog
    - Auth & Profiles: Customer Registration, APIM Customer Login, APIM Internal (Manager) Login, Customer Service List & Ping
    - Loan Service: Application Submission, ID Lookup, Customer History Query, Audit Trail
    - Report Service: Operations Summary, Executive Analytics Metrics
    - Notification Service: Manager In-App Alerts Queue (via APIM), Azure Logic App Email Dispatch
    Every API request strictly passes client-key, Ocp-Apim-Subscription-Key, role, and Bearer JWT where applicable.
#>

$ErrorActionPreference = "Continue"

$APIM_BASE_URL = "https://team6-api-management.azure-api.net"
$APIM_KEY      = "e668065d6523405f912e56c3fe3c2ca9"
$NOTIF_SVC_URL = "https://team6-notification-service-function-app-f3afbxhbfnbbaner.southindia-01.azurewebsites.net"

$TOTAL_TESTS  = 0
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

function Get-ApimHeaders {
    param(
        [string]$Role = "ROLE_CUSTOMER",
        [string]$Token = "",
        [switch]$IncludeJson
    )
    $h = @{
        "Ocp-Apim-Subscription-Key" = $APIM_KEY
        "client-key"                = $APIM_KEY
        "X-User-Role"               = $Role
    }
    if ($IncludeJson) {
        $h["Content-Type"] = "application/json"
    }
    if ($Token) {
        $cleanToken = if ($Token.StartsWith("Bearer ")) { $Token } else { "Bearer $Token" }
        $h["Authorization"] = $cleanToken
    }
    return $h
}

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " DIGITAL LENDING CAPSTONE - APIM END-TO-END TEST SUITE" -ForegroundColor Cyan
Write-Host " Gateway: $APIM_BASE_URL" -ForegroundColor DarkCyan
Write-Host "========================================================`n" -ForegroundColor Cyan

# -------------------------------------------------------------
# SUITE 1: MASTER DATA (LOAN & DOCUMENT SERVICES VIA APIM)
# -------------------------------------------------------------
Write-Host "--- 1. Testing Master Data via APIM Gateway ---" -ForegroundColor White

try {
    $schemesUrl = "$APIM_BASE_URL/loan-applications/api/v1/loans/schemes"
    $schemes = Invoke-RestMethod -Uri $schemesUrl -Headers (Get-ApimHeaders -Role "ROLE_EMPLOYEE") -Method Get -TimeoutSec 20
    $schemeCount = ($schemes | Measure-Object).Count
    Report-Result -TestName "GET /loan-applications/api/v1/loans/schemes (Schemes Catalog)" `
                  -Success ($schemeCount -ge 5) `
                  -Details "Retrieved $schemeCount active schemes (e.g. $($schemes[0].schemeName))"
} catch {
    Report-Result -TestName "GET /loan-applications/api/v1/loans/schemes" -Success $false -Details $_.Exception.Message
}

try {
    $docTypesUrl = "$APIM_BASE_URL/documents/api/v1/documents/types"
    $docTypes = Invoke-RestMethod -Uri $docTypesUrl -Headers (Get-ApimHeaders -Role "ROLE_EMPLOYEE") -Method Get -TimeoutSec 20
    $dtCount = ($docTypes | Measure-Object).Count
    Report-Result -TestName "GET /documents/api/v1/documents/types (Master Document Types Catalog)" `
                  -Success ($dtCount -ge 3) `
                  -Details "Retrieved $dtCount supported document types"
} catch {
    Report-Result -TestName "GET /documents/api/v1/documents/types" -Success $false -Details $_.Exception.Message
}

try {
    $tempDocPath = [System.IO.Path]::GetTempFileName() + ".pdf"
    [System.IO.File]::WriteAllBytes($tempDocPath, [System.Text.Encoding]::UTF8.GetBytes("%PDF-1.4 dummy test document"))
    $upResRaw = & curl.exe -s -X POST "$APIM_BASE_URL/documents/api/v1/documents/upload" `
                           -H "Ocp-Apim-Subscription-Key: $APIM_KEY" `
                           -H "client-key: $APIM_KEY" `
                           -H "X-User-Role: ROLE_CUSTOMER" `
                           -F "customerId=CUST-06195662-545d-4e12-9b96-9d0e9ea323cb" `
                           -F "applicationId=APP-37155A60" `
                           -F "documentType=IDENTITY_PROOF" `
                           -F "documentName=SuiteVerificationDoc.pdf" `
                           -F "file=@$tempDocPath"
    $upRes = $upResRaw | ConvertFrom-Json
    Report-Result -TestName "POST /documents/api/v1/documents/upload (Document Upload via APIM)" `
                  -Success ($upRes.documentId -gt 0) `
                  -Details "Uploaded Document ID: $($upRes.documentId) | Blob: $($upRes.blobPath)"
    Remove-Item $tempDocPath -ErrorAction SilentlyContinue
} catch {
    Report-Result -TestName "POST /documents/api/v1/documents/upload" -Success $false -Details $_.Exception.Message
}

# -------------------------------------------------------------
# SUITE 2: AUTHENTICATION & CUSTOMER PROFILES (VIA APIM)
# -------------------------------------------------------------
Write-Host "`n--- 2. Testing Authentication & Profiles via APIM Gateway ---" -ForegroundColor White

# 2.1 Register New Customer via APIM
$randNum = Get-Random -Minimum 10000 -Maximum 99999
$testEmail = "suite_cust_${randNum}@banktest.com"
$testPassword = "Password@123"
$regPayload = @{
    firstName    = "TestFirstName"
    lastName     = "TestLastName"
    email        = $testEmail
    password     = $testPassword
    phoneNumber  = "+91 98765 43210"
    addressLine1 = "100 MG Road"
    city         = "Bengaluru"
    state        = "Karnataka"
    postalCode   = "560001"
    countryCode  = "IN"
} | ConvertTo-Json

$custAuthRes = $null
$registeredLoginId = ""
try {
    $regUrl = "$APIM_BASE_URL/customers/api/customers/auth/register"
    $custAuthRes = Invoke-RestMethod -Uri $regUrl -Method Post `
                                    -Headers (Get-ApimHeaders -Role "ROLE_CUSTOMER" -IncludeJson) `
                                    -Body $regPayload -TimeoutSec 20
    $hasIds = ($custAuthRes.userId -gt 0) -and (![string]::IsNullOrEmpty($custAuthRes.customerId))
    $registeredLoginId = $custAuthRes.username
    Report-Result -TestName "POST /customers/api/customers/auth/register (Customer Registration)" `
                  -Success $hasIds `
                  -Details "Created User_ID: $($custAuthRes.userId) | Customer UUID: $($custAuthRes.customerId)"
} catch {
    Report-Result -TestName "POST /customers/api/customers/auth/register" -Success $false -Details $_.Exception.Message
}

# 2.2 Customer Login via APIM
$custToken = ""
try {
    # APIM user-validator authenticates against the users table loginid (cmto55vth5x)
    $loginUsername = "cmto55vth5x"
    $loginPassword = "password1"

    $loginPayload = @{ username = $loginUsername; password = $loginPassword } | ConvertTo-Json
    $loginUrl = "$APIM_BASE_URL/auth/customer/login"
    $custLoginRes = Invoke-RestMethod -Uri $loginUrl -Method Post `
                                      -Headers (Get-ApimHeaders -Role "ROLE_CUSTOMER" -IncludeJson) `
                                      -Body $loginPayload -TimeoutSec 20
    $custToken = $custLoginRes.access_token
    Report-Result -TestName "POST /auth/customer/login (Customer Login via APIM: cmto55vth5x)" `
                  -Success (![string]::IsNullOrEmpty($custToken)) `
                  -Details "Authenticated customer ($loginUsername). JWT length: $($custToken.Length)"
} catch {
    Report-Result -TestName "POST /auth/customer/login" -Success $false -Details $_.Exception.Message
}

# 2.3 Manager Login via APIM
$mgrToken = ""
try {
    $mgrPayload = @{ username = "mgr1"; password = "Password@123" } | ConvertTo-Json
    $mgrLoginUrl = "$APIM_BASE_URL/auth/internal/login"
    $mgrLoginRes = Invoke-RestMethod -Uri $mgrLoginUrl -Method Post `
                                     -Headers (Get-ApimHeaders -Role "ROLE_EMPLOYEE" -IncludeJson) `
                                     -Body $mgrPayload -TimeoutSec 20
    $mgrToken = $mgrLoginRes.access_token
    Report-Result -TestName "POST /auth/internal/login (Manager Login via APIM: mgr1)" `
                  -Success (![string]::IsNullOrEmpty($mgrToken)) `
                  -Details "Manager verified. JWT length: $($mgrToken.Length)"
} catch {
    Report-Result -TestName "POST /auth/internal/login" -Success $false -Details $_.Exception.Message
}

# 2.4 Customer Service Ping via APIM
try {
    $pingUrl = "$APIM_BASE_URL/customers/api/customers/ping"
    $pingRes = Invoke-RestMethod -Uri $pingUrl -Headers (Get-ApimHeaders -Role "ROLE_CUSTOMER" -Token $custToken) -Method Get -TimeoutSec 20
    $isUp = ($pingRes.status -eq "UP")
    Report-Result -TestName "GET /customers/api/customers/ping (Customer Service Health via APIM)" `
                  -Success $isUp `
                  -Details "Status: $($pingRes.status) | Service: $($pingRes.service)"
} catch {
    Report-Result -TestName "GET /customers/api/customers/ping" -Success $false -Details $_.Exception.Message
}

# 2.5 Customer Profiles List via APIM
try {
    $custListUrl = "$APIM_BASE_URL/customers/api/customers?page=0&size=5"
    $custList = Invoke-RestMethod -Uri $custListUrl -Headers (Get-ApimHeaders -Role "ROLE_CUSTOMER" -Token $custToken) -Method Get -TimeoutSec 20
    $custCount = if ($custList.content) { $custList.content.Count } else { ($custList | Measure-Object).Count }
    Report-Result -TestName "GET /customers/api/customers (Customer Profiles Catalog via APIM)" `
                  -Success ($custCount -ge 1) `
                  -Details "Retrieved $custCount customer profile(s)"
} catch {
    Report-Result -TestName "GET /customers/api/customers" -Success $false -Details $_.Exception.Message
}

# -------------------------------------------------------------
# SUITE 3: LOAN SERVICE - APPLICATION LIFECYCLE (VIA APIM)
# -------------------------------------------------------------
Write-Host "`n--- 3. Testing Loan Service (Application Lifecycle via APIM) ---" -ForegroundColor White

$createdAppId = ""
$customerIdToUse = "f1919793-5e22-4e11-a140-ea0bb02a0f84"
$customerEmailToUse = "jane.doe.1788582483005@example.com"

try {
    $applyBody = @{
        customerId          = $customerIdToUse
        customerName        = "Jane Doe"
        customerEmail       = $customerEmailToUse
        customerPhone       = "9876543210"
        monthlyIncome       = 85000
        existingLiabilities = 4000
        employmentType      = "SALARIED"
        schemeId            = "SCHEME-PL-01"
        loanAmount          = 250000
        tenureMonths        = 24
        documentIds         = @()
    } | ConvertTo-Json

    $applyUrl = "$APIM_BASE_URL/loan-applications/api/v1/loans/apply"
    $applyRes = Invoke-RestMethod -Uri $applyUrl -Method Post `
                                  -Headers (Get-ApimHeaders -Role "ROLE_CUSTOMER" -Token $custToken -IncludeJson) `
                                  -Body $applyBody -TimeoutSec 30
    $createdAppId = $applyRes.applicationId
    $hasAppId = (![string]::IsNullOrEmpty($createdAppId))
    Report-Result -TestName "POST /loan-applications/api/v1/loans/apply (Submit Loan Application via APIM)" `
                  -Success $hasAppId `
                  -Details "Created: $createdAppId | Assigned Manager: $($applyRes.assignedManagerName) ($($applyRes.assignedManager)) | Status: $($applyRes.status)"
} catch {
    Report-Result -TestName "POST /loan-applications/api/v1/loans/apply" -Success $false -Details $_.Exception.Message
}

if ($createdAppId) {
    # Query application by ID via APIM
    try {
        $getAppUrl = "$APIM_BASE_URL/loan-applications/api/v1/loans/applications/$createdAppId"
        $getApp = Invoke-RestMethod -Uri $getAppUrl -Headers (Get-ApimHeaders -Role "ROLE_EMPLOYEE" -Token $mgrToken) -Method Get -TimeoutSec 20
        Report-Result -TestName "GET /loan-applications/api/v1/loans/applications/$createdAppId (Query Application by ID via APIM)" `
                      -Success ($getApp.applicationId -eq $createdAppId) `
                      -Details "Verified Status: $($getApp.status) | EMI: ₹$($getApp.calculatedEMI)"
    } catch {
        Report-Result -TestName "GET /loan-applications/api/v1/loans/applications/$createdAppId" -Success $false -Details $_.Exception.Message
    }

    # Query customer applications by email via APIM
    try {
        $custAppsUrl = "$APIM_BASE_URL/loan-applications/api/v1/loans/applications?customerEmail=$customerEmailToUse"
        $custApps = Invoke-RestMethod -Uri $custAppsUrl -Headers (Get-ApimHeaders -Role "ROLE_CUSTOMER" -Token $custToken) -Method Get -TimeoutSec 20
        $count = ($custApps | Measure-Object).Count
        Report-Result -TestName "GET /loan-applications/api/v1/loans/applications?customerEmail=... (Customer History via APIM)" `
                      -Success ($count -ge 1) `
                      -Details "Found $count application(s) for customer"
    } catch {
        Report-Result -TestName "GET /loan-applications/api/v1/loans/applications?customerEmail=..." -Success $false -Details $_.Exception.Message
    }

    # Query audit logs via APIM
    try {
        $auditUrl = "$APIM_BASE_URL/loan-applications/api/v1/loans/applications/$createdAppId/audit-logs"
        $auditLogs = Invoke-RestMethod -Uri $auditUrl -Headers (Get-ApimHeaders -Role "ROLE_EMPLOYEE" -Token $mgrToken) -Method Get -TimeoutSec 20
        $logCount = ($auditLogs | Measure-Object).Count
        Report-Result -TestName "GET /loan-applications/api/v1/loans/applications/$createdAppId/audit-logs (Application Audit Trail via APIM)" `
                      -Success ($logCount -ge 1) `
                      -Details "Found $logCount audit log entry(s)"
    } catch {
        Report-Result -TestName "GET /loan-applications/api/v1/loans/applications/$createdAppId/audit-logs" -Success $false -Details $_.Exception.Message
    }
}

# -------------------------------------------------------------
# SUITE 4: REPORT SERVICE - ANALYTICS VIA APIM
# -------------------------------------------------------------
Write-Host "`n--- 4. Testing Report Service via APIM Gateway ---" -ForegroundColor White

try {
    $summaryUrl = "$APIM_BASE_URL/api/v1/reports/operations/summary"
    $summary = Invoke-RestMethod -Uri $summaryUrl -Headers (Get-ApimHeaders -Role "ROLE_EMPLOYEE" -Token $mgrToken) -Method Get -TimeoutSec 20
    $hasSummary = ($null -ne $summary.statusCounts)
    Report-Result -TestName "GET /api/v1/reports/operations/summary (APIM Role-Authorized Summary)" `
                  -Success $hasSummary `
                  -Details "Retrieved status counts (Pending: $($summary.statusCounts.DOCUMENT_REVIEW_PENDING), Approved: $($summary.statusCounts.APPROVED))"
} catch {
    Report-Result -TestName "GET /api/v1/reports/operations/summary" -Success $false -Details $_.Exception.Message
}

try {
    $metricsUrl = "$APIM_BASE_URL/api/v1/reports/executives/metrics"
    $metrics = Invoke-RestMethod -Uri $metricsUrl -Headers (Get-ApimHeaders -Role "ROLE_EMPLOYEE" -Token $mgrToken) -Method Get -TimeoutSec 20
    $metricsCount = ($metrics | Measure-Object).Count
    Report-Result -TestName "GET /api/v1/reports/executives/metrics (APIM Executive Analytics)" `
                  -Success ($metricsCount -ge 1) `
                  -Details "Monthly trend data points: $metricsCount (Month: $($metrics[0].month), New Customers: $($metrics[0].newCustomers))"
} catch {
    Report-Result -TestName "GET /api/v1/reports/executives/metrics" -Success $false -Details $_.Exception.Message
}

# -------------------------------------------------------------
# SUITE 5: NOTIFICATION SERVICE & ALERTS (VIA APIM & LOGIC APP)
# -------------------------------------------------------------
Write-Host "`n--- 5. Testing Notification Service & Alerts ---" -ForegroundColor White

# 5.1 Manager In-App Notifications Queue via APIM Gateway
try {
    $notifsUrl = "$APIM_BASE_URL/loan-applications/api/v1/notifications?username=mgr1"
    $mgrNotifs = Invoke-RestMethod -Uri $notifsUrl -Headers (Get-ApimHeaders -Role "ROLE_EMPLOYEE" -Token $mgrToken) -Method Get -TimeoutSec 20
    $notifCount = ($mgrNotifs | Measure-Object).Count
    Report-Result -TestName "GET /loan-applications/api/v1/notifications?username=mgr1 (Manager In-App Alerts via APIM)" `
                  -Success ($notifCount -ge 1) `
                  -Details "Retrieved $notifCount alert(s) for manager queue (Latest: $($mgrNotifs[0].title))"
} catch {
    Report-Result -TestName "GET /loan-applications/api/v1/notifications?username=mgr1" -Success $false -Details $_.Exception.Message
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
    Write-Host "🎉 ALL API TESTS PASSED 100% SUCCESSFULLY VIA APIM GATEWAY!`n" -ForegroundColor Green
} else {
    Write-Host "⚠️ Some tests encountered issues. Review log above for details.`n" -ForegroundColor Yellow
}

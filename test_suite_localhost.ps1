<#
.SYNOPSIS
    Digital Lending & Banking Capstone - Localhost Loan Service Test Suite
.DESCRIPTION
    Automated test suite executing loan service calls against localhost:8080
    to verify the recently added SecurityConfig and @PreAuthorize annotations.
#>

$ErrorActionPreference = "Continue"

$LOAN_SVC_URL = "http://localhost:8080"

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

function Get-LocalHeaders {
    param(
        [string]$Role = "ROLE_CUSTOMER",
        [string]$UserId = "test-user-id",
        [switch]$IncludeJson
    )
    $h = @{
        "X-User-Role" = $Role
        "X-User-Id"   = $UserId
    }
    if ($IncludeJson) {
        $h["Content-Type"] = "application/json"
    }
    return $h
}

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " DIGITAL LENDING CAPSTONE - LOCALHOST TEST SUITE" -ForegroundColor Cyan
Write-Host " Loan Service: $LOAN_SVC_URL" -ForegroundColor DarkCyan
Write-Host "========================================================`n" -ForegroundColor Cyan


# -------------------------------------------------------------
# LOAN SERVICE - APPLICATION LIFECYCLE (VIA LOCALHOST)
# -------------------------------------------------------------
Write-Host "`n--- Testing Loan Service (Application Lifecycle via Localhost) ---" -ForegroundColor White

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

    $applyUrl = "$LOAN_SVC_URL/api/v1/loans/apply"
    $applyRes = Invoke-RestMethod -Uri $applyUrl -Method Post `
                                  -Headers (Get-LocalHeaders -Role "ROLE_CUSTOMER" -IncludeJson) `
                                  -Body $applyBody -TimeoutSec 30
    $createdAppId = $applyRes.applicationId
    $hasAppId = (![string]::IsNullOrEmpty($createdAppId))
    Report-Result -TestName "POST /api/v1/loans/apply (Submit Loan Application)" `
                  -Success $hasAppId `
                  -Details "Created: $createdAppId | Status: $($applyRes.status)"
} catch {
    Report-Result -TestName "POST /api/v1/loans/apply" -Success $false -Details $_.Exception.Message
}

if ($createdAppId) {
    # Query application by ID 
    try {
        $getAppUrl = "$LOAN_SVC_URL/api/v1/loans/applications/$createdAppId"
        $getApp = Invoke-RestMethod -Uri $getAppUrl -Headers (Get-LocalHeaders -Role "ROLE_EMPLOYEE") -Method Get -TimeoutSec 20
        Report-Result -TestName "GET /api/v1/loans/applications/$createdAppId (Query Application by ID)" `
                      -Success ($getApp.applicationId -eq $createdAppId) `
                      -Details "Verified Status: $($getApp.status) | EMI: ₹$($getApp.calculatedEMI)"
    } catch {
        Report-Result -TestName "GET /api/v1/loans/applications/$createdAppId" -Success $false -Details $_.Exception.Message
    }

    # Query customer applications by email
    try {
        $custAppsUrl = "$LOAN_SVC_URL/api/v1/loans/applications?customerEmail=$customerEmailToUse"
        $custApps = Invoke-RestMethod -Uri $custAppsUrl -Headers (Get-LocalHeaders -Role "ROLE_CUSTOMER") -Method Get -TimeoutSec 20
        $count = ($custApps | Measure-Object).Count
        Report-Result -TestName "GET /api/v1/loans/applications?customerEmail=... (Customer History)" `
                      -Success ($count -ge 1) `
                      -Details "Found $count application(s) for customer"
    } catch {
        Report-Result -TestName "GET /api/v1/loans/applications?customerEmail=..." -Success $false -Details $_.Exception.Message
    }

    # Query audit logs
    try {
        $auditUrl = "$LOAN_SVC_URL/api/v1/loans/applications/$createdAppId/audit-logs"
        $auditLogs = Invoke-RestMethod -Uri $auditUrl -Headers (Get-LocalHeaders -Role "ROLE_EMPLOYEE") -Method Get -TimeoutSec 20
        $logCount = ($auditLogs | Measure-Object).Count
        Report-Result -TestName "GET /api/v1/loans/applications/$createdAppId/audit-logs (Application Audit Trail)" `
                      -Success ($logCount -ge 1) `
                      -Details "Found $logCount audit log entry(s)"
    } catch {
        Report-Result -TestName "GET /api/v1/loans/applications/$createdAppId/audit-logs" -Success $false -Details $_.Exception.Message
    }
}

# -------------------------------------------------------------
# FINAL SCORECARD
# -------------------------------------------------------------
Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " TEST RESULTS SCORECARD: $PASSED_TESTS / $TOTAL_TESTS PASSED" -ForegroundColor $(if ($FAILED_TESTS -eq 0) { "Green" } else { "Yellow" })
Write-Host "========================================================`n" -ForegroundColor Cyan

if ($FAILED_TESTS -eq 0) {
    Write-Host "🎉 ALL API TESTS PASSED SUCCESSFULLY VIA LOCALHOST!`n" -ForegroundColor Green
} else {
    Write-Host "⚠️ Some tests encountered issues. Review log above for details.`n" -ForegroundColor Yellow
}

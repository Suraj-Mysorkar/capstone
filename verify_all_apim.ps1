$apimKey = "e668065d6523405f912e56c3fe3c2ca9"

Write-Host "`n=== 1. Testing Employee Auth via APIM ===" -ForegroundColor Cyan
$empBody = @{ username = "mgr1"; password = "Password@123" } | ConvertTo-Json
$empHeaders = @{
    "Content-Type" = "application/json"
    "Ocp-Apim-Subscription-Key" = $apimKey
    "client-key" = $apimKey
    "X-User-Role" = "ROLE_EMPLOYEE"
}
$empRes = Invoke-RestMethod -Uri "https://team6-api-management.azure-api.net/auth/internal/login" -Method Post -Headers $empHeaders -Body $empBody
Write-Host "Employee Auth OK! User:" $empRes.username "Role:" $empRes.roles
$empJwt = $empRes.access_token

Write-Host "`n=== 2. Testing Customer Auth via APIM ===" -ForegroundColor Cyan
$custBody = @{ username = "cmto55vth5x"; password = "password1" } | ConvertTo-Json
$custHeaders = @{
    "Content-Type" = "application/json"
    "Ocp-Apim-Subscription-Key" = $apimKey
    "client-key" = $apimKey
    "X-User-Role" = "ROLE_CUSTOMER"
}
$custRes = Invoke-RestMethod -Uri "https://team6-api-management.azure-api.net/auth/customer/login" -Method Post -Headers $custHeaders -Body $custBody
Write-Host "Customer Auth OK! Token received. Length:" $custRes.access_token.Length
$custJwt = $custRes.access_token

Write-Host "`n=== 3. Testing Loan Schemes via APIM ===" -ForegroundColor Cyan
$loanHeaders = @{
    "Ocp-Apim-Subscription-Key" = $apimKey
    "client-key" = $apimKey
    "X-User-Role" = "ROLE_EMPLOYEE"
    "Authorization" = "Bearer $empJwt"
}
$schemes = Invoke-RestMethod -Uri "https://team6-api-management.azure-api.net/loan-applications/api/v1/loans/schemes" -Headers $loanHeaders
Write-Host "Schemes count:" $schemes.Count

Write-Host "`n=== 4. Testing Loan Applications via APIM ===" -ForegroundColor Cyan
$apps = Invoke-RestMethod -Uri "https://team6-api-management.azure-api.net/loan-applications/api/v1/loans/applications" -Headers $loanHeaders
Write-Host "Applications count:" $apps.Count

Write-Host "`n=== 5. Testing Document Types via APIM ===" -ForegroundColor Cyan
$docHeaders = @{
    "Ocp-Apim-Subscription-Key" = $apimKey
    "client-key" = $apimKey
    "X-User-Role" = "ROLE_EMPLOYEE"
    "Authorization" = "Bearer $empJwt"
}
$docTypes = Invoke-RestMethod -Uri "https://team6-api-management.azure-api.net/documents/api/v1/documents/types" -Headers $docHeaders
Write-Host "Document types count:" $docTypes.Count

Write-Host "`n=== 6. Testing Notifications via APIM ===" -ForegroundColor Cyan
$notifs = Invoke-RestMethod -Uri "https://team6-api-management.azure-api.net/loan-applications/api/v1/notifications?username=mgr1" -Headers $loanHeaders
Write-Host "Notifications count:" $notifs.Count

Write-Host "`n=== 7. Testing Customer Service Ping via APIM ===" -ForegroundColor Cyan
$custSvcHeaders = @{
    "Ocp-Apim-Subscription-Key" = $apimKey
    "client-key" = $apimKey
    "X-User-Role" = "ROLE_CUSTOMER"
    "Authorization" = "Bearer $custJwt"
}
try {
    $ping = Invoke-RestMethod -Uri "https://team6-api-management.azure-api.net/customers/api/customers/ping" -Headers $custSvcHeaders
    Write-Host "Customer service ping:" ($ping | ConvertTo-Json -Compress)
} catch {
    Write-Host "Customer service ping failed:" $_.Exception.Message
}

Write-Host "`n=== 8. Testing Customer Service List via APIM ===" -ForegroundColor Cyan
try {
    $custs = Invoke-RestMethod -Uri "https://team6-api-management.azure-api.net/customers/api/customers?page=0&size=5" -Headers $custSvcHeaders
    Write-Host "Customers list items:" $custs.content.Count
} catch {
    Write-Host "Customer service list failed:" $_.Exception.Message
}

Write-Host "`n=== 9. Testing Report Service Operations Summary via APIM ===" -ForegroundColor Cyan
$reportHeaders = @{
    "Ocp-Apim-Subscription-Key" = $apimKey
    "client-key" = $apimKey
    "X-User-Role" = "ROLE_EMPLOYEE"
    "Authorization" = "Bearer $empJwt"
}
try {
    $rep = Invoke-RestMethod -Uri "https://team6-api-management.azure-api.net/api/v1/reports/operations/summary" -Headers $reportHeaders
    Write-Host "Report service operations summary:" ($rep | ConvertTo-Json -Compress)
} catch {
    Write-Host "Report service operations summary failed:" $_.Exception.Message
}

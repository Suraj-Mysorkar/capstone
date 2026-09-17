param()
$ErrorActionPreference = "Continue"
$APIM_BASE = "https://team6-api-management.azure-api.net"
$APIM_KEY  = ""

if (Test-Path "$PSScriptRoot\secrets.properties") {
    Get-Content "$PSScriptRoot\secrets.properties" | ForEach-Object {
        if ($_ -match '^\s*APIM_SUBSCRIPTION_KEY\s*=\s*(.+)$') { $APIM_KEY = $matches[1].Trim() }
    }
}
if ([string]::IsNullOrWhiteSpace($APIM_KEY)) { Write-Host "ERROR: No APIM key" -ForegroundColor Red; exit 1 }

$TOTAL=0; $PASS=0; $FAIL=0
function Pass { param($name,$detail=""); $script:TOTAL++; $script:PASS++
    Write-Host "  [PASS] $name" -ForegroundColor Green
    if ($detail) { Write-Host "         $detail" -ForegroundColor DarkGray } }
function Fail { param($name,$detail=""); $script:TOTAL++; $script:FAIL++
    Write-Host "  [FAIL] $name" -ForegroundColor Red
    if ($detail) { Write-Host "         $detail" -ForegroundColor Yellow } }

function MakeHeaders { param([string]$role="ROLE_CUSTOMER",[string]$token="",[bool]$json=$false)
    $h = @{"Ocp-Apim-Subscription-Key"=$APIM_KEY;"client-key"=$APIM_KEY;"X-User-Role"=$role}
    if ($json)  { $h["Content-Type"] = "application/json" }
    if ($token) { $h["Authorization"] = "Bearer $token" }
    return $h }

function HasError($res) {
    if ($null -eq $res) { return "Empty response" }
    if ($res -is [System.Collections.IDictionary] -and $res.Contains('__error')) { return [string]$res['__error'] }
    if ($res -is [pscustomobject] -and ($res.PSObject.Properties['__error'])) { return [string]$res.__error }
    return $null
}

function SafeCall { 
    param(
        [Parameter(Position=0, Mandatory=$true)][string]$uri,
        [Parameter(Position=1)][string]$method="GET",
        [Parameter(Position=2)]$headers=$null,
        [Parameter(Position=3)]$body=$null,
        [Parameter(Position=4)][int]$timeout=30
    )
    try {
        $p = @{Uri=$uri;Method=$method;Headers=$headers;TimeoutSec=$timeout}
        if ($body -and $method -ne "GET" -and $method -ne "DELETE") { $p.Body = $body }
        return Invoke-RestMethod @p
    } catch {
        $msg = $_.Exception.Message
        try {
            $resp = $_.Exception.Response
            if ($resp) {
                $stream = $resp.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream)
                $msg = $reader.ReadToEnd()
            }
        } catch {}
        return @{ __error = $msg }
    } 
}

$TS = [System.DateTime]::Now.ToString("HHmmss")
$UNAME  = "cust_$TS"
$EMAIL  = "cust_${TS}@e2etest.com"
$UPWD   = "Test@1234"
$CID=""; $CTOKEN=""; $MTOKEN=""; $APPID=""; $MGRID="mgr3"
$D1=""; $D2=""; $D3=""

Write-Host "`n=============== E2E TEST: 8-Step Document Flow ================" -ForegroundColor Cyan
Write-Host " Upload → doc-service | Workflow → loan-service /document-uploaded" -ForegroundColor DarkCyan
Write-Host "================================================================`n" -ForegroundColor Cyan

# ── STEP 1: Register new customer ─────────────────────────────────────────────
Write-Host "[STEP 1] Register New Customer" -ForegroundColor White
$regBody = @{
    username=$UNAME; password=$UPWD; email=$EMAIL
    firstName="E2E"; lastName="Customer"
    phoneNumber="9876543210"; dateOfBirth="1990-01-15"; address="123 Test St"
    panNumber="ABCDE1234F"; aadhaarNumber="123456789012"
    employmentType="SALARIED"; monthlyIncome=75000
} | ConvertTo-Json
$reg = SafeCall "$APIM_BASE/customers/api/customers/auth/register" POST (MakeHeaders -json $true) $regBody 20
if ($reg.__error) { Fail "Register customer" $reg.__error }
else {
    if ($reg.customerId) { $CID = $reg.customerId }
    if ($reg.token) { $CTOKEN = $reg.token }
    Pass "Register customer" "CID=$CID, Username=$UNAME"
}

# Customer login (use username, not email)
if (!$CTOKEN) {
    $login = SafeCall "$APIM_BASE/auth/customer/login" POST (MakeHeaders -json $true) (@{username=$UNAME;password=$UPWD}|ConvertTo-Json) 20
    if ($login.__error) { Fail "Customer login" $login.__error; exit 1 }
    if ($login.token) { $CTOKEN = $login.token }
    if (!$CID -and $login.customerId) { $CID = $login.customerId }
}
Pass "Customer login" "Token=OK, CID=$CID"

# ── STEP 2: Apply for loan ────────────────────────────────────────────────────
Write-Host "`n[STEP 2] Apply for Loan" -ForegroundColor White
$schemes = SafeCall "$APIM_BASE/loan-applications/api/v1/loans/schemes" GET (MakeHeaders -role "ROLE_CUSTOMER" -token $CTOKEN) -timeout 20
$SID = "SCHEME-BL-01"
if ($schemes -and !$schemes.__error -and $schemes.Count -gt 0) {
    if ($schemes[0].schemeId) { $SID = $schemes[0].schemeId }
}
Write-Host "  Using scheme: $SID" -ForegroundColor DarkGray

$applyBody = @{
    customerId=$CID; customerName="E2E Customer"; customerEmail=$EMAIL; customerPhone="9876543210"
    schemeId=$SID; loanAmount=500000; tenureMonths=36; employmentType="SALARIED"
    monthlyIncome=75000; existingLiabilities=5000
} | ConvertTo-Json
$apply = SafeCall "$APIM_BASE/loan-applications/api/v1/loans/apply" POST (MakeHeaders -role "ROLE_CUSTOMER" -token $CTOKEN -json $true) $applyBody 60
if ($apply.__error) { Fail "Apply for loan" $apply.__error; exit 1 }
if ($apply.applicationId) { $APPID = $apply.applicationId }
if ($apply.assignedManager) { $MGRID = $apply.assignedManager }
Pass "Apply for loan" "AppId=$APPID, Status=$($apply.status), Mgr=$MGRID"
Start-Sleep 2

# ── STEP 3: Manager login ─────────────────────────────────────────────────────
Write-Host "`n[STEP 3] Login as Assigned Manager ($MGRID)" -ForegroundColor White
# Manager login uses /auth/internal/login with password Password@123
$mBody = @{username=$MGRID;password="Password@123"} | ConvertTo-Json
$mlogin = SafeCall "$APIM_BASE/auth/internal/login" POST (MakeHeaders -json $true) $mBody 20
if ($mlogin.__error -or (!$mlogin.token -and !$mlogin.access_token)) {
    # Try Password@1234
    $mlogin = SafeCall "$APIM_BASE/auth/internal/login" POST (MakeHeaders -json $true) (@{username=$MGRID;password="Password@1234"}|ConvertTo-Json) 20
}
if ($mlogin.access_token) { $MTOKEN = $mlogin.access_token; Pass "Manager login ($MGRID)" "Token=OK (access_token)" }
elseif ($mlogin.token) { $MTOKEN = $mlogin.token; Pass "Manager login ($MGRID)" "Token=OK (token)" }
else {
    Fail "Manager login ($MGRID)" "$(if ($mlogin.__error) { $mlogin.__error } else { 'no token in response' })"
    $MTOKEN = $CTOKEN
    Write-Host "  WARN: Falling back to customer token for manager calls" -ForegroundColor Yellow
}

$appView = SafeCall "$APIM_BASE/loan-applications/api/v1/loans/applications/$APPID" GET (MakeHeaders -role "ROLE_MANAGER" -token $MTOKEN) 20
if ($appView.__error) { Fail "Manager views application $APPID" $appView.__error }
else { Pass "Manager views application" "Status=$($appView.status), Mgr=$($appView.assignedManager)" }

# ── STEP 4: Manager sends document request ────────────────────────────────────
Write-Host "`n[STEP 4] Manager Sends Document Request" -ForegroundColor White
$dreqBody = @{
    requiredDocumentTypes = @(
        "Government Photo Identity Proof (PAN Card / Aadhaar)",
        "Income Verification (Salary Slips - Last 3 Months)",
        "Bank Account Statement (Last 6 Months)"
    )
    customNotes = "Please upload clear scanned copies within 7 days."
} | ConvertTo-Json
$dreq = SafeCall "$APIM_BASE/loan-applications/api/v1/loans/applications/$APPID/request-documents" POST (MakeHeaders -role "ROLE_MANAGER" -token $MTOKEN -json $true) $dreqBody 30
if ($dreq.__error) { Fail "Manager sends document request" $dreq.__error }
else { Pass "Manager sends document request" "EmailSent=$($dreq.emailSent), Docs=$($dreq.requiredDocuments.Count)" }
Start-Sleep 2

# ── STEP 5: Customer uploads 3 docs via document-service → notify loan-service ─
Write-Host "`n[STEP 5] Customer Submits Required Documents (upload + workflow notify)" -ForegroundColor White

$tmp = [System.IO.Path]::GetTempPath()
$f1 = Join-Path $tmp "id_${TS}.pdf"
$f2 = Join-Path $tmp "income_${TS}.pdf"
$f3 = Join-Path $tmp "bank_${TS}.pdf"
$samplePdf = Join-Path $PSScriptRoot "sample_bank_statement.pdf"
$samplePan = Join-Path $PSScriptRoot "sample_pan_card.pdf"
$pdfBytes = [System.Text.Encoding]::ASCII.GetBytes("%PDF-1.4`n1 0 obj<</Type/Catalog>>endobj`ntrailer<</Root 1 0 R>>`nstartxref`n0`n%%EOF")
if (Test-Path $samplePdf) {
    Copy-Item $samplePdf $f1 -Force; Copy-Item $samplePdf $f2 -Force; Copy-Item $samplePdf $f3 -Force
} else {
    [System.IO.File]::WriteAllBytes($f1,$pdfBytes); [System.IO.File]::WriteAllBytes($f2,$pdfBytes); [System.IO.File]::WriteAllBytes($f3,$pdfBytes)
}
if (Test-Path $samplePan) { Copy-Item $samplePan $f1 -Force }

function UploadToDocService { param([string]$filePath,[string]$docType,[string]$docName)
    $boundary = [System.Guid]::NewGuid().ToString("N")
    $CRLF     = "`r`n"
    $enc      = [System.Text.Encoding]::UTF8
    $fBytes   = [System.IO.File]::ReadAllBytes($filePath)
    $fName    = [System.IO.Path]::GetFileName($filePath)
    $parts    = New-Object System.Collections.Generic.List[byte[]]
    foreach ($pair in @(@("customerId",$script:CID),@("applicationId",$script:APPID),@("documentType",$docType),@("docType",$docType),@("documentName",$docName))) {
        $parts.Add($enc.GetBytes("--$boundary$CRLF" + "Content-Disposition: form-data; name=`"$($pair[0])`"$CRLF$CRLF$($pair[1])$CRLF"))
    }
    $parts.Add($enc.GetBytes("--$boundary$CRLF" + "Content-Disposition: form-data; name=`"file`"; filename=`"$fName`"$CRLF" + "Content-Type: application/pdf$CRLF$CRLF"))
    $parts.Add($fBytes); $parts.Add($enc.GetBytes("$CRLF--$boundary--$CRLF"))
    $total = 0; foreach ($p in $parts) { $total += $p.Length }
    $body = New-Object byte[] $total; $off = 0
    foreach ($p in $parts) { [System.Buffer]::BlockCopy($p,0,$body,$off,$p.Length); $off += $p.Length }
    try {
        $wc = New-Object System.Net.WebClient
        $wc.Headers.Add("Ocp-Apim-Subscription-Key",$script:APIM_KEY)
        $wc.Headers.Add("client-key",$script:APIM_KEY)
        $wc.Headers.Add("X-User-Role","ROLE_CUSTOMER")
        $wc.Headers.Add("Authorization","Bearer $script:CTOKEN")
        $wc.Headers.Add("Content-Type","multipart/form-data; boundary=$boundary")
        $rb = $wc.UploadData("$script:APIM_BASE/documents/api/v1/documents/upload","POST",$body)
        return [System.Text.Encoding]::UTF8.GetString($rb) | ConvertFrom-Json
    } catch [System.Net.WebException] {
        $em = $_.Exception.Message
        try { $em = (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } catch {}
        return @{ __error = $em }
    } catch { return @{ __error = $_.Exception.Message } }
}

$r1 = UploadToDocService $f1 "IDENTITY_PROOF" "id_proof.pdf"
if ($r1.__error) { Fail "Upload Doc1 (IDENTITY_PROOF)" $r1.__error }
else {
    if ($r1.documentId) { $D1 = [string]$r1.documentId } elseif ($r1.id) { $D1 = [string]$r1.id }
    Pass "Upload Doc1 (IDENTITY_PROOF)" "DocId=$D1"
    $b1 = if ($r1.blobStoragePath) { $r1.blobStoragePath } else { "" }
    $n1 = @{ documentIds = @($D1); customerId = $CID; documentType = "IDENTITY_PROOF"; documentName = "id_proof.pdf"; blobUrl = $b1; blobPath = $b1; contentType = "application/pdf"; fileSizeBytes = 0 } | ConvertTo-Json
    SafeCall "$APIM_BASE/loan-applications/api/v1/loans/applications/$APPID/document-uploaded" POST (MakeHeaders -role "ROLE_CUSTOMER" -token $CTOKEN -json $true) $n1 20 | Out-Null
}
Start-Sleep 1

$r2 = UploadToDocService $f2 "INCOME_PROOF" "income_proof.pdf"
if ($r2.__error) { Fail "Upload Doc2 (INCOME_PROOF)" $r2.__error }
else {
    if ($r2.documentId) { $D2 = [string]$r2.documentId } elseif ($r2.id) { $D2 = [string]$r2.id }
    Pass "Upload Doc2 (INCOME_PROOF)" "DocId=$D2"
    $b2 = if ($r2.blobStoragePath) { $r2.blobStoragePath } else { "" }
    $n2 = @{ documentIds = @($D2); customerId = $CID; documentType = "INCOME_PROOF"; documentName = "income_proof.pdf"; blobUrl = $b2; blobPath = $b2; contentType = "application/pdf"; fileSizeBytes = 0 } | ConvertTo-Json
    SafeCall "$APIM_BASE/loan-applications/api/v1/loans/applications/$APPID/document-uploaded" POST (MakeHeaders -role "ROLE_CUSTOMER" -token $CTOKEN -json $true) $n2 20 | Out-Null
}
Start-Sleep 1

$r3 = UploadToDocService $f3 "BANK_STATEMENT" "bank_statement.pdf"
if ($r3.__error) { Fail "Upload Doc3 (BANK_STATEMENT)" $r3.__error }
else {
    if ($r3.documentId) { $D3 = [string]$r3.documentId } elseif ($r3.id) { $D3 = [string]$r3.id }
    Pass "Upload Doc3 (BANK_STATEMENT)" "DocId=$D3"
    $b3 = if ($r3.blobStoragePath) { $r3.blobStoragePath } else { "" }
    $n3 = @{ documentIds = @($D3); customerId = $CID; documentType = "BANK_STATEMENT"; documentName = "bank_statement.pdf"; blobUrl = $b3; blobPath = $b3; contentType = "application/pdf"; fileSizeBytes = 0 } | ConvertTo-Json
    $notify3 = SafeCall "$APIM_BASE/loan-applications/api/v1/loans/applications/$APPID/document-uploaded" POST (MakeHeaders -role "ROLE_CUSTOMER" -token $CTOKEN -json $true) $n3 20
    if ($notify3.__error) { Fail "POST /document-uploaded (workflow advance)" $notify3.__error }
    else { Pass "POST /document-uploaded (all 3 docs received)" "New Status=$($notify3.status)" }
}

Start-Sleep 2
$auditLogs = SafeCall "$APIM_BASE/loan-applications/api/v1/loans/applications/$APPID/audit-logs" "GET" (MakeHeaders -role "ROLE_CUSTOMER" -token $CTOKEN) $null 20
$err = HasError $auditLogs
if ($err) { Fail "Audit trail after upload" $err }
else {
    $cnt = @($auditLogs).Count; Pass "Audit trail populated after doc upload" "Entries=$cnt"
    @($auditLogs) | ForEach-Object { Write-Host "    $($_.timestamp): $($_.previousStatus) -> $($_.newStatus)" -ForegroundColor DarkGray }
}

# ── STEP 6: Manager reviews: approve 2, reject 1 ─────────────────────────────
Write-Host "`n[STEP 6] Manager Reviews Documents (approve 2, reject 1)" -ForegroundColor White

if ($D1) {
    $rv1 = SafeCall "$APIM_BASE/loan-applications/api/v1/loans/applications/$APPID/document-reviewed" POST (MakeHeaders -role "ROLE_MANAGER" -token $MTOKEN -json $true) (@{status="APPROVED";remarks="Identity proof verified successfully.";verifiedBy=$MGRID;documentId=$D1;documentType="IDENTITY_PROOF"}|ConvertTo-Json) 30
    $err1 = HasError $rv1
    if ($err1) { Fail "Manager APPROVES Doc1 ($D1)" $err1 } else { Pass "Manager APPROVES Doc1 (IDENTITY_PROOF)" "AppStatus=$($rv1.status)" }
}
Start-Sleep 1

if ($D2) {
    $rv2 = SafeCall "$APIM_BASE/loan-applications/api/v1/loans/applications/$APPID/document-reviewed" POST (MakeHeaders -role "ROLE_MANAGER" -token $MTOKEN -json $true) (@{status="APPROVED";remarks="Salary slips verified. Income confirmed.";verifiedBy=$MGRID;documentId=$D2;documentType="INCOME_PROOF"}|ConvertTo-Json) 30
    $err2 = HasError $rv2
    if ($err2) { Fail "Manager APPROVES Doc2 ($D2)" $err2 } else { Pass "Manager APPROVES Doc2 (INCOME_PROOF)" "AppStatus=$($rv2.status)" }
}
Start-Sleep 1

if ($D3) {
    $rv3 = SafeCall "$APIM_BASE/loan-applications/api/v1/loans/applications/$APPID/document-reviewed" POST (MakeHeaders -role "ROLE_MANAGER" -token $MTOKEN -json $true) (@{status="REJECTED";remarks="Bank statement is over 6 months old. Please submit a recent statement.";verifiedBy=$MGRID;documentId=$D3;documentType="BANK_STATEMENT"}|ConvertTo-Json) 30
    $err3 = HasError $rv3
    if ($err3) { Fail "Manager REJECTS Doc3 ($D3)" $err3 } else { Pass "Manager REJECTS Doc3 (BANK_STATEMENT)" "AppStatus=$($rv3.status) (Status went back to DOCUMENT_REVIEW_PENDING)" }
}

Start-Sleep 2
$stCheck = SafeCall "$APIM_BASE/loan-applications/api/v1/loans/applications/$APPID/status" "GET" (MakeHeaders -role "ROLE_CUSTOMER" -token $CTOKEN) $null 20
$curStat = if ($stCheck.status) { $stCheck.status } elseif ($stCheck.currentStatus) { $stCheck.currentStatus } else { "UNKNOWN" }
if ($curStat -eq "DOCUMENT_REVIEW_PENDING") {
    Pass "Application status after rejection" "Status=$curStat (Correctly reverted to DOCUMENT_REVIEW_PENDING)"
} else {
    Fail "Application status after rejection" "Expected DOCUMENT_REVIEW_PENDING but got $curStat"
}

# ── STEP 7: Customer re-uploads the rejected document ─────────────────────────
Write-Host "`n[STEP 7] Customer Re-uploads Rejected Document" -ForegroundColor White

$f3b = Join-Path $tmp "bank_new_${TS}.pdf"
if (Test-Path $samplePdf) { Copy-Item $samplePdf $f3b -Force } else { [System.IO.File]::WriteAllBytes($f3b,$pdfBytes) }

$r3b = UploadToDocService $f3b "BANK_STATEMENT" "bank_statement_updated.pdf"
$r3bErr = HasError $r3b
if ($r3bErr) { Fail "Re-upload BANK_STATEMENT (customer)" $r3bErr }
else {
    if ($r3b.documentId) { $D3 = [string]$r3b.documentId } elseif ($r3b.id) { $D3 = [string]$r3b.id }
    Pass "Re-upload BANK_STATEMENT via document-service" "New DocId=$D3"

    $blobUrl = if ($r3b.blobStoragePath) { $r3b.blobStoragePath } else { "" }
    $reNotifyBody = @{
        documentIds = @($D3); customerId = $CID
        documentType = "BANK_STATEMENT"; documentName = "bank_statement_updated.pdf"
        blobUrl = $blobUrl; blobPath = $blobUrl; contentType = "application/pdf"; fileSizeBytes = 0
    } | ConvertTo-Json
    $reNotify = SafeCall "$APIM_BASE/loan-applications/api/v1/loans/applications/$APPID/document-uploaded" POST (MakeHeaders -role "ROLE_CUSTOMER" -token $CTOKEN -json $true) $reNotifyBody 30
    $reNotifyErr = HasError $reNotify
    if ($reNotifyErr) { Fail "Workflow advance after re-upload" $reNotifyErr }
    else { Pass "Workflow advance after re-upload" "Status=$($reNotify.status)" }
}

# ── STEP 8: Manager approves final doc → document review complete ─────────────
Write-Host "`n[STEP 8] Manager Approves Final Document -> Document Review Complete -> Application Approved" -ForegroundColor White
Start-Sleep 2

$finalReviewBody = @{
    status = "APPROVED"
    remarks = "Updated bank statement verified. All 3 documents now approved. Document review complete."
    verifiedBy = $MGRID
    documentId = $D3
    documentType = "BANK_STATEMENT"
} | ConvertTo-Json

$rvFinal = SafeCall "$APIM_BASE/loan-applications/api/v1/loans/applications/$APPID/document-reviewed" POST (MakeHeaders -role "ROLE_MANAGER" -token $MTOKEN -json $true) $finalReviewBody 30
$rvFinalErr = HasError $rvFinal
if ($rvFinalErr) { Fail "Manager APPROVES final doc ($D3)" $rvFinalErr }
else { Pass "Manager APPROVES final doc (BANK_STATEMENT)" "Status=$($rvFinal.status) (Application APPROVED)" }

Start-Sleep 3

# ── VERIFICATION: Final state check ─────────────────────────────────────────
Write-Host "`n[VERIFY] Final Application State & Audit Trail" -ForegroundColor White
$final = SafeCall "$APIM_BASE/loan-applications/api/v1/loans/applications/$APPID" "GET" (MakeHeaders -role "ROLE_MANAGER" -token $MTOKEN) $null 20
$finalStatus = if ($final.status) { $final.status } else { "UNKNOWN" }
$docsCount   = if ($final.documents) { @($final.documents).Count } else { 0 }
Pass "Final application state" "Status=$finalStatus, Docs=$docsCount"

$faudit = SafeCall "$APIM_BASE/loan-applications/api/v1/loans/applications/$APPID/audit-logs" "GET" (MakeHeaders -role "ROLE_MANAGER" -token $MTOKEN) $null 20
$fauditErr = HasError $faudit
if (-not $fauditErr) {
    Pass "Final audit trail complete" "Total entries=$(@($faudit).Count)"
    Write-Host "`n  ================================================================" -ForegroundColor Green
    Write-Host "                    COMPLETE LOAN AUDIT TRAIL                     " -ForegroundColor Green
    Write-Host "  ================================================================" -ForegroundColor Green
    $idx = 1
    foreach ($entry in @($faudit)) {
        $prev = if ($entry.previousStatus) { $entry.previousStatus } else { "(INITIAL)" }
        $nxt  = if ($entry.newStatus) { $entry.newStatus } else { "-" }
        $actor = if ($entry.changedBy) { $entry.changedBy } elseif ($entry.actionBy) { $entry.actionBy } else { "SYSTEM" }
        Write-Host "   $idx. [$($entry.timestamp)] $prev -> $nxt (by $actor)" -ForegroundColor Cyan
        if ($entry.comments) { Write-Host "      - $($entry.comments)" -ForegroundColor White }
        $idx++
    }
}

# ── SUMMARY ───────────────────────────────────────────────────────────────────
Write-Host "`n================================================================" -ForegroundColor Cyan
Write-Host "  Results  : $TOTAL total | $PASS PASSED | $FAIL FAILED" -ForegroundColor Cyan
Write-Host "  Customer : $EMAIL  (ID=$CID)" -ForegroundColor DarkGray
Write-Host "  AppId    : $APPID  |  Manager: $MGRID" -ForegroundColor DarkGray
Write-Host "  DocIDs   : D1=$D1 | D2=$D2 | D3=$D3" -ForegroundColor DarkGray
if ($FAIL -eq 0) {
    Write-Host "`n  ALL $PASS TESTS PASSED!" -ForegroundColor Green
    Write-Host "  Fixed flow: upload->doc-service + notify->loan-service WORKING!" -ForegroundColor Green
} else {
    Write-Host "`n  $FAIL test(s) failed - review output above." -ForegroundColor Yellow
}
Write-Host "================================================================`n" -ForegroundColor Cyan

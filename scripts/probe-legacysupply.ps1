<#
.SYNOPSIS
    LegacySupply Partner Interface Probe Script (Lab 3 Contract Discovery)
.DESCRIPTION
    Diagnostic probe for contract discovery against LegacySupply API v1.
    Strictly performs 5 read-only requests:
      1. Authenticate (POST /auth/token)
      2. List Catalog (GET /catalog)
      3. Single Product Lookup (GET /catalog/{SupplierSku})
      4. Malformed Request (GET /catalog/ without SKU)
      5. Unauthenticated Request (GET /catalog/{SupplierSku} without session)
    Safe to rerun. Does NOT modify or create orders.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = "Continue"

# ---------------------------------------------------------------------------
# 1. Credential & Environment Validation
# ---------------------------------------------------------------------------
$baseUrl = if ($env:LS_BASE_URL) { $env:LS_BASE_URL.TrimEnd('/') } else { "https://legacysupply.onrender.com/api/v1" }
$clientId = $env:LS_CLIENT_ID
$apiKey = $env:LS_API_KEY

if ([string]::IsNullOrWhiteSpace($clientId)) {
    Write-Error "Missing required environment variable: LS_CLIENT_ID. Please set your student ID: `$env:LS_CLIENT_ID = 'your-student-id'"
    exit 1
}

if ([string]::IsNullOrWhiteSpace($apiKey)) {
    Write-Error "Missing required environment variable: LS_API_KEY. Please set your API key: `$env:LS_API_KEY = 'your-api-key'"
    exit 1
}

Write-Host "=========================================================="
Write-Host " LegacySupply Contract Discovery Probe (Lab 3)"
Write-Host " Base URL  : $baseUrl"
Write-Host " Client ID : $clientId"
Write-Host " API Key   : <REDACTED>"
Write-Host " Budget    : 5 requests max"
Write-Host "==========================================================`n"

# Helper function to redact secrets
function Redact-Secret {
    param([string]$Text)
    if ([string]::IsNullOrEmpty($Text) -or [string]::IsNullOrEmpty($apiKey)) {
        return $Text
    }
    return $Text.Replace($apiKey, "<REDACTED>")
}

# Helper function to invoke HTTP request and capture all details
function Send-ProbeRequest {
    param(
        [int]$StepNum,
        [string]$StepLabel,
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers,
        [string]$Body = $null
    )

    $redactedBody = if ($Body) { Redact-Secret -Text $Body } else { "" }

    Write-Host "================ REQUEST $StepNum ================"
    Write-Host "LABEL  : $StepLabel"
    Write-Host "METHOD $Method"
    Write-Host "URL $Url"
    if ($Body) {
        Write-Host "BODY $redactedBody"
    } else {
        Write-Host "BODY (none)"
    }

    $statusCode = 0
    $respBody = ""
    $responseHeaders = @{}

    try {
        $params = @{
            Uri             = $Url
            Method          = $Method
            Headers         = $Headers
            UseBasicParsing = $true
        }
        if ($Body) {
            $params["Body"] = $Body
            $params["ContentType"] = "application/xml; charset=utf-8"
        }

        $res = Invoke-WebRequest @params
        $statusCode = [int]$res.StatusCode
        $respBody = $res.Content

        if ($res.Headers) {
            foreach ($k in $res.Headers.Keys) {
                $responseHeaders[$k] = $res.Headers[$k]
            }
        }
    } catch [System.Net.WebException] {
        $we = $_.Exception
        if ($we.Response) {
            $httpRes = [System.Net.HttpWebResponse]$we.Response
            $statusCode = [int]$httpRes.StatusCode

            if ($httpRes.Headers) {
                foreach ($k in $httpRes.Headers.Keys) {
                    $responseHeaders[$k] = $httpRes.Headers[$k]
                }
            }

            $stream = $httpRes.GetResponseStream()
            if ($stream) {
                $reader = New-Object System.IO.StreamReader($stream)
                $respBody = $reader.ReadToEnd()
                $reader.Close()
            }
        } else {
            $statusCode = 0
            $respBody = $we.Message
        }
    } catch {
        # PowerShell 7 / Core HttpRequestException fallback
        if ($_.Exception.Response) {
            $res = $_.Exception.Response
            $statusCode = [int]$res.StatusCode
            if ($res.Content) {
                $respBody = $res.Content.ReadAsStringAsync().Result
            }
        } else {
            $statusCode = 0
            $respBody = $_.Exception.Message
        }
    }

    $redactedResp = Redact-Secret -Text $respBody

    Write-Host "--------------- RESPONSE $StepNum ---------------"
    Write-Host "STATUS $statusCode"
    Write-Host "BODY $redactedResp"
    Write-Host ""

    return [PSCustomObject]@{
        StatusCode = $statusCode
        RawBody    = $respBody
        Headers    = $responseHeaders
    }
}

# ---------------------------------------------------------------------------
# REQUEST 1 / 5: (a) AUTHENTICATE
# ---------------------------------------------------------------------------
$authUrl = "$baseUrl/auth/token"
$authBody = @"
<AuthRequest>
  <ClientId>$clientId</ClientId>
  <ApiKey>$apiKey</ApiKey>
</AuthRequest>
"@

$req1 = Send-ProbeRequest -StepNum 1 `
                          -StepLabel "(a) AUTHENTICATE" `
                          -Method "POST" `
                          -Url $authUrl `
                          -Headers @{ "Accept" = "application/xml" } `
                          -Body $authBody

$sessionToken = ""
$issuedAt = ""
if ($req1.RawBody) {
    if ($req1.RawBody -match '<SessionToken>([^<]+)</SessionToken>') {
        $sessionToken = $matches[1].Trim()
    }
    if ($req1.RawBody -match '<IssuedAt>([^<]+)</IssuedAt>') {
        $issuedAt = $matches[1].Trim()
    }
}

$setCookie = if ($req1.Headers.ContainsKey("Set-Cookie")) { $req1.Headers["Set-Cookie"] } else { "(none)" }

Write-Host "--- Auth Extraction Details ---"
Write-Host "Session Token : $sessionToken"
Write-Host "Issued At     : $issuedAt"
Write-Host "Set-Cookie    : $setCookie`n"

if ([string]::IsNullOrWhiteSpace($sessionToken)) {
    Write-Error "Failed to obtain session token from request 1. Cannot proceed with subsequent authenticated probes."
    exit 1
}

# ---------------------------------------------------------------------------
# REQUEST 2 / 5: (b) LIST PRODUCTS
# ---------------------------------------------------------------------------
$catalogUrl = "$baseUrl/catalog"
$authHeaders = @{
    "X-LS-Session" = $sessionToken
    "Accept"       = "application/xml"
}

$req2 = Send-ProbeRequest -StepNum 2 `
                          -StepLabel "(b) LIST PRODUCTS (GET /catalog)" `
                          -Method "GET" `
                          -Url $catalogUrl `
                          -Headers $authHeaders

$firstSku = ""
if ($req2.StatusCode -eq 200 -and $req2.RawBody) {
    Write-Host "--- Parsed Catalog Table ---"
    try {
        [xml]$catalogXml = $req2.RawBody
        $items = $catalogXml.Catalog.Item
        if ($items) {
            $totalCount = $items.Count
            $displayItems = if ($totalCount -gt 15) { $items[0..14] } else { $items }

            $rows = foreach ($it in $displayItems) {
                $sku = if ($it.SupplierSku) { $it.SupplierSku } else { "" }
                $desc = if ($it.Description) { $it.Description } elseif ($it.Name) { $it.Name } else { "" }
                $pack = if ($it.PackSize) { $it.PackSize } else { "" }
                $uom = if ($it.Uom) { $it.Uom } else { "N/A" }
                $cost = if ($it.UnitCost) { "$($it.UnitCost.'#text') $($it.UnitCost.currency)" } else { "" }

                [PSCustomObject]@{
                    SupplierSku = $sku
                    Name        = $desc
                    PackSize    = $pack
                    Uom         = $uom
                    UnitCost    = $cost
                }
            }

            $rows | Format-Table -AutoSize | Out-String | Write-Host

            if ($totalCount -gt 15) {
                Write-Host "(Showing first 15 rows of $totalCount total items)"
            }

            if ($items[0].SupplierSku) {
                $firstSku = $items[0].SupplierSku.Trim()
            }
        } else {
            Write-Host "Catalog contains no <Item> elements."
        }
    } catch {
        Write-Warning "Failed to parse catalog XML into table: $_"
    }
}

if ([string]::IsNullOrWhiteSpace($firstSku)) {
    # Fallback SKU if parsing didn't find one
    $firstSku = "ABC-1234"
    Write-Host "Using default SKU for subsequent single-item probes: $firstSku`n"
} else {
    Write-Host "Selected first SKU for single-item probes: $firstSku`n"
}

# ---------------------------------------------------------------------------
# REQUEST 3 / 5: (c) SINGLE PRODUCT LOOKUP
# ---------------------------------------------------------------------------
$singleUrl = "$baseUrl/catalog/$firstSku"
$req3 = Send-ProbeRequest -StepNum 3 `
                          -StepLabel "(c) SINGLE PRODUCT LOOKUP (GET /catalog/$firstSku)" `
                          -Method "GET" `
                          -Url $singleUrl `
                          -Headers $authHeaders

# ---------------------------------------------------------------------------
# REQUEST 4 / 5: (d) MALFORMED REQUEST (Omit SKU)
# ---------------------------------------------------------------------------
$malformedUrl = "$baseUrl/catalog/"
$req4 = Send-ProbeRequest -StepNum 4 `
                          -StepLabel "(d) MALFORMED REQUEST (Omit SKU: GET /catalog/)" `
                          -Method "GET" `
                          -Url $malformedUrl `
                          -Headers $authHeaders

# Extract error code if present
if ($req4.RawBody -match '<Code>([^<]+)</Code>') {
    Write-Host "Parsed Error Code   : $($matches[1])"
}
if ($req4.RawBody -match '<Message>([^<]+)</Message>') {
    Write-Host "Parsed Error Message: $($matches[1])`n"
}

# ---------------------------------------------------------------------------
# REQUEST 5 / 5: (e) UNAUTHENTICATED REQUEST (Omit X-LS-Session header)
# ---------------------------------------------------------------------------
$unauthUrl = "$baseUrl/catalog/$firstSku"
$unauthHeaders = @{
    "Accept" = "application/xml"
}

$req5 = Send-ProbeRequest -StepNum 5 `
                          -StepLabel "(e) UNAUTHENTICATED REQUEST (No session token: GET /catalog/$firstSku)" `
                          -Method "GET" `
                          -Url $unauthUrl `
                          -Headers $unauthHeaders

# Extract error code if present
if ($req5.RawBody -match '<Code>([^<]+)</Code>') {
    Write-Host "Parsed Error Code   : $($matches[1])"
}
if ($req5.RawBody -match '<Message>([^<]+)</Message>') {
    Write-Host "Parsed Error Message: $($matches[1])`n"
}

Write-Host "=========================================================="
Write-Host " LegacySupply Probe Complete: 5 of 5 requests executed."
Write-Host " Copy the captured values into INTEGRATION.md."
Write-Host "=========================================================="

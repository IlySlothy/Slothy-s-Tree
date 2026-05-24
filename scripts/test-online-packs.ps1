# Smoke test for Slothy's Tree online pack catalog + downloads
# Usage: .\scripts\test-online-packs.ps1 [-ServerUrl "https://ilyslothy.github.io/Slothy-s-Tree"]

param(
    [string]$ServerUrl = "https://ilyslothy.github.io/Slothy-s-Tree"
)

$ErrorActionPreference = "Stop"
$ServerUrl = $ServerUrl.TrimEnd("/")
$catalogUrl = "$ServerUrl/api/packs.json"

Write-Host "=== Slothy's Tree online pack test ===" -ForegroundColor Cyan
Write-Host "Catalog: $catalogUrl`n"

function Test-ZipBytes([byte[]]$bytes) {
    return $bytes.Length -ge 4 -and $bytes[0] -eq 0x50 -and $bytes[1] -eq 0x4B
}

try {
    $resp = Invoke-WebRequest -Uri $catalogUrl -UseBasicParsing -TimeoutSec 20 `
        -Headers @{ "User-Agent" = "SlothyHub-Mod/1.0"; "Accept" = "application/json" }
    Write-Host "[PASS] Catalog HTTP $($resp.StatusCode)" -ForegroundColor Green
    $packs = $resp.Content | ConvertFrom-Json
    Write-Host "       Found $($packs.Count) pack(s)`n"
} catch {
    Write-Host "[FAIL] Catalog: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

$failures = 0
foreach ($pack in $packs) {
    $url = $pack.pack_url
    if ([string]::IsNullOrWhiteSpace($url)) {
        Write-Host "[FAIL] $($pack.name): missing pack_url" -ForegroundColor Red
        $failures++
        continue
    }

    Write-Host "Testing $($pack.name) ($($pack.id))..."
    Write-Host "  URL: $url"
    try {
        $head = Invoke-WebRequest -Uri $url -Method Head -UseBasicParsing -MaximumRedirection 5 -TimeoutSec 30 `
            -Headers @{ "User-Agent" = "SlothyHub-Mod/1.0" }
        $size = $head.Headers["Content-Length"]
        Write-Host "  [PASS] HTTP $($head.StatusCode) ($size bytes)" -ForegroundColor Green

        # Spot-check first 4 bytes are ZIP (PK)
        $range = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 30 `
            -Headers @{ "User-Agent" = "SlothyHub-Mod/1.0"; "Range" = "bytes=0-3" }
        $magic = [byte[]]$range.Content
        if (Test-ZipBytes $magic) {
            Write-Host "  [PASS] Valid ZIP header" -ForegroundColor Green
        } else {
            Write-Host "  [WARN] Response is not a ZIP file (may be HTML error page)" -ForegroundColor Yellow
            $failures++
        }
    } catch {
        Write-Host "  [FAIL] $($_.Exception.Message)" -ForegroundColor Red
        $failures++
    }
    Write-Host ""
}

if ($failures -eq 0) {
    Write-Host "All checks passed. In-game: open Slothy's Tree, press RECONNECT, download a pack." -ForegroundColor Green
    exit 0
} else {
    Write-Host "$failures check(s) failed." -ForegroundColor Red
    Write-Host "Catalog works but pack downloads need a GitHub Release (tag packs-v1) with the .zip assets." -ForegroundColor Yellow
    Write-Host "See docs/PACKS.md - upload Summer.zip, FallenSnow.zip, Fortnite_Pack_Ilyslothy.zip from resourcepacks."
    exit 1
}

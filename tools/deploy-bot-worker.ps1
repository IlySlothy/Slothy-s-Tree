#requires -Version 5.1
# Deploy bot-worker to Cloudflare (pack upload DM/ticket + Discord interactions).
param(
    [switch] $Login
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$worker = Join-Path $repo 'bot-worker'
$cfProps = Join-Path $repo '.gradle\secrets\cloudflare.properties'

if (Test-Path $cfProps) {
    Get-Content $cfProps | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        $eq = $line.IndexOf('=')
        if ($eq -lt 1) { return }
        $k = $line.Substring(0, $eq).Trim()
        $v = $line.Substring($eq + 1).Trim()
        if ($k -eq 'token' -and $v) { $env:CLOUDFLARE_API_TOKEN = $v }
        if ($k -eq 'accountId' -and $v) { $env:CLOUDFLARE_ACCOUNT_ID = $v }
    }
}

Push-Location $worker
try {
    if ($Login -and -not $env:CLOUDFLARE_API_TOKEN) {
        Write-Host 'Opening Cloudflare login in browser...'
        npx wrangler login
    }

    $who = npx wrangler whoami 2>&1 | Out-String
    if ($who -match 'not authenticated' -and -not $env:CLOUDFLARE_API_TOKEN) {
        throw @'
Not logged in to Cloudflare. Either:
  1. Run: .\tools\deploy-bot-worker.ps1 -Login
  2. Or create .gradle\secrets\cloudflare.properties (see gradle\secrets\cloudflare.properties.example)
'@
    }

    Write-Host 'Deploying slothys-tree-bot...'
    npx wrangler deploy
    Write-Host 'Done. Worker URL: https://slothys-tree-bot.elytrapacks.workers.dev'
} finally {
    Pop-Location
}

#requires -Version 5.1
<#
.SYNOPSIS
  Replace wrong jar assets on existing GitHub releases (e.g. fallback uploaded 1.21.11 jars to 1.21.8 tag).
#>
param(
  [string] $BaseVer = '1.0.6',
  [string] $AssetsDir = '',
  [string] $NotesFile = ''
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo      = Split-Path -Parent $scriptDir
if (-not $AssetsDir) { $AssetsDir = Join-Path $repo 'release-assets' }
if (-not $NotesFile) { $NotesFile = Join-Path $repo "release-notes-v$BaseVer.md" }

$token = $env:GITHUB_TOKEN
if (-not $token) { $token = (gh auth token 2>$null) }
if (-not $token) { throw 'Set GITHUB_TOKEN or run gh auth login' }

$headers = @{
  Authorization = "Bearer $token"
  Accept        = 'application/vnd.github+json'
  'User-Agent'  = 'SlothyHubRelease/1.0'
  'X-GitHub-Api-Version' = '2022-11-28'
}

$bodyText = if (Test-Path $NotesFile) {
  [System.IO.File]::ReadAllText((Resolve-Path $NotesFile).Path)
} else { "Slothy's Tree v$BaseVer" }

function Fix-Release([string]$tag, [string[]]$assetNames) {
  Write-Host "`n=== $tag ==="
  $rel = Invoke-RestMethod -Uri "https://api.github.com/repos/IlySlothy/Slothy-s-Tree/releases/tags/$tag" -Headers $headers
  foreach ($a in @($rel.assets)) {
    Write-Host "  delete $($a.name)"
    Invoke-RestMethod -Uri "https://api.github.com/repos/IlySlothy/Slothy-s-Tree/releases/assets/$($a.id)" `
      -Method Delete -Headers $headers | Out-Null
  }
  foreach ($name in $assetNames) {
    $path = Join-Path $AssetsDir $name
    if (-not (Test-Path $path)) { throw "Missing $path" }
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $uploadHeaders = @{
      Authorization = $headers.Authorization
      Accept        = 'application/vnd.github+json'
      'Content-Type'= 'application/java-archive'
      'User-Agent'  = 'SlothyHubRelease/1.0'
    }
    Invoke-RestMethod -Uri "https://uploads.github.com/repos/IlySlothy/Slothy-s-Tree/releases/$($rel.id)/assets?name=$name" `
      -Method Post -Headers $uploadHeaders -Body $bytes | Out-Null
    Write-Host "  uploaded $name"
  }
  $patch = @{ body = $bodyText } | ConvertTo-Json
  $utf8 = New-Object System.Text.UTF8Encoding $false
  Invoke-RestMethod -Uri "https://api.github.com/repos/IlySlothy/Slothy-s-Tree/releases/$($rel.id)" `
    -Method Patch -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $utf8.GetBytes($patch) | Out-Null
}

Fix-Release "v${BaseVer}-mc1.21.8" @("slothyhub-${BaseVer}-mc1.21.8.jar")
Fix-Release "v${BaseVer}-mc1.21.11" @(
  "slothyhub-${BaseVer}-mc1.21.11.jar",
  "slothyhub-cit-${BaseVer}-mc1.21.11.jar"
)
Fix-Release "v${BaseVer}-mc1.20-1.21.1" @("slothyhub-${BaseVer}-mc1.20-1.21.1.jar")

Write-Host "`nAll release assets fixed for v$BaseVer."

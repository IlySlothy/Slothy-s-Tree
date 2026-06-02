#requires -Version 5.1
param(
  [string] $ProjectId = 'do54gChK',
  [string] $ConfigPath = '',
  [string] $BodyFile = '',
  [string] $IconPath = '',
  [switch] $SkipDescription,
  [switch] $UploadIcon,
  [string[]] $Categories = @('library', 'utility', 'game-mechanics')
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo      = Split-Path -Parent $scriptDir
if (-not $ConfigPath) {
  $ConfigPath = Join-Path $repo '.gradle\secrets\modrinth.properties'
}
if (-not $BodyFile) {
  $BodyFile = Join-Path $repo 'docs\modrinth-description.md'
}
if (-not $IconPath) {
  $IconPath = Join-Path $repo 'src\main\resources\assets\slothyhub\icon.png'
}
if (-not (Test-Path $ConfigPath)) { throw "Missing $ConfigPath" }
if (-not (Test-Path $BodyFile)) { throw "Missing body file $BodyFile" }

$cfg = @{}
Get-Content $ConfigPath | ForEach-Object {
  $line = $_.Trim()
  if (-not $line -or $line.StartsWith('#')) { return }
  $eq = $line.IndexOf('=')
  if ($eq -lt 1) { return }
  $cfg[$line.Substring(0, $eq).Trim()] = $line.Substring($eq + 1).Trim()
}
$token = $cfg['token']
if (-not $token) { $token = $cfg['modrinthToken'] }
if (-not $token) { throw 'modrinth token missing' }

$bodyMarkdown = [System.IO.File]::ReadAllText((Resolve-Path $BodyFile).Path)

$shortDescription = 'Client-side pack browser, Texture Builder, and OptiFine CIT support. Discloses network use: GitHub Pages catalog/downloads and optional Cloudflare Worker (heartbeats, stars, uploads). See full description for details.'

$headers = @{
  Authorization = $token
  'User-Agent'  = 'SlothyHubRelease/1.0'
  'Content-Type'= 'application/json'
}

$payload = @{
  description = $shortDescription
  body        = $bodyMarkdown
  categories  = @($Categories)
  link_urls   = @{
    website = 'https://ilyslothy.github.io/Slothy-s-Tree'
    source  = 'https://github.com/IlySlothy/Slothy-s-Tree'
    issues  = 'https://github.com/IlySlothy/Slothy-s-Tree/issues'
  }
}
$json = $payload | ConvertTo-Json -Depth 4
$utf8 = New-Object System.Text.UTF8Encoding $false
$bytes = $utf8.GetBytes($json)

if (-not $SkipDescription) {
  Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$ProjectId" -Method Patch -Headers $headers -Body $bytes | Out-Null
  Write-Host "Updated Modrinth project $ProjectId (description + body)."
}

if ($UploadIcon -and (Test-Path $IconPath)) {
  $len = (Get-Item $IconPath).Length
  if ($len -gt 256KB) { throw "Icon exceeds 256 KiB: $IconPath" }
  $out = Join-Path $env:TEMP 'modrinth-icon-upload.txt'
  $code = & curl.exe -s -o $out -w '%{http_code}' --max-time 30 `
    -X PATCH "https://api.modrinth.com/v2/project/$ProjectId/icon?ext=png" `
    -H "Authorization: $token" `
    -H 'User-Agent: SlothyHubRelease/1.0' `
    -H 'Content-Type: image/png' `
    --data-binary "@$IconPath"
  if ($code -ne '204') {
    $body = Get-Content $out -Raw -ErrorAction SilentlyContinue
    throw "Icon upload failed ($code): $body"
  }
  Write-Host "Updated Modrinth project icon from $IconPath"
} elseif (-not $SkipDescription) {
  Write-Host "Icon not found (skipped): $IconPath"
}

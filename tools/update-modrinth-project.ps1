#requires -Version 5.1
param(
  [string] $ProjectId = 'do54gChK',
  [string] $ConfigPath = ''
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo      = Split-Path -Parent $scriptDir
if (-not $ConfigPath) {
  $ConfigPath = Join-Path $repo '.gradle\secrets\modrinth.properties'
}
if (-not (Test-Path $ConfigPath)) { throw "Missing $ConfigPath" }

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

$headers = @{
  Authorization = $token
  'User-Agent'  = 'SlothyHubRelease/1.0'
  'Content-Type'= 'application/json'
}

$payload = @{
  body = @"
**Slothy's Tree** is a client-side Fabric mod for browsing, applying, and building custom Minecraft resource packs.

**Pack catalog:** https://ilyslothy.github.io/Slothy-s-Tree
**Source:** https://github.com/IlySlothy/Slothy-s-Tree
**Issues:** https://github.com/IlySlothy/Slothy-s-Tree/issues

Requires Fabric Loader and Fabric API. Client-side only.
"@
  link_urls = @{
    website = 'https://ilyslothy.github.io/Slothy-s-Tree'
    source  = 'https://github.com/IlySlothy/Slothy-s-Tree'
    issues  = 'https://github.com/IlySlothy/Slothy-s-Tree/issues'
  }
}
$json = $payload | ConvertTo-Json -Depth 4
$utf8 = New-Object System.Text.UTF8Encoding $false
$bytes = $utf8.GetBytes($json)

Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$ProjectId" -Method Patch -Headers $headers -Body $bytes | Out-Null
Write-Host "Updated Modrinth project $ProjectId metadata."

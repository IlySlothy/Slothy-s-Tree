#requires -Version 5.1
<#
.SYNOPSIS
  Add Fabric API (required) to Modrinth versions that are missing it.
#>
param(
  [string] $ProjectId = 'do54gChK',
  [string] $ConfigPath = '',
  [string] $VersionPrefix = '1.0.4',
  [string] $FabricApiProjectId = 'P7dR8mSH'
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo      = Split-Path -Parent $scriptDir
if (-not $ConfigPath) { $ConfigPath = Join-Path $repo '.gradle\secrets\modrinth.properties' }

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

$versions = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$ProjectId/version" -Headers $headers
$targets = @($versions | Where-Object { $_.version_number -like "$VersionPrefix*" })

if (-not $targets.Count) {
  Write-Host "No versions matching $VersionPrefix*"
  exit 0
}

foreach ($v in $targets) {
  $hasFabric = $false
  foreach ($d in @($v.dependencies)) {
    if ($d.project_id -eq $FabricApiProjectId) { $hasFabric = $true; break }
  }
  if ($hasFabric) {
    Write-Host "Skip $($v.version_number) (already has Fabric API)"
    continue
  }

  $deps = @(
    @{
      project_id       = $FabricApiProjectId
      dependency_type  = 'required'
    }
  )
  foreach ($d in @($v.dependencies)) {
    if ($d.project_id -and $d.project_id -ne $FabricApiProjectId) {
      $deps += @{
        project_id       = $d.project_id
        version_id       = $d.version_id
        dependency_type  = $d.dependency_type
      }
    }
  }

  $body = @{ dependencies = $deps } | ConvertTo-Json -Depth 5
  $utf8 = New-Object System.Text.UTF8Encoding $false
  $bytes = $utf8.GetBytes($body)
  Invoke-RestMethod -Uri "https://api.modrinth.com/v2/version/$($v.id)" -Method Patch -Headers $headers -Body $bytes | Out-Null
  Write-Host "Patched $($v.version_number) ($($v.id)) -> Fabric API required"
}

Write-Host 'Done.'

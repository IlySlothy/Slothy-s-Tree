#requires -Version 5.1
<#
.SYNOPSIS
  Full release: build jars, GitHub releases, optional Modrinth, Discord announcement.

.DESCRIPTION
  Discord is always the final step when not using -SkipDiscord.
#>
param(
  [Parameter(Mandatory = $true)]
  [string] $Version,
  [string] $NotesFile = '',
  [switch] $SkipBuild,
  [switch] $SkipGitHub,
  [switch] $SkipModrinth,
  [switch] $SkipDiscord
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo      = Split-Path -Parent $scriptDir

$baseVer = ($Version -replace '-mc.*$', '').Trim()
if (-not $NotesFile) {
  $NotesFile = Join-Path $repo "release-notes-v$baseVer.md"
}

Write-Host "=== Slothy's Tree release v$baseVer ==="

if (-not $SkipBuild) {
  Write-Host "`n[1/4] Building release assets..."
  Push-Location $repo
  try {
    & .\gradlew.bat copyReleaseAssets --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed ($LASTEXITCODE)" }
  } finally {
    Pop-Location
  }
} else {
  Write-Host "`n[1/4] Build skipped"
}

if (-not $SkipGitHub) {
  Write-Host "`n[2/4] Creating GitHub releases..."
  $ghArgs = @{
    BaseVer   = $baseVer
    NotesFile = $NotesFile
  }
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $scriptDir 'create-github-releases.ps1') @ghArgs
  if ($LASTEXITCODE -ne 0) { throw 'GitHub release step failed (run gh auth login?)' }
} else {
  Write-Host "`n[2/4] GitHub skipped"
}

if (-not $SkipModrinth) {
  Write-Host "`n[3/4] Publishing to Modrinth..."
  Push-Location $repo
  try {
    & .\gradlew.bat publishModrinth --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Modrinth publish failed ($LASTEXITCODE)" }
  } finally {
    Pop-Location
  }
} else {
  Write-Host "`n[3/4] Modrinth skipped"
}

if (-not $SkipDiscord) {
  Write-Host "`n[4/4] Posting Discord announcement..."
  $discordArgs = @{
    Version   = $Version
    NotesFile = $NotesFile
  }
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $scriptDir 'post-release-discord.ps1') @discordArgs
  if ($LASTEXITCODE -ne 0) { throw 'Discord post failed' }
} else {
  Write-Host "`n[4/4] Discord skipped"
}

Write-Host "`nRelease v$baseVer complete."

#requires -Version 5.1
param(
  [string] $BaseVer = '1.0.5',
  [string] $NotesFile = '',
  [string] $AssetsDir = ''
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo      = Split-Path -Parent $scriptDir
if (-not $AssetsDir) { $AssetsDir = Join-Path $repo 'release-assets' }

$token = $env:GITHUB_TOKEN
if (-not $token) {
  $token = (gh auth token 2>$null)
}
if (-not $token) {
  throw 'Set GITHUB_TOKEN or run gh auth login'
}

$headers = @{
  Authorization = "Bearer $token"
  Accept        = 'application/vnd.github+json'
  'User-Agent'  = 'SlothyHubRelease/1.0'
  'X-GitHub-Api-Version' = '2022-11-28'
}

$bodyText = if ($NotesFile -and (Test-Path $NotesFile)) {
  Get-Content $NotesFile -Raw
} else {
  @"
## Slothy's Tree v$BaseVer

### Pack browser
- Star / unstar packs in-game (community vote counts)
- Featured pack pinned at top of browser
- Upload tags, My Uploads dashboard, What's New screen

### Fixes
- Texture Builder no longer hangs on active resource pack scan

### Discord
- Live top-10 pack star leaderboard (`/setup-leaderboard`)
"@
}

function Upload-Release($tag, $name, [string[]] $assets) {
  $existing = Invoke-RestMethod -Uri "https://api.github.com/repos/IlySlothy/Slothy-s-Tree/releases/tags/$tag" -Headers $headers -ErrorAction SilentlyContinue
  if ($existing -and $existing.tag_name) {
    Write-Host "Release $tag already exists — skipping create"
    return $existing
  }

  $payload = @{
    tag_name   = $tag
    name       = $name
    body       = $bodyText
    draft      = $false
    prerelease = $false
  } | ConvertTo-Json -Compress

  $rel = Invoke-RestMethod -Uri 'https://api.github.com/repos/IlySlothy/Slothy-s-Tree/releases' -Method Post -Headers $headers -ContentType 'application/json' -Body $payload
  foreach ($asset in $assets) {
    $path = Join-Path $AssetsDir $asset
    if (-not (Test-Path $path)) { throw "Missing asset: $path" }
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $uploadHeaders = @{
      Authorization = $headers.Authorization
      Accept        = 'application/vnd.github+json'
      'Content-Type'= 'application/java-archive'
      'User-Agent'  = 'SlothyHubRelease/1.0'
    }
    Invoke-RestMethod -Uri "https://uploads.github.com/repos/IlySlothy/Slothy-s-Tree/releases/$($rel.id)/assets?name=$asset" `
      -Method Post -Headers $uploadHeaders -Body $bytes | Out-Null
    Write-Host "  uploaded $asset"
  }
  Write-Host "Created $tag"
  return $rel
}

$tag111 = "v${BaseVer}-mc1.21.11"
$tag118 = "v${BaseVer}-mc1.21.8"
$tag201 = "v${BaseVer}-mc1.20-1.21.1"

Upload-Release $tag111 "Slothy's Tree v$BaseVer (MC 1.21.9-1.21.11)" @(
  "slothyhub-${BaseVer}-mc1.21.11.jar",
  "slothyhub-cit-${BaseVer}-mc1.21.11.jar"
) | Out-Null

Upload-Release $tag118 "Slothy's Tree v$BaseVer (MC 1.21.8)" @(
  "slothyhub-${BaseVer}-mc1.21.8.jar"
) | Out-Null

Upload-Release $tag201 "Slothy's Tree v$BaseVer (MC 1.20-1.21.1)" @(
  "slothyhub-${BaseVer}-mc1.20-1.21.1.jar",
  "slothyhub-legacy-cit-${BaseVer}-mc1.21.8-legacy.jar"
) | Out-Null

Write-Host 'All GitHub releases done.'

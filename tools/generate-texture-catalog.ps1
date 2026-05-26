#requires -Version 5.1
<#
.SYNOPSIS
  Build textures.json + a PNG mirror tree from installed catalog packs.

.DESCRIPTION
  - Reads src/main/resources/assets/slothyhub/packs.json so we know which packs
    the in-game browser considers official.
  - For each pack, looks for the corresponding folder/zip in
    %APPDATA%\.minecraft\resourcepacks\ and harvests every CIT sword PNG plus
    its sibling .mcmeta (if any).
  - Mirrors them under <Out>/textures/<pack_id>/swords/<key>.png
  - Writes <Out>/textures.json - the JSON the mod fetches from GitHub Pages.

.PARAMETER Out
  Output directory. Defaults to ./docs (the GitHub Pages source for
  IlySlothy/Slothy-s-Tree). The textures/ subdirectory is wiped and rebuilt
  on every run; other files in $Out (packs.json, index.html, etc.) are left
  alone.

.PARAMETER PublicBase
  Public URL prefix that the rendered files will be served under. Defaults to
  https://ilyslothy.github.io/Slothy-s-Tree. Each texture entry's "png" url
  will be PublicBase + "/textures/<pack_id>/swords/<key>.png".
#>
[CmdletBinding()]
param(
  [string] $Out        = '',
  [string] $PublicBase = 'https://ilyslothy.github.io/Slothy-s-Tree'
)

$ErrorActionPreference = 'Stop'

# Resolve script + repo paths AFTER param block so $PSScriptRoot is populated.
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo      = Split-Path -Parent $scriptDir
if (-not $Out) { $Out = Join-Path $repo 'docs' }
$packsJson  = Join-Path $repo 'src\main\resources\assets\slothyhub\packs.json'
$rpRoot     = Join-Path $env:APPDATA '.minecraft\resourcepacks'

if (-not (Test-Path $packsJson)) { throw "Cannot find $packsJson" }
if (-not (Test-Path $rpRoot))    { throw "Cannot find $rpRoot - install some catalog packs first" }

Write-Host "Output         : $Out"
Write-Host "Public base    : $PublicBase"
Write-Host "Packs catalog  : $packsJson"
Write-Host "Local packs    : $rpRoot"

# Reset only the textures subtree - leave other docs/ files (packs.json, index.html) alone.
$texturesDir = Join-Path $Out 'textures'
if (Test-Path $texturesDir) { Remove-Item $texturesDir -Recurse -Force }
New-Item -ItemType Directory -Path $texturesDir -Force | Out-Null

# Friendly labels mapping for known sword filenames -> "Pretty Name"
$prettyMap = @{
  'noob_sword'    = 'Noob Sword'
  'good_sword'    = 'Good Sword'
  'pro_sword'     = 'Pro Sword'
  'perfect_sword' = 'Perfect Sword'
  'warden_sword'  = 'Warden Sword'
  'hippo_sword'   = 'Hippo Sword'
}

function Get-PrettyLabel([string]$key) {
  if ($prettyMap.ContainsKey($key)) { return $prettyMap[$key] }
  # Title-case underscores -> spaces
  return (($key -split '_') | ForEach-Object {
    if ($_.Length -gt 0) { $_.Substring(0,1).ToUpper() + $_.Substring(1) } else { '' }
  }) -join ' '
}

function Is-SwordCitPath([string]$rel) {
  $lower = $rel.ToLower()
  if (-not ($lower -match '/cit/' -or $lower -match '\\cit\\')) { return $false }
  if ($lower -match '_armor|_layer_|/armor/|\\armor\\') { return $false }
  if ($lower -match '/cit/swords/|\\cit\\swords\\') { return $true }
  if ($lower -match 'sword') { return $true }
  return $false
}

function Get-PackStagingDir([string]$packId) {
  $candidates = @($packId)
  # filename without extension (lowercased)
  $base = [System.IO.Path]::GetFileNameWithoutExtension($packId)
  if ($base -ne $packId) { $candidates += $base }
  foreach ($c in $candidates) {
    $p = Join-Path $rpRoot $c
    if (Test-Path $p -PathType Container) { return $p }
  }
  return $null
}

function Resolve-PackContent([string]$packId, [string]$packFilename) {
  # 1) Installed folder named exactly like pack_filename without .zip
  $folderName = if ($packFilename -match '\.zip$') {
    [System.IO.Path]::GetFileNameWithoutExtension($packFilename)
  } else { $packFilename }
  $folderPath = Join-Path $rpRoot $folderName
  if (Test-Path $folderPath -PathType Container) { return @{ Mode = 'folder'; Path = $folderPath } }

  # 2) Installed zip
  $zipPath = Join-Path $rpRoot $packFilename
  if (Test-Path $zipPath -PathType Leaf) { return @{ Mode = 'zip'; Path = $zipPath } }

  return $null
}

$packCatalog = Get-Content $packsJson -Raw | ConvertFrom-Json
$webPacks = @()
$harvestedFiles = 0

foreach ($pack in $packCatalog) {
  $resolved = Resolve-PackContent $pack.id $pack.pack_filename
  if (-not $resolved) {
    Write-Host ("  [skip] {0,-18} - no installed copy" -f $pack.id) -ForegroundColor DarkGray
    continue
  }

  $textures = @()
  $targetSwordDir = Join-Path $texturesDir "$($pack.id)\swords"

  if ($resolved.Mode -eq 'folder') {
    # Walk the assets dir for matching PNGs
    $assetsRoot = Join-Path $resolved.Path 'assets'
    if (-not (Test-Path $assetsRoot)) { Write-Host ("  [skip] {0,-18} - no assets/" -f $pack.id); continue }
    $pngs = Get-ChildItem $assetsRoot -Recurse -File -Filter '*.png' -ErrorAction SilentlyContinue
    foreach ($png in $pngs) {
      $rel = $png.FullName.Substring($resolved.Path.Length).TrimStart('\','/') -replace '\\','/'
      if (-not (Is-SwordCitPath $rel)) { continue }
      $key = [System.IO.Path]::GetFileNameWithoutExtension($png.FullName).ToLower()
      $mcmeta = "$($png.FullName).mcmeta"
      if (-not (Test-Path $targetSwordDir)) { New-Item -ItemType Directory -Path $targetSwordDir -Force | Out-Null }
      $targetPng = Join-Path $targetSwordDir "$key.png"
      Copy-Item $png.FullName $targetPng -Force
      $harvestedFiles++
      $entry = [ordered]@{
        category = 'swords'
        key      = $key
        label    = (Get-PrettyLabel $key)
        png      = "$PublicBase/textures/$($pack.id)/swords/$key.png"
      }
      if (Test-Path $mcmeta) {
        $targetMcmeta = "$targetPng.mcmeta"
        Copy-Item $mcmeta $targetMcmeta -Force
        $entry.mcmeta = "$PublicBase/textures/$($pack.id)/swords/$key.png.mcmeta"
        $harvestedFiles++
      }
      $textures += [pscustomobject]$entry
    }
  } else {
    # Zip mode - enumerate entries via System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($resolved.Path)
    try {
      $candidates = $zip.Entries | Where-Object { $_.FullName.ToLower().EndsWith('.png') -and (Is-SwordCitPath $_.FullName) }
      foreach ($entry in $candidates) {
        $key = [System.IO.Path]::GetFileNameWithoutExtension($entry.Name).ToLower()
        if (-not (Test-Path $targetSwordDir)) { New-Item -ItemType Directory -Path $targetSwordDir -Force | Out-Null }
        $targetPng = Join-Path $targetSwordDir "$key.png"
        $inStream = $entry.Open()
        try {
          $outStream = [System.IO.File]::Create($targetPng)
          try { $inStream.CopyTo($outStream) } finally { $outStream.Close() }
        } finally { $inStream.Close() }
        $harvestedFiles++

        $mcmetaEntry = $zip.Entries | Where-Object { $_.FullName -eq ($entry.FullName + '.mcmeta') } | Select-Object -First 1
        $hasMcmeta = $false
        if ($mcmetaEntry) {
          $targetMcmeta = "$targetPng.mcmeta"
          $inStream = $mcmetaEntry.Open()
          try {
            $outStream = [System.IO.File]::Create($targetMcmeta)
            try { $inStream.CopyTo($outStream) } finally { $outStream.Close() }
          } finally { $inStream.Close() }
          $hasMcmeta = $true
          $harvestedFiles++
        }
        $entryObj = [ordered]@{
          category = 'swords'
          key      = $key
          label    = (Get-PrettyLabel $key)
          png      = "$PublicBase/textures/$($pack.id)/swords/$key.png"
        }
        if ($hasMcmeta) { $entryObj.mcmeta = "$PublicBase/textures/$($pack.id)/swords/$key.png.mcmeta" }
        $textures += [pscustomobject]$entryObj
      }
    } finally { $zip.Dispose() }
  }

  # Dedupe by key (keep first occurrence)
  $byKey = @{}
  $deduped = @()
  foreach ($t in $textures) {
    if (-not $byKey.ContainsKey($t.key)) { $byKey[$t.key] = $true; $deduped += $t }
  }

  if ($deduped.Count -eq 0) {
    Write-Host ("  [skip] {0,-18} - no sword CITs found" -f $pack.id) -ForegroundColor DarkGray
    continue
  }
  Write-Host ("  [ ok ] {0,-18} - {1,2} sword texture(s)" -f $pack.id, $deduped.Count) -ForegroundColor Green
  $webPacks += [pscustomobject]@{
    id       = $pack.id
    name     = $pack.name
    textures = $deduped
  }
}

# Write textures.json
$catalog = [ordered]@{
  version      = 1
  generated_at = (Get-Date).ToUniversalTime().ToString('o')
  source       = 'tools/generate-texture-catalog.ps1'
  packs        = $webPacks
}
$outJson = Join-Path $Out 'textures.json'
$catalog | ConvertTo-Json -Depth 10 | Set-Content -Path $outJson -Encoding UTF8

Write-Host ""
Write-Host "Wrote $outJson" -ForegroundColor Cyan
Write-Host "Total files mirrored : $harvestedFiles"
Write-Host "Total packs included : $($webPacks.Count)"
Write-Host ""
Write-Host "Upload this folder's contents to https://ilyslothy.github.io/Slothy-s-Tree/" -ForegroundColor Yellow
Write-Host "  - textures.json"
Write-Host "  - textures/* (full directory)"

#requires -Version 5.1
<#
.SYNOPSIS
  Build textures.json + mirrored assets from installed catalog packs.

  Harvests CIT swords, vanilla texture overrides (GUI / particles / items / kill FX),
  and totem-style sounds, then writes docs/textures.json for GitHub Pages.
#>
[CmdletBinding()]
param(
  [string] $Out        = '',
  [string] $PublicBase = 'https://ilyslothy.github.io/Slothy-s-Tree'
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo      = Split-Path -Parent $scriptDir
if (-not $Out) { $Out = Join-Path $repo 'docs' }
$packsJson = Join-Path $repo 'src\main\resources\assets\slothyhub\packs.json'
$rpRoot    = Join-Path $env:APPDATA '.minecraft\resourcepacks'

if (-not (Test-Path $packsJson)) { throw "Cannot find $packsJson" }
if (-not (Test-Path $rpRoot))    { throw "Cannot find $rpRoot" }

Write-Host "Output      : $Out"
Write-Host "Public base : $PublicBase"

$texturesDir = Join-Path $Out 'textures'
if (Test-Path $texturesDir) { Remove-Item $texturesDir -Recurse -Force }
New-Item -ItemType Directory -Path $texturesDir -Force | Out-Null

function Get-PrettyLabel([string]$key) {
  $map = @{
    'noob_sword'='Noob Sword'; 'good_sword'='Good Sword'; 'pro_sword'='Pro Sword'
    'perfect_sword'='Perfect Sword'; 'warden_sword'='Warden Sword'
  }
  if ($map.ContainsKey($key)) { return $map[$key] }
  return (($key -split '_') | ForEach-Object {
    if ($_.Length -gt 0) { $_.Substring(0,1).ToUpper() + $_.Substring(1) } else { '' }
  }) -join ' '
}

function Is-SwordCitPath([string]$rel) {
  $lower = $rel.ToLower()
  if ($lower -notmatch '/cit/|\\cit\\') { return $false }
  if ($lower -match '_armor|_layer_|/armor/|\\armor\\') { return $false }
  if ($lower -match '/cit/swords/|\\cit\\swords\\') { return $true }
  if ($lower -match 'sword') { return $true }
  return $false
}

# Paths/keywords mirrored from TexturePickerScreen slot definitions — only these
# are published so the catalog stays small and the picker stays fast.
$Script:ExactGuiAssets = @(
  'assets/minecraft/textures/gui/sprites/hud/hotbar.png',
  'assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png',
  'assets/minecraft/textures/gui/sprites/hud/hotbar_offhand_left.png',
  'assets/minecraft/textures/gui/sprites/hud/hotbar_offhand_right.png',
  'assets/minecraft/textures/gui/sprites/hud/heart/full.png',
  'assets/minecraft/textures/gui/sprites/hud/heart/half.png',
  'assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full.png',
  'assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half.png',
  'assets/minecraft/textures/gui/container/inventory.png'
)

function Match-PickerPng([string]$asset) {
  $lower = $asset.ToLower().Replace('\','/')
  if ($lower -notmatch '^assets/minecraft/textures/') { return $null }
  if ($lower -match '/cit/') { return $null }
  foreach ($g in $Script:ExactGuiAssets) {
    if ($lower -eq $g.ToLower()) { return 'gui' }
  }
  if ($lower -match '/particle/') {
    foreach ($kw in @('golden_crit','critical_hit','enchanted_hit')) {
      if ($lower -match $kw) { return 'particles' }
    }
    return $null
  }
  if ($lower -eq 'assets/minecraft/textures/block/dead_tube_coral.png') { return 'items' }
  if ($lower -match '/item/|/block/') {
    $file = [System.IO.Path]::GetFileNameWithoutExtension($lower)
    if ($lower -match 'firework_rocket' -or $file -eq 'firework_rocket') { return 'items' }
    if ($lower -match 'golden_apple' -or $file -eq 'golden_apple') { return 'items' }
    if ($lower -match 'netherite_sword' -or $file -eq 'netherite_sword') { return 'items' }
    if ($lower -match 'cornflower' -or $file -eq 'cornflower') { return 'items' }
    if ($lower -match 'totem' -or $file -match 'totem') { return 'items' }
  }
  return $null
}

function Match-PickerSound([string]$asset) {
  $lower = $asset.ToLower().Replace('\','/')
  if (-not $lower.EndsWith('.ogg')) { return $null }
  if ($lower -notmatch '^assets/minecraft/sounds/') { return $null }
  if ($lower -match 'totem|use_totem') { return 'sounds' }
  return $null
}

function Mirror-RelPath([string]$packId, [string]$asset) {
  $safe = $asset.Replace('\','/')
  return "textures/$packId/mirror/$safe"
}

function Publish-Bytes([string]$packId, [string]$asset, [byte[]]$bytes) {
  $rel = Mirror-RelPath $packId $asset
  $dest = Join-Path $Out ($rel.Replace('/','\'))
  $dir = Split-Path $dest -Parent
  if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
  [System.IO.File]::WriteAllBytes($dest, $bytes)
  return "$PublicBase/$($rel.Replace('\','/'))"
}

function Resolve-PackContent([string]$packId, [string]$packFilename) {
  $folderName = if ($packFilename -match '\.zip$') {
    [System.IO.Path]::GetFileNameWithoutExtension($packFilename)
  } else { $packFilename }
  $folderPath = Join-Path $rpRoot $folderName
  if (Test-Path $folderPath -PathType Container) { return @{ Mode = 'folder'; Path = $folderPath } }
  $zipPath = Join-Path $rpRoot $packFilename
  if (Test-Path $zipPath -PathType Leaf) { return @{ Mode = 'zip'; Path = $zipPath } }
  return $null
}

function Add-Entry([System.Collections.ArrayList]$list, [hashtable]$byKey, $entry) {
  $dedupe = "$($entry.category)|$($entry.asset)"
  if ($byKey.ContainsKey($dedupe)) { return }
  $byKey[$dedupe] = $true
  [void]$list.Add([pscustomobject]$entry)
}

function Process-PackPng([string]$packId, [string]$asset, [byte[]]$bytes,
    [System.Collections.ArrayList]$textures, [hashtable]$byKey, [ref]$fileCount) {
  $asset = $asset.Replace('\','/')
  if (Is-SwordCitPath $asset) {
    $key = [System.IO.Path]::GetFileNameWithoutExtension($asset).ToLower()
    $citAsset = if ($asset -match '^assets/') { $asset } else { "assets/minecraft/optifine/cit/swords/$key.png" }
    $url = Publish-Bytes $packId $citAsset $bytes
    $fileCount.Value++
    $e = [ordered]@{ category='swords'; key=$key; label=(Get-PrettyLabel $key); asset=$citAsset; png=$url }
    $mcmetaAsset = "$citAsset.mcmeta"
    $mcmetaPath = Join-Path (Split-Path (Join-Path $Out (Mirror-RelPath $packId $citAsset)) -Parent) ([System.IO.Path]::GetFileName($mcmetaAsset))
    Add-Entry $textures $byKey $e
    return
  }
  $cat = Match-PickerPng $asset
  if (-not $cat) { return }
  $key = [System.IO.Path]::GetFileNameWithoutExtension($asset).ToLower()
  $url = Publish-Bytes $packId $asset $bytes
  $fileCount.Value++
  $e = [ordered]@{
    category = $cat
    key      = $key
    label    = $key
    asset    = $asset
    png      = $url
  }
  Add-Entry $textures $byKey $e
}

function Process-PackSound([string]$packId, [string]$asset, [byte[]]$bytes,
    [System.Collections.ArrayList]$textures, [hashtable]$byKey, [ref]$fileCount) {
  $asset = $asset.Replace('\','/')
  if (-not (Match-PickerSound $asset)) { return }
  $key = [System.IO.Path]::GetFileNameWithoutExtension($asset).ToLower()
  $url = Publish-Bytes $packId $asset $bytes
  $fileCount.Value++
  $e = [ordered]@{
    category = 'sounds'
    key      = $key
    label    = $key
    asset    = $asset
    sound    = $url
  }
  Add-Entry $textures $byKey $e
}

function Attach-McmetaAndCritJson([string]$packId, [System.Collections.ArrayList]$textures, [string]$rootPath, [string]$mode) {
  foreach ($t in @($textures)) {
    if (-not $t.png) { continue }
    $asset = $t.asset
    if (-not $asset.EndsWith('.png')) { continue }
    $mcmetaAsset = "$asset.mcmeta"
    $critAsset = 'assets/minecraft/particles/crit.json'
    if ($mode -eq 'folder') {
      $mcmetaFile = Join-Path $rootPath ($mcmetaAsset.Replace('/','\'))
      if (Test-Path $mcmetaFile) {
        $bytes = [System.IO.File]::ReadAllBytes($mcmetaFile)
        $t | Add-Member -NotePropertyName mcmeta -NotePropertyValue (Publish-Bytes $packId $mcmetaAsset $bytes) -Force
      }
      if ($t.category -eq 'particles' -and $asset -match 'golden_crit') {
        $critFile = Join-Path $rootPath ($critAsset.Replace('/','\'))
        if (Test-Path $critFile) {
          $bytes = [System.IO.File]::ReadAllBytes($critFile)
          $t | Add-Member -NotePropertyName particle_json -NotePropertyValue (Publish-Bytes $packId $critAsset $bytes) -Force
        }
      }
    } else {
      Add-Type -AssemblyName System.IO.Compression.FileSystem
      $zip = [System.IO.Compression.ZipFile]::OpenRead($rootPath)
      try {
        $me = $zip.Entries | Where-Object { $_.FullName.Replace('\','/') -eq $mcmetaAsset } | Select-Object -First 1
        if ($me) {
          $s = $me.Open(); try { $b = (New-Object byte[] $me.Length); $s.Read($b,0,$b.Length) | Out-Null } finally { $s.Close() }
          $t | Add-Member -NotePropertyName mcmeta -NotePropertyValue (Publish-Bytes $packId $mcmetaAsset $b) -Force
        }
        if ($t.category -eq 'particles' -and $asset -match 'golden_crit') {
          $ce = $zip.Entries | Where-Object { $_.FullName.Replace('\','/') -eq $critAsset } | Select-Object -First 1
          if ($ce) {
            $s = $ce.Open(); try { $b = (New-Object byte[] $ce.Length); $s.Read($b,0,$b.Length) | Out-Null } finally { $s.Close() }
            $t | Add-Member -NotePropertyName particle_json -NotePropertyValue (Publish-Bytes $packId $critAsset $b) -Force
          }
        }
      } finally { $zip.Dispose() }
    }
  }
}

$packCatalog = Get-Content $packsJson -Raw | ConvertFrom-Json
$webPacks = @()
$totalFiles = 0

foreach ($pack in $packCatalog) {
  $resolved = Resolve-PackContent $pack.id $pack.pack_filename
  if (-not $resolved) {
    Write-Host ("  [skip] {0,-18} no local copy" -f $pack.id) -ForegroundColor DarkGray
    continue
  }

  $textures = [System.Collections.ArrayList]::new()
  $byKey = @{}
  $fc = 0

  if ($resolved.Mode -eq 'folder') {
    Get-ChildItem $resolved.Path -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
      $rel = $_.FullName.Substring($resolved.Path.Length).TrimStart('\','/') -replace '\\','/'
      if ($rel.ToLower().EndsWith('.png')) {
        Process-PackPng $pack.id $rel ([System.IO.File]::ReadAllBytes($_.FullName)) $textures $byKey ([ref]$fc)
      } elseif ($rel.ToLower().EndsWith('.ogg')) {
        Process-PackSound $pack.id $rel ([System.IO.File]::ReadAllBytes($_.FullName)) $textures $byKey ([ref]$fc)
      }
    }
    Attach-McmetaAndCritJson $pack.id $textures $resolved.Path 'folder'
  } else {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($resolved.Path)
    try {
      foreach ($entry in $zip.Entries) {
        if ($entry.Length -le 0) { continue }
        $rel = $entry.FullName.Replace('\','/')
        $buf = New-Object byte[] $entry.Length
        $s = $entry.Open()
        try { $s.Read($buf, 0, $buf.Length) | Out-Null } finally { $s.Close() }
        if ($rel.ToLower().EndsWith('.png')) {
          Process-PackPng $pack.id $rel $buf $textures $byKey ([ref]$fc)
        } elseif ($rel.ToLower().EndsWith('.ogg')) {
          Process-PackSound $pack.id $rel $buf $textures $byKey ([ref]$fc)
        }
      }
    } finally { $zip.Dispose() }
    Attach-McmetaAndCritJson $pack.id $textures $resolved.Path 'zip'
  }

  $totalFiles += $fc
  if ($textures.Count -eq 0) {
    Write-Host ("  [skip] {0,-18} no textures" -f $pack.id) -ForegroundColor DarkGray
    continue
  }
  $swords = ($textures | Where-Object { $_.category -eq 'swords' }).Count
  $gui = ($textures | Where-Object { $_.category -eq 'gui' }).Count
  $parts = ($textures | Where-Object { $_.category -eq 'particles' }).Count
  $items = ($textures | Where-Object { $_.category -eq 'items' }).Count
  $snd = ($textures | Where-Object { $_.category -eq 'sounds' }).Count
  Write-Host ("  [ ok ] {0,-18} {1,3} total (sw={2} gui={3} pt={4} it={5} snd={6})" -f $pack.id, $textures.Count, $swords, $gui, $parts, $items, $snd) -ForegroundColor Green
  $webPacks += [pscustomobject]@{ id = $pack.id; name = $pack.name; textures = @($textures) }
}

$catalog = [ordered]@{
  version      = 2
  generated_at = (Get-Date).ToUniversalTime().ToString('o')
  source       = 'tools/generate-texture-catalog.ps1'
  packs        = $webPacks
}
$outJson = Join-Path $Out 'textures.json'
$catalog | ConvertTo-Json -Depth 12 | Set-Content -Path $outJson -Encoding UTF8

Write-Host ""
Write-Host "Wrote $outJson ($($webPacks.Count) packs, $totalFiles mirrored files)" -ForegroundColor Cyan

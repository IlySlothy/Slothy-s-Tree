#requires -Version 5.1
<#
.SYNOPSIS
  Upload gallery images to a Modrinth project (Content Rules section 2.1).

.DESCRIPTION
  Reads docs/modrinth-gallery/manifest.json and POSTs each PNG to
  POST https://api.modrinth.com/v2/project/{id}/gallery
  Auth: raw token from .gradle/secrets/modrinth.properties (same as publishModrinth).
#>
param(
  [string] $ProjectId = 'do54gChK',
  [string] $ConfigPath = '',
  [string] $GalleryDir = '',
  [string] $Manifest = '',
  [switch] $ClearExisting
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo      = Split-Path -Parent $scriptDir
if (-not $ConfigPath) { $ConfigPath = Join-Path $repo '.gradle\secrets\modrinth.properties' }
if (-not $GalleryDir) { $GalleryDir = Join-Path $repo 'docs\modrinth-gallery' }
if (-not $Manifest)   { $Manifest   = Join-Path $GalleryDir 'manifest.json' }

if (-not (Test-Path $ConfigPath)) { throw "Missing $ConfigPath" }
if (-not (Test-Path $Manifest))   { throw "Missing $Manifest" }

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
if (-not $token) { throw 'modrinth token missing in modrinth.properties' }

function Get-ModrinthProject {
  Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$ProjectId" -Headers @{
    Authorization = $token
    'User-Agent'  = 'SlothyHubGallery/1.0'
  }
}

function Remove-ModrinthGalleryImage([string] $ImageUrl) {
  $q = [uri]::EscapeDataString($ImageUrl)
  $out = Join-Path $env:TEMP "modrinth-gallery-del.txt"
  $code = & curl.exe -s -o $out -w '%{http_code}' --max-time 30 `
    -X DELETE "https://api.modrinth.com/v2/project/$ProjectId/gallery?url=$q" `
    -H "Authorization: $token" `
    -H 'User-Agent: SlothyHubGallery/1.0'
  if ($code -ne '204') {
    $body = Get-Content $out -Raw -ErrorAction SilentlyContinue
    throw "DELETE gallery failed ($code): $body"
  }
}

function Add-ModrinthGalleryImage(
  [string] $FilePath,
  [bool] $Featured,
  [int] $Ordering,
  [string] $Title,
  [string] $Description
) {
  if (-not (Test-Path $FilePath)) { throw "Missing image: $FilePath" }
  $len = (Get-Item $FilePath).Length
  if ($len -gt 5MB) { throw "Image exceeds 5 MiB: $FilePath ($len bytes)" }

  $ext = [IO.Path]::GetExtension($FilePath).TrimStart('.').ToLowerInvariant()
  if ($ext -ne 'png' -and $ext -ne 'jpg' -and $ext -ne 'jpeg' -and $ext -ne 'webp') {
    throw "Unsupported extension .$ext - use png, jpg, or webp"
  }
  if ($ext -eq 'jpg') { $ext = 'jpeg' }

  $titleQ = [uri]::EscapeDataString($Title)
  $descQ  = [uri]::EscapeDataString($Description)
  $feat   = if ($Featured) { 'true' } else { 'false' }
  $uri = ('https://api.modrinth.com/v2/project/{0}/gallery?ext={1}&featured={2}&ordering={3}&title={4}&description={5}' -f $ProjectId, $ext, $feat, $Ordering, $titleQ, $descQ)

  $ctype = switch ($ext) {
    'png'   { 'image/png' }
    'jpeg'  { 'image/jpeg' }
    'webp'  { 'image/webp' }
    default { 'application/octet-stream' }
  }

  $out = Join-Path $env:TEMP "modrinth-gallery-upload.txt"
  $code = & curl.exe -s -o $out -w '%{http_code}' --max-time 60 `
    -X POST $uri `
    -H "Authorization: $token" `
    -H 'User-Agent: SlothyHubGallery/1.0' `
    -H "Content-Type: $ctype" `
    --data-binary "@$FilePath"

  if ($code -ne '204') {
    $body = Get-Content $out -Raw -ErrorAction SilentlyContinue
    throw "POST gallery failed for $Title ($code): $body"
  }
}

if ($ClearExisting) {
  $proj = Get-ModrinthProject
  foreach ($img in @($proj.gallery)) {
    if ($img.url) {
      Write-Host "Removing: $($img.title)"
      Remove-ModrinthGalleryImage $img.url
    }
  }
}

$entries = Get-Content $Manifest -Raw | ConvertFrom-Json
$featuredCount = @($entries | Where-Object { $_.featured }).Count
if ($featuredCount -ne 1) {
  throw "manifest.json must mark exactly one image featured=true (found $featuredCount)"
}

foreach ($entry in $entries) {
  $path = Join-Path $GalleryDir $entry.file
  Write-Host "Uploading: $($entry.title) ($(Split-Path $path -Leaf))"
  Add-ModrinthGalleryImage $path $entry.featured $entry.ordering $entry.title $entry.description
}

$after = Get-ModrinthProject
Write-Host "Done. Gallery now has $($after.gallery.Count) image(s) on project $ProjectId."
$after.gallery | Sort-Object ordering | ForEach-Object {
  $f = if ($_.featured) { '*' } else { ' ' }
  Write-Host "  $f [$($_.ordering)] $($_.title)"
}

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
if (-not $token) { throw 'modrinth token missing' }

$body = @{
  body = @"
**Slothy's Tree** is a client-side Fabric mod for browsing, applying, and building custom Minecraft resource packs — without leaving the game.

### Features
- Pack browser with 40+ curated packs (GitHub Pages catalog)
- Texture Builder — mix item textures into custom packs
- OptiFine CIT support (by item name, damage, NBT)
- Community pack stars, uploads, and featured pack of the week

### Links
- **Pack catalog & site:** https://ilyslothy.github.io/Slothy-s-Tree
- **Source code:** https://github.com/IlySlothy/Slothy-s-Tree
- **Issue tracker:** https://github.com/IlySlothy/Slothy-s-Tree/issues
- **Releases:** https://github.com/IlySlothy/Slothy-s-Tree/releases

### Requirements
- Fabric Loader (0.19+)
- [Fabric API](https://modrinth.com/mod/fabric-api) for your Minecraft version
- **Client-side only** — not required on servers

Optional online features (pack stars, upload review) use a Cloudflare Worker; the mod works offline with the bundled catalog.
"@
  link_urls = @{
    homepage = 'https://ilyslothy.github.io/Slothy-s-Tree'
    source   = 'https://github.com/IlySlothy/Slothy-s-Tree'
    issues   = 'https://github.com/IlySlothy/Slothy-s-Tree/issues'
    wiki     = 'https://github.com/IlySlothy/Slothy-s-Tree#readme'
  }
  client_side = 'required'
  server_side = 'unsupported'
} | ConvertTo-Json -Depth 4 -Compress

$headers = @{
  Authorization = $token
  'User-Agent'  = 'SlothyHubRelease/1.0'
  'Content-Type'= 'application/json'
}

Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$ProjectId" -Method Patch -Headers $headers -Body $body | Out-Null
Write-Host "Updated Modrinth project $ProjectId metadata."

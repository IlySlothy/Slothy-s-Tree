#requires -Version 5.1
param(
  [string] $Version = '',
  [string] $Notes = '',
  [string] $ConfigPath = ''
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo      = Split-Path -Parent $scriptDir
if (-not $ConfigPath) {
  $ConfigPath = Join-Path $repo '.gradle\secrets\discord.properties'
}

if (-not (Test-Path $ConfigPath)) {
  throw "Missing discord.properties at $ConfigPath"
}

$cfg = @{}
Get-Content $ConfigPath | ForEach-Object {
  $line = $_.Trim()
  if (-not $line -or $line.StartsWith('#')) { return }
  $eq = $line.IndexOf('=')
  if ($eq -lt 1) { return }
  $cfg[$line.Substring(0, $eq).Trim()] = $line.Substring($eq + 1).Trim()
}

$channelId = $cfg['channelId']
$webhook   = $cfg['webhookUrl']
$botToken  = $cfg['botToken']
if (-not $botToken) {
  $envFile = Join-Path $repo 'discord-bot\.env'
  if (Test-Path $envFile) {
    $line = Get-Content $envFile | Where-Object { $_ -match '^DISCORD_TOKEN=' } | Select-Object -First 1
    if ($line) { $botToken = ($line -replace '^DISCORD_TOKEN=', '').Trim().Trim('"') }
  }
}

if (-not $channelId -and -not $webhook) {
  throw 'Set channelId or webhookUrl in discord.properties'
}

if (-not $Version) {
  $gp = Join-Path $repo 'gradle.properties'
  if (Test-Path $gp) {
    $m = Select-String -Path $gp -Pattern '^mod_version=(.+)$' | Select-Object -First 1
    if ($m) { $Version = $m.Matches[0].Groups[1].Value.Trim() }
  }
  if (-not $Version) { $Version = '1.0.3' }
}

$baseVer = ($Version -replace '-mc.*$', '').Trim()
$repoUrl = 'https://github.com/IlySlothy/Slothy-s-Tree/releases'

$roleIds = @()
if ($cfg['modUpdatesRoleId']) {
  $roleIds = @($cfg['modUpdatesRoleId'].Trim())
} elseif ($cfg['roleIds']) {
  $roleIds = @($cfg['roleIds'] -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}
$pingParts = @()
foreach ($id in $roleIds) { $pingParts += "<@&$id>" }
$content = ($pingParts -join ' ')
if (-not $content) { $content = 'New release:' }

$noteLines = if ($Notes) {
  ($Notes -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
} else {
  @(
    'Offhands slot - ITEM/BLOCK tabs plus amethyst_shard textures pulled from every pack'
    'Pack Library edit - reopen a saved pack and save updates the same entry (no duplicate packs)'
    'Heart slots - Heart Half auto-selects matching Heart Full from the same pack'
    'Performance - CIT name cache, early exit for non-CIT items, lighter HUD tick work'
    'Web catalog - bundled + live merge so new textures appear before GitHub Pages updates'
  )
}

$descLines = @(
  "Fresh v$baseVer builds are on GitHub. Pick the jar for your Minecraft version below."
  ''
  '**What is new**'
)
foreach ($n in $noteLines) {
  if ($n -match '^\*\*') { $descLines += $n } else { $descLines += "- $n" }
}

$tag111 = "v${baseVer}-mc1.21.11"
$tag118 = "v${baseVer}-mc1.21.8"
$tag201 = "v${baseVer}-mc1.20-1.21.1"

$embed = @{
  title       = "Slothy's Tree v$baseVer"
  url         = "$repoUrl/latest"
  color       = 5414522
  description = ($descLines -join "`n")
  fields      = @(
    @{
      name   = 'MC 1.21.9 - 1.21.11'
      value  = "[$tag111]($repoUrl/tag/$tag111)`nMain jar + CIT companion (both required)"
      inline = $true
    },
    @{
      name   = 'MC 1.21.8'
      value  = "[$tag118]($repoUrl/tag/$tag118)`nAll-in-one jar (CIT built in)"
      inline = $true
    },
    @{
      name   = 'MC 1.20 - 1.21.1'
      value  = "[$tag201]($repoUrl/tag/$tag201)`nLegacy jar + CIT companion"
      inline = $true
    },
    @{
      name   = 'Get it in Discord'
      value  = 'Run /download and pick your version for direct links.'
      inline = $false
    }
  )
  footer = @{ text = "Slothy's Tree - github.com/IlySlothy/Slothy-s-Tree" }
  timestamp = (Get-Date).ToUniversalTime().ToString('o')
}

$payload = @{
  content          = $content
  embeds           = @($embed)
  allowed_mentions = @{
    parse = @()
    roles = $roleIds
  }
}

$json = $payload | ConvertTo-Json -Depth 8 -Compress
$bytes = [System.Text.Encoding]::UTF8.GetBytes($json)

Write-Host 'Posting release announcement to Discord...'
if ($channelId -and $botToken) {
  $headers = @{
    Authorization = "Bot $botToken"
    'User-Agent'  = 'SlothyHubRelease/1.0'
  }
  Invoke-RestMethod -Uri "https://discord.com/api/v10/channels/$channelId/messages" `
    -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $bytes | Out-Null
} else {
  Invoke-RestMethod -Uri $webhook -Method Post -ContentType 'application/json; charset=utf-8' -Body $bytes | Out-Null
}
Write-Host 'Done.'

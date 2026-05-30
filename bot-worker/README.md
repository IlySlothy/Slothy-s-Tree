# Slothy's Tree Bot - Cloudflare Workers edition

This is the **free, forever, no-credit-card** version of the bot. It runs
on Cloudflare Workers and handles Discord interactions (slash commands,
buttons, dropdowns) over plain HTTPS instead of a long-lived WebSocket.

**Trade-off**: the bot appears **offline** in Discord member lists because
there is no gateway connection. Commands and buttons still work normally.

The same Worker also serves the mod-client heartbeat endpoint that feeds
`/modstats` "Active Users (15 min)".

## Routes the Worker serves

| Method | Path             | Purpose                                                    |
| ------ | ---------------- | ---------------------------------------------------------- |
| POST   | `/`              | Discord Interactions endpoint (Ed25519 signature required) |
| POST   | `/v1/heartbeat`  | Mod clients ping with `{ "clientId": "<stable-uuid>" }`    |
| POST   | `/v1/pack-submit`| Mod uploads a built pack zip — **DMs you** and/or **opens a ticket** |

### Approve or deny uploads (Discord commands)

When a pack is submitted, the bot stores it for **30 days** and includes a **submission ID** in the DM/ticket.

Only `PACK_REVIEW_OWNER_USER_ID` can run these (you):

| Command | What it does |
| ------- | ------------ |
| `/pack-approve id:<id>` | Uploads the zip to `docs/downloads/`, adds an entry to `docs/api/packs.json`, and publishes via GitHub Pages |
| `/pack-deny id:<id> reason:<optional>` | Rejects the submission and removes the stored zip |

**Requires `GITHUB_TOKEN`** (secret) with **repo** write access on `IlySlothy/Slothy-s-Tree`:

```bash
npx wrangler secret put GITHUB_TOKEN
```

Use a [fine-grained PAT](https://github.com/settings/tokens?type=beta) or classic token scoped to **Contents: Read and write** on that repo.

After `/pack-approve`, GitHub Pages redeploys in ~1 minute. Mod users press **Reconnect** in the pack browser to see the new pack.

Register the new slash commands after deploy:

```bash
$env:DISCORD_TOKEN="..."; $env:DISCORD_APP_ID="..."; npm run register
```

### Pack upload review (mod → Discord)

When someone hits **UPLOAD** in **My Pack Library**, the mod POSTs the zip to `/v1/pack-submit`.
Configure **one or both** in `wrangler.toml`:

| Variable | What it does |
| -------- | ------------ |
| `PACK_REVIEW_OWNER_USER_ID` | Bot **DMs you** every request (zip attached). **Recommended.** |
| `PACK_TICKET_GUILD_ID` + `PACK_TICKET_CATEGORY_ID` | Bot creates a **private ticket channel** per upload (only you + bot can see it). |
| `PACK_REVIEW_CHANNEL_ID` | Fallback public channel if DM/ticket fail. |

**Get your user ID:** Discord → Settings → Advanced → Developer Mode ON → right-click your name → **Copy User ID**.

**Get guild/category IDs:** right-click server / category → Copy ID.

The bot needs **Manage Channels** (for tickets) and must share a server with you (for DMs).

Example `wrangler.toml`:

```toml
PACK_REVIEW_OWNER_USER_ID = "123456789012345678"
PACK_TICKET_GUILD_ID = "987654321098765432"
PACK_TICKET_CATEGORY_ID = "111222333444555666"
```

Then redeploy: `npx wrangler deploy`

| GET    | `/v1/stats`      | `{ activeUsers, heartbeatTtlSec }`                         |
| GET    | `/healthz`       | `{ ok: true }`                                             |

## One-time setup (about 10 minutes)

### 1. Create the Cloudflare account

1. Sign up at <https://dash.cloudflare.com/sign-up> (email only, no card).
2. From the left sidebar, click **Workers & Pages** > **Get started**.
3. The Workers free plan is auto-enabled - 100,000 requests/day.

### 2. Install wrangler locally

```bash
cd bot-worker
npm install
```

(`wrangler` is a devDependency, so it installs into `node_modules/.bin`.)

### 3. Authenticate wrangler

```bash
npx wrangler login
```

This opens a browser, you click "Allow", done.

### 4. Create the KV namespace for heartbeats

```bash
npx wrangler kv namespace create HEARTBEATS
```

Wrangler prints:

```
[[kv_namespaces]]
binding = "HEARTBEATS"
id = "abcd1234..."
```

Copy the `id` value into `wrangler.toml` where it says `PASTE-KV-ID-HERE`.

### 5. Grab your Discord application's credentials

Go to <https://discord.com/developers/applications> > your app:

- **General Information** > **Application ID** -> `DISCORD_APP_ID`
- **General Information** > **Public Key** -> `DISCORD_PUBLIC_KEY`
- **Bot** > **Reset Token** -> `DISCORD_TOKEN`

### 6. Set the secrets in Cloudflare

```bash
npx wrangler secret put DISCORD_PUBLIC_KEY
npx wrangler secret put DISCORD_TOKEN
npx wrangler secret put DISCORD_APP_ID
# Optional, raises GitHub API rate limit:
npx wrangler secret put GITHUB_TOKEN
```

Each command prompts you to paste the value. Secrets are encrypted at rest
and never appear in logs.

### 7. Deploy

```bash
npx wrangler deploy
```

Wrangler prints something like:

```
Published slothys-tree-bot (1.23 sec)
  https://slothys-tree-bot.<your-cf-subdomain>.workers.dev
```

That `https://...` is your Interactions Endpoint URL.

### 8. Tell Discord where to POST interactions

1. Back at <https://discord.com/developers/applications> > your app
   > **General Information**.
2. Find **Interactions Endpoint URL** > paste the URL from step 7.
3. Click **Save Changes**. Discord sends a test PING to verify; if the
   button stays clicked, you're good. (If Discord rejects it, the
   Worker's logs at `npx wrangler tail` will show why - usually a
   `DISCORD_PUBLIC_KEY` typo.)

### 9. Register the slash commands

```bash
# PowerShell:
$env:DISCORD_TOKEN="..."; $env:DISCORD_APP_ID="..."; npm run register

# bash/zsh:
DISCORD_TOKEN="..." DISCORD_APP_ID="..." npm run register
```

This pushes `/modinfo`, `/download`, `/setup`, `/invite`, `/modstats`, `/pack-approve`, and `/pack-deny` to
Discord globally. New commands take up to an hour to fan out across all
servers; existing-server upgrades are usually instant.

(If you want them to appear in *only one server* for testing, also set
`DISCORD_GUILD_ID=<id>`.)

## That's it

Try `/modinfo` in any server the bot is in. The bot will still show as
"offline" in member lists - that's expected and not a bug.

## Release announcements (Discord)

After every GitHub release, post to **#bug-fixes** with `@here` and your usual role pings:

1. Copy `gradle/secrets/discord.properties.example` → `.gradle/secrets/discord.properties`
2. Set `webhookUrl` (bug-fixes channel webhook) and `roleIds` (comma-separated)
3. Run after tagging / pushing releases:

```powershell
.\tools\post-release-discord.ps1 -Notes "Pack library customize, bubble coral, three-tier downloads."
# or
.\gradlew.bat announceReleaseDiscord -PreleaseNotes="Your bullet points here"
```

When releasing via Cursor, the agent will run this automatically after GitHub tags are pushed.

## Updating

After any code change, just:

```bash
npx wrangler deploy
```

To watch live logs:

```bash
npm run tail
```

## Cost

Workers free tier:

- 100,000 requests/day (Discord sends one request per slash command click)
- 10ms CPU per request
- 1,000 KV writes/day (each heartbeat = 1 write)

For a small mod community this is well within the free tier. If you ever
exceed 1,000 heartbeats/day, bump the mod-side ping interval (15 min is
plenty) or migrate the heartbeat store to D1 (also free, 100k writes/day).

## Future: bringing the bot "online" again

If you ever want the green dot back, pair this Worker with a tiny
gateway-only process running on:

- An old laptop / Raspberry Pi at home (always-on, free)
- A friend's VPS
- Cloudflare Workers Paid + Durable Objects ($5/mo, fully managed)

That process opens a WebSocket to Discord, identifies as the same bot,
sets presence, and does nothing else. The Worker continues handling all
the actual commands.

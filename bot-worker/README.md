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

This pushes `/modinfo`, `/download`, `/setup`, `/invite`, `/modstats` to
Discord globally. New commands take up to an hour to fan out across all
servers; existing-server upgrades are usually instant.

(If you want them to appear in *only one server* for testing, also set
`DISCORD_GUILD_ID=<id>`.)

## That's it

Try `/modinfo` in any server the bot is in. The bot will still show as
"offline" in member lists - that's expected and not a bug.

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

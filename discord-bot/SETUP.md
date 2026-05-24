# Slothy's Tree Bot — Setup Guide

## 1. Create the Discord Application

1. Go to https://discord.com/developers/applications
2. **New Application** → name it "Slothy's Tree"
3. Go to **Bot** tab → **Add Bot**
4. Copy the **Token** (you'll need it below)
5. Under **Privileged Gateway Intents**, enable nothing extra (default intents are fine)
6. Go to **OAuth2 → URL Generator**:
   - Scopes: `bot` + `applications.commands`
   - Bot Permissions: `Send Messages`, `Embed Links`, `Read Messages/View Channels`
7. Copy the generated URL and open it to invite the bot to your server

## 2. Configure the Bot

```bash
cd discord-bot
cp .env.example .env
```

Edit `.env`:
```
DISCORD_TOKEN=paste-your-bot-token-here
GUILD_ID=paste-your-server-id-here
```

**Getting your Server ID:**  
Enable Developer Mode (User Settings → Advanced → Developer Mode),  
then right-click your server icon → **Copy Server ID**.

## 3. Install & Run

```bash
pip install -r requirements.txt
python bot.py
```

## 4. Set Up in Your Server

Once the bot is online, go to the channel where you want the download button  
(e.g. `#mod-info`) and run:

```
/setup
```

This posts a permanent **"📥 Get the Mod"** button. When any member clicks it,  
they get a private ephemeral dropdown — only they can see it.

## Commands

| Command      | Who can use | Description |
|-------------|------------|-------------|
| `/modinfo`   | Everyone    | Shows mod info privately |
| `/download`  | Everyone    | Opens the dropbox-style selector privately |
| `/setup`     | Manage Messages | Posts the persistent button in the current channel |

## Updating the Mod Version

Edit `bot.py` lines at the top:
```python
MOD_VERSION = "1.0.0"
MC_VERSION  = "1.21.8"
GITHUB_RELEASES = "https://github.com/IlySlothy/Slothy-s-Tree/releases/latest"
```

## Keeping It Running

Use **PM2** (Node process manager, works for Python too):
```bash
npm install -g pm2
pm2 start bot.py --interpreter python
pm2 save
pm2 startup
```

Or run it on a free host like [Railway](https://railway.app) or [Replit](https://replit.com).

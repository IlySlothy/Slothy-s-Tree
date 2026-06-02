**Slothy's Tree** is a client-side Fabric mod for browsing, applying, and building custom Minecraft resource packs — without leaving the game.

---

## Features

### Pack Browser
Browse 40+ curated resource packs by **ilyslothy**. Apply any pack with one click. Filter by tags, sort by community stars, and see featured community picks.

### Texture Builder
Mix and match item textures, GUI elements, particles, and sounds from different packs. Build a custom pack locally and save it to your library.

### CIT Support
**OptiFine CIT** (Custom Item Textures) — item names, damage, NBT, and related rules.

| Minecraft | CIT on Modrinth downloads |
|---|---|
| **1.21.8** | Built into the main jar |
| **1.20 – 1.21.1** | Use **[CIT Resewn](https://modrinth.com/mod/cit-resewn)** for your version (recommended). The legacy Slothy's Tree jar includes a fallback engine that **automatically yields** when CIT Resewn or similar is installed — no separate Slothy CIT companion jar. |
| **1.21.9 – 1.21.11** | Pack browser and Texture Builder work now; **embedded CIT for these versions is still in development** — do not expect full CIT parity yet. |

### Community
- **Star packs** in the browser (toggle on/off)
- **Upload packs** for moderator review (optional)
- **My Uploads** dashboard to track submission status
- **What's New** screen after updates

### Extras
Kill effects, Feather profile import, local pack library, and customizable UI themes.

### Screenshots
See the **Gallery** tab on this project page for labeled screenshots (Pack Browser, Texture Builder, in-game custom items, and more).

---

## Requirements

| Dependency | Notes |
|---|---|
| **Fabric Loader** | 0.19+ recommended |
| **[Fabric API](https://modrinth.com/mod/fabric-api)** | Required — match your MC version |
| **Minecraft** | Pick the version file that matches your game |
| **[CIT Resewn](https://modrinth.com/mod/cit-resewn)** | Optional but recommended on **1.20 – 1.21.1** for full CIT |

**Client-side only** — do not install on a server.

| MC version | Modrinth file to download |
|---|---|
| **1.21.8** | `slothyhub-*-mc1.21.8.jar` (main only) |
| **1.21.9 – 1.21.11** | `slothyhub-*-mc1.21.11.jar` (main only; CIT WIP) |
| **1.20 – 1.21.1** | `slothyhub-*-mc1.20-1.21.1.jar` (legacy main; add CIT Resewn for CIT) |

---

## Copyright & catalog

- **Slothy's Tree** (mod code) is **MIT** — see the [LICENSE](https://github.com/IlySlothy/Slothy-s-Tree/blob/main/LICENSE) in the repository.
- **Default catalog packs** are created and distributed by **ilyslothy** for use with this mod. Do not re-upload those packs elsewhere without permission.
- **Community pack uploads** are moderated before they can appear in the public catalog.
- Portions of the embedded CIT fallback are adapted from **[CIT Resewn](https://github.com/SHsuperCM/CITResewn)** (MIT).

---

## Network connections & data collection

> Per Modrinth Content Rules §1.11 — this mod makes **optional and required network requests**. The mod works offline for many features using bundled catalog data; online features need the connections below.

### 1. Pack catalog & downloads (GitHub Pages)
**Host:** `https://ilyslothy.github.io/Slothy-s-Tree`

| When | What is sent | What is received |
|---|---|---|
| Opening Pack Browser | A random install UUID (`X-SlothyHub-Voter` header) when fetching star counts | `packs.json` catalog, star counts |
| Applying a pack | Standard HTTP GET (no account login) | Resource pack `.zip` files |
| Texture Builder | Standard HTTP GET | `textures.json` index, PNG/OGG preview files |

**Data collected by the mod author on this host:** none beyond normal web server logs (IP, user-agent) if you visit the site. The mod does **not** send your Minecraft username or Mojang UUID to GitHub Pages.

### 2. Community services (Cloudflare Worker)
**Host:** `https://slothys-tree-bot.elytrapacks.workers.dev`  
*(Override in mod settings → heartbeat URL if needed.)*

| When | Endpoint | What is sent | Purpose |
|---|---|---|---|
| While playing (~every 14 min) | `POST /v1/heartbeat` | Random **client UUID** only (stored in `config/slothyhub.json`) | Aggregate “active users” stat for Discord `/modstats` |
| Starring a pack | `POST /v1/pack-star` | Client UUID + pack ID | Community star counts |
| Loading pack list | `GET /v1/pack-stars` | Client UUID (header) | Your starred packs + counts |
| **Only if you upload a pack** | `POST /v1/pack-submit` | Pack zip, name, description, tags, optional contact, client UUID | Moderator review queue |
| **Only if you use My Uploads** | `GET /v1/submit-status` | Client UUID | Your submission statuses |

**Not sent:** Minecraft username, Mojang UUID, chat, world data, or installed-mod lists (except the pack file you explicitly upload).

**Stored server-side:** star votes (client UUID + pack ID), heartbeat presence (~15 min TTL), upload metadata until approved/denied. You can regenerate your local UUID in mod config (`voterId`).

### 3. Direct pack URLs
Some catalog entries link to **GitHub Releases** or other HTTPS URLs listed in `packs.json`. Downloading those packs is a normal GET request initiated when you click Apply.

---

## Offline behavior

- A **bundled pack catalog** ships inside the jar if GitHub Pages is unreachable.
- Texture Builder falls back to bundled/local scans when the web catalog is empty.
- Heartbeats, stars, and uploads require the worker; they fail silently or show an error in UI if offline.

---

## Links

- **Pack catalog & site:** https://ilyslothy.github.io/Slothy-s-Tree
- **Source code:** https://github.com/IlySlothy/Slothy-s-Tree
- **Issues / privacy questions:** https://github.com/IlySlothy/Slothy-s-Tree/issues

---

## License

MIT — see [LICENSE](https://github.com/IlySlothy/Slothy-s-Tree/blob/main/LICENSE) in the repository.

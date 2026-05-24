# Hosting resource packs on GitHub (free)

Slothy's Tree reads the pack catalog from **GitHub Pages** and downloads `.zip` files from **GitHub Releases**.

**Catalog URL (default in mod):**  
`https://ilyslothy.github.io/Slothy-s-Tree`

The mod fetches: `https://ilyslothy.github.io/Slothy-s-Tree/api/packs.json`

---

## One-time setup (GitHub repo settings)

1. Open **Settings → Pages**
2. Under **Build and deployment**, set **Source** to **GitHub Actions**
3. Push to `main` — the `Deploy GitHub Pages` workflow publishes the `docs/` folder

Your site will be live at:  
`https://ilyslothy.github.io/Slothy-s-Tree/`

---

## Add or update a pack

### 1. Upload the `.zip` to a Release

1. Go to **Releases → Draft a new release** (or edit an existing packs release)
2. Tag example: `packs-v1` (use `packs-v2` when you add more packs later)
3. Attach the `.zip` files (Summer.zip, FallenSnow.zip, etc.)
4. Publish the release

Direct download URL format:

```
https://github.com/IlySlothy/Slothy-s-Tree/releases/download/packs-v1/YourPack.zip
```

### 2. Add an entry to `docs/api/packs.json`

```json
{
  "id": "my-pack",
  "name": "My Pack",
  "pack_filename": "MyPack.zip",
  "author_name": "ilyslothy",
  "author_id": "ilyslothy",
  "showcase_path": "",
  "pack_url": "https://github.com/IlySlothy/Slothy-s-Tree/releases/download/packs-v1/MyPack.zip",
  "tags": ["pvp"],
  "is_zip": true,
  "has_local_file": false,
  "star_count": 0,
  "downloads": 0,
  "sha256": "",
  "viewer_starred": false
}
```

- **`pack_url`** — full HTTPS link to the release asset (required for GitHub hosting)
- **`id`** — unique slug (no spaces)
- **`showcase_path`** — optional thumbnail, e.g. `/showcases/summer.png` (put PNG in `docs/showcases/`)

### 3. Push to `main`

GitHub Actions redeploys Pages. Users press **Reconnect** in the mod (or reopen the GUI) to see new packs.

---

## Moving to a paid domain later

1. Host the same files on your new server (same JSON + zip URLs or your own API)
2. In mod **Settings**, change **Server URL** to your new base URL
3. Keep the same pack `id` values so nothing breaks for existing users

---

## Notes

- **Stars** do not work on static GitHub Pages (no backend). The mod still browses and downloads packs.
- Large packs are fine on Releases (up to 2 GB per file).
- Optional: set `"sha256"` in JSON and enable **Verify downloads** in mod settings for checksum checks.

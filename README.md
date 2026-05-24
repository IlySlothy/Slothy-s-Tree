<div align="center">

<!-- LOGO / BANNER -->
<img src="src/main/resources/assets/slothyhub/textures/gui/logo.png" alt="Slothy's Tree" width="340"/>

<br/>

# 🌿 Slothy's Tree

**A sloth-themed Minecraft Fabric mod for browsing, applying, and building custom resource packs.**

<br/>

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20–1.21.x-2d4a36?style=for-the-badge&logo=minecraft&logoColor=52D47A)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-Loader%200.19%2B-52D47A?style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAzMiAzMiI+PHBhdGggZmlsbD0iI2ZmZiIgZD0iTTE2IDJMMiAyOGgxMkwxNiA4bDIgNmg2eiIvPjwvc3ZnPg==)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-21%2B-7DE89C?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-253C2C?style=for-the-badge)](LICENSE)
[![GitHub Release](https://img.shields.io/github/v/release/IlySlothy/Slothy-s-Tree?style=for-the-badge&color=52D47A&label=Latest)](https://github.com/IlySlothy/Slothy-s-Tree/releases/latest)

<br/>

*Hang from Slothy's Tree — where every texture has a home.*

</div>

---

## ✨ Features

<table>
<tr>
<td width="50%">

### 🎒 Pack Browser
Browse 40+ curated resource packs by **ilyslothy** directly in-game.  
Apply any pack with a single click — no more digging through folders.

</td>
<td width="50%">

### 🎨 Texture Builder
Mix and match item textures from different packs.  
Build your own custom pack without leaving Minecraft.

</td>
</tr>
<tr>
<td width="50%">

### 🔮 CIT Support
Full **OptiFine CIT** (Custom Item Textures) support.  
Textures activate by item name, damage, NBT — just like OptiFine.

</td>
<td width="50%">

### ⚡ Kill Effects & Profiles
Configure kill effects and **Feather profiles** to personalise your gameplay experience.

</td>
</tr>
</table>

---

## 🖥️ Screenshots

> *In-game screenshots coming soon — open a PR if you'd like to contribute!*

---

## 📦 Installation

### Requirements

| Dependency | Version | Download |
|---|---|---|
| Minecraft | 1.20 – 1.21.x | [minecraft.net](https://minecraft.net) |
| Fabric Loader | ≥ 0.19 | [fabricmc.net](https://fabricmc.net/use/installer/) |
| Fabric API | Latest for your MC version | [Modrinth](https://modrinth.com/mod/fabric-api) |

### Steps

```
1. Install Fabric Loader for your Minecraft version.

2. Download the latest release JAR:
   slothyhub-1.0.0-mc1.21.8.jar

3. Drop the JAR into:
   .minecraft/mods/

4. Drop Fabric API into:
   .minecraft/mods/

5. Launch Minecraft with the Fabric profile.
```

> **CIT textures by name** require your resource packs to include OptiFine-format `.properties` files under `assets/minecraft/optifine/cit/`.

---

## 🎮 Usage

| Keybind | Default | Action |
|---|---|---|
| Open Hub | `H` | Opens Slothy's Tree pack browser |

### Pack Browser
- **PACKS** tab — browse and apply resource packs
- **TEXTURES** tab — open the Texture Builder
- **KILL FX** tab — configure kill effects
- **FEATHER** tab — manage profiles

### Texture Builder
1. Select an item on the left panel
2. Choose a texture override from the right panel (scanned from your packs)
3. Press **Build & Apply** — your custom pack is created and applied instantly

---

## 🛠️ Building from Source

### Prerequisites
- JDK 21+
- Internet connection (Gradle downloads Minecraft assets on first run)

```bash
# Clone the repo
git clone https://github.com/IlySlothy/Slothy-s-Tree.git
cd Slothy-s-Tree

# Build
./gradlew build

# Output JAR
build/libs/slothyhub-1.0.0-mc1.21.8.jar
```

---

## 🏗️ Project Structure

```
src/main/java/com/slothyhub/
├── SlothyHubMod.java              # Mod entry point
├── SlothyConfig.java              # Settings & configuration
├── SlothyHubScreenBase.java       # Core GUI (pack browser)
├── TexturePickerScreen.java       # Texture Builder UI
├── KillEffectsScreen.java         # Kill effects configuration
├── cit/
│   ├── CitEngine.java             # CIT system entry point
│   ├── CitRuleParser.java         # OptiFine .properties parser
│   ├── CitRuleSet.java            # Active rules manager
│   ├── CitItemRenderer.java       # Runtime model replacement
│   └── CitResourceReloadListener.java
├── local/
│   └── LocalPackManager.java      # Local pack discovery & import
├── compat/
│   ├── DrawHelper.java            # Multi-version draw utilities
│   └── VersionCompat.java         # Reflection-based version compat
└── mixin/
    └── MixinCitItemRenderState.java  # CIT render hook
```

---

## 🤝 Contributing

Pull requests are welcome! Please:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit your changes: `git commit -m "Add my feature"`
4. Push and open a PR

For bug reports and feature requests, use [GitHub Issues](https://github.com/IlySlothy/Slothy-s-Tree/issues).

---

## 📜 License

This project is licensed under the **MIT License** — see [LICENSE](LICENSE) for details.

---

## 💬 Community

Join the Discord server to download the mod, get help, and chat:

<div align="center">

[![Discord](https://img.shields.io/badge/Discord-Join%20Server-52D47A?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/rWkgYHB9f)

</div>

---

<div align="center">

Made with 🦥 by **ilyslothy**

</div>

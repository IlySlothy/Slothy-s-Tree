"""
Slothy's Tree — Discord Bot
===========================
Provides a slash-command + select-menu (dropbox) to privately deliver the
mod download link and all required dependencies.

Usage
-----
1. Install deps:  pip install -r requirements.txt
2. Copy .env.example to .env and fill in your values.
3. Run:           python bot.py

How it works
------------
• /modinfo  — instantly replies with an ephemeral "How to install" embed.
• /download — shows an ephemeral select-menu where the user picks what they
              want, then presses the button to get links (visible only to them).
• A permanent "📥 Get the Mod" button in #mod-info channel (optional — run
  /setup in that channel to post it).
"""

import os
import discord
from discord import app_commands
from discord.ui import View, Select, Button
from dotenv import load_dotenv

load_dotenv()

TOKEN      = os.getenv("DISCORD_TOKEN")
GUILD_ID   = int(os.getenv("GUILD_ID", "0"))          # Your server ID
MOD_ROLE   = os.getenv("MOD_ROLE_ID", None)           # Optional: role for /setup

# ── Mod info ─────────────────────────────────────────────────────────────────

MOD_NAME        = "Slothy's Tree"
MOD_VERSION     = "1.0.0"
MC_VERSION      = "1.21.8"
GITHUB_RELEASES = "https://github.com/IlySlothy/Slothy-s-Tree/releases/latest"
FABRIC_LOADER   = "https://fabricmc.net/use/installer/"
FABRIC_API      = "https://modrinth.com/mod/fabric-api"
MODRINTH_PAGE   = "https://github.com/IlySlothy/Slothy-s-Tree"   # update if published on Modrinth

DEPENDENCIES = [
    {"name": "Fabric Loader",   "url": FABRIC_LOADER,  "required": True,  "note": "≥ 0.19"},
    {"name": "Fabric API",      "url": FABRIC_API,      "required": True,  "note": "Grab the 1.21.8 version"},
    {"name": "OptiFine / CIT Resewn", "url": "https://modrinth.com/mod/cit-resewn", "required": False, "note": "Only needed for CIT textures by name"},
]

ACCENT_COLOUR = 0x52D47A   # matches the mod's green
DANGER_COLOUR = 0xDE5050

# ─────────────────────────────────────────────────────────────────────────────

intents = discord.Intents.default()
client  = discord.Client(intents=intents)
tree    = app_commands.CommandTree(client)
guild_obj = discord.Object(id=GUILD_ID)


def mod_download_embed() -> discord.Embed:
    """Main download embed shown privately to the user."""
    e = discord.Embed(
        title=f"📦  {MOD_NAME}  v{MOD_VERSION}",
        description=(
            f"Requires **Minecraft {MC_VERSION}** + Fabric.\n"
            "All links are only visible to you."
        ),
        colour=ACCENT_COLOUR,
    )
    e.add_field(
        name="⬇️  Download the Mod",
        value=f"[GitHub Releases]({GITHUB_RELEASES})\n"
              f"`slothyhub-{MOD_VERSION}-mc{MC_VERSION}.jar`",
        inline=False,
    )
    e.add_field(
        name="🔧  Fabric Loader",
        value=f"[fabricmc.net installer]({FABRIC_LOADER})\n"
              f"Download & run the installer, pick **{MC_VERSION}**.",
        inline=False,
    )
    e.add_field(
        name="📚  Dependencies",
        value="\n".join(
            f"{'✅' if d['required'] else '🔷'} [{d['name']}]({d['url']}) — {d['note']}"
            for d in DEPENDENCIES
        ),
        inline=False,
    )
    e.add_field(
        name="📁  Install steps",
        value=(
            "1. Install Fabric Loader for 1.21.8\n"
            "2. Drop `slothyhub-…jar` into `.minecraft/mods/`\n"
            "3. Drop `fabric-api-…jar` into `.minecraft/mods/`\n"
            "4. Launch Minecraft with the **Fabric** profile"
        ),
        inline=False,
    )
    e.set_footer(text=f"{MOD_NAME} · github.com/IlySlothy/Slothy-s-Tree")
    return e


def info_embed() -> discord.Embed:
    """Brief info embed for /modinfo."""
    e = discord.Embed(
        title=f"🌿  {MOD_NAME}",
        description=(
            "A sloth-themed resource pack hub for Minecraft.\n"
            "Browse, apply, and build custom texture packs — hang from Slothy's Tree."
        ),
        colour=ACCENT_COLOUR,
        url=MODRINTH_PAGE,
    )
    e.add_field(name="Version",     value=MOD_VERSION,  inline=True)
    e.add_field(name="MC Version",  value=MC_VERSION,   inline=True)
    e.add_field(name="Loader",      value="Fabric",      inline=True)
    e.add_field(
        name="Features",
        value=(
            "• Browse 40+ ilyslothy resource packs\n"
            "• One-click apply to active packs\n"
            "• Texture Builder — mix & match item textures\n"
            "• CIT support (custom item textures by name)\n"
            "• Kill effects, Feather profiles, and more"
        ),
        inline=False,
    )
    e.set_footer(text="Use /download to get the mod privately.")
    return e


# ── Views ────────────────────────────────────────────────────────────────────

class DownloadSelect(Select):
    """Dropdown menu — user picks what they want."""
    def __init__(self):
        options = [
            discord.SelectOption(
                label="🌿  Download Slothy's Tree Mod",
                description="Get the latest JAR + install instructions",
                value="mod",
                emoji="📦",
            ),
            discord.SelectOption(
                label="🔧  Fabric Loader",
                description="Download the Fabric installer",
                value="fabric",
                emoji="🔧",
            ),
            discord.SelectOption(
                label="📚  Fabric API",
                description="Required dependency — Modrinth link",
                value="fabric_api",
                emoji="📚",
            ),
            discord.SelectOption(
                label="📋  All dependencies at once",
                description="Everything you need in one message",
                value="all",
                emoji="📋",
            ),
        ]
        super().__init__(
            placeholder="What do you need?",
            min_values=1,
            max_values=1,
            options=options,
        )

    async def callback(self, interaction: discord.Interaction):
        await interaction.response.defer(ephemeral=True)
        choice = self.values[0]

        if choice == "mod":
            e = discord.Embed(
                title="📦  Slothy's Tree — Download",
                description=f"[Click here to download the latest release]({GITHUB_RELEASES})",
                colour=ACCENT_COLOUR,
            )
            e.add_field(
                name="File",
                value=f"`slothyhub-{MOD_VERSION}-mc{MC_VERSION}.jar`",
                inline=False,
            )
            e.add_field(
                name="Install",
                value="Drop the JAR into your `.minecraft/mods/` folder.",
                inline=False,
            )

        elif choice == "fabric":
            e = discord.Embed(
                title="🔧  Fabric Loader",
                description=f"[Download the Fabric Installer]({FABRIC_LOADER})",
                colour=ACCENT_COLOUR,
            )
            e.add_field(
                name="Steps",
                value=(
                    f"1. Download the installer\n"
                    f"2. Run it and select **Minecraft {MC_VERSION}**\n"
                    f"3. Press Install Client"
                ),
                inline=False,
            )

        elif choice == "fabric_api":
            e = discord.Embed(
                title="📚  Fabric API",
                description=f"[Download from Modrinth]({FABRIC_API})",
                colour=ACCENT_COLOUR,
            )
            e.add_field(
                name="Note",
                value=f"Download the version for Minecraft **{MC_VERSION}** and drop it in `.minecraft/mods/`.",
                inline=False,
            )

        else:  # all
            e = mod_download_embed()

        await interaction.followup.send(embed=e, ephemeral=True)


class DownloadView(View):
    def __init__(self):
        super().__init__(timeout=180)
        self.add_item(DownloadSelect())

    @discord.ui.button(label="📋  Show Everything", style=discord.ButtonStyle.green)
    async def show_all(self, interaction: discord.Interaction, button: Button):
        await interaction.response.send_message(embed=mod_download_embed(), ephemeral=True)


class PersistentDownloadView(View):
    """Persistent view posted in a channel — button survives bot restarts."""
    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(
        label="📥  Get the Mod",
        style=discord.ButtonStyle.green,
        custom_id="slothy_get_mod_persistent",
        emoji="🌿",
    )
    async def get_mod(self, interaction: discord.Interaction, button: Button):
        await interaction.response.send_message(
            view=DownloadView(),
            content="**Choose what you need** — this message is only visible to you.",
            ephemeral=True,
        )


# ── Slash commands ────────────────────────────────────────────────────────────

@tree.command(
    name="modinfo",
    description="Show information about Slothy's Tree mod.",
    guild=guild_obj,
)
async def cmd_modinfo(interaction: discord.Interaction):
    await interaction.response.send_message(embed=info_embed(), ephemeral=True)


@tree.command(
    name="download",
    description="Get download links for Slothy's Tree and its dependencies (only you can see this).",
    guild=guild_obj,
)
async def cmd_download(interaction: discord.Interaction):
    await interaction.response.send_message(
        content="**Pick what you need** — only you can see this.",
        view=DownloadView(),
        ephemeral=True,
    )


@tree.command(
    name="setup",
    description="[Mod only] Post the persistent download button in this channel.",
    guild=guild_obj,
)
@app_commands.checks.has_permissions(manage_messages=True)
async def cmd_setup(interaction: discord.Interaction):
    e = discord.Embed(
        title=f"🌿  Get {MOD_NAME}",
        description=(
            f"Click the button below to privately receive the mod download link "
            f"and all required dependencies.\n\n"
            f"**Minecraft {MC_VERSION}  ·  Fabric**"
        ),
        colour=ACCENT_COLOUR,
    )
    e.set_footer(text="Only you will see the download links — they won't be visible to others.")
    await interaction.channel.send(embed=e, view=PersistentDownloadView())
    await interaction.response.send_message("✅ Done! Persistent button posted.", ephemeral=True)


@cmd_setup.error
async def setup_error(interaction: discord.Interaction, error: app_commands.AppCommandError):
    await interaction.response.send_message(
        "❌ You need **Manage Messages** permission to use this command.",
        ephemeral=True,
    )


# ── Events ────────────────────────────────────────────────────────────────────

@client.event
async def on_ready():
    # Re-register the persistent view so the button works after restarts
    client.add_view(PersistentDownloadView())
    # Sync slash commands to the guild
    await tree.sync(guild=guild_obj)
    print(f"✅  Logged in as {client.user}  |  Synced commands to guild {GUILD_ID}")


client.run(TOKEN)

/**
 * Slothy's Tree - Discord bot, Cloudflare Workers edition.
 *
 * Routes:
 *   POST  /              - Discord interactions endpoint (signature-required)
 *   POST  /v1/heartbeat  - Mod clients ping with {clientId: "<uuid>"}
 *   GET   /v1/stats      - Read active count (mostly for debugging)
 *   GET   /healthz       - Liveness check
 *
 * The bot will appear "offline" in Discord member lists because we don't hold
 * a gateway WebSocket. Slash commands, buttons, and select menus all work.
 */

import {
    InteractionType,
    InteractionResponseType,
    InteractionResponseFlags,
    MessageComponentType,
    ButtonStyle,
} from './constants.js';

// Static config

const MOD_NAME      = "Slothy's Tree";
const FABRIC_LOADER = 'https://fabricmc.net/use/installer/';
const FABRIC_API    = 'https://modrinth.com/mod/fabric-api';
const GITHUB_REPO   = 'https://github.com/IlySlothy/Slothy-s-Tree';
const ACCENT        = 0x52D47A;

const HEARTBEAT_TTL_SEC = 15 * 60;
const STATS_CACHE_MS    = 5 * 60 * 1000;
const USER_AGENT        = 'slothys-tree-bot/worker (+https://github.com/IlySlothy/Slothy-s-Tree)';

const RELEASES = [
    {
        id: '1.0.3-mc1.21.11',
        version: '1.0.3',
        label: 'v1.0.3 - MC 1.21.11',
        description: 'For Minecraft 1.21.9 - 1.21.11 (Fabric / Feather)',
        jar: 'slothyhub-1.0.3-mc1.21.11.jar',
        tag: 'v1.0.3-mc1.21.11',
        mc: '1.21.9 - 1.21.11',
        downloadUrl: 'https://github.com/IlySlothy/Slothy-s-Tree/releases/download/v1.0.3-mc1.21.11/slothyhub-1.0.3-mc1.21.11.jar',
        pageUrl:     'https://github.com/IlySlothy/Slothy-s-Tree/releases/tag/v1.0.3-mc1.21.11',
        extraJar: 'slothyhub-cit-1.0.3-mc1.21.11.jar',
        extraUrl:  'https://github.com/IlySlothy/Slothy-s-Tree/releases/download/v1.0.3-mc1.21.11/slothyhub-cit-1.0.3-mc1.21.11.jar',
        extraNote: 'Required CIT companion on 1.21.9+',
    },
    {
        id: '1.0.3-mc1.21.8',
        version: '1.0.3',
        label: 'v1.0.3 - MC 1.21.8',
        description: 'For Minecraft 1.21.8 (Fabric)',
        jar: 'slothyhub-1.0.3-mc1.21.8.jar',
        tag: 'v1.0.3-mc1.21.8',
        mc: '1.21.8',
        downloadUrl: 'https://github.com/IlySlothy/Slothy-s-Tree/releases/download/v1.0.3-mc1.21.8/slothyhub-1.0.3-mc1.21.8.jar',
        pageUrl:     'https://github.com/IlySlothy/Slothy-s-Tree/releases/tag/v1.0.3-mc1.21.8',
    },
    {
        id: '1.0.3-mc1.20-1.21.1',
        version: '1.0.3',
        label: 'v1.0.3 - MC 1.20 - 1.21.1',
        description: 'Legacy build (MC 1.20 - 1.21.1)',
        jar: 'slothyhub-1.0.3-mc1.20-1.21.1.jar',
        tag: 'v1.0.3-mc1.20-1.21.1',
        mc: '1.20 - 1.21.1',
        downloadUrl: 'https://github.com/IlySlothy/Slothy-s-Tree/releases/download/v1.0.3-mc1.20-1.21.1/slothyhub-1.0.3-mc1.20-1.21.1.jar',
        pageUrl:     'https://github.com/IlySlothy/Slothy-s-Tree/releases/tag/v1.0.3-mc1.20-1.21.1',
        extraJar: 'slothyhub-legacy-cit-1.0.3-mc1.21.8-legacy.jar',
        extraUrl:  'https://github.com/IlySlothy/Slothy-s-Tree/releases/download/v1.0.3-mc1.20-1.21.1/slothyhub-legacy-cit-1.0.3-mc1.21.8-legacy.jar',
        extraNote: 'CIT companion for MC 1.20 - 1.21.7',
    },
];

function getRelease(id) {
    return RELEASES.find(r => r.id === id) ?? RELEASES[0];
}
// Signature verification (Ed25519 via Web Crypto)

function hex2bin(hex) {
    const bin = new Uint8Array(hex.length / 2);
    for (let i = 0; i < bin.length; i++) bin[i] = parseInt(hex.substr(i * 2, 2), 16);
    return bin;
}

const PUBKEY_CACHE = new Map();
async function getPublicKey(pubKeyHex) {
    let key = PUBKEY_CACHE.get(pubKeyHex);
    if (!key) {
        key = await crypto.subtle.importKey(
            'raw',
            hex2bin(pubKeyHex),
            { name: 'Ed25519' },
            false,
            ['verify'],
        );
        PUBKEY_CACHE.set(pubKeyHex, key);
    }
    return key;
}

async function verifyDiscordSignature(request, rawBody, pubKeyHex) {
    const sig = request.headers.get('x-signature-ed25519');
    const ts  = request.headers.get('x-signature-timestamp');
    if (!sig || !ts) return false;
    try {
        const key = await getPublicKey(pubKeyHex);
        const data = new TextEncoder().encode(ts + rawBody);
        return await crypto.subtle.verify({ name: 'Ed25519' }, key, hex2bin(sig), data);
    } catch (err) {
        console.warn('Signature verify failed:', err.message ?? err);
        return false;
    }
}
// JSON response helpers

function json(data, init = {}) {
    return new Response(JSON.stringify(data), {
        ...init,
        headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
    });
}

function pong() {
    return json({ type: InteractionResponseType.PONG });
}

function reply(payload, opts = {}) {
    const flags = (opts.ephemeral ?? true) ? InteractionResponseFlags.EPHEMERAL : 0;
    return json({
        type: InteractionResponseType.CHANNEL_MESSAGE_WITH_SOURCE,
        data: { flags, ...payload },
    });
}

function deferred(opts = {}) {
    const flags = (opts.ephemeral ?? true) ? InteractionResponseFlags.EPHEMERAL : 0;
    return json({
        type: InteractionResponseType.DEFERRED_CHANNEL_MESSAGE_WITH_SOURCE,
        data: { flags },
    });
}

function updateMessage(payload) {
    return json({ type: InteractionResponseType.UPDATE_MESSAGE, data: payload });
}
// Discord REST helpers

const DISCORD_API = 'https://discord.com/api/v10';

async function discordPatchFollowup(env, interactionToken, payload) {
    const url = `${DISCORD_API}/webhooks/${env.DISCORD_APP_ID}/${interactionToken}/messages/@original`;
    const res = await fetch(url, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'User-Agent': USER_AGENT },
        body: JSON.stringify(payload),
    });
    if (!res.ok) console.warn('discordPatchFollowup', res.status, await res.text());
    return res;
}

async function discordBotRequest(env, method, path, body) {
    return fetch(`${DISCORD_API}${path}`, {
        method,
        headers: {
            'Authorization': `Bot ${env.DISCORD_TOKEN}`,
            'Content-Type':  'application/json',
            'User-Agent':    USER_AGENT,
        },
        body: body ? JSON.stringify(body) : undefined,
    });
}
// Embeds

function companionField(release) {
    if (!release.extraJar || !release.extraUrl) return null;
    const note = release.extraNote ? `\n${release.extraNote}` : '';
    return {
        name: 'Companion JAR',
        value: `[Direct download](${release.extraUrl})\n\`${release.extraJar}\`${note}`,
        inline: false,
    };
}

function buildFullEmbed(release) {
    const fields = [
        { name: 'Download the Mod',  value: `[Direct download](${release.downloadUrl})\n\`${release.jar}\``, inline: false },
    ];
    const companion = companionField(release);
    if (companion) fields.push(companion);
    fields.push(
        { name: 'Fabric Loader',     value: `[fabricmc.net installer](${FABRIC_LOADER})\nDownload & run the installer, pick your MC version (${release.mc}).`, inline: false },
        { name: 'Fabric API',        value: `[Modrinth](${FABRIC_API})\nGrab the version matching your MC version and drop into \`.minecraft/mods/\``, inline: false },
        { name: 'Install steps',     value: `1. Install Fabric Loader for your MC version\n2. Drop \`${release.jar}\` into \`.minecraft/mods/\`${release.extraJar ? `\n3. Drop \`${release.extraJar}\` into \`.minecraft/mods/\`` : ''}\n${release.extraJar ? '4' : '3'}. Drop \`fabric-api-...jar\` into \`.minecraft/mods/\`\n${release.extraJar ? '5' : '4'}. Launch Minecraft with the **Fabric** profile`, inline: false },
    );
    return {
        title: `Download ${MOD_NAME} v${release.version} - MC ${release.mc}`,
        description: `Compatible with **Minecraft ${release.mc}** + Fabric.\n*Only visible to you.*`,
        color: ACCENT,
        fields,
        footer: { text: `${MOD_NAME} - github.com/IlySlothy/Slothy-s-Tree` },
    };
}

function buildInfoEmbed() {
    const latest = RELEASES[0];
    const versionList = RELEASES.map(r => `**v${r.version}** - MC ${r.mc}`).join('\n');
    const allMc = [...new Set(RELEASES.map(r => r.mc))].join(', ');
    return {
        title: MOD_NAME,
        url: latest.pageUrl,
        description: 'A sloth-themed resource pack hub for Minecraft.\nBrowse, apply, and build custom texture packs.',
        color: ACCENT,
        fields: [
            { name: 'Latest',           value: `v${latest.version}`, inline: true },
            { name: 'MC Versions',      value: allMc,                inline: true },
            { name: 'Loader',           value: 'Fabric',             inline: true },
            { name: 'Available builds', value: versionList,          inline: false },
            {
                name: 'Features',
                value: '- 40+ ilyslothy resource packs\n- One-click apply\n- Texture Builder - mix & match item textures\n- CIT support (custom item textures by name)\n- Kill effects & Feather profiles',
                inline: false,
            },
        ],
        footer: { text: 'Use /download to get the mod privately.' },
    };
}

function buildSetupEmbed() {
    const latest = RELEASES[0];
    const mcList = [...new Set(RELEASES.map(r => r.mc))].map(m => `\`${m}\``).join(', ');
    return {
        title: `${MOD_NAME} - Minecraft Resource Pack Mod`,
        url: latest.pageUrl,
        description:
            `**${MOD_NAME}** is a Fabric mod that lets you browse, apply, and build ` +
            `custom resource packs without leaving Minecraft.\n\n` +
            `> Hang from Slothy's Tree - where every texture has a home.`,
        color: ACCENT,
        fields: [
            {
                name: 'Features',
                value: [
                    '**Pack Browser** - Browse 40+ curated packs by ilyslothy, apply with one click',
                    '**Texture Builder** - Mix & match item textures from different packs',
                    '**CIT Support** - Custom item textures by name (OptiFine format)',
                    '**Kill Effects** - Configure kill effects',
                    '**Feather Profiles** - Manage gameplay profiles',
                ].join('\n'),
                inline: false,
            },
            {
                name: 'Requirements',
                value: [
                    `> Minecraft ${mcList} (pick yours via /download)`,
                    `> [Fabric Loader >= 0.19](${FABRIC_LOADER})`,
                    `> [Fabric API](${FABRIC_API}) (matching your MC version)`,
                ].join('\n'),
                inline: false,
            },
            {
                name: 'How to Install',
                value: [
                    '**1.** Install [Fabric Loader](https://fabricmc.net/use/installer/) for your Minecraft version',
                    '**2.** Click **Get the Mod** below and pick your version + download link',
                    '**3.** Download [Fabric API](https://modrinth.com/mod/fabric-api) for your MC version',
                    '**4.** Drop both JARs into `.minecraft/mods/`',
                    '**5.** Launch Minecraft with the **Fabric** profile',
                ].join('\n'),
                inline: false,
            },
            {
                name: 'Links',
                value: [
                    `[Latest Release](${latest.pageUrl})`,
                    `[Source Code on GitHub](${GITHUB_REPO})`,
                    `[Fabric API on Modrinth](${FABRIC_API})`,
                    `[Fabric Loader Installer](${FABRIC_LOADER})`,
                ].join('\n'),
                inline: false,
            },
        ],
        footer: { text: `${MOD_NAME} v${latest.version} - Click the button below for private download links` },
        timestamp: new Date().toISOString(),
    };
}

function buildInviteEmbed(env) {
    const appId = env.DISCORD_APP_ID;
    const permsBits =
        BigInt(1 << 10) |
        BigInt(1 << 11) |
        BigInt(1 << 14) |
        BigInt(1 << 13);
    const url = `https://discord.com/oauth2/authorize?client_id=${appId}&permissions=${permsBits}&scope=bot%20applications.commands&integration_type=0`;
    return {
        title: 'Add bot to a server',
        color: ACCENT,
        description: `[Click here to invite ${MOD_NAME} bot](${url})`,
        fields: [
            { name: 'Scopes',      value: '`bot` + `applications.commands`',                                            inline: false },
            { name: 'Permissions', value: 'View Channels, Send Messages, Embed Links, Manage Messages (for `/setup`)', inline: false },
            { name: 'Note',        value: 'You need **Manage Server** on the target server.',                           inline: false },
        ],
    };
}
// Components (select menus, buttons)

function versionMenuRow() {
    return {
        type: MessageComponentType.ACTION_ROW,
        components: [{
            type: MessageComponentType.STRING_SELECT,
            custom_id: 'version_select',
            placeholder: 'Choose a mod version...',
            options: RELEASES.map(r => ({
                label: r.label,
                description: r.description,
                value: r.id,
            })),
        }],
    };
}

function downloadMenuRow(releaseId) {
    return {
        type: MessageComponentType.ACTION_ROW,
        components: [{
            type: MessageComponentType.STRING_SELECT,
            custom_id: `download_select:${releaseId}`,
            placeholder: 'What do you need?',
            options: [
                { label: "Download Slothy's Tree Mod", description: 'Direct JAR download link',       value: 'mod' },
                { label: 'Fabric Loader',              description: 'Download the Fabric installer',  value: 'fabric' },
                { label: 'Fabric API',                 description: 'Required dependency - Modrinth', value: 'fabric_api' },
                { label: 'Everything at once',         description: 'All links in one message',       value: 'all' },
            ],
        }],
    };
}

function showAllButtonRow(releaseId) {
    return {
        type: MessageComponentType.ACTION_ROW,
        components: [{
            type: MessageComponentType.BUTTON,
            custom_id: `show_all:${releaseId}`,
            label: 'Show Everything',
            style: ButtonStyle.SUCCESS,
        }],
    };
}

function channelButtonRow() {
    return {
        type: MessageComponentType.ACTION_ROW,
        components: [{
            type: MessageComponentType.BUTTON,
            custom_id: 'persistent_get_mod',
            label: 'Get the Mod',
            style: ButtonStyle.SUCCESS,
        }],
    };
}
// /modstats: GitHub + heartbeats

let STATS_CACHE = null;

async function ghFetch(env, pathname) {
    const headers = { 'User-Agent': USER_AGENT, 'Accept': 'application/vnd.github+json' };
    if (env.GITHUB_TOKEN) headers.Authorization = `Bearer ${env.GITHUB_TOKEN}`;
    const res = await fetch(`https://api.github.com${pathname}`, { headers });
    if (!res.ok) throw new Error(`GitHub ${pathname} -> HTTP ${res.status}`);
    return res.json();
}

async function fetchRepoStats(env) {
    if (STATS_CACHE && Date.now() - STATS_CACHE.fetchedAt < STATS_CACHE_MS) return STATS_CACHE;
    const [repo, releases, packs] = await Promise.all([
        ghFetch(env, `/repos/${env.GH_OWNER}/${env.GH_REPO}`),
        ghFetch(env, `/repos/${env.GH_OWNER}/${env.GH_REPO}/releases?per_page=100`),
        fetch(env.PACKS_JSON_URL, { headers: { 'User-Agent': USER_AGENT } })
            .then(r => r.ok ? r.json() : []),
    ]);
    const totalDownloads = releases.reduce(
        (s, r) => s + (r.assets ?? []).reduce((a, x) => a + (x.download_count ?? 0), 0), 0);
    STATS_CACHE = {
        stars:          repo.stargazers_count ?? 0,
        totalDownloads,
        packsApproved:  Array.isArray(packs) ? packs.length : 0,
        releasesCount:  releases.length,
        fetchedAt:      Date.now(),
    };
    return STATS_CACHE;
}

async function countActiveUsers(env) {
    const result = await env.HEARTBEATS.list({ prefix: 'hb:', limit: 1000 });
    return result.keys.length;
}

const fmt = n => Number(n).toLocaleString('en-US');

function buildStatsEmbed(stats, activeUsers, errMessage) {
    const activeLine = activeUsers > 0
        ? `${fmt(activeUsers)} mod client${activeUsers === 1 ? '' : 's'} online`
        : 'No heartbeats yet';
    return {
        title: `${MOD_NAME} - Mod Stats`,
        color: ACCENT,
        description: errMessage ? `Couldn't reach GitHub: \`${errMessage}\`\nShowing partial data.` : undefined,
        fields: [
            { name: 'Active Users (15 min)', value: activeLine,                                            inline: true },
            { name: 'Packs',                  value: stats ? `${fmt(stats.packsApproved)} approved` : '-', inline: true },
            { name: 'Total Stars',            value: stats ? fmt(stats.stars) : '-',                       inline: true },
            { name: 'Total Downloads',        value: stats ? fmt(stats.totalDownloads) : '-',              inline: true },
        ],
        footer: { text: 'Heartbeats are counted from mod clients seen in the last 15 minutes.' },
        timestamp: new Date().toISOString(),
    };
}
// Interaction handlers

async function handleCommand(interaction, env, ctx) {
    const name = interaction.data.name;

    if (name === 'modinfo') {
        return reply({ embeds: [buildInfoEmbed()] });
    }

    if (name === 'download') {
        return reply({
            content: '**Step 1 - pick a mod version** (only you can see this).',
            components: [versionMenuRow()],
        });
    }

    if (name === 'invite') {
        return reply({ embeds: [buildInviteEmbed(env)] });
    }

    if (name === 'modstats') {
        ctx.waitUntil((async () => {
            const activeUsers = await countActiveUsers(env).catch(() => 0);
            let stats = null, errMessage = null;
            try { stats = await fetchRepoStats(env); }
            catch (err) { errMessage = (err.message ?? String(err)).slice(0, 120); }
            await discordPatchFollowup(env, interaction.token, {
                embeds: [buildStatsEmbed(stats, activeUsers, errMessage)],
            });
        })());
        return deferred({ ephemeral: true });
    }

    if (name === 'setup') {
        if (!interaction.guild_id) {
            return reply({ content: '`/setup` can only be used inside a server.' });
        }
        const channelId = interaction.channel_id;
        const appId     = env.DISCORD_APP_ID;

        ctx.waitUntil((async () => {
            try {
                const listRes = await discordBotRequest(env, 'GET', `/channels/${channelId}/messages?limit=100`);
                if (listRes.ok) {
                    const messages = await listRes.json();
                    const mine = messages.filter(m => m.author?.id === appId).map(m => m.id);
                    if (mine.length >= 2) {
                        const bulk = await discordBotRequest(
                            env, 'POST', `/channels/${channelId}/messages/bulk-delete`,
                            { messages: mine });
                        if (!bulk.ok && bulk.status !== 204) {
                            for (const id of mine) {
                                await discordBotRequest(env, 'DELETE', `/channels/${channelId}/messages/${id}`);
                            }
                        }
                    } else {
                        for (const id of mine) {
                            await discordBotRequest(env, 'DELETE', `/channels/${channelId}/messages/${id}`);
                        }
                    }
                }
                const postRes = await discordBotRequest(env, 'POST', `/channels/${channelId}/messages`, {
                    embeds: [buildSetupEmbed()],
                    components: [channelButtonRow()],
                });
                if (!postRes.ok) {
                    await discordPatchFollowup(env, interaction.token, {
                        content: 'Missing permissions. I need **Send Messages**, **Embed Links**, and **Manage Messages** in this channel.',
                    });
                    return;
                }
                await discordPatchFollowup(env, interaction.token, {
                    content: 'Done - old posts cleared, fresh one posted.',
                });
            } catch (err) {
                console.warn('/setup failed:', err.message ?? err);
                await discordPatchFollowup(env, interaction.token, {
                    content: 'Something went wrong while running `/setup`. Check the bot has Send Messages + Embed Links + Manage Messages here.',
                });
            }
        })());
        return deferred({ ephemeral: true });
    }

    return reply({ content: `Unknown command: \`${name}\`` });
}

async function handleComponent(interaction, env) {
    const customId = interaction.data.custom_id;

    if (customId === 'version_select') {
        const release = getRelease(interaction.data.values[0]);
        return updateMessage({
            content: `**Step 2 - pick what you need** for **v${release.version}** (MC ${release.mc}).`,
            components: [downloadMenuRow(release.id), showAllButtonRow(release.id)],
        });
    }

    if (customId.startsWith('download_select:')) {
        const releaseId = customId.split(':')[1];
        const release = getRelease(releaseId);
        const choice = interaction.data.values[0];
        let embed;

        if (choice === 'mod') {
            const modFields = [
                { name: 'File',        value: `\`${release.jar}\``,                       inline: false },
                { name: 'MC versions', value: release.mc,                                  inline: true },
                { name: 'Install',     value: 'Drop the JAR into `.minecraft/mods/`',     inline: false },
            ];
            const companion = companionField(release);
            if (companion) modFields.splice(1, 0, companion);
            embed = {
                title: `Download the Mod v${release.version} - MC ${release.mc}`,
                color: ACCENT,
                url: release.downloadUrl,
                description: `[Click here to download](${release.downloadUrl})`,
                fields: modFields,
            };
        } else if (choice === 'fabric') {
            embed = {
                title: 'Fabric Loader',
                color: ACCENT,
                url: FABRIC_LOADER,
                description: `[Download the Fabric Installer](${FABRIC_LOADER})`,
                fields: [{ name: 'Steps', value: `1. Download the installer\n2. Select your Minecraft version (${release.mc})\n3. Press Install Client`, inline: false }],
            };
        } else if (choice === 'fabric_api') {
            embed = {
                title: 'Fabric API',
                color: ACCENT,
                url: FABRIC_API,
                description: `[Download from Modrinth](${FABRIC_API})`,
                fields: [{ name: 'Note', value: `Get the version matching your Minecraft version (${release.mc}) and drop it in \`.minecraft/mods/\``, inline: false }],
            };
        } else {
            embed = buildFullEmbed(release);
        }
        return reply({ embeds: [embed] });
    }

    if (customId.startsWith('show_all:')) {
        const releaseId = customId.split(':')[1];
        return reply({ embeds: [buildFullEmbed(getRelease(releaseId))] });
    }

    if (customId === 'persistent_get_mod') {
        return reply({
            content: '**Step 1 - pick a mod version** (only you can see this).',
            components: [versionMenuRow()],
        });
    }

    return reply({ content: `Unknown component: \`${customId}\`` });
}
// Heartbeat routes

async function handleHeartbeat(request, env) {
    let body;
    try { body = await request.json(); } catch { body = {}; }
    const clientId = String(body?.clientId ?? '').trim();
    if (!clientId || clientId.length > 128 || !/^[A-Za-z0-9_\-:.]+$/.test(clientId)) {
        return new Response('bad_client_id', { status: 400 });
    }
    await env.HEARTBEATS.put(`hb:${clientId}`, '1', { expirationTtl: HEARTBEAT_TTL_SEC });
    return new Response(null, { status: 204 });
}

async function handleStats(env) {
    const activeUsers = await countActiveUsers(env);
    return json({ activeUsers, heartbeatTtlSec: HEARTBEAT_TTL_SEC });
}
// Main entry point

export default {
    async fetch(request, env, ctx) {
        const url = new URL(request.url);

        if (request.method === 'GET' && url.pathname === '/healthz') {
            return json({ ok: true });
        }
        if (request.method === 'GET' && url.pathname === '/v1/stats') {
            return handleStats(env);
        }
        if (request.method === 'POST' && url.pathname === '/v1/heartbeat') {
            return handleHeartbeat(request, env);
        }

        if (request.method === 'POST' && (url.pathname === '/' || url.pathname === '/interactions')) {
            if (!env.DISCORD_PUBLIC_KEY) {
                return new Response('server misconfigured: DISCORD_PUBLIC_KEY missing', { status: 500 });
            }
            const rawBody = await request.text();
            const valid = await verifyDiscordSignature(request, rawBody, env.DISCORD_PUBLIC_KEY);
            if (!valid) return new Response('invalid request signature', { status: 401 });

            const interaction = JSON.parse(rawBody);
            if (interaction.type === InteractionType.PING) {
                return pong();
            }
            try {
                if (interaction.type === InteractionType.APPLICATION_COMMAND) {
                    return await handleCommand(interaction, env, ctx);
                }
                if (interaction.type === InteractionType.MESSAGE_COMPONENT) {
                    return await handleComponent(interaction, env);
                }
            } catch (err) {
                console.error('interaction handler threw:', err.message ?? err, err.stack);
                return reply({ content: 'Something went wrong handling that. Try again in a moment.' });
            }
            return reply({ content: `Unhandled interaction type: ${interaction.type}` });
        }

        if (url.pathname === '/') {
            return new Response(
                `${MOD_NAME} bot worker - healthy.\n` +
                `Set this URL as your Discord application's Interactions Endpoint URL.`,
                { headers: { 'Content-Type': 'text/plain' } });
        }
        return new Response('not_found', { status: 404 });
    },
};

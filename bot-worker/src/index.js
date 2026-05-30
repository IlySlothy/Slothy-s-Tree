/**
 * Slothy's Tree - Discord bot, Cloudflare Workers edition.
 *
 * Routes:
 *   POST  /              - Discord interactions endpoint (signature-required)
 *   POST  /v1/heartbeat  - Mod clients ping with {clientId: "<uuid>"}
 *   POST  /v1/pack-submit - Mod uploads a built pack zip for moderator review
 *   POST  /v1/pack-star   - Toggle star on a catalog pack (one vote per client UUID)
 *   GET   /v1/pack-stars  - Star counts + which packs this client starred
 *   POST  /v1/setup-leaderboard - [Admin] Post live star leaderboard embed in a channel
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
const MAX_PACK_BYTES    = 8 * 1024 * 1024;
const SUBMIT_RATE_MAX   = 3;
const SUBMIT_RATE_TTL   = 60 * 60;
const SUBMIT_STORE_TTL  = 30 * 24 * 60 * 60;
const PACKS_JSON_PATH   = 'docs/api/packs.json';
const STAR_COUNT_PREFIX = 'ps:star:count:';
const STAR_VOTE_PREFIX  = 'ps:star:vote:';
const STAR_RATE_PREFIX  = 'ps:star:rate:';
const STAR_RATE_MAX     = 60;
const STAR_RATE_TTL     = 60 * 60;
const LEADERBOARD_CONFIG_KEY = 'ps:leaderboard:config';
const LEADERBOARD_HASH_KEY   = 'ps:leaderboard:hash';
const LEADERBOARD_TOP_N      = 10;

const RELEASES = [
    {
        id: '1.0.5-mc1.21.11',
        version: '1.0.5',
        label: 'v1.0.5 - MC 1.21.11',
        description: 'For Minecraft 1.21.9 - 1.21.11 (Fabric / Feather)',
        jar: 'slothyhub-1.0.5-mc1.21.11.jar',
        tag: 'v1.0.5-mc1.21.11',
        mc: '1.21.9 - 1.21.11',
        downloadUrl: 'https://github.com/IlySlothy/Slothy-s-Tree/releases/download/v1.0.5-mc1.21.11/slothyhub-1.0.5-mc1.21.11.jar',
        pageUrl:     'https://github.com/IlySlothy/Slothy-s-Tree/releases/tag/v1.0.5-mc1.21.11',
        extraJar: 'slothyhub-cit-1.0.5-mc1.21.11.jar',
        extraUrl:  'https://github.com/IlySlothy/Slothy-s-Tree/releases/download/v1.0.5-mc1.21.11/slothyhub-cit-1.0.5-mc1.21.11.jar',
        extraNote: 'Required CIT companion on 1.21.9+',
    },
    {
        id: '1.0.5-mc1.21.8',
        version: '1.0.5',
        label: 'v1.0.5 - MC 1.21.8',
        description: 'For Minecraft 1.21.8 (Fabric)',
        jar: 'slothyhub-1.0.5-mc1.21.8.jar',
        tag: 'v1.0.5-mc1.21.8',
        mc: '1.21.8',
        downloadUrl: 'https://github.com/IlySlothy/Slothy-s-Tree/releases/download/v1.0.5-mc1.21.8/slothyhub-1.0.5-mc1.21.8.jar',
        pageUrl:     'https://github.com/IlySlothy/Slothy-s-Tree/releases/tag/v1.0.5-mc1.21.8',
    },
    {
        id: '1.0.5-mc1.20-1.21.1',
        version: '1.0.5',
        label: 'v1.0.5 - MC 1.20 - 1.21.1',
        description: 'Legacy build (MC 1.20 - 1.21.1)',
        jar: 'slothyhub-1.0.5-mc1.20-1.21.1.jar',
        tag: 'v1.0.5-mc1.20-1.21.1',
        mc: '1.20 - 1.21.1',
        downloadUrl: 'https://github.com/IlySlothy/Slothy-s-Tree/releases/download/v1.0.5-mc1.20-1.21.1/slothyhub-1.0.5-mc1.20-1.21.1.jar',
        pageUrl:     'https://github.com/IlySlothy/Slothy-s-Tree/releases/tag/v1.0.5-mc1.20-1.21.1',
        extraJar: 'slothyhub-legacy-cit-1.0.5-mc1.21.8-legacy.jar',
        extraUrl:  'https://github.com/IlySlothy/Slothy-s-Tree/releases/download/v1.0.5-mc1.20-1.21.1/slothyhub-legacy-cit-1.0.5-mc1.21.8-legacy.jar',
        extraNote: 'CIT companion for MC 1.20 - 1.21.7',
    },
];

function getRelease(id) {
    return RELEASES.find(r => r.id === id) ?? RELEASES[0];
}

function trimField(value, maxLen) {
    return String(value ?? '').trim().slice(0, maxLen);
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

async function discordBotPut(env, path) {
    return fetch(`${DISCORD_API}${path}`, {
        method: 'PUT',
        headers: {
            Authorization: `Bot ${env.DISCORD_TOKEN}`,
            'User-Agent': USER_AGENT,
        },
    });
}

async function discordPostMultipart(env, channelId, payload, zipBytes, filename) {
    const form = new FormData();
    form.append('payload_json', JSON.stringify(payload));
    form.append('files[0]', new Blob([zipBytes], { type: 'application/zip' }), filename);
    const res = await fetch(`${DISCORD_API}/channels/${channelId}/messages`, {
        method: 'POST',
        headers: {
            Authorization: `Bot ${env.DISCORD_TOKEN}`,
            'User-Agent': USER_AGENT,
        },
        body: form,
    });
    if (!res.ok) {
        const text = await res.text();
        throw new Error(`Discord post HTTP ${res.status}: ${text.slice(0, 200)}`);
    }
    return res;
}

async function openDmChannel(env, userId) {
    const res = await discordBotRequest(env, 'POST', '/users/@me/channels', { recipient_id: userId });
    if (!res.ok) {
        const text = await res.text();
        throw new Error(`open DM HTTP ${res.status}: ${text.slice(0, 160)}`);
    }
    return (await res.json()).id;
}

async function createPackTicketChannel(env, meta) {
    const guildId = trimField(env.PACK_TICKET_GUILD_ID, 32);
    const categoryId = trimField(env.PACK_TICKET_CATEGORY_ID, 32);
    const ownerId = trimField(env.PACK_REVIEW_OWNER_USER_ID, 32);
    if (!guildId || !categoryId) return null;

    const slug = meta.packName
        .replace(/[^a-z0-9]+/gi, '-')
        .replace(/^-+|-+$/g, '')
        .toLowerCase()
        .slice(0, 24) || 'upload';
    const name = `pack-${slug}-${meta.submissionId}`.slice(0, 100);

    const overwrites = [{ id: guildId, type: 0, deny: '1024' }];
    if (ownerId) {
        overwrites.push({ id: ownerId, type: 1, allow: '117760' });
    }
    const botId = trimField(env.DISCORD_APP_ID, 32);
    if (botId) {
        // Bot must keep view/send on private ticket channels it creates.
        overwrites.push({ id: botId, type: 1, allow: '117760' });
    }

    const res = await discordBotRequest(env, 'POST', `/guilds/${guildId}/channels`, {
        name,
        type: 0,
        parent_id: categoryId,
        topic: `Pack review · ${meta.packName} · ${meta.submissionId}`,
        permission_overwrites: overwrites,
    });
    if (!res.ok) {
        const text = await res.text();
        throw new Error(`create ticket HTTP ${res.status}: ${text.slice(0, 160)}`);
    }
    return (await res.json()).id;
}

function buildPackReviewPayload(env, meta, zipBytes, filename, context) {
    const descLines = [
        `**Pack:** ${meta.packName}`,
        `**Minecraft author:** ${meta.authorName || 'Unknown'}`,
    ];
    if (meta.contact) descLines.push(`**Contact:** ${meta.contact}`);
    if (meta.tags?.length) descLines.push(`**Tags:** ${meta.tags.map(t => `\`${t}\``).join(' ')}`);
    if (meta.description) descLines.push('', meta.description);
    descLines.push('', `**Submission ID:** \`${meta.submissionId}\``);
    descLines.push(`**Client:** \`${meta.clientId.slice(0, 8)}…\``);
    descLines.push(`**File:** \`${filename}\` (${(zipBytes.byteLength / 1024).toFixed(1)} KB)`);
    if (context === 'ticket') {
        descLines.push('', 'Use `/pack-approve id:<submission id>` to publish, or `/pack-deny id:<submission id>`.');
    } else {
        descLines.push('', `Review: \`/pack-approve id:${meta.submissionId}\` or \`/pack-deny id:${meta.submissionId}\``);
    }

    const ownerId = trimField(env.PACK_REVIEW_OWNER_USER_ID, 32);
    let content = '📤 **New pack upload request**';
    if (context === 'dm') {
        content = '📤 **Pack upload for review** (DM from SlothyHub mod)';
    } else if (context === 'ticket') {
        content = '📤 **Pack upload ticket** — review and approve when ready.';
    }
    if (ownerId) content = `<@${ownerId}> ${content}`;

    return {
        content,
        embeds: [{
            title: meta.packName,
            color: ACCENT,
            description: descLines.join('\n').slice(0, 4000),
            footer: { text: `${MOD_NAME} pack review · ${meta.packId || 'library pack'}` },
            timestamp: new Date().toISOString(),
        }],
    };
}

/** DM owner and/or open a private ticket channel for each mod upload. */
async function notifyPackSubmission(env, meta, zipBytes, filename) {
    if (!env.DISCORD_TOKEN) {
        throw new Error('DISCORD_TOKEN not configured');
    }

    const ownerId = trimField(env.PACK_REVIEW_OWNER_USER_ID, 32);
    const hasTicket = trimField(env.PACK_TICKET_GUILD_ID, 32)
        && trimField(env.PACK_TICKET_CATEGORY_ID, 32);
    const fallbackChannel = trimField(env.PACK_REVIEW_CHANNEL_ID, 32);

    if (!ownerId && !hasTicket && !fallbackChannel) {
        throw new Error('Set PACK_REVIEW_OWNER_USER_ID and/or PACK_TICKET_GUILD_ID + PACK_TICKET_CATEGORY_ID');
    }

    const errors = [];
    let notified = false;

    if (ownerId) {
        try {
            const dmId = await openDmChannel(env, ownerId);
            const payload = buildPackReviewPayload(env, meta, zipBytes, filename, 'dm');
            await discordPostMultipart(env, dmId, payload, zipBytes, filename);
            notified = true;
        } catch (err) {
            errors.push(`DM: ${err.message ?? err}`);
            console.warn('pack-submit DM failed:', err.message ?? err);
        }
    }

    if (hasTicket) {
        try {
            const ticketId = await createPackTicketChannel(env, meta);
            const payload = buildPackReviewPayload(env, meta, zipBytes, filename, 'ticket');
            await discordPostMultipart(env, ticketId, payload, zipBytes, filename);
            notified = true;
        } catch (err) {
            errors.push(`ticket: ${err.message ?? err}`);
            console.warn('pack-submit ticket failed:', err.message ?? err);
        }
    }

    if (!notified && fallbackChannel) {
        const payload = buildPackReviewPayload(env, meta, zipBytes, filename, 'channel');
        await discordPostMultipart(env, fallbackChannel, payload, zipBytes, filename);
        notified = true;
    }

    if (!notified) {
        throw new Error(errors.join('; ') || 'Could not deliver pack review notification');
    }
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

async function ghRequest(env, method, pathname, body = null) {
    const headers = {
        'User-Agent': USER_AGENT,
        'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
    };
    if (env.GITHUB_TOKEN) headers.Authorization = `Bearer ${env.GITHUB_TOKEN}`;
    const init = { method, headers };
    if (body != null) {
        headers['Content-Type'] = 'application/json';
        init.body = JSON.stringify(body);
    }
    const res = await fetch(`https://api.github.com${pathname}`, init);
    return res;
}

async function ghFetch(env, pathname) {
    const res = await ghRequest(env, 'GET', pathname);
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

    if (name === 'setup-leaderboard') {
        if (!interaction.guild_id) {
            return reply({ content: '`/setup-leaderboard` can only be used inside a server.' });
        }
        const channelId = interaction.channel_id;
        ctx.waitUntil((async () => {
            try {
                const result = await setupLeaderboardInChannel(env, channelId);
                await discordPatchFollowup(env, interaction.token, {
                    content: [
                        '⭐ **Pack leaderboard posted** in this channel.',
                        `Showing **top ${LEADERBOARD_TOP_N}** of **${result.packCount}** pack(s) — most stars at the top.`,
                        'The list updates automatically when players star packs in the mod.',
                        result.pinned ? 'Pinned the leaderboard message.' : '',
                    ].filter(Boolean).join('\n'),
                });
            } catch (err) {
                console.warn('/setup-leaderboard failed:', err.message ?? err);
                await discordPatchFollowup(env, interaction.token, {
                    content: 'Could not post the leaderboard. I need **Send Messages**, **Embed Links**, and **Manage Messages** here.',
                });
            }
        })());
        return deferred({ ephemeral: true });
    }

    if (name === 'pack-approve') {
        return handlePackReviewCommand(interaction, env, ctx, 'approve');
    }

    if (name === 'pack-deny') {
        return handlePackReviewCommand(interaction, env, ctx, 'deny');
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

async function checkSubmitRate(env, clientId) {
    if (!clientId) return null;
    const key = `submit:${clientId}`;
    const raw = await env.HEARTBEATS.get(key);
    const count = raw ? parseInt(raw, 10) : 0;
    if (count >= SUBMIT_RATE_MAX) {
        return json({
            error: 'rate_limited',
            message: `Max ${SUBMIT_RATE_MAX} pack uploads per hour. Try again later.`,
        }, { status: 429 });
    }
    await env.HEARTBEATS.put(key, String(count + 1), { expirationTtl: SUBMIT_RATE_TTL });
    return null;
}

function slugifyPackId(text, max = 36) {
    return String(text ?? '')
        .replace(/[^a-z0-9]+/gi, '-')
        .replace(/^-+|-+$/g, '')
        .toLowerCase()
        .slice(0, max) || 'pack';
}

function bytesToBase64(bytes) {
    if (typeof bytes === 'string') return btoa(bytes);
    const arr = bytes instanceof ArrayBuffer ? new Uint8Array(bytes) : bytes;
    let binary = '';
    const chunk = 0x8000;
    for (let i = 0; i < arr.length; i += chunk) {
        binary += String.fromCharCode(...arr.subarray(i, i + chunk));
    }
    return btoa(binary);
}

function decodeGitHubContent(content) {
    const raw = content.replace(/\n/g, '');
    const bin = atob(raw);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
}

function interactionUserId(interaction) {
    return interaction.member?.user?.id ?? interaction.user?.id ?? '';
}

function isPackReviewer(interaction, env) {
    const ownerId = trimField(env.PACK_REVIEW_OWNER_USER_ID, 32);
    if (!ownerId) return false;
    return interactionUserId(interaction) === ownerId;
}

function packsSiteBase(env) {
    return trimField(env.PACKS_SITE_BASE, 200) || 'https://ilyslothy.github.io/Slothy-s-Tree';
}

async function savePendingSubmission(env, submissionId, meta, zipBytes, filename) {
    const record = {
        ...meta,
        filename,
        status: 'pending',
        submittedAt: new Date().toISOString(),
    };
    await env.HEARTBEATS.put(`ps:meta:${submissionId}`, JSON.stringify(record), {
        expirationTtl: SUBMIT_STORE_TTL,
    });
    await env.HEARTBEATS.put(`ps:zip:${submissionId}`, zipBytes, {
        expirationTtl: SUBMIT_STORE_TTL,
    });
    await indexClientSubmission(env, meta.clientId, submissionId);
}

async function indexClientSubmission(env, clientId, submissionId) {
    const id = trimField(clientId, 128);
    if (!id || id === 'anonymous') return;
    const key = `ps:client:${id}`;
    const raw = await env.HEARTBEATS.get(key);
    let ids = [];
    try { ids = raw ? JSON.parse(raw) : []; } catch { ids = []; }
    ids = [submissionId, ...ids.filter(x => x !== submissionId)].slice(0, 50);
    await env.HEARTBEATS.put(key, JSON.stringify(ids), { expirationTtl: SUBMIT_STORE_TTL });
}

function parseSubmitTags(raw) {
    const allowed = new Set(['pvp', 'cit', 'swords', 'armor']);
    const out = [];
    for (const part of String(raw ?? '').split(/[,;]+/)) {
        const t = part.trim().toLowerCase();
        if (allowed.has(t) && !out.includes(t)) out.push(t);
    }
    return out;
}

async function loadPendingSubmission(env, submissionId) {
    const id = trimField(submissionId, 16).toLowerCase();
    if (!id) return null;
    const metaRaw = await env.HEARTBEATS.get(`ps:meta:${id}`);
    if (!metaRaw) return null;
    const meta = JSON.parse(metaRaw);
    if (meta.status && meta.status !== 'pending') {
        return { meta, zipBytes: null, alreadyHandled: meta.status };
    }
    const zipBytes = await env.HEARTBEATS.get(`ps:zip:${id}`, 'arrayBuffer');
    if (!zipBytes || zipBytes.byteLength === 0) return null;
    return { meta, zipBytes, alreadyHandled: null };
}

async function finalizeSubmission(env, submissionId, status, extra = {}) {
    const id = trimField(submissionId, 16).toLowerCase();
    const metaRaw = await env.HEARTBEATS.get(`ps:meta:${id}`);
    if (!metaRaw) return;
    const meta = { ...JSON.parse(metaRaw), status, ...extra, resolvedAt: new Date().toISOString() };
    await env.HEARTBEATS.put(`ps:meta:${id}`, JSON.stringify(meta), { expirationTtl: 7 * 24 * 3600 });
    await env.HEARTBEATS.delete(`ps:zip:${id}`);
}

function buildCatalogEntry(meta, filename, packUrl, catalogId) {
    const author = meta.authorName || 'Community';
    const tagList = Array.isArray(meta.tags) && meta.tags.length
        ? [...new Set([...meta.tags, 'community'])]
        : ['community', 'pvp'];
    return {
        id: catalogId,
        name: meta.packName,
        pack_filename: filename,
        author_name: author,
        author_id: slugifyPackId(author, 32),
        showcase_path: '',
        pack_url: packUrl,
        tags: tagList,
        is_zip: true,
        has_local_file: false,
        star_count: 0,
        downloads: 0,
        sha256: '',
        viewer_starred: false,
        featured: false,
        featured_until: 0,
    };
}

async function publishApprovedPack(env, meta, zipBytes, filename) {
    if (!env.GITHUB_TOKEN) {
        throw new Error('GITHUB_TOKEN secret is not set — run `wrangler secret put GITHUB_TOKEN` with repo write access.');
    }

    const submissionId = meta.submissionId;
    const catalogId = `${slugifyPackId(meta.packName, 28)}-${submissionId}`;
    const safeFilename = filename.replace(/[^\w.\- ()\[\]]+/g, '_').slice(0, 80);
    const downloadPath = `docs/downloads/${safeFilename}`;
    const siteBase = packsSiteBase(env);
    const packUrl = `${siteBase}/downloads/${encodeURI(safeFilename)}`;

    const zipRes = await ghRequest(
        env, 'PUT',
        `/repos/${env.GH_OWNER}/${env.GH_REPO}/contents/${downloadPath}`,
        {
            message: `Add community pack: ${meta.packName} (${submissionId})`,
            content: bytesToBase64(zipBytes),
        },
    );
    if (!zipRes.ok) {
        const text = await zipRes.text();
        if (zipRes.status === 422 && text.includes('already exists')) {
            // Overwrite if same filename was retried
            const existing = await ghFetch(env, `/repos/${env.GH_OWNER}/${env.GH_REPO}/contents/${downloadPath}`);
            const retry = await ghRequest(
                env, 'PUT',
                `/repos/${env.GH_OWNER}/${env.GH_REPO}/contents/${downloadPath}`,
                {
                    message: `Update community pack: ${meta.packName} (${submissionId})`,
                    content: bytesToBase64(zipBytes),
                    sha: existing.sha,
                },
            );
            if (!retry.ok) throw new Error(`GitHub upload failed: ${(await retry.text()).slice(0, 200)}`);
        } else {
            throw new Error(`GitHub upload failed (${zipRes.status}): ${text.slice(0, 200)}`);
        }
    }

    const fileRes = await ghRequest(
        env, 'GET',
        `/repos/${env.GH_OWNER}/${env.GH_REPO}/contents/${PACKS_JSON_PATH}`,
    );
    if (!fileRes.ok) {
        throw new Error(`Could not read ${PACKS_JSON_PATH}: HTTP ${fileRes.status}`);
    }
    const fileData = await fileRes.json();
    const packsJson = new TextDecoder().decode(decodeGitHubContent(fileData.content));
    const packs = JSON.parse(packsJson);
    if (!Array.isArray(packs)) throw new Error('packs.json is not an array');

    const entry = buildCatalogEntry(meta, safeFilename, packUrl, catalogId);
    const existingIdx = packs.findIndex(p => p.id === catalogId);
    if (existingIdx >= 0) packs[existingIdx] = entry;
    else packs.push(entry);

    const weekSec = 7 * 24 * 3600;
    const featuredUntil = Math.floor(Date.now() / 1000) + weekSec;
    for (const p of packs) {
        p.featured = false;
        p.featured_until = 0;
    }
    entry.featured = true;
    entry.featured_until = featuredUntil;

    const pretty = JSON.stringify(packs, null, 4) + '\n';
    const catalogRes = await ghRequest(
        env, 'PUT',
        `/repos/${env.GH_OWNER}/${env.GH_REPO}/contents/${PACKS_JSON_PATH}`,
        {
            message: `Catalog: approve community pack ${meta.packName} (${submissionId})`,
            content: bytesToBase64(new TextEncoder().encode(pretty)),
            sha: fileData.sha,
        },
    );
    if (!catalogRes.ok) {
        throw new Error(`Could not update packs.json: ${(await catalogRes.text()).slice(0, 200)}`);
    }

    STATS_CACHE = null;
    return { catalogId, packUrl, filename: safeFilename };
}

async function runPackApprove(interaction, env, submissionId) {
    const loaded = await loadPendingSubmission(env, submissionId);
    if (!loaded) {
        return { content: `No pending submission found for \`${submissionId}\`. Check the ID in the upload message.` };
    }
    if (loaded.alreadyHandled) {
        return { content: `Submission \`${submissionId}\` was already **${loaded.alreadyHandled}**.` };
    }

    const { meta, zipBytes } = loaded;
    const result = await publishApprovedPack(env, meta, zipBytes, meta.filename || `${meta.packName}.zip`);
    await finalizeSubmission(env, meta.submissionId, 'approved', {
        catalogId: result.catalogId,
        packUrl: result.packUrl,
        approvedBy: interactionUserId(interaction),
    });

    return {
        content: [
            `✅ **Approved** \`${meta.submissionId}\` — **${meta.packName}**`,
            `Catalog ID: \`${result.catalogId}\``,
            `Download: ${result.packUrl}`,
            '',
            'GitHub Pages will refresh in ~1 minute. Mod users can press **Reconnect** to see the new pack.',
        ].join('\n'),
    };
}

async function runPackDeny(interaction, env, submissionId, reason) {
    const loaded = await loadPendingSubmission(env, submissionId);
    if (!loaded) {
        return { content: `No pending submission found for \`${submissionId}\`.` };
    }
    if (loaded.alreadyHandled) {
        return { content: `Submission \`${submissionId}\` was already **${loaded.alreadyHandled}**.` };
    }

    const { meta } = loaded;
    await finalizeSubmission(env, meta.submissionId, 'denied', {
        deniedBy: interactionUserId(interaction),
        denyReason: reason || '',
    });

    let msg = `❌ **Denied** \`${meta.submissionId}\` — **${meta.packName}**`;
    if (reason) msg += `\nReason: ${reason}`;
    return { content: msg };
}

async function handlePackReviewCommand(interaction, env, ctx, action) {
    if (!isPackReviewer(interaction, env)) {
        return reply({ content: 'Only the pack review owner can approve or deny uploads.' });
    }

    const opts = interaction.data.options ?? [];
    const submissionId = trimField(opts.find(o => o.name === 'id')?.value, 16);
    if (!submissionId) {
        return reply({ content: 'Pass the submission ID from the upload notification, e.g. `/pack-approve id:abc12def`.' });
    }

    const reason = trimField(opts.find(o => o.name === 'reason')?.value, 300);

    ctx.waitUntil((async () => {
        try {
            const payload = action === 'approve'
                ? await runPackApprove(interaction, env, submissionId)
                : await runPackDeny(interaction, env, submissionId, reason);
            await discordPatchFollowup(env, interaction.token, payload);
            if (action === 'approve') {
                await refreshLeaderboardIfConfigured(env);
            }
        } catch (err) {
            console.warn(`/pack-${action} failed:`, err.message ?? err);
            await discordPatchFollowup(env, interaction.token, {
                content: `Failed to ${action} \`${submissionId}\`: ${(err.message ?? String(err)).slice(0, 300)}`,
            });
        }
    })());
    return deferred({ ephemeral: true });
}

async function postPackReviewMessage(env, meta, zipBytes, filename) {
    await notifyPackSubmission(env, meta, zipBytes, filename);
}

async function handlePackSubmit(request, env) {
    const ct = request.headers.get('content-type') || '';
    if (!ct.includes('multipart/form-data')) {
        return json({ error: 'expected_multipart', message: 'Send multipart/form-data.' }, { status: 400 });
    }

    let form;
    try { form = await request.formData(); }
    catch { return json({ error: 'bad_form', message: 'Could not read upload.' }, { status: 400 }); }

    const file = form.get('pack');
    if (!file || typeof file.arrayBuffer !== 'function') {
        return json({ error: 'missing_file', message: 'Pack zip is required.' }, { status: 400 });
    }

    const packName = trimField(form.get('packName'), 80);
    if (!packName) {
        return json({ error: 'missing_name', message: 'Pack name is required.' }, { status: 400 });
    }

    const clientId = trimField(form.get('clientId'), 128);
    const rateBlock = await checkSubmitRate(env, clientId);
    if (rateBlock) return rateBlock;

    const zipBytes = await file.arrayBuffer();
    if (zipBytes.byteLength === 0) {
        return json({ error: 'empty_file', message: 'Pack zip is empty.' }, { status: 400 });
    }
    if (zipBytes.byteLength > MAX_PACK_BYTES) {
        return json({ error: 'too_large', message: 'Pack exceeds 8 MB upload limit.' }, { status: 413 });
    }

    const submissionId = crypto.randomUUID().slice(0, 8);
    const meta = {
        submissionId,
        packName,
        description: trimField(form.get('description'), 500),
        authorName: trimField(form.get('authorName'), 64),
        contact: trimField(form.get('contact'), 64),
        packId: trimField(form.get('packId'), 128),
        clientId: clientId || 'anonymous',
        tags: parseSubmitTags(form.get('tags')),
    };

    let filename = 'pack.zip';
    if (file.name && String(file.name).trim()) {
        filename = String(file.name).trim().replace(/[^\w.\- ]+/g, '_').slice(0, 80);
        if (!filename.toLowerCase().endsWith('.zip')) filename += '.zip';
    } else {
        filename = `${packName.replace(/[^\w.\- ]+/g, '_').slice(0, 40) || 'pack'}.zip`;
    }

    try {
        await savePendingSubmission(env, submissionId, meta, zipBytes, filename);
        await postPackReviewMessage(env, meta, zipBytes, filename);
    } catch (err) {
        console.warn('pack-submit failed:', err.message ?? err);
        return json({
            error: 'review_channel_failed',
            message: 'Upload server is not ready for reviews yet.',
        }, { status: 503 });
    }

    return json({
        ok: true,
        submissionId,
        message: 'Submitted for review! If approved, your pack will appear in the public browser.',
    }, { status: 201 });
}

async function handleSubmitStatus(request, env) {
    const url = new URL(request.url);
    const clientId = trimField(url.searchParams.get('clientId'), 128);
    if (!clientId) {
        return json({ error: 'missing_client_id', message: 'clientId query param required.' }, { status: 400 });
    }

    const key = `ps:client:${clientId}`;
    const raw = await env.HEARTBEATS.get(key);
    let ids = [];
    try { ids = raw ? JSON.parse(raw) : []; } catch { ids = []; }

    const submissions = [];
    for (const sid of ids) {
        const metaRaw = await env.HEARTBEATS.get(`ps:meta:${sid}`);
        if (!metaRaw) continue;
        const meta = JSON.parse(metaRaw);
        submissions.push({
            submissionId: meta.submissionId,
            packName: meta.packName,
            status: meta.status || 'pending',
            submittedAt: meta.submittedAt || '',
            resolvedAt: meta.resolvedAt || '',
            catalogId: meta.catalogId || '',
            packUrl: meta.packUrl || '',
            denyReason: meta.denyReason || '',
            tags: meta.tags || [],
        });
    }
    return json({ submissions });
}

function voterIdFromRequest(request) {
    const voter = trimField(request.headers.get('X-SlothyHub-Voter'), 128);
    if (!voter || voter.length < 8 || !/^[A-Za-z0-9_\-:.]+$/.test(voter)) return null;
    return voter;
}

function packIdFromBody(body) {
    const id = trimField(body?.packId ?? body?.pack_id, 128);
    if (!id || !/^[A-Za-z0-9_\-:.]+$/.test(id)) return null;
    return id;
}

async function readStarCount(env, packId) {
    const raw = await env.HEARTBEATS.get(`${STAR_COUNT_PREFIX}${packId}`);
    const n = raw ? parseInt(raw, 10) : 0;
    return Number.isFinite(n) && n > 0 ? n : 0;
}

async function readVoterStars(env, voterId) {
    const raw = await env.HEARTBEATS.get(`ps:star:voter:${voterId}`);
    try {
        const parsed = raw ? JSON.parse(raw) : [];
        return Array.isArray(parsed) ? parsed.filter(Boolean) : [];
    } catch {
        return [];
    }
}

async function recordVoterStar(env, voterId, packId) {
    const key = `ps:star:voter:${voterId}`;
    const ids = await readVoterStars(env, voterId);
    if (ids.includes(packId)) return;
    ids.push(packId);
    await env.HEARTBEATS.put(key, JSON.stringify(ids));
}

async function removeVoterStar(env, voterId, packId) {
    const key = `ps:star:voter:${voterId}`;
    const ids = await readVoterStars(env, voterId);
    const next = ids.filter(id => id !== packId);
    if (next.length === 0) await env.HEARTBEATS.delete(key);
    else await env.HEARTBEATS.put(key, JSON.stringify(next));
}

async function checkStarRate(env, voterId) {
    const key = `${STAR_RATE_PREFIX}${voterId}`;
    const raw = await env.HEARTBEATS.get(key);
    const count = raw ? parseInt(raw, 10) : 0;
    if (count >= STAR_RATE_MAX) {
        return json({
            error: 'rate_limited',
            message: 'Too many stars too fast — try again in a minute.',
        }, { status: 429 });
    }
    await env.HEARTBEATS.put(key, String(count + 1), { expirationTtl: STAR_RATE_TTL });
    return null;
}

async function handlePackStar(request, env) {
    const voterId = voterIdFromRequest(request);
    if (!voterId) {
        return json({ error: 'missing_voter', message: 'X-SlothyHub-Voter header required.' }, { status: 400 });
    }

    let body;
    try { body = await request.json(); } catch { body = {}; }
    const packId = packIdFromBody(body);
    if (!packId) {
        return json({ error: 'missing_pack_id', message: 'packId required.' }, { status: 400 });
    }

    const voteKey = `${STAR_VOTE_PREFIX}${voterId}:${packId}`;
    const countKey = `${STAR_COUNT_PREFIX}${packId}`;
    const already = await env.HEARTBEATS.get(voteKey);
    const count = await readStarCount(env, packId);

    if (already) {
        const rateBlock = await checkStarRate(env, voterId);
        if (rateBlock) return rateBlock;

        await env.HEARTBEATS.delete(voteKey);
        const newCount = Math.max(0, count - 1);
        if (newCount === 0) await env.HEARTBEATS.delete(countKey);
        else await env.HEARTBEATS.put(countKey, String(newCount));
        await removeVoterStar(env, voterId, packId);
        return json({ star_count: newCount, viewer_starred: false }, { status: 200 });
    }

    const rateBlock = await checkStarRate(env, voterId);
    if (rateBlock) return rateBlock;

    await env.HEARTBEATS.put(voteKey, '1');
    const newCount = count + 1;
    await env.HEARTBEATS.put(countKey, String(newCount));
    await recordVoterStar(env, voterId, packId);
    return json({ star_count: newCount, viewer_starred: true }, { status: 201 });
}

async function handlePackStars(request, env) {
    const voterId = voterIdFromRequest(request);
    const url = new URL(request.url);
    const idsParam = trimField(url.searchParams.get('ids'), 8000);
    const ids = idsParam
        ? idsParam.split(',').map(s => s.trim()).filter(Boolean).slice(0, 500)
        : [];

    const counts = {};
    if (ids.length > 0) {
        await Promise.all(ids.map(async (packId) => {
            if (!/^[A-Za-z0-9_\-:.]+$/.test(packId)) return;
            const c = await readStarCount(env, packId);
            if (c > 0) counts[packId] = c;
        }));
    } else {
        const countsList = await env.HEARTBEATS.list({ prefix: STAR_COUNT_PREFIX, limit: 1000 });
        await Promise.all((countsList.keys ?? []).map(async (entry) => {
            const packId = entry.name.slice(STAR_COUNT_PREFIX.length);
            if (!packId) return;
            counts[packId] = await readStarCount(env, packId);
        }));
    }

    const starred = voterId ? await readVoterStars(env, voterId) : [];
    return json({ counts, starred });
}

// Pack star leaderboard (Discord channel embed, auto-updated)

async function fetchCatalogPacks(env) {
    const res = await fetch(env.PACKS_JSON_URL, { headers: { 'User-Agent': USER_AGENT } });
    if (!res.ok) return [];
    const data = await res.json();
    return Array.isArray(data) ? data : [];
}

async function loadAllStarCounts(env) {
    const counts = {};
    const countsList = await env.HEARTBEATS.list({ prefix: STAR_COUNT_PREFIX, limit: 1000 });
    await Promise.all((countsList.keys ?? []).map(async (entry) => {
        const packId = entry.name.slice(STAR_COUNT_PREFIX.length);
        if (packId) counts[packId] = await readStarCount(env, packId);
    }));
    return counts;
}

async function buildLeaderboardEntries(env) {
    const [catalog, starCounts] = await Promise.all([
        fetchCatalogPacks(env),
        loadAllStarCounts(env),
    ]);
    const seen = new Set();
    const entries = catalog.map(p => {
        const id = String(p.id ?? '').trim();
        if (!id) return null;
        seen.add(id);
        return {
            id,
            name: String(p.name ?? id).trim() || id,
            stars: starCounts[id] ?? Math.max(0, p.star_count ?? 0),
        };
    }).filter(Boolean);

    for (const [id, stars] of Object.entries(starCounts)) {
        if (seen.has(id)) continue;
        entries.push({ id, name: id, stars });
    }

    entries.sort((a, b) => b.stars - a.stars || a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }));
    return entries;
}

function leaderboardRankLabel(rank) {
    if (rank === 1) return '🥇';
    if (rank === 2) return '🥈';
    if (rank === 3) return '🥉';
    return `\`${String(rank).padStart(2, ' ')}\``;
}

function topLeaderboardEntries(entries) {
    return entries.slice(0, LEADERBOARD_TOP_N);
}

function hashLeaderboardEntries(entries) {
    return topLeaderboardEntries(entries).map(e => `${e.id}:${e.stars}`).join('|');
}

function buildLeaderboardEmbed(entries) {
    const top = topLeaderboardEntries(entries);
    const lines = top.length
        ? top.map((e, i) => `${leaderboardRankLabel(i + 1)} **${e.name}** — ★ ${fmt(e.stars)}`)
        : ['_No packs in the catalog yet._'];

    const description = lines.join('\n');

    return {
        title: '⭐ Pack Leaderboard — Top 10',
        color: ACCENT,
        description,
        footer: { text: 'Top 10 by community stars · updates when players star packs in-game' },
        timestamp: new Date().toISOString(),
    };
}

async function readLeaderboardConfig(env) {
    const raw = await env.HEARTBEATS.get(LEADERBOARD_CONFIG_KEY);
    if (!raw) return null;
    try {
        const cfg = JSON.parse(raw);
        if (cfg?.channelId && cfg?.messageId) return cfg;
    } catch { /* ignore */ }
    return null;
}

async function saveLeaderboardConfig(env, channelId, messageId) {
    await env.HEARTBEATS.put(
        LEADERBOARD_CONFIG_KEY,
        JSON.stringify({ channelId, messageId, updatedAt: new Date().toISOString() }),
    );
}

async function setupLeaderboardInChannel(env, channelId) {
    const id = trimField(channelId, 32);
    if (!id) throw new Error('channelId required');

    const entries = await buildLeaderboardEntries(env);
    const embed = buildLeaderboardEmbed(entries);
    const appId = env.DISCORD_APP_ID;

    const listRes = await discordBotRequest(env, 'GET', `/channels/${id}/messages?limit=50`);
    if (listRes.ok) {
        const msgs = await listRes.json();
        for (const msg of msgs) {
            const title = msg.embeds?.[0]?.title ?? '';
            if (msg.author?.id === appId && title.includes('Leaderboard')) {
                await discordBotRequest(env, 'DELETE', `/channels/${id}/messages/${msg.id}`);
            }
        }
    }

    const postRes = await discordBotRequest(env, 'POST', `/channels/${id}/messages`, { embeds: [embed] });
    if (!postRes.ok) {
        throw new Error(`POST leaderboard HTTP ${postRes.status}: ${(await postRes.text()).slice(0, 200)}`);
    }
    const posted = await postRes.json();
    await saveLeaderboardConfig(env, id, posted.id);
    await env.HEARTBEATS.put(LEADERBOARD_HASH_KEY, hashLeaderboardEntries(entries));
    try { await discordBotPut(env, `/channels/${id}/pins/${posted.id}`); } catch { /* optional */ }
    return { channelId: id, messageId: posted.id, packCount: entries.length, pinned: true };
}

async function refreshLeaderboard(env) {
    const cfg = await readLeaderboardConfig(env);
    if (!cfg) return { ok: false, reason: 'not_configured' };

    const entries = await buildLeaderboardEntries(env);
    const embed = buildLeaderboardEmbed(entries);
    const hash = hashLeaderboardEntries(entries);
    const prevHash = await env.HEARTBEATS.get(LEADERBOARD_HASH_KEY);
    if (prevHash === hash) return { ok: true, skipped: true };

    const patchRes = await discordBotRequest(
        env, 'PATCH',
        `/channels/${cfg.channelId}/messages/${cfg.messageId}`,
        { embeds: [embed] },
    );
    if (patchRes.ok) {
        await env.HEARTBEATS.put(LEADERBOARD_HASH_KEY, hash);
        return { ok: true, updated: true, packCount: entries.length };
    }
    if (patchRes.status === 404) {
        const setup = await setupLeaderboardInChannel(env, cfg.channelId);
        return { ok: true, recreated: true, ...setup };
    }
    console.warn('leaderboard PATCH', patchRes.status, (await patchRes.text()).slice(0, 200));
    return { ok: false, status: patchRes.status };
}

async function refreshLeaderboardIfConfigured(env) {
    const cfg = await readLeaderboardConfig(env);
    if (cfg) await refreshLeaderboard(env);
}

async function handleSetupLeaderboard(request, env) {
    const ownerId = trimField(env.PACK_REVIEW_OWNER_USER_ID, 32);
    const key = trimField(request.headers.get('X-Admin-Key'), 64);
    if (!ownerId || key !== ownerId) {
        return new Response('forbidden', { status: 403 });
    }

    let channelId = trimField(env.LEADERBOARD_CHANNEL_ID, 32);
    try {
        const body = await request.json();
        if (body?.channelId) channelId = trimField(body.channelId, 32);
    } catch { /* optional body */ }

    if (!channelId) {
        return json({ ok: false, error: 'channelId required (body or LEADERBOARD_CHANNEL_ID)' }, { status: 400 });
    }

    try {
        const result = await setupLeaderboardInChannel(env, channelId);
        return json({ ok: true, ...result });
    } catch (err) {
        return json({ ok: false, error: (err.message ?? String(err)).slice(0, 300) }, { status: 500 });
    }
}

const PERM_ADMINISTRATOR = '8';

const SLASH_COMMANDS = [
    { name: 'modinfo', description: "Show info about Slothy's Tree mod." },
    { name: 'download', description: 'Get mod download links privately (only you see this).' },
    {
        name: 'setup',
        description: '[Admins] Post the persistent download button in this channel.',
        default_member_permissions: PERM_ADMINISTRATOR,
        dm_permission: false,
    },
    {
        name: 'setup-leaderboard',
        description: '[Admins] Post a live pack star leaderboard in this channel.',
        default_member_permissions: PERM_ADMINISTRATOR,
        dm_permission: false,
    },
    { name: 'invite', description: 'Get the bot invite link (add Slothy bot to another server).' },
    {
        name: 'modstats',
        description: "Show Slothy's Tree mod stats - active users, pack count, stars, downloads.",
    },
    {
        name: 'pack-approve',
        description: '[Owner] Approve a pack upload — publishes it to the public catalog.',
        options: [{
            name: 'id',
            description: 'Submission ID from the upload DM/ticket',
            type: 3,
            required: true,
        }],
    },
    {
        name: 'pack-deny',
        description: '[Owner] Deny a pack upload request.',
        options: [
            {
                name: 'id',
                description: 'Submission ID from the upload DM/ticket',
                type: 3,
                required: true,
            },
            {
                name: 'reason',
                description: 'Optional reason (for your notes)',
                type: 3,
                required: false,
            },
        ],
    },
];

async function registerDiscordCommands(env) {
    if (!env.DISCORD_TOKEN || !env.DISCORD_APP_ID) {
        throw new Error('DISCORD_TOKEN and DISCORD_APP_ID must be configured');
    }
    const guildId = trimField(env.PACK_TICKET_GUILD_ID, 32);
    const results = [];

    if (guildId) {
        const guildRes = await discordBotRequest(
            env, 'PUT', `/applications/${env.DISCORD_APP_ID}/guilds/${guildId}/commands`, SLASH_COMMANDS,
        );
        const guildBody = await guildRes.text();
        if (!guildRes.ok) {
            throw new Error(`Guild command register HTTP ${guildRes.status}: ${guildBody.slice(0, 300)}`);
        }
        results.push({ scope: 'guild', count: JSON.parse(guildBody).length });
    }

    const globalRes = await discordBotRequest(
        env, 'PUT', `/applications/${env.DISCORD_APP_ID}/commands`, SLASH_COMMANDS,
    );
    const globalBody = await globalRes.text();
    if (!globalRes.ok) {
        throw new Error(`Global command register HTTP ${globalRes.status}: ${globalBody.slice(0, 300)}`);
    }
    results.push({ scope: 'global', count: JSON.parse(globalBody).length });

    return results;
}

async function handleRegisterCommands(request, env) {
    const ownerId = trimField(env.PACK_REVIEW_OWNER_USER_ID, 32);
    const key = trimField(request.headers.get('X-Admin-Key'), 64);
    if (!ownerId || key !== ownerId) {
        return new Response('forbidden', { status: 403 });
    }
    try {
        const registered = await registerDiscordCommands(env);
        return json({ ok: true, registered });
    } catch (err) {
        return json({ ok: false, error: (err.message ?? String(err)).slice(0, 300) }, { status: 500 });
    }
}

/** VIEW + READ_HISTORY + ADD_REACTIONS (no SEND) for @everyone */
const PERM_VIEW_REACT = '66624';
/** VIEW + SEND + READ + embed + attach */
const PERM_STAFF_SEND = '117760';
/** Staff + MANAGE_MESSAGES (prune bad reactions, pin) */
const PERM_BOT_STAFF = '126976';

function buildModSuggestionsTemplatePayload() {
    return {
        content: [
            '📋 **Mod suggestions**',
            '',
            'Only **staff** can post here. Everyone else: read and vote with **✅** only.',
            '',
            'When you post a real suggestion, copy the format below.',
        ].join('\n'),
        embeds: [{
            title: '✅ Example: Add pack tags in the upload form',
            color: ACCENT,
            description: 'Example suggestion — not a live request.',
            fields: [
                {
                    name: 'What',
                    value: 'Let uploaders pick tags (PvP, CIT, swords) when submitting a pack.',
                    inline: false,
                },
                {
                    name: 'Why',
                    value: 'Makes `/pack-approve` faster and helps players filter community packs.',
                    inline: false,
                },
                {
                    name: 'MC versions',
                    value: 'All tiers (1.20 – 1.21.11)',
                    inline: true,
                },
                {
                    name: 'Priority',
                    value: 'Nice to have',
                    inline: true,
                },
                {
                    name: 'Voting',
                    value: 'React with **✅** below if you want this. One ✅ per person — no other emoji.',
                    inline: false,
                },
            ],
            footer: { text: "Slothy's Tree · mod suggestions" },
            timestamp: new Date().toISOString(),
        }],
    };
}

async function setupSuggestionsChannel(env, channelId) {
    const ownerId = trimField(env.PACK_REVIEW_OWNER_USER_ID, 32);
    const botId = trimField(env.DISCORD_APP_ID, 32);
    const id = trimField(channelId, 32);
    if (!id) throw new Error('channelId required');

    const chRes = await discordBotRequest(env, 'GET', `/channels/${id}`);
    if (!chRes.ok) {
        throw new Error(`GET channel HTTP ${chRes.status}: ${(await chRes.text()).slice(0, 160)}`);
    }
    const channel = await chRes.json();
    const guildId = channel.guild_id;
    if (!guildId) throw new Error('Channel is not in a server');

    const everyoneDeny = channel.type === 15 ? '34359740416' : '2048'; // forum: no posts; text: no send
    const staffAllow = channel.type === 15 ? '34359856128' : PERM_STAFF_SEND; // forum: send + create posts
    const botAllow = channel.type === 15 ? '34359864320' : PERM_BOT_STAFF; // + manage messages

    const overwriteMap = new Map((channel.permission_overwrites ?? []).map(o => [o.id, o]));
    overwriteMap.set(guildId, { id: guildId, type: 0, allow: PERM_VIEW_REACT, deny: everyoneDeny });
    if (ownerId) {
        overwriteMap.set(ownerId, { id: ownerId, type: 1, allow: staffAllow, deny: '0' });
    }
    if (botId) {
        overwriteMap.set(botId, { id: botId, type: 1, allow: botAllow, deny: '0' });
    }

    const patchBody = {
        topic: 'Mod suggestions — staff post ideas; members vote with ✅ only.',
        permission_overwrites: [...overwriteMap.values()],
    };
    // Forum: only staff create posts; everyone can view/react inside threads.
    if (channel.type === 15) {
        patchBody.default_auto_archive_duration = 10080;
        patchBody.default_reaction_emoji = { name: '✅' };
        patchBody.default_thread_rate_limit_per_user = 0;
    }

    const patchRes = await discordBotRequest(env, 'PATCH', `/channels/${id}`, patchBody);
    if (!patchRes.ok) {
        throw new Error(`PATCH channel HTTP ${patchRes.status}: ${(await patchRes.text()).slice(0, 200)}`);
    }

    const payload = buildModSuggestionsTemplatePayload();
    const appId = botId;
    let targetChannelId = id;
    let posted;

    if (channel.type === 15) {
        // Forum channel — create a starter post (thread).
        const threadRes = await discordBotRequest(env, 'POST', `/channels/${id}/threads`, {
            name: 'Example mod suggestion template',
            auto_archive_duration: 10080,
            message: payload,
        });
        if (!threadRes.ok) {
            throw new Error(`POST forum thread HTTP ${threadRes.status}: ${(await threadRes.text()).slice(0, 200)}`);
        }
        posted = await threadRes.json();
        targetChannelId = posted.id;
    } else if (channel.type === 0 || channel.type === 5) {
        const listRes = await discordBotRequest(env, 'GET', `/channels/${id}/messages?limit=50`);
        if (listRes.ok) {
            const messages = await listRes.json();
            for (const msg of messages) {
                if (msg.author?.id === appId && msg.embeds?.length) {
                    await discordBotRequest(env, 'DELETE', `/channels/${id}/messages/${msg.id}`);
                }
            }
        }
        const postRes = await discordBotRequest(env, 'POST', `/channels/${id}/messages`, payload);
        if (!postRes.ok) {
            throw new Error(`POST message HTTP ${postRes.status}: ${(await postRes.text()).slice(0, 200)}`);
        }
        posted = await postRes.json();
    } else {
        throw new Error(`Unsupported channel type ${channel.type} — use a text, announcement, or forum channel`);
    }

    const messageId = posted.id ?? posted.message?.id;
    if (!messageId) throw new Error('Could not resolve posted message id');

    const reactRes = await discordBotPut(
        env,
        `/channels/${targetChannelId}/messages/${messageId}/reactions/${encodeURIComponent('✅')}/@me`,
    );
    if (!reactRes.ok && reactRes.status !== 204) {
        console.warn('Could not add ✅ reaction:', reactRes.status, await reactRes.text());
    }

    if (channel.type !== 15) {
        await discordBotPut(env, `/channels/${id}/pins/${messageId}`);
    }

    return {
        channelId: id,
        channelType: channel.type,
        messageId,
        threadId: channel.type === 15 ? targetChannelId : null,
        pinned: channel.type !== 15,
    };
}

async function handleSetupSuggestions(request, env) {
    const ownerId = trimField(env.PACK_REVIEW_OWNER_USER_ID, 32);
    const key = trimField(request.headers.get('X-Admin-Key'), 64);
    if (!ownerId || key !== ownerId) {
        return new Response('forbidden', { status: 403 });
    }

    let channelId = trimField(env.SUGGESTIONS_CHANNEL_ID, 32);
    try {
        const body = await request.json();
        if (body?.channelId) channelId = trimField(body.channelId, 32);
    } catch { /* optional body */ }

    if (!channelId) {
        return json({ ok: false, error: 'channelId required (body or SUGGESTIONS_CHANNEL_ID)' }, { status: 400 });
    }

    try {
        const result = await setupSuggestionsChannel(env, channelId);
        return json({ ok: true, ...result });
    } catch (err) {
        return json({ ok: false, error: (err.message ?? String(err)).slice(0, 300) }, { status: 500 });
    }
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
        if (request.method === 'POST' && url.pathname === '/v1/pack-submit') {
            return handlePackSubmit(request, env);
        }
        if (request.method === 'GET' && url.pathname === '/v1/submit-status') {
            return handleSubmitStatus(request, env);
        }
        if (request.method === 'POST' && url.pathname === '/v1/pack-star') {
            const res = await handlePackStar(request, env);
            ctx.waitUntil(refreshLeaderboardIfConfigured(env).catch(err => {
                console.warn('leaderboard refresh:', err.message ?? err);
            }));
            return res;
        }
        if (request.method === 'GET' && url.pathname === '/v1/pack-stars') {
            return handlePackStars(request, env);
        }
        if (request.method === 'POST' && url.pathname === '/v1/register-commands') {
            return handleRegisterCommands(request, env);
        }
        if (request.method === 'POST' && url.pathname === '/v1/setup-suggestions') {
            return handleSetupSuggestions(request, env);
        }
        if (request.method === 'POST' && url.pathname === '/v1/setup-leaderboard') {
            return handleSetupLeaderboard(request, env);
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

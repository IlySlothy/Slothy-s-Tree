#!/usr/bin/env node
// One-shot global slash-command registration for the Slothy's Tree worker bot.
//
// Usage:
//   node scripts/register-commands.mjs
//
// Reads DISCORD_TOKEN and DISCORD_APP_ID from process.env. You can pass them
// inline like:
//   $env:DISCORD_TOKEN="..."; $env:DISCORD_APP_ID="..."; node scripts/register-commands.mjs
//
// or set them in a local .env loaded by your shell. (Avoid committing them.)

const TOKEN   = process.env.DISCORD_TOKEN;
const APP_ID  = process.env.DISCORD_APP_ID;
const GUILD   = process.env.DISCORD_GUILD_ID;

if (!TOKEN || !APP_ID) {
    console.error('Missing DISCORD_TOKEN or DISCORD_APP_ID env var.');
    process.exit(1);
}

const PERM_ADMINISTRATOR = '8';

const commands = [
    {
        name: 'modinfo',
        description: "Show info about Slothy's Tree mod.",
    },
    {
        name: 'download',
        description: 'Get mod download links privately (only you see this).',
    },
    {
        name: 'setup',
        description: '[Admins] Post the persistent download button in this channel.',
        default_member_permissions: PERM_ADMINISTRATOR,
        dm_permission: false,
    },
    {
        name: 'invite',
        description: 'Get the bot invite link (add Slothy bot to another server).',
    },
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

const base = GUILD
    ? `https://discord.com/api/v10/applications/${APP_ID}/guilds/${GUILD}/commands`
    : `https://discord.com/api/v10/applications/${APP_ID}/commands`;

const res = await fetch(base, {
    method: 'PUT',
    headers: {
        Authorization: `Bot ${TOKEN}`,
        'Content-Type': 'application/json',
        'User-Agent': 'slothys-tree-bot-register-script',
    },
    body: JSON.stringify(commands),
});

const body = await res.text();
if (!res.ok) {
    console.error(`Discord returned HTTP ${res.status}:`);
    console.error(body);
    process.exit(2);
}

const parsed = JSON.parse(body);
console.log(`Registered ${parsed.length} command(s) ${GUILD ? `to guild ${GUILD}` : 'globally'}:`);
for (const c of parsed) console.log(`  - /${c.name}: ${c.description}`);
if (!GUILD) console.log('Global commands can take up to ~1 hour to appear in every server.');

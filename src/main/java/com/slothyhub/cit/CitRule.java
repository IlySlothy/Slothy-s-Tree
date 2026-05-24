package com.slothyhub.cit;

import java.util.List;

/**
 * A compiled CIT rule — represents one {@code .properties} CIT entry.
 */
public final class CitRule {

    /** Rule source path (used for logging and deduplication). */
    public final String id;

    /** Minecraft item IDs this rule applies to (e.g. "minecraft:diamond_sword"). */
    public final List<String> items;

    /** Override texture path, relative to the pack's assets folder. */
    public final String texture;

    /** Override model path. */
    public final String model;

    /** Item display name match (supports literal or regex: prefix with 'regex:'). */
    public final String nameMatcher;

    /** Damage range, e.g. "0-100" or "0". Empty means any. */
    public final String damage;

    /** Damage mask for the damage comparison. */
    public final String damageMask;

    /** Stack size range, e.g. "1-64". */
    public final String stackSize;

    /** NBT path → pattern conditions. */
    public final List<NbtCondition> nbtConditions;

    CitRule(String id, List<String> items, String texture, String model,
            String nameMatcher, String damage, String damageMask,
            String stackSize, List<NbtCondition> nbtConditions) {
        this.id            = id;
        this.items         = items;
        this.texture       = texture;
        this.model         = model;
        this.nameMatcher   = nameMatcher;
        this.damage        = damage;
        this.damageMask    = damageMask;
        this.stackSize     = stackSize;
        this.nbtConditions = nbtConditions;
    }

    /** Returns true if this rule applies to a given item ID. */
    public boolean matchesItem(String itemId) {
        if (itemId == null) return false;
        for (String id : items) {
            String norm = id.contains(":") ? id : "minecraft:" + id;
            if (norm.equals(itemId)) return true;
        }
        return false;
    }

    public record NbtCondition(String path, String pattern) {}
}

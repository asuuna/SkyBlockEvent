/*
 * SkyBlockEvent
 * Copyright (c) 2026 Shirito. All rights reserved.
 */
package com.skyblockevent.model;

import java.util.List;
import org.bukkit.Material;

public final class CustomDropSettings {

    public static final CustomDropSettings DISABLED = new CustomDropSettings(
        false,
        Material.AIR,
        "",
        List.of(),
        0.0D,
        0,
        false,
        0L
    );

    private final boolean enabled;
    private final Material material;
    private final String displayName;
    private final List<String> lore;
    private final double chance;
    private final int score;
    private final boolean glowing;
    private final long despawnTicks;

    public CustomDropSettings(
        boolean enabled,
        Material material,
        String displayName,
        List<String> lore,
        double chance,
        int score,
        boolean glowing,
        long despawnTicks
    ) {
        this.enabled = enabled;
        this.material = material == null ? Material.AIR : material;
        this.displayName = displayName == null ? "" : displayName;
        this.lore = List.copyOf(lore);
        this.chance = Math.max(0.0D, Math.min(1.0D, chance));
        this.score = Math.max(0, score);
        this.glowing = glowing;
        this.despawnTicks = Math.max(0L, despawnTicks);
    }

    public boolean isEnabled() {
        return enabled && !material.isAir() && chance > 0.0D && score > 0;
    }

    public Material getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    public double getChance() {
        return chance;
    }

    public int getScore() {
        return score;
    }

    public boolean isGlowing() {
        return glowing;
    }

    public long getDespawnTicks() {
        return despawnTicks;
    }
}

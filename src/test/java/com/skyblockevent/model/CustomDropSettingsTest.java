/*
 * SkyBlockEvent
 * Copyright (c) 2026 Shirito. All rights reserved.
 */
package com.skyblockevent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

final class CustomDropSettingsTest {

    @Test
    void chanceAndDespawnAreClamped() {
        CustomDropSettings settings = new CustomDropSettings(
            true,
            Material.AMETHYST_SHARD,
            "Shard",
            List.of("Lore"),
            2.0D,
            10,
            true,
            -20L
        );

        assertTrue(settings.isEnabled());
        assertEquals(1.0D, settings.getChance());
        assertEquals(0L, settings.getDespawnTicks());
    }

    @Test
    void invalidScoreDisablesDrop() {
        CustomDropSettings settings = new CustomDropSettings(
            true,
            Material.AMETHYST_SHARD,
            "Shard",
            List.of(),
            0.5D,
            0,
            false,
            200L
        );

        assertFalse(settings.isEnabled());
    }
}

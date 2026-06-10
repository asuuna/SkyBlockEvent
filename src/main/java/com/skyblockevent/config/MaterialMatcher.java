package com.skyblockevent.config;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;

public final class MaterialMatcher {

    private MaterialMatcher() {
    }

    public static Set<Material> resolve(Iterable<String> entries, Logger logger) {
        EnumSet<Material> materials = EnumSet.noneOf(Material.class);
        for (String rawEntry : entries) {
            if (rawEntry == null || rawEntry.isBlank()) {
                continue;
            }
            String entry = rawEntry.trim();
            if (entry.startsWith("#")) {
                addCategory(materials, entry.substring(1), logger);
                continue;
            }
            if ("ALL".equalsIgnoreCase(entry) || "*".equals(entry)) {
                for (Material material : Material.values()) {
                    if (material.isBlock() || material.isItem()) {
                        materials.add(material);
                    }
                }
                continue;
            }
            Material material = Material.matchMaterial(entry);
            if (material == null) {
                logger.warning("Unknown material in event config: " + entry);
                continue;
            }
            materials.add(material);
        }
        return materials;
    }

    private static void addCategory(EnumSet<Material> materials, String category, Logger logger) {
        String normalized = category.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "ores":
                addByName(materials, material -> material.name().endsWith("_ORE")
                    || material.name().endsWith("_DEBRIS")
                    || material.name().contains("RAW_"));
                break;
            case "crops":
                addByName(materials, material -> isCropName(material.name()));
                break;
            case "logs":
                addByName(materials, material -> material.name().endsWith("_LOG")
                    || material.name().endsWith("_STEM")
                    || material.name().endsWith("_HYPHAE"));
                break;
            case "skyblock_blocks":
                addNamed(materials,
                    "COBBLESTONE",
                    "STONE",
                    "DEEPSLATE",
                    "BASALT",
                    "NETHERRACK",
                    "DIRT",
                    "GRASS_BLOCK",
                    "SAND",
                    "GRAVEL",
                    "CLAY",
                    "OBSIDIAN");
                break;
            default:
                logger.warning("Unknown material category in event config: #" + category);
                break;
        }
    }

    private static boolean isCropName(String name) {
        return name.equals("WHEAT")
            || name.equals("CARROTS")
            || name.equals("POTATOES")
            || name.equals("BEETROOTS")
            || name.equals("NETHER_WART")
            || name.equals("COCOA")
            || name.equals("MELON")
            || name.equals("PUMPKIN")
            || name.equals("SUGAR_CANE")
            || name.equals("CACTUS")
            || name.equals("BAMBOO")
            || name.equals("KELP")
            || name.endsWith("_MUSHROOM");
    }

    private static void addByName(EnumSet<Material> materials, MaterialPredicate predicate) {
        for (Material material : Material.values()) {
            if (predicate.matches(material)) {
                materials.add(material);
            }
        }
    }

    private static void addNamed(EnumSet<Material> materials, String... names) {
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                materials.add(material);
            }
        }
    }

    @FunctionalInterface
    private interface MaterialPredicate {
        boolean matches(Material material);
    }
}

/*
 * SkyBlockEvent
 * Copyright (c) 2026 Shirito. All rights reserved.
 */
package com.skyblockevent.config;

import com.skyblockevent.model.ComboSettings;
import com.skyblockevent.model.CustomDropSettings;
import com.skyblockevent.model.EventDefinition;
import com.skyblockevent.model.EventType;
import com.skyblockevent.model.MilestoneReward;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

public final class EventConfigService {

    private final Plugin plugin;
    private final Logger logger;
    private volatile Map<String, EventDefinition> definitionsById = Collections.emptyMap();
    private volatile AutoRotationSettings autoRotationSettings = new AutoRotationSettings(false, "sequence", 10, 90, 1);
    private volatile boolean debug;
    private volatile int saveActiveEverySeconds;
    private volatile int historyLimit;
    private volatile String broadcastPermission;

    public EventConfigService(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void reload() {
        debug = plugin.getConfig().getBoolean("settings.debug", false);
        saveActiveEverySeconds = Math.max(15, plugin.getConfig().getInt("settings.save-active-every-seconds", 60));
        historyLimit = Math.max(1, plugin.getConfig().getInt("settings.history-limit", 50));
        broadcastPermission = plugin.getConfig().getString("settings.broadcast-permission", "skyblockevent.notify");
        autoRotationSettings = loadAutoRotation();
        definitionsById = loadDefinitions();
    }

    public List<EventDefinition> getDefinitions() {
        return new ArrayList<>(definitionsById.values());
    }

    public List<String> getDefinitionIds() {
        return new ArrayList<>(definitionsById.keySet());
    }

    public Optional<EventDefinition> findDefinition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitionsById.get(id.toLowerCase(Locale.ROOT)));
    }

    public AutoRotationSettings getAutoRotationSettings() {
        return autoRotationSettings;
    }

    public boolean isDebug() {
        return debug;
    }

    public int getSaveActiveEverySeconds() {
        return saveActiveEverySeconds;
    }

    public int getHistoryLimit() {
        return historyLimit;
    }

    public String getBroadcastPermission() {
        return broadcastPermission;
    }

    private AutoRotationSettings loadAutoRotation() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("auto-rotation");
        if (section == null) {
            return new AutoRotationSettings(false, "sequence", 10, 90, 1);
        }
        return new AutoRotationSettings(
            section.getBoolean("enabled", true),
            section.getString("mode", "sequence"),
            Math.max(1, section.getInt("initial-delay-minutes", 10)),
            Math.max(1, section.getInt("interval-minutes", 90)),
            Math.max(0, section.getInt("min-online-players", 1))
        );
    }

    private Map<String, EventDefinition> loadDefinitions() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("events");
        if (root == null) {
            logger.warning("No events section found in config.yml");
            return Collections.emptyMap();
        }

        Map<String, EventDefinition> loaded = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }
            parseDefinition(id, section).ifPresent(definition -> loaded.put(definition.getId(), definition));
        }
        return Collections.unmodifiableMap(loaded);
    }

    private Optional<EventDefinition> parseDefinition(String rawId, ConfigurationSection section) {
        String id = rawId.toLowerCase(Locale.ROOT);
        EventType type;
        try {
            type = EventType.valueOf(section.getString("type", "MINING").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            logger.warning("Invalid event type for " + rawId + ": " + section.getString("type"));
            return Optional.empty();
        }

        List<String> materialEntries = section.getStringList("materials");
        Set<Material> materials = MaterialMatcher.resolve(materialEntries, logger);
        int duration = Math.max(10, section.getInt("duration-seconds", 600));
        int targetScore = Math.max(0, section.getInt("target-score", 0));
        int points = Math.max(0, section.getInt("points-per-action", 1));
        double multiplier = Math.max(1.0D, section.getDouble("drop-multiplier", 1.0D));

        return Optional.of(new EventDefinition(
            id,
            section.getString("display-name", id),
            type,
            duration,
            targetScore,
            points,
            multiplier,
            materials,
            readComboSettings(section.getConfigurationSection("combo")),
            readCustomDropSettings(section.getConfigurationSection("custom-drop"), id),
            readMilestones(section.getConfigurationSection("milestones.server"), "server", id),
            readMilestones(section.getConfigurationSection("milestones.personal"), "personal", id),
            readTopRewards(section.getConfigurationSection("rewards.top")),
            Math.max(0, section.getInt("rewards.participation.min-score", 0)),
            section.getStringList("rewards.participation.commands")
        ));
    }

    private CustomDropSettings readCustomDropSettings(ConfigurationSection section, String eventId) {
        if (section == null || !section.getBoolean("enabled", false)) {
            return CustomDropSettings.DISABLED;
        }
        Material material = Material.matchMaterial(section.getString("material", "AMETHYST_SHARD"));
        if (material == null || material.isAir()) {
            logger.warning("Invalid custom-drop material for " + eventId + ": " + section.getString("material"));
            return CustomDropSettings.DISABLED;
        }
        return new CustomDropSettings(
            true,
            material,
            section.getString("display-name", "&dEvent Fragment"),
            section.getStringList("lore"),
            section.getDouble("chance", 0.05D),
            Math.max(1, section.getInt("score", 10)),
            section.getBoolean("glowing", true)
        );
    }

    private ComboSettings readComboSettings(ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", false)) {
            return ComboSettings.DISABLED;
        }
        return new ComboSettings(
            true,
            Math.max(1, section.getInt("window-seconds", 8)) * 1000L,
            Math.max(1, section.getInt("actions-per-tier", 5)),
            Math.max(0.0D, section.getDouble("multiplier-step", 0.25D)),
            Math.max(1.0D, section.getDouble("max-multiplier", 3.0D)),
            section.getBoolean("actionbar", true)
        );
    }

    private Map<Integer, MilestoneReward> readMilestones(ConfigurationSection section, String scope, String eventId) {
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<Integer, MilestoneReward> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            int threshold;
            try {
                threshold = Integer.parseInt(key);
            } catch (NumberFormatException exception) {
                logger.warning("Invalid " + scope + " milestone threshold for " + eventId + ": " + key);
                continue;
            }
            if (threshold <= 0) {
                logger.warning("Milestone threshold must be positive for " + eventId + ": " + key);
                continue;
            }

            ConfigurationSection milestone = section.getConfigurationSection(key);
            if (milestone == null) {
                result.put(threshold, new MilestoneReward(threshold, defaultMilestoneMessage(scope), List.of()));
                continue;
            }
            result.put(threshold, new MilestoneReward(
                threshold,
                milestone.getString("message", defaultMilestoneMessage(scope)),
                milestone.getStringList("commands")
            ));
        }
        return Collections.unmodifiableMap(result);
    }

    private String defaultMilestoneMessage(String scope) {
        return "personal".equals(scope) ? "personal-milestone" : "server-milestone";
    }

    private Map<Integer, List<String>> readTopRewards(ConfigurationSection section) {
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            try {
                int rank = Integer.parseInt(key);
                if (rank <= 0) {
                    logger.warning("Reward rank must be positive: " + key);
                    continue;
                }
                result.put(rank, List.copyOf(section.getStringList(key)));
            } catch (NumberFormatException exception) {
                logger.warning("Invalid reward rank: " + key);
            }
        }
        return result;
    }
}

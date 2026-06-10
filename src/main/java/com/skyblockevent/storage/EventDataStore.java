/*
 * SkyBlockEvent
 * Copyright (c) 2026 Shirito. All rights reserved.
 */
package com.skyblockevent.storage;

import com.skyblockevent.model.ActiveEvent;
import com.skyblockevent.model.EventDefinition;
import com.skyblockevent.model.EventHistoryEntry;
import com.skyblockevent.model.EventSnapshot;
import com.skyblockevent.model.EventType;
import com.skyblockevent.model.ParticipantScore;
import com.skyblockevent.platform.PlatformScheduler;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class EventDataStore {

    private final Plugin plugin;
    private final PlatformScheduler scheduler;
    private final File dataFile;
    private final Object ioLock = new Object();

    public EventDataStore(Plugin plugin, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.dataFile = new File(plugin.getDataFolder(), "data/events.yml");
    }

    public void saveActiveSnapshot(EventSnapshot snapshot) {
        scheduler.runAsync(() -> saveActiveSnapshotNow(snapshot));
    }

    public void saveActiveSnapshotNow(EventSnapshot snapshot) {
        synchronized (ioLock) {
            YamlConfiguration yaml = load();
            writeSnapshot(yaml, "active", snapshot);
            save(yaml);
        }
    }

    public void clearActiveSnapshot() {
        scheduler.runAsync(this::clearActiveSnapshotNow);
    }

    public void clearActiveSnapshotNow() {
        synchronized (ioLock) {
            YamlConfiguration yaml = load();
            yaml.set("active", null);
            save(yaml);
        }
    }

    public void appendHistory(EventSnapshot snapshot, int historyLimit) {
        scheduler.runAsync(() -> appendHistoryNow(snapshot, historyLimit));
    }

    public void appendHistoryNow(EventSnapshot snapshot, int historyLimit) {
        synchronized (ioLock) {
            YamlConfiguration yaml = load();
            List<String> historyKeys = yaml.getStringList("history-order");
            String key = DateTimeFormatter.ISO_INSTANT.format(snapshot.getEndedAt())
                .replace(':', '-')
                .replace('.', '-');
            writeSnapshot(yaml, "history." + key, snapshot);
            historyKeys.add(0, key);
            while (historyKeys.size() > historyLimit) {
                String removed = historyKeys.remove(historyKeys.size() - 1);
                yaml.set("history." + removed, null);
            }
            yaml.set("history-order", historyKeys);
            yaml.set("active", null);
            save(yaml);
        }
    }

    public Optional<ActiveEvent> loadActiveEvent(List<EventDefinition> definitions) {
        synchronized (ioLock) {
            YamlConfiguration yaml = load();
            ConfigurationSection active = yaml.getConfigurationSection("active");
            if (active == null) {
                return Optional.empty();
            }

            Map<String, EventDefinition> definitionsById = definitions.stream()
                .collect(Collectors.toMap(EventDefinition::getId, definition -> definition));
            String eventId = active.getString("id", "").toLowerCase(Locale.ROOT);
            EventDefinition definition = definitionsById.get(eventId);
            if (definition == null) {
                plugin.getLogger().warning("Stored active event no longer exists in config: " + eventId);
                yaml.set("active", null);
                save(yaml);
                return Optional.empty();
            }

            Optional<Instant> startedAt = parseInstant(active.getString("started-at"));
            if (startedAt.isEmpty()) {
                yaml.set("active", null);
                save(yaml);
                return Optional.empty();
            }

            long durationMillis = active.getLong("duration-millis", definition.getDurationSeconds() * 1000L);
            Optional<Instant> storedEndsAt = parseInstant(active.getString("ends-at"));
            Instant endsAt = storedEndsAt.orElseGet(() -> startedAt.get().plusMillis(durationMillis));
            if (endsAt.toEpochMilli() <= System.currentTimeMillis()) {
                yaml.set("active", null);
                save(yaml);
                return Optional.empty();
            }

            ActiveEvent restored = new ActiveEvent(
                definition,
                startedAt.get(),
                Math.max(1000L, endsAt.toEpochMilli() - startedAt.get().toEpochMilli())
            );
            ConfigurationSection scores = active.getConfigurationSection("scores");
            if (scores != null) {
                for (String key : scores.getKeys(false)) {
                    ConfigurationSection scoreSection = scores.getConfigurationSection(key);
                    if (scoreSection == null) {
                        continue;
                    }
                    try {
                        UUID uuid = UUID.fromString(scoreSection.getString("uuid", ""));
                        String player = scoreSection.getString("player", uuid.toString());
                        int score = Math.max(0, scoreSection.getInt("score", 0));
                        restored.restoreScore(uuid, player, score);
                    } catch (IllegalArgumentException exception) {
                        plugin.getLogger().log(Level.FINE, "Skipping invalid stored participant score: " + key, exception);
                    }
                }
            }
            restored.markExistingMilestonesClaimed();
            return Optional.of(restored);
        }
    }

    public void loadHistory(int limit, Consumer<List<EventHistoryEntry>> callback) {
        scheduler.runAsync(() -> {
            List<EventHistoryEntry> entries;
            synchronized (ioLock) {
                entries = readHistory(limit);
            }
            scheduler.runGlobal(() -> callback.accept(entries));
        });
    }

    private List<EventHistoryEntry> readHistory(int limit) {
        YamlConfiguration yaml = load();
        List<String> historyKeys = yaml.getStringList("history-order");
        List<EventHistoryEntry> entries = new ArrayList<>();
        for (String key : historyKeys) {
            if (entries.size() >= limit) {
                break;
            }
            ConfigurationSection section = yaml.getConfigurationSection("history." + key);
            if (section == null) {
                continue;
            }
            Optional<Instant> endedAt = parseInstant(section.getString("ended-at"));
            if (endedAt.isEmpty()) {
                continue;
            }
            EventType type;
            try {
                type = EventType.valueOf(section.getString("type", "MINING").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                type = EventType.MINING;
            }
            entries.add(new EventHistoryEntry(
                section.getString("id", key),
                section.getString("display-name", key),
                type,
                endedAt.get(),
                section.getString("reason", "unknown"),
                section.getInt("total-score", 0)
            ));
        }
        return entries;
    }

    private YamlConfiguration load() {
        ensureParentDirectory();
        return YamlConfiguration.loadConfiguration(dataFile);
    }

    private void writeSnapshot(YamlConfiguration yaml, String path, EventSnapshot snapshot) {
        yaml.set(path + ".id", snapshot.getEventId());
        yaml.set(path + ".display-name", snapshot.getDisplayName());
        yaml.set(path + ".type", snapshot.getType().name());
        yaml.set(path + ".started-at", DateTimeFormatter.ISO_INSTANT.format(snapshot.getStartedAt()));
        yaml.set(path + ".ended-at", snapshot.getEndedAt() == null ? null : DateTimeFormatter.ISO_INSTANT.format(snapshot.getEndedAt()));
        yaml.set(path + ".duration-millis", snapshot.getDurationMillis());
        yaml.set(path + ".ends-at", snapshot.getEndsAt() == null ? null : DateTimeFormatter.ISO_INSTANT.format(snapshot.getEndsAt()));
        yaml.set(path + ".reason", snapshot.getReason());
        yaml.set(path + ".total-score", snapshot.getTotalScore());
        yaml.set(path + ".scores", null);

        ConfigurationSection scores = yaml.createSection(path + ".scores");
        int rank = 1;
        for (ParticipantScore score : snapshot.getScores()) {
            String scorePath = String.valueOf(rank);
            scores.set(scorePath + ".uuid", score.getUuid().toString());
            scores.set(scorePath + ".player", score.getPlayerName());
            scores.set(scorePath + ".score", score.getScore());
            rank++;
        }
    }

    private void save(YamlConfiguration yaml) {
        ensureParentDirectory();
        try {
            yaml.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to save event data", exception);
        }
    }

    private Optional<Instant> parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException exception) {
            plugin.getLogger().log(Level.WARNING, "Invalid timestamp in event data: " + value, exception);
            return Optional.empty();
        }
    }

    private void ensureParentDirectory() {
        File parent = dataFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Unable to create data directory: " + parent.getAbsolutePath());
        }
    }
}

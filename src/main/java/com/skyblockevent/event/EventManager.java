/*
 * SkyBlockEvent
 * Copyright (c) 2026 Shirito. All rights reserved.
 */
package com.skyblockevent.event;

import com.skyblockevent.config.AutoRotationSettings;
import com.skyblockevent.config.EventConfigService;
import com.skyblockevent.message.MessageService;
import com.skyblockevent.model.ActiveEvent;
import com.skyblockevent.model.EventDefinition;
import com.skyblockevent.model.EventHistoryEntry;
import com.skyblockevent.model.EventSnapshot;
import com.skyblockevent.model.EventType;
import com.skyblockevent.model.MilestoneReward;
import com.skyblockevent.model.ParticipantScore;
import com.skyblockevent.model.ScoreUpdate;
import com.skyblockevent.platform.PlatformScheduler;
import com.skyblockevent.platform.TaskHandle;
import com.skyblockevent.storage.EventDataStore;
import com.skyblockevent.util.TimeFormatter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class EventManager {

    private static final String PARTICIPATE_PERMISSION = "skyblockevent.participate";

    private final Plugin plugin;
    private final EventConfigService configService;
    private final MessageService messages;
    private final PlatformScheduler scheduler;
    private final EventDataStore dataStore;
    private final Object stateLock = new Object();

    private ActiveEvent activeEvent;
    private TaskHandle tickTask = TaskHandle.NOOP;
    private TaskHandle autoTask = TaskHandle.NOOP;
    private TaskHandle saveTask = TaskHandle.NOOP;
    private int rotationCursor;

    public EventManager(
        Plugin plugin,
        EventConfigService configService,
        MessageService messages,
        PlatformScheduler scheduler,
        EventDataStore dataStore
    ) {
        this.plugin = plugin;
        this.configService = configService;
        this.messages = messages;
        this.scheduler = scheduler;
        this.dataStore = dataStore;
    }

    public void reload() {
        restoreActiveEventIfPresent();
        restartPersistenceTask();
        startAutoRotation();
    }

    public void startAutoRotation() {
        autoTask.cancel();
        AutoRotationSettings settings = configService.getAutoRotationSettings();
        if (!settings.isEnabled()) {
            return;
        }
        long initialDelay = minutesToTicks(settings.getInitialDelayMinutes());
        long period = minutesToTicks(settings.getIntervalMinutes());
        autoTask = scheduler.runGlobalTimer(this::tryStartAutomaticEvent, initialDelay, period);
    }

    public void shutdown() {
        tickTask.cancel();
        autoTask.cancel();
        saveTask.cancel();
        ActiveEvent snapshot;
        synchronized (stateLock) {
            snapshot = activeEvent;
            activeEvent = null;
        }
        if (snapshot != null) {
            dataStore.saveActiveSnapshotNow(EventSnapshot.active(snapshot));
        }
    }

    public Optional<ActiveEvent> getActiveEvent() {
        synchronized (stateLock) {
            return Optional.ofNullable(activeEvent);
        }
    }

    public Optional<EventDefinition> getActiveDefinition(EventType requiredType) {
        synchronized (stateLock) {
            if (activeEvent == null || activeEvent.getDefinition().getType() != requiredType) {
                return Optional.empty();
            }
            return Optional.of(activeEvent.getDefinition());
        }
    }

    public boolean isAnyDropBoostActive() {
        synchronized (stateLock) {
            return activeEvent != null && activeEvent.getDefinition().getDropMultiplier() > 1.0D;
        }
    }

    public boolean startEvent(String id, Integer durationOverrideSeconds, CommandSender initiator, boolean silent) {
        Optional<EventDefinition> optionalDefinition = configService.findDefinition(id);
        if (optionalDefinition.isEmpty()) {
            if (initiator != null) {
                messages.send(initiator, "event-not-found", Map.of("event", id));
            }
            return false;
        }
        return startEvent(optionalDefinition.get(), durationOverrideSeconds, initiator, silent);
    }

    public boolean startEvent(EventDefinition definition, Integer durationOverrideSeconds, CommandSender initiator, boolean silent) {
        ActiveEvent created;
        synchronized (stateLock) {
            if (activeEvent != null) {
                if (initiator != null) {
                    messages.send(initiator, "event-already-active", Map.of("event", messages.color(activeEvent.getDefinition().getDisplayName())));
                }
                return false;
            }
            int seconds = durationOverrideSeconds == null ? definition.getDurationSeconds() : Math.max(10, durationOverrideSeconds);
            created = new ActiveEvent(definition, Instant.now(), seconds * 1000L);
            activeEvent = created;
        }

        tickTask.cancel();
        tickTask = scheduler.runGlobalTimer(this::tick, 20L, 20L);
        restartPersistenceTask();

        Map<String, String> placeholders = placeholders(created);
        if (initiator != null) {
            messages.send(initiator, "event-started", placeholders);
        }
        if (!silent) {
            messages.broadcast(configService.getBroadcastPermission(), "event-start-broadcast", placeholders);
        }
        dataStore.saveActiveSnapshot(EventSnapshot.active(created));
        return true;
    }

    public boolean stopActiveEvent(String reason, CommandSender initiator, boolean announce) {
        ActiveEvent stopped;
        synchronized (stateLock) {
            if (activeEvent == null) {
                if (initiator != null) {
                    messages.send(initiator, "no-active-event");
                }
                return false;
            }
            stopped = activeEvent;
            activeEvent = null;
        }

        finishStoppedEvent(stopped, reason, initiator, announce);
        return true;
    }

    public void addScore(Player player, EventDefinition definition, int amount) {
        if (player == null || definition == null || amount <= 0 || !player.hasPermission(PARTICIPATE_PERMISSION)) {
            return;
        }

        ScoreUpdate update;
        ActiveEvent completed = null;
        synchronized (stateLock) {
            if (activeEvent == null || activeEvent.getDefinition() != definition) {
                return;
            }
            update = activeEvent.recordAction(player.getUniqueId(), player.getName(), amount, System.currentTimeMillis());
            if (definition.getTargetScore() > 0 && update.getTotalScore() >= definition.getTargetScore()) {
                completed = activeEvent;
                activeEvent = null;
            }
        }

        handleScoreUpdate(player, definition, update);
        if (completed != null) {
            messages.broadcast(configService.getBroadcastPermission(), "event-target-reached",
                Map.of("event", messages.color(definition.getDisplayName()), "score", String.valueOf(update.getTotalScore())));
            finishStoppedEvent(completed, "target-reached", null, true);
        }
    }

    public double getDropMultiplier(EventDefinition definition) {
        if (definition == null) {
            return 1.0D;
        }
        synchronized (stateLock) {
            if (activeEvent == null || activeEvent.getDefinition() != definition) {
                return 1.0D;
            }
            return definition.getDropMultiplier();
        }
    }

    public List<ParticipantScore> getTopScores(int limit) {
        synchronized (stateLock) {
            if (activeEvent == null) {
                return List.of();
            }
            List<ParticipantScore> scores = activeEvent.getSortedScores();
            return scores.subList(0, Math.min(limit, scores.size()));
        }
    }

    public void loadHistory(int limit, Consumer<List<EventHistoryEntry>> callback) {
        dataStore.loadHistory(Math.max(1, Math.min(20, limit)), callback);
    }

    public Optional<EventDefinition> getActiveDefinitionById(String eventId) {
        synchronized (stateLock) {
            if (activeEvent == null || eventId == null || !activeEvent.getDefinition().getId().equalsIgnoreCase(eventId)) {
                return Optional.empty();
            }
            return Optional.of(activeEvent.getDefinition());
        }
    }

    private void tick() {
        ActiveEvent current;
        synchronized (stateLock) {
            current = activeEvent;
        }
        if (current == null) {
            tickTask.cancel();
            return;
        }
        if (current.getRemainingMillis() <= 0L) {
            stopActiveEvent("duration-ended", null, true);
        }
    }

    private void restartPersistenceTask() {
        saveTask.cancel();
        if (getActiveEvent().isEmpty()) {
            return;
        }
        int seconds = configService.getSaveActiveEverySeconds();
        saveTask = scheduler.runGlobalTimer(() -> getActiveEvent()
            .ifPresent(active -> dataStore.saveActiveSnapshot(EventSnapshot.active(active))), seconds * 20L, seconds * 20L);
    }

    private void restoreActiveEventIfPresent() {
        synchronized (stateLock) {
            if (activeEvent != null) {
                return;
            }
        }
        dataStore.loadActiveEvent(configService.getDefinitions()).ifPresent(restored -> {
            synchronized (stateLock) {
                if (activeEvent != null) {
                    return;
                }
                activeEvent = restored;
            }
            tickTask.cancel();
            tickTask = scheduler.runGlobalTimer(this::tick, 20L, 20L);
            plugin.getLogger().info("Restored active event: " + restored.getDefinition().getId());
        });
    }

    private void tryStartAutomaticEvent() {
        synchronized (stateLock) {
            if (activeEvent != null) {
                return;
            }
        }
        AutoRotationSettings settings = configService.getAutoRotationSettings();
        if (!settings.isEnabled() || Bukkit.getOnlinePlayers().size() < settings.getMinOnlinePlayers()) {
            return;
        }
        List<EventDefinition> definitions = configService.getDefinitions();
        if (definitions.isEmpty()) {
            return;
        }

        EventDefinition selected;
        if (settings.isRandomMode()) {
            selected = definitions.get(ThreadLocalRandom.current().nextInt(definitions.size()));
        } else {
            selected = definitions.get(rotationCursor % definitions.size());
            rotationCursor++;
        }
        startEvent(selected, null, null, false);
    }

    private void finishStoppedEvent(ActiveEvent stopped, String reason, CommandSender initiator, boolean announce) {
        tickTask.cancel();
        saveTask.cancel();

        EventSnapshot snapshot = EventSnapshot.ended(stopped, reason);
        reward(snapshot, stopped.getDefinition());
        dataStore.appendHistory(snapshot, configService.getHistoryLimit());

        Map<String, String> placeholders = placeholders(snapshot);
        if (initiator != null) {
            messages.send(initiator, "event-stopped", placeholders);
        }
        if (announce) {
            messages.broadcast(configService.getBroadcastPermission(), "event-end-broadcast", placeholders);
        }
    }

    private void handleScoreUpdate(Player player, EventDefinition definition, ScoreUpdate update) {
        Map<String, String> placeholders = scorePlaceholders(player, definition, update, null);
        if (definition.getComboSettings().isActionBar() && update.isComboTierChanged()) {
            messages.sendActionBar(player, "combo-actionbar", placeholders);
        }
        for (MilestoneReward milestone : update.getPersonalMilestones()) {
            Map<String, String> milestonePlaceholders = scorePlaceholders(player, definition, update, milestone);
            if (milestone.hasMessage()) {
                messages.send(player, milestone.getMessageKey(), milestonePlaceholders);
            }
            dispatchMilestoneCommands(milestone, milestonePlaceholders);
        }
        for (MilestoneReward milestone : update.getServerMilestones()) {
            Map<String, String> milestonePlaceholders = scorePlaceholders(player, definition, update, milestone);
            if (milestone.hasMessage()) {
                messages.broadcast(configService.getBroadcastPermission(), milestone.getMessageKey(), milestonePlaceholders);
            }
            dispatchMilestoneCommands(milestone, milestonePlaceholders);
        }
    }

    private void reward(EventSnapshot snapshot, EventDefinition definition) {
        List<ParticipantScore> sortedScores = new ArrayList<>(snapshot.getScores());
        sortedScores.sort(Comparator.comparingInt(ParticipantScore::getScore).reversed());

        Map<UUID, Integer> ranks = new HashMap<>();
        for (int i = 0; i < sortedScores.size(); i++) {
            ranks.put(sortedScores.get(i).getUuid(), i + 1);
        }

        for (ParticipantScore score : sortedScores) {
            int rank = ranks.get(score.getUuid());
            List<String> rankCommands = definition.getTopRewardCommands().getOrDefault(rank, List.of());
            for (String command : rankCommands) {
                dispatchRewardCommand(command, snapshot, score, rank);
            }
            if (score.getScore() >= definition.getParticipationMinScore()) {
                for (String command : definition.getParticipationCommands()) {
                    dispatchRewardCommand(command, snapshot, score, rank);
                }
            }
        }
    }

    private void dispatchMilestoneCommands(MilestoneReward milestone, Map<String, String> placeholders) {
        for (String command : milestone.getCommands()) {
            dispatchParsedCommand(command, placeholders);
        }
    }

    private void dispatchRewardCommand(String command, EventSnapshot snapshot, ParticipantScore score, int rank) {
        if (command == null || command.isBlank()) {
            return;
        }
        String parsed = command
            .replace("%player%", score.getPlayerName())
            .replace("%uuid%", score.getUuid().toString())
            .replace("%score%", String.valueOf(score.getScore()))
            .replace("%rank%", String.valueOf(rank))
            .replace("%event%", snapshot.getEventId());

        dispatchParsedCommand(parsed, Map.of());
    }

    private void dispatchParsedCommand(String command, Map<String, String> placeholders) {
        if (command == null || command.isBlank()) {
            return;
        }
        String parsed = command;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            parsed = parsed.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        String finalParsed = parsed;
        scheduler.runGlobal(() -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalParsed);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Configured command failed: " + finalParsed, exception);
            }
        });
    }

    private Map<String, String> scorePlaceholders(
        Player player,
        EventDefinition definition,
        ScoreUpdate update,
        MilestoneReward milestone
    ) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("uuid", player.getUniqueId().toString());
        placeholders.put("event", messages.color(definition.getDisplayName()));
        placeholders.put("id", definition.getId());
        placeholders.put("type", definition.getType().name().toLowerCase(Locale.ROOT));
        placeholders.put("points", String.valueOf(update.getAwardedPoints()));
        placeholders.put("score", String.valueOf(update.getPlayerScore()));
        placeholders.put("total", String.valueOf(update.getTotalScore()));
        placeholders.put("streak", String.valueOf(update.getStreak()));
        placeholders.put("multiplier", String.format(Locale.ROOT, "%.2f", update.getComboMultiplier()));
        placeholders.put("threshold", milestone == null ? "0" : String.valueOf(milestone.getThreshold()));
        return placeholders;
    }

    private Map<String, String> placeholders(ActiveEvent event) {
        EventDefinition definition = event.getDefinition();
        return Map.of(
            "event", messages.color(definition.getDisplayName()),
            "id", definition.getId(),
            "type", definition.getType().name().toLowerCase(Locale.ROOT),
            "duration", TimeFormatter.millis(event.getDurationMillis()),
            "remaining", TimeFormatter.millis(event.getRemainingMillis()),
            "score", String.valueOf(event.getTotalScore()),
            "target", definition.getTargetScore() <= 0 ? "illimite" : String.valueOf(definition.getTargetScore()),
            "reason", "active"
        );
    }

    private Map<String, String> placeholders(EventSnapshot snapshot) {
        return Map.of(
            "event", messages.color(snapshot.getDisplayName()),
            "id", snapshot.getEventId(),
            "type", snapshot.getType().name().toLowerCase(Locale.ROOT),
            "duration", "-",
            "remaining", "0s",
            "score", String.valueOf(snapshot.getTotalScore()),
            "target", "-",
            "reason", snapshot.getReason()
        );
    }

    private long minutesToTicks(int minutes) {
        return Math.max(20L, minutes * 60L * 20L);
    }
}

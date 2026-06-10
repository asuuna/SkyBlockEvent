package com.skyblockevent.command;

import com.skyblockevent.SkyBlockEventPlugin;
import com.skyblockevent.config.EventConfigService;
import com.skyblockevent.event.EventManager;
import com.skyblockevent.message.MessageService;
import com.skyblockevent.model.ActiveEvent;
import com.skyblockevent.model.EventDefinition;
import com.skyblockevent.model.EventHistoryEntry;
import com.skyblockevent.model.ParticipantScore;
import com.skyblockevent.util.TimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class SkyBlockEventCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "skyblockevent.admin";
    private static final String COMMAND_PERMISSION = "skyblockevent.command";

    private final SkyBlockEventPlugin plugin;
    private final EventManager eventManager;
    private final EventConfigService configService;
    private final MessageService messages;

    public SkyBlockEventCommand(
        SkyBlockEventPlugin plugin,
        EventManager eventManager,
        EventConfigService configService,
        MessageService messages
    ) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.configService = configService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(COMMAND_PERMISSION)) {
            messages.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            messages.sendList(sender, "help", Map.of());
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "status":
                showStatus(sender);
                return true;
            case "list":
                showList(sender);
                return true;
            case "top":
                showTop(sender);
                return true;
            case "history":
                showHistory(sender, args);
                return true;
            case "start":
                start(sender, args);
                return true;
            case "stop":
                stop(sender, args);
                return true;
            case "reload":
                reload(sender);
                return true;
            default:
                messages.send(sender, "unknown-command");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(COMMAND_PERMISSION)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of("help", "status", "list", "top", "history"));
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                base.add("start");
                base.add("stop");
                base.add("reload");
            }
            return filter(base, args[0]);
        }
        if (args.length == 2 && "start".equalsIgnoreCase(args[0]) && sender.hasPermission(ADMIN_PERMISSION)) {
            return filter(configService.getDefinitionIds(), args[1]);
        }
        if (args.length == 3 && "start".equalsIgnoreCase(args[0]) && sender.hasPermission(ADMIN_PERMISSION)) {
            return filter(List.of("300", "600", "900", "1800"), args[2]);
        }
        return Collections.emptyList();
    }

    private void showStatus(CommandSender sender) {
        Optional<ActiveEvent> optionalActiveEvent = eventManager.getActiveEvent();
        if (optionalActiveEvent.isEmpty()) {
            messages.send(sender, "status-none");
            return;
        }
        ActiveEvent activeEvent = optionalActiveEvent.get();
        EventDefinition definition = activeEvent.getDefinition();
        messages.send(sender, "status-active", Map.of(
            "event", messages.color(definition.getDisplayName()),
            "remaining", TimeFormatter.millis(activeEvent.getRemainingMillis()),
            "score", String.valueOf(activeEvent.getTotalScore()),
            "target", definition.getTargetScore() <= 0 ? "illimite" : String.valueOf(definition.getTargetScore())
        ));
    }

    private void showList(CommandSender sender) {
        List<EventDefinition> definitions = configService.getDefinitions();
        if (definitions.isEmpty()) {
            messages.send(sender, "no-events");
            return;
        }
        messages.send(sender, "list-header");
        for (EventDefinition definition : definitions) {
            messages.send(sender, "list-entry", Map.of(
                "id", definition.getId(),
                "event", messages.color(definition.getDisplayName()),
                "type", definition.getType().name().toLowerCase(Locale.ROOT),
                "duration", TimeFormatter.seconds(definition.getDurationSeconds())
            ));
        }
    }

    private void showTop(CommandSender sender) {
        Optional<ActiveEvent> optionalActiveEvent = eventManager.getActiveEvent();
        if (optionalActiveEvent.isEmpty()) {
            messages.send(sender, "no-active-event");
            return;
        }
        ActiveEvent activeEvent = optionalActiveEvent.get();
        List<ParticipantScore> scores = eventManager.getTopScores(10);
        if (scores.isEmpty()) {
            messages.send(sender, "top-empty");
            return;
        }
        messages.send(sender, "top-header", Map.of("event", messages.color(activeEvent.getDefinition().getDisplayName())));
        int rank = 1;
        for (ParticipantScore score : scores) {
            messages.send(sender, "top-entry", Map.of(
                "rank", String.valueOf(rank),
                "player", score.getPlayerName(),
                "score", String.valueOf(score.getScore())
            ));
            rank++;
        }
    }

    private void showHistory(CommandSender sender, String[] args) {
        int limit = 5;
        if (args.length >= 2) {
            try {
                limit = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                messages.sendRaw(sender, messages.color("&cLa limite doit etre un nombre."));
                return;
            }
        }

        eventManager.loadHistory(limit, entries -> {
            if (entries.isEmpty()) {
                messages.send(sender, "history-empty");
                return;
            }
            messages.send(sender, "history-header");
            for (EventHistoryEntry entry : entries) {
                messages.send(sender, "history-entry", Map.of(
                    "id", entry.getEventId(),
                    "event", messages.color(entry.getDisplayName()),
                    "type", entry.getType().name().toLowerCase(Locale.ROOT),
                    "score", String.valueOf(entry.getTotalScore()),
                    "reason", entry.getReason(),
                    "ended", entry.getEndedAt().toString()
                ));
            }
        });
    }

    private void start(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            messages.sendRaw(sender, messages.color("&cUsage: /sbevent start <id> [secondes]"));
            return;
        }
        Integer duration = null;
        if (args.length >= 3) {
            try {
                duration = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                messages.sendRaw(sender, messages.color("&cLa duree doit etre un nombre de secondes."));
                return;
            }
        }
        eventManager.startEvent(args[1], duration, sender, false);
    }

    private void stop(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            messages.send(sender, "no-permission");
            return;
        }
        String reason = args.length >= 2
            ? java.util.Arrays.stream(args).skip(1).collect(Collectors.joining(" "))
            : "manual";
        eventManager.stopActiveEvent(reason, sender, true);
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            messages.send(sender, "no-permission");
            return;
        }
        plugin.reloadPluginState();
        messages.send(sender, "reloaded", Map.of("count", String.valueOf(configService.getDefinitions().size())));
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
            .collect(Collectors.toList());
    }
}

/*
 * SkyBlockEvent
 * Copyright (c) 2026 Shirito. All rights reserved.
 */
package com.skyblockevent.message;

import com.skyblockevent.platform.PlatformScheduler;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class MessageService {

    private final Plugin plugin;
    private final PlatformScheduler scheduler;
    private volatile FileConfiguration messages;
    private volatile String prefix;

    public MessageService(Plugin plugin, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        messages = YamlConfiguration.loadConfiguration(file);
        prefix = color(messages.getString("prefix", "&8[&6SkyBlockEvent&8]&r "));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Collections.emptyMap());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String text = format(messages.getString("messages." + key, key), placeholders);
        sendRaw(sender, text);
    }

    public void sendRaw(CommandSender sender, String text) {
        sender.sendMessage(text);
    }

    public void sendActionBar(Player player, String key, Map<String, String> placeholders) {
        String text = format(messages.getString("messages." + key, key), placeholders);
        Runnable task = () -> player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(text));
        task.run();
    }

    public void sendList(CommandSender sender, String key, Map<String, String> placeholders) {
        List<String> lines = messages.getStringList("messages." + key);
        if (lines.isEmpty()) {
            send(sender, key, placeholders);
            return;
        }
        for (String line : lines) {
            sendRaw(sender, format(line, placeholders));
        }
    }

    public void broadcast(String permission, String key, Map<String, String> placeholders) {
        String text = format(messages.getString("messages." + key, key), placeholders);
        broadcastRaw(permission, text);
    }

    public void broadcastRaw(String permission, String text) {
        scheduler.runGlobal(() -> {
            Bukkit.getConsoleSender().sendMessage(text);
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduler.runForEntity(player, () -> {
                    if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
                        return;
                    }
                    player.sendMessage(text);
                });
            }
        });
    }

    public String format(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        result = result.replace("%prefix%", prefix);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return color(result);
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}

/*
 * SkyBlockEvent
 * Copyright (c) 2026 Shirito. All rights reserved.
 */
package com.skyblockevent;

import com.skyblockevent.command.SkyBlockEventCommand;
import com.skyblockevent.config.EventConfigService;
import com.skyblockevent.event.EventManager;
import com.skyblockevent.listener.SkyBlockEventListener;
import com.skyblockevent.message.MessageService;
import com.skyblockevent.platform.PlatformScheduler;
import com.skyblockevent.storage.EventDataStore;
import java.io.File;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkyBlockEventPlugin extends JavaPlugin {

    private PlatformScheduler scheduler;
    private EventConfigService configService;
    private MessageService messages;
    private EventDataStore dataStore;
    private EventManager eventManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBundledResource("messages.yml");

        scheduler = new PlatformScheduler(this);
        configService = new EventConfigService(this);
        messages = new MessageService(this, scheduler);
        dataStore = new EventDataStore(this, scheduler);
        eventManager = new EventManager(this, configService, messages, scheduler, dataStore);

        reloadPluginState();
        registerCommands();
        getServer().getPluginManager().registerEvents(new SkyBlockEventListener(this, eventManager), this);
        eventManager.startAutoRotation();

        getLogger().info("SkyBlockEvent enabled on " + scheduler.getPlatformName()
            + ". Events loaded: " + configService.getDefinitions().size());
    }

    @Override
    public void onDisable() {
        if (eventManager != null) {
            eventManager.shutdown();
        }
        if (scheduler != null) {
            scheduler.cancelPluginTasks();
        }
    }

    public void reloadPluginState() {
        reloadConfig();
        saveBundledResource("messages.yml");
        messages.reload();
        configService.reload();
        if (eventManager != null) {
            eventManager.reload();
        }
    }

    public EventConfigService getEventConfigService() {
        return configService;
    }

    public MessageService getMessages() {
        return messages;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public PlatformScheduler getPlatformScheduler() {
        return scheduler;
    }

    private void registerCommands() {
        SkyBlockEventCommand commandHandler = new SkyBlockEventCommand(this, eventManager, configService, messages);
        PluginCommand command = Objects.requireNonNull(getCommand("sbevent"), "Command sbevent is missing in plugin.yml");
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
    }

    private void saveBundledResource(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            try {
                saveResource(name, false);
            } catch (IllegalArgumentException exception) {
                getLogger().log(Level.WARNING, "Bundled resource missing: " + name, exception);
            }
        }
    }
}

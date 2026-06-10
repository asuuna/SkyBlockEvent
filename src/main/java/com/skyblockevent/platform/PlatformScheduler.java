/*
 * SkyBlockEvent
 * Copyright (c) 2026 Shirito. All rights reserved.
 */
package com.skyblockevent.platform;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PlatformScheduler {

    private final Plugin plugin;
    private final Set<TaskHandle> activeHandles = ConcurrentHashMap.newKeySet();

    public PlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    public String getPlatformName() {
        String name = Bukkit.getServer().getName();
        return name == null || name.isBlank() ? "Bukkit-compatible" : name;
    }

    public TaskHandle runGlobal(Runnable task) {
        return track(fromBukkitTask(Bukkit.getScheduler().runTask(plugin, wrap(task))));
    }

    public TaskHandle runGlobalLater(Runnable task, long delayTicks) {
        return track(fromBukkitTask(Bukkit.getScheduler().runTaskLater(plugin, wrap(task), delayTicks)));
    }

    public TaskHandle runGlobalTimer(Runnable task, long initialDelayTicks, long periodTicks) {
        return track(fromBukkitTask(Bukkit.getScheduler()
            .runTaskTimer(plugin, wrap(task), initialDelayTicks, Math.max(1L, periodTicks))));
    }

    public TaskHandle runAsync(Runnable task) {
        return track(fromBukkitTask(Bukkit.getScheduler().runTaskAsynchronously(plugin, wrap(task))));
    }

    public TaskHandle runAsyncLater(Runnable task, long delayMillis) {
        long delayTicks = Math.max(1L, delayMillis / 50L);
        return track(fromBukkitTask(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, wrap(task), delayTicks)));
    }

    public TaskHandle runAtLocation(Location location, Runnable task) {
        return runGlobal(task);
    }

    public TaskHandle runForEntity(Entity entity, Runnable task) {
        return runGlobal(task);
    }

    public void cancelPluginTasks() {
        for (TaskHandle handle : activeHandles) {
            try {
                handle.cancel();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.FINE, "Failed to cancel task handle", exception);
            }
        }
        activeHandles.clear();
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    private TaskHandle track(TaskHandle handle) {
        if (handle == TaskHandle.NOOP) {
            return handle;
        }
        activeHandles.add(handle);
        return () -> {
            try {
                handle.cancel();
            } finally {
                activeHandles.remove(handle);
            }
        };
    }

    private TaskHandle fromBukkitTask(BukkitTask task) {
        return task::cancel;
    }

    private Runnable wrap(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Scheduled task failed", exception);
            }
        };
    }
}

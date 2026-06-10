/*
 * SkyBlockEvent
 * Copyright (c) 2026 Shirito. All rights reserved.
 */
package com.skyblockevent.platform;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
        return scheduleBukkit(task, false, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
    }

    public TaskHandle runGlobalLater(Runnable task, long delayTicks) {
        long safeDelayTicks = Math.max(1L, delayTicks);
        return scheduleBukkit(task, false, runnable -> Bukkit.getScheduler().runTaskLater(plugin, runnable, safeDelayTicks));
    }

    public TaskHandle runGlobalTimer(Runnable task, long initialDelayTicks, long periodTicks) {
        long safeInitialDelayTicks = Math.max(1L, initialDelayTicks);
        return scheduleBukkit(task, true, runnable -> Bukkit.getScheduler()
            .runTaskTimer(plugin, runnable, safeInitialDelayTicks, Math.max(1L, periodTicks)));
    }

    public TaskHandle runAsync(Runnable task) {
        return scheduleBukkit(task, false, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public TaskHandle runAsyncLater(Runnable task, long delayMillis) {
        long delayTicks = Math.max(1L, delayMillis / 50L);
        return scheduleBukkit(task, false, runnable -> Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delayTicks));
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

    private TaskHandle scheduleBukkit(Runnable task, boolean repeating, BukkitTaskFactory taskFactory) {
        ScheduledTaskHandle handle = new ScheduledTaskHandle();
        activeHandles.add(handle);
        Runnable wrapped = () -> {
            try {
                task.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Scheduled task failed", exception);
            } finally {
                if (!repeating) {
                    handle.markCompleted();
                    activeHandles.remove(handle);
                }
            }
        };

        try {
            handle.setTask(taskFactory.schedule(wrapped));
            if (handle.isCompleted()) {
                activeHandles.remove(handle);
            }
            return handle;
        } catch (RuntimeException exception) {
            activeHandles.remove(handle);
            throw exception;
        }
    }

    @FunctionalInterface
    private interface BukkitTaskFactory {
        BukkitTask schedule(Runnable runnable);
    }

    private final class ScheduledTaskHandle implements TaskHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile BukkitTask task;
        private volatile boolean completed;

        private void setTask(BukkitTask task) {
            this.task = task;
            if (cancelled.get()) {
                task.cancel();
            }
        }

        private void markCompleted() {
            completed = true;
        }

        private boolean isCompleted() {
            return completed;
        }

        @Override
        public void cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            BukkitTask currentTask = task;
            if (currentTask != null) {
                currentTask.cancel();
            }
            activeHandles.remove(this);
        }
    }
}

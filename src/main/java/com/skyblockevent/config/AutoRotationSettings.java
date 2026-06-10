package com.skyblockevent.config;

public final class AutoRotationSettings {

    private final boolean enabled;
    private final String mode;
    private final int initialDelayMinutes;
    private final int intervalMinutes;
    private final int minOnlinePlayers;

    public AutoRotationSettings(
        boolean enabled,
        String mode,
        int initialDelayMinutes,
        int intervalMinutes,
        int minOnlinePlayers
    ) {
        this.enabled = enabled;
        this.mode = mode;
        this.initialDelayMinutes = initialDelayMinutes;
        this.intervalMinutes = intervalMinutes;
        this.minOnlinePlayers = minOnlinePlayers;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isRandomMode() {
        return "random".equalsIgnoreCase(mode);
    }

    public int getInitialDelayMinutes() {
        return initialDelayMinutes;
    }

    public int getIntervalMinutes() {
        return intervalMinutes;
    }

    public int getMinOnlinePlayers() {
        return minOnlinePlayers;
    }
}

package com.skyblockevent.util;

public final class TimeFormatter {

    private TimeFormatter() {
    }

    public static String seconds(int seconds) {
        return millis(seconds * 1000L);
    }

    public static String millis(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}

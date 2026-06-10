package com.skyblockevent.model;

public final class ComboSettings {

    public static final ComboSettings DISABLED = new ComboSettings(false, 0L, 1, 0.0D, 1.0D, false);

    private final boolean enabled;
    private final long windowMillis;
    private final int actionsPerTier;
    private final double multiplierStep;
    private final double maxMultiplier;
    private final boolean actionBar;

    public ComboSettings(
        boolean enabled,
        long windowMillis,
        int actionsPerTier,
        double multiplierStep,
        double maxMultiplier,
        boolean actionBar
    ) {
        this.enabled = enabled;
        this.windowMillis = Math.max(0L, windowMillis);
        this.actionsPerTier = Math.max(1, actionsPerTier);
        this.multiplierStep = Math.max(0.0D, multiplierStep);
        this.maxMultiplier = Math.max(1.0D, maxMultiplier);
        this.actionBar = actionBar;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getWindowMillis() {
        return windowMillis;
    }

    public int getActionsPerTier() {
        return actionsPerTier;
    }

    public double getMultiplierStep() {
        return multiplierStep;
    }

    public double getMaxMultiplier() {
        return maxMultiplier;
    }

    public boolean isActionBar() {
        return actionBar;
    }

    public double multiplierForStreak(int streak) {
        if (!enabled || streak <= 1 || multiplierStep <= 0.0D) {
            return 1.0D;
        }
        int tiers = (streak - 1) / actionsPerTier;
        return Math.min(maxMultiplier, 1.0D + tiers * multiplierStep);
    }

    public int tierForStreak(int streak) {
        if (!enabled || streak <= 1) {
            return 0;
        }
        return (streak - 1) / actionsPerTier;
    }

    public int pointsFor(int basePoints, int streak) {
        return Math.max(1, (int) Math.round(basePoints * multiplierForStreak(streak)));
    }
}

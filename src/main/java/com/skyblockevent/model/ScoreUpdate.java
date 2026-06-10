package com.skyblockevent.model;

import java.util.List;

public final class ScoreUpdate {

    private final int awardedPoints;
    private final int playerScore;
    private final int totalScore;
    private final int streak;
    private final double comboMultiplier;
    private final boolean comboTierChanged;
    private final List<MilestoneReward> serverMilestones;
    private final List<MilestoneReward> personalMilestones;

    public ScoreUpdate(
        int awardedPoints,
        int playerScore,
        int totalScore,
        int streak,
        double comboMultiplier,
        boolean comboTierChanged,
        List<MilestoneReward> serverMilestones,
        List<MilestoneReward> personalMilestones
    ) {
        this.awardedPoints = awardedPoints;
        this.playerScore = playerScore;
        this.totalScore = totalScore;
        this.streak = streak;
        this.comboMultiplier = comboMultiplier;
        this.comboTierChanged = comboTierChanged;
        this.serverMilestones = List.copyOf(serverMilestones);
        this.personalMilestones = List.copyOf(personalMilestones);
    }

    public int getAwardedPoints() {
        return awardedPoints;
    }

    public int getPlayerScore() {
        return playerScore;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public int getStreak() {
        return streak;
    }

    public double getComboMultiplier() {
        return comboMultiplier;
    }

    public boolean isComboTierChanged() {
        return comboTierChanged;
    }

    public List<MilestoneReward> getServerMilestones() {
        return serverMilestones;
    }

    public List<MilestoneReward> getPersonalMilestones() {
        return personalMilestones;
    }
}

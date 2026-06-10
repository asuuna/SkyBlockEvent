package com.skyblockevent.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ActiveEvent {

    private final EventDefinition definition;
    private final Instant startedAt;
    private final long durationMillis;
    private final ConcurrentHashMap<UUID, ScoreBucket> scores = new ConcurrentHashMap<>();
    private final Set<Integer> claimedServerMilestones = ConcurrentHashMap.newKeySet();
    private final AtomicInteger totalScore = new AtomicInteger();

    public ActiveEvent(EventDefinition definition, Instant startedAt, long durationMillis) {
        this.definition = definition;
        this.startedAt = startedAt;
        this.durationMillis = durationMillis;
    }

    public EventDefinition getDefinition() {
        return definition;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public long getEndsAtEpochMillis() {
        return startedAt.toEpochMilli() + durationMillis;
    }

    public long getRemainingMillis() {
        return Math.max(0L, getEndsAtEpochMillis() - System.currentTimeMillis());
    }

    public int addScore(UUID uuid, String playerName, int amount) {
        if (amount <= 0) {
            return totalScore.get();
        }
        return recordAction(uuid, playerName, amount, System.currentTimeMillis()).getTotalScore();
    }

    public ScoreUpdate recordAction(UUID uuid, String playerName, int basePoints, long nowMillis) {
        if (uuid == null || basePoints <= 0) {
            return new ScoreUpdate(0, 0, totalScore.get(), 0, 1.0D, false, List.of(), List.of());
        }

        ComboSettings comboSettings = definition.getComboSettings();
        ScoreBucket bucket = scores.computeIfAbsent(uuid, ignored -> new ScoreBucket(playerName, 0));
        int awardedPoints;
        int playerScore;
        int streak;
        double multiplier;
        boolean comboTierChanged;
        List<MilestoneReward> personalMilestones;

        synchronized (bucket) {
            bucket.playerName = playerName;
            int previousTier = bucket.lastComboTier;
            streak = bucket.registerAction(nowMillis, comboSettings);
            multiplier = comboSettings.multiplierForStreak(streak);
            awardedPoints = comboSettings.pointsFor(basePoints, streak);
            playerScore = bucket.score.addAndGet(awardedPoints);
            bucket.lastComboTier = comboSettings.tierForStreak(streak);
            comboTierChanged = bucket.lastComboTier > previousTier && bucket.lastComboTier > 0;
            personalMilestones = collectReachedMilestones(
                definition.getPersonalMilestones(),
                playerScore,
                bucket.claimedPersonalMilestones
            );
        }

        int total = totalScore.addAndGet(awardedPoints);
        List<MilestoneReward> serverMilestones = collectReachedMilestones(
            definition.getServerMilestones(),
            total,
            claimedServerMilestones
        );
        return new ScoreUpdate(
            awardedPoints,
            playerScore,
            total,
            streak,
            multiplier,
            comboTierChanged,
            serverMilestones,
            personalMilestones
        );
    }

    public void restoreScore(UUID uuid, String playerName, int score) {
        if (uuid == null || score <= 0) {
            return;
        }
        ScoreBucket bucket = new ScoreBucket(playerName, score);
        for (Integer threshold : definition.getPersonalMilestones().keySet()) {
            if (threshold <= score) {
                bucket.claimedPersonalMilestones.add(threshold);
            }
        }
        scores.put(uuid, bucket);
        totalScore.addAndGet(score);
    }

    public void markExistingMilestonesClaimed() {
        int total = totalScore.get();
        for (Integer threshold : definition.getServerMilestones().keySet()) {
            if (threshold <= total) {
                claimedServerMilestones.add(threshold);
            }
        }
    }

    public int getTotalScore() {
        return totalScore.get();
    }

    public List<ParticipantScore> getSortedScores() {
        List<ParticipantScore> result = new ArrayList<>();
        for (Map.Entry<UUID, ScoreBucket> entry : scores.entrySet()) {
            result.add(new ParticipantScore(entry.getKey(), entry.getValue().playerName, entry.getValue().score.get()));
        }
        result.sort(Comparator.comparingInt(ParticipantScore::getScore).reversed()
            .thenComparing(ParticipantScore::getPlayerName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static final class ScoreBucket {
        private volatile String playerName;
        private final AtomicInteger score;
        private final Set<Integer> claimedPersonalMilestones = ConcurrentHashMap.newKeySet();
        private int streak;
        private int lastComboTier;
        private long lastActionAtMillis;

        private ScoreBucket(String playerName, int score) {
            this.playerName = playerName;
            this.score = new AtomicInteger(score);
        }

        private int registerAction(long nowMillis, ComboSettings comboSettings) {
            if (!comboSettings.isEnabled()) {
                streak = 1;
                lastActionAtMillis = nowMillis;
                return streak;
            }
            if (lastActionAtMillis > 0L && nowMillis - lastActionAtMillis <= comboSettings.getWindowMillis()) {
                streak++;
            } else {
                streak = 1;
                lastComboTier = 0;
            }
            lastActionAtMillis = nowMillis;
            return streak;
        }
    }

    private List<MilestoneReward> collectReachedMilestones(
        Map<Integer, MilestoneReward> milestones,
        int score,
        Set<Integer> claimedMilestones
    ) {
        if (milestones.isEmpty()) {
            return List.of();
        }
        List<MilestoneReward> reached = new ArrayList<>();
        for (Map.Entry<Integer, MilestoneReward> entry : milestones.entrySet()) {
            if (entry.getKey() <= score && claimedMilestones.add(entry.getKey())) {
                reached.add(entry.getValue());
            }
        }
        reached.sort(Comparator.comparingInt(MilestoneReward::getThreshold));
        return reached;
    }
}

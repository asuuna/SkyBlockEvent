package com.skyblockevent.model;

import java.time.Instant;
import java.util.List;

public final class EventSnapshot {

    private final String eventId;
    private final String displayName;
    private final EventType type;
    private final Instant startedAt;
    private final Instant endedAt;
    private final long durationMillis;
    private final Instant endsAt;
    private final String reason;
    private final int totalScore;
    private final List<ParticipantScore> scores;

    public EventSnapshot(
        String eventId,
        String displayName,
        EventType type,
        Instant startedAt,
        Instant endedAt,
        long durationMillis,
        Instant endsAt,
        String reason,
        int totalScore,
        List<ParticipantScore> scores
    ) {
        this.eventId = eventId;
        this.displayName = displayName;
        this.type = type;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationMillis = durationMillis;
        this.endsAt = endsAt;
        this.reason = reason;
        this.totalScore = totalScore;
        this.scores = List.copyOf(scores);
    }

    public static EventSnapshot active(ActiveEvent activeEvent) {
        return new EventSnapshot(
            activeEvent.getDefinition().getId(),
            activeEvent.getDefinition().getDisplayName(),
            activeEvent.getDefinition().getType(),
            activeEvent.getStartedAt(),
            null,
            activeEvent.getDurationMillis(),
            Instant.ofEpochMilli(activeEvent.getEndsAtEpochMillis()),
            "active",
            activeEvent.getTotalScore(),
            activeEvent.getSortedScores()
        );
    }

    public static EventSnapshot ended(ActiveEvent activeEvent, String reason) {
        return new EventSnapshot(
            activeEvent.getDefinition().getId(),
            activeEvent.getDefinition().getDisplayName(),
            activeEvent.getDefinition().getType(),
            activeEvent.getStartedAt(),
            Instant.now(),
            activeEvent.getDurationMillis(),
            Instant.ofEpochMilli(activeEvent.getEndsAtEpochMillis()),
            reason,
            activeEvent.getTotalScore(),
            activeEvent.getSortedScores()
        );
    }

    public String getEventId() {
        return eventId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public EventType getType() {
        return type;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public String getReason() {
        return reason;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public List<ParticipantScore> getScores() {
        return scores;
    }
}

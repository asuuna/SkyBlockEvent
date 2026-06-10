package com.skyblockevent.model;

import java.time.Instant;

public final class EventHistoryEntry {

    private final String eventId;
    private final String displayName;
    private final EventType type;
    private final Instant endedAt;
    private final String reason;
    private final int totalScore;

    public EventHistoryEntry(
        String eventId,
        String displayName,
        EventType type,
        Instant endedAt,
        String reason,
        int totalScore
    ) {
        this.eventId = eventId;
        this.displayName = displayName;
        this.type = type;
        this.endedAt = endedAt;
        this.reason = reason;
        this.totalScore = totalScore;
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

    public Instant getEndedAt() {
        return endedAt;
    }

    public String getReason() {
        return reason;
    }

    public int getTotalScore() {
        return totalScore;
    }
}

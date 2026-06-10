package com.skyblockevent.model;

import java.util.UUID;

public final class ParticipantScore {

    private final UUID uuid;
    private final String playerName;
    private final int score;

    public ParticipantScore(UUID uuid, String playerName, int score) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.score = score;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getScore() {
        return score;
    }
}

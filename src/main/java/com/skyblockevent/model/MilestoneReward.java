/*
 * SkyBlockEvent
 * Copyright (c) 2026 Shirito. All rights reserved.
 */
package com.skyblockevent.model;

import java.util.List;

public final class MilestoneReward {

    private final int threshold;
    private final String messageKey;
    private final List<String> commands;

    public MilestoneReward(int threshold, String messageKey, List<String> commands) {
        this.threshold = threshold;
        this.messageKey = messageKey == null ? "" : messageKey;
        this.commands = List.copyOf(commands);
    }

    public int getThreshold() {
        return threshold;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public List<String> getCommands() {
        return commands;
    }

    public boolean hasMessage() {
        return !messageKey.isBlank();
    }
}

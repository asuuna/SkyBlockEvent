/*
 * SkyBlockEvent
 * Copyright (c) 2026 Shirito. All rights reserved.
 */
package com.skyblockevent.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class TimeFormatterTest {

    @Test
    void formatsShortDurations() {
        assertEquals("0s", TimeFormatter.millis(-1L));
        assertEquals("7s", TimeFormatter.millis(7_200L));
        assertEquals("2m 5s", TimeFormatter.millis(125_000L));
    }

    @Test
    void formatsHourDurations() {
        assertEquals("1h 3m", TimeFormatter.millis(3_780_000L));
    }
}

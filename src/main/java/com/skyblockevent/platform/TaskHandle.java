/*
 * SkyBlockEvent
 * Copyright (c) 2026 Shirito. All rights reserved.
 */
package com.skyblockevent.platform;

@FunctionalInterface
public interface TaskHandle {

    TaskHandle NOOP = () -> { };

    void cancel();
}

package com.skyblockevent.platform;

@FunctionalInterface
public interface TaskHandle {

    TaskHandle NOOP = () -> { };

    void cancel();
}

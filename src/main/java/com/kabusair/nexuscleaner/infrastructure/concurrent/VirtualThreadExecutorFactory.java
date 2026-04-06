package com.kabusair.nexuscleaner.infrastructure.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Factory for virtual-thread executors. Centralized so every scan phase shares
 * a single creation policy and can be swapped in tests.
 */
public final class VirtualThreadExecutorFactory {

    public ExecutorService newPerTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

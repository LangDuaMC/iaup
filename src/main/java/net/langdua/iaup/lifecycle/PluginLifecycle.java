package net.langdua.iaup.lifecycle;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class PluginLifecycle {
    private final AtomicLong epoch = new AtomicLong(0L);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    public long beginNewEpoch() {
        shuttingDown.set(false);
        return epoch.incrementAndGet();
    }

    public long currentEpoch() {
        return epoch.get();
    }

    public boolean isCurrent(long value) {
        return !shuttingDown.get() && epoch.get() == value;
    }

    public void shutdown() {
        shuttingDown.set(true);
        epoch.incrementAndGet();
    }

    public boolean isShuttingDown() {
        return shuttingDown.get();
    }
}

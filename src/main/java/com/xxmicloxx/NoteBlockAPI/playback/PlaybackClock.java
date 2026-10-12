package com.xxmicloxx.NoteBlockAPI.playback;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.concurrent.locks.LockSupport;

/** Absolute monotonic deadlines preserve the source tempo without millisecond rounding drift. */
public final class PlaybackClock {
    private final LongSupplier time;
    private final LongConsumer park;
    private long deadline;

    public PlaybackClock() { this(System::nanoTime, LockSupport::parkNanos); }

    PlaybackClock(LongSupplier time, LongConsumer park) {
        this.time = time;
        this.park = park;
        this.deadline = time.getAsLong();
    }

    /** After a long host stall, resume the next original frame; never skip or rewrite notes. */
    public void awaitNextFrame(float tempo, boolean active) {
        if (!Float.isFinite(tempo) || tempo <= 0) throw new IllegalArgumentException("Invalid NBS tempo");
        long now = time.getAsLong();
        deadline = active ? deadline + Math.max(1, Math.round(1_000_000_000d / tempo)) : now + 50_000_000;
        if (deadline < now - 250_000_000) deadline = now;
        long remaining;
        while ((remaining = deadline - time.getAsLong()) > 0 && !Thread.currentThread().isInterrupted()) {
            park.accept(remaining);
        }
    }
}

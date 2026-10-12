package com.xxmicloxx.NoteBlockAPI.playback;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackClockTest {
    @Test void renderCostDoesNotAccumulateIntoTempoDrift() {
        AtomicLong time = new AtomicLong();
        List<Long> waits = new ArrayList<>();
        PlaybackClock clock = new PlaybackClock(time::get, delay -> { waits.add(delay); time.addAndGet(delay); });
        for (int tick = 0; tick < 1000; tick++) {
            time.addAndGet(700_000); // Encoding/dispatch cost for this original frame.
            clock.awaitNextFrame(100, true);
        }
        assertEquals(10_000_000_000L, time.get());
        assertEquals(1000, waits.size());
        assertTrue(waits.stream().allMatch(delay -> delay == 9_300_000));
    }

    @Test void sourceResolutionRemainsIndependentOfGameTicks() {
        AtomicLong time = new AtomicLong();
        PlaybackClock clock = new PlaybackClock(time::get, time::addAndGet);
        for (int tick = 0; tick < 5; tick++) clock.awaitNextFrame(100, true);
        assertEquals(50_000_000L, time.get()); // Five original frames within one Minecraft tick.
    }

    @Test void hostStallRebasesDeadlineWithoutDroppingFrames() {
        AtomicLong time = new AtomicLong();
        PlaybackClock clock = new PlaybackClock(time::get, time::addAndGet);
        clock.awaitNextFrame(100, true);
        time.addAndGet(2_000_000_000L);
        clock.awaitNextFrame(100, true);
        long resumed = time.get();
        clock.awaitNextFrame(100, true);
        assertEquals(10_000_000L, time.get() - resumed);
    }
}

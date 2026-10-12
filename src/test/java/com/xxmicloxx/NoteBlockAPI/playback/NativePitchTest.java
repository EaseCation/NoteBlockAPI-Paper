package com.xxmicloxx.NoteBlockAPI.playback;

import com.xxmicloxx.NoteBlockAPI.utils.NoteUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePitchTest {
    @AfterEach void reset() { NativePitch.setEnabled(false); }

    @Test void switchPreservesRawNotesOutsideVanillaRangeAndFineTuning() {
        NativePitch.setEnabled(true);
        assertEquals(.11136234f, NoteUtils.getPitchTransposed((byte) 7, (short) 0), 1e-7);
        assertEquals(8f, NoteUtils.getPitchTransposed((byte) 81, (short) 0), 1e-7);
        assertEquals(8f * (float) Math.pow(2, .5 / 12), NoteUtils.getPitchInOctave((byte) 81, (short) 50), 1e-6);
        assertFalse(NoteUtils.isOutOfRange((byte) 81, (short) 0));
    }

    @Test void disabledSwitchRetainsUpstreamOctaveFolding() {
        NativePitch.setEnabled(false);
        float folded = NoteUtils.getPitchTransposed((byte) 81, (short) 0);
        assertTrue(folded >= .5f && folded <= 2f);
        assertTrue(NoteUtils.isOutOfRange((byte) 81, (short) 0));
    }
}

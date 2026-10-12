package com.xxmicloxx.NoteBlockAPI.playback;

/** Opt-in pitch policy; the original lookup tables and octave wrapping remain the default. */
public final class NativePitch {
    private static volatile boolean enabled;

    private NativePitch() { }

    public static boolean isEnabled() { return enabled; }

    public static void setEnabled(boolean value) { enabled = value; }

    /** NBS key 45 is the unshifted sample; the extra pitch is expressed in cents. */
    public static float calculate(byte key, short cents) {
        return (float) Math.pow(2, (Byte.toUnsignedInt(key) - 45) / 12.0 + cents / 1200.0);
    }
}

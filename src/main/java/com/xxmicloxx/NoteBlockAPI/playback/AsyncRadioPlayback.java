package com.xxmicloxx.NoteBlockAPI.playback;

import com.xxmicloxx.NoteBlockAPI.NoteBlockAPI;
import com.xxmicloxx.NoteBlockAPI.model.SoundCategory;
import com.xxmicloxx.NoteBlockAPI.songplayer.RadioSongPlayer;
import com.xxmicloxx.NoteBlockAPI.songplayer.SongPlayer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

/** Small opt-in boundary; the default path never loads PacketEvents or replaces the upstream API. */
public final class AsyncRadioPlayback {
    public interface Backend extends AutoCloseable {
        void start(SongPlayer player, Runnable playback);
        void playFrame(SongPlayer player, int tick);
        void close();
    }
    @FunctionalInterface
    interface SoundSender {
        void send(Location at, Object sound, SoundCategory category, float volume, float pitch, float distance);
    }
    private record ListenerFrame(Location eye, byte volume, SoundSender sender) { }
    private static final ThreadLocal<ListenerFrame> CURRENT = new ThreadLocal<>();
    private static final Map<SongPlayer, DoubleSupplier> GAINS = new ConcurrentHashMap<>();
    private static volatile Backend backend;

    private AsyncRadioPlayback() { }

    public static void configure(NoteBlockAPI plugin) {
        plugin.saveDefaultConfig();
        NativePitch.setEnabled(plugin.getConfig().getBoolean("playback.native-unrestricted-pitch", false));
        if (plugin.getConfig().getBoolean("playback.high-resolution-radio", false)) {
            try {
                // The optional transport and its dependency are loaded only for this explicit switch.
                backend = (Backend) Class.forName("com.xxmicloxx.NoteBlockAPI.playback.PacketRadioPlayback")
                    .getConstructor(NoteBlockAPI.class).newInstance(plugin);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot start high-resolution Radio transport", e);
            }
        }
        plugin.getLogger().info("Playback native-pitch=" + NativePitch.isEnabled() + ", high-resolution-radio=" + isEnabled());
    }

    public static void shutdown() {
        if (backend != null) backend.close();
        NativePitch.setEnabled(false);
        GAINS.clear();
    }

    public static boolean isEnabled() { return backend != null; }
    public static boolean isHighResolution(SongPlayer player) { return isEnabled() && player instanceof RadioSongPlayer; }

    public static void runPlayback(NoteBlockAPI plugin, SongPlayer player, Runnable playback) {
        if (isHighResolution(player)) backend.start(player, playback);
        else plugin.doAsync(playback);
    }

    public static void dispatchPlayback(NoteBlockAPI plugin, SongPlayer player, int tick, Runnable ordinaryPlayback) {
        if (isHighResolution(player)) backend.playFrame(player, tick);
        else plugin.doSync(ordinaryPlayback);
    }

    /** The supplier executes on the audio worker and must not access Bukkit state or perform IO. */
    public static void setGainSupplier(SongPlayer player, DoubleSupplier supplier) {
        GAINS.put(player, java.util.Objects.requireNonNull(supplier));
    }
    public static float gain(SongPlayer player) {
        DoubleSupplier supplier = GAINS.get(player);
        double value = supplier == null ? 1 : supplier.getAsDouble();
        return Double.isFinite(value) ? (float) Math.max(0, value) : 0;
    }
    public static void forget(SongPlayer player) { GAINS.remove(player); }

    static void withListener(Location eye, byte volume, SoundSender sender, Runnable frame) {
        CURRENT.set(new ListenerFrame(eye, volume, sender));
        try { frame.run(); }
        finally { CURRENT.remove(); }
    }
    public static Location eyeLocation(Player player) {
        ListenerFrame listener = CURRENT.get();
        return listener == null ? player.getEyeLocation() : listener.eye().clone();
    }
    public static byte playerVolume(Player player) {
        ListenerFrame listener = CURRENT.get();
        return listener == null ? NoteBlockAPI.getPlayerVolume(player) : listener.volume();
    }
    public static boolean sendSound(Location at, Object sound, SoundCategory category, float volume, float pitch, float distance) {
        ListenerFrame listener = CURRENT.get();
        if (listener == null) return false;
        listener.sender().send(at, sound, category, volume, pitch, distance);
        return true;
    }
}

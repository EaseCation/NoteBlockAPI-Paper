package com.xxmicloxx.NoteBlockAPI.playback;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.sound.Sound;
import com.github.retrooper.packetevents.protocol.sound.Sounds;
import com.github.retrooper.packetevents.protocol.sound.StaticSound;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import com.xxmicloxx.NoteBlockAPI.NoteBlockAPI;
import com.xxmicloxx.NoteBlockAPI.model.SoundCategory;
import com.xxmicloxx.NoteBlockAPI.songplayer.SongPlayer;
import com.xxmicloxx.NoteBlockAPI.utils.InstrumentUtils;
import com.xxmicloxx.NoteBlockAPI.utils.MathUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Opt-in transport for RadioSongPlayer. Bukkit state is captured on the main thread; audio workers
 * read immutable listener snapshots and send packets directly through PacketEvents/Netty.
 * Decoding, note/layer data, source tick coordinates and upstream event dispatch remain unchanged.
 */
public final class PacketRadioPlayback implements AsyncRadioPlayback.Backend, Listener {
    private record ListenerView(Player player, User user, Location eye, byte volume) { }

    private final NoteBlockAPI plugin;
    private final Map<Object, String> builtinNames = new HashMap<>();
    private final Map<String, Sound> sounds = new ConcurrentHashMap<>();
    private final Map<SongPlayer, Thread> workers = new ConcurrentHashMap<>();
    private volatile Map<UUID, ListenerView> listeners = Map.of();
    private volatile boolean running = true;
    private final BukkitTask sampler;

    public PacketRadioPlayback(NoteBlockAPI plugin) {
        this.plugin = plugin;
        if (!Bukkit.isPrimaryThread() || !Bukkit.getPluginManager().isPluginEnabled("packetevents")) {
            throw new IllegalStateException("High-resolution Radio requires PacketEvents on the main thread");
        }
        // Initialize Bukkit and packet sound registries before any worker starts.
        for (byte instrument = 0; instrument < InstrumentUtils.getCustomInstrumentFirstIndex(); instrument++) {
            Object sound = InstrumentUtils.getInstrument(instrument);
            String name = InstrumentUtils.getSoundNameByInstrument(instrument);
            builtinNames.put(sound, name);
            sounds.put(name, Sounds.getByNameOrCreate(name));
        }
        refreshListeners();
        sampler = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshListeners, 1, 1);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void start(SongPlayer player, Runnable playback) {
        Thread worker = Thread.ofVirtual().name("NoteBlockAPI-radio-" + Integer.toHexString(System.identityHashCode(player)))
            .unstarted(() -> {
                try { playback.run(); }
                finally { workers.remove(player); AsyncRadioPlayback.forget(player); }
            });
        workers.put(player, worker);
        worker.start();
    }

    /** Called with the upstream player's lock held; each frame keeps its own tick value. */
    public void playFrame(SongPlayer player, int tick) {
        if (!running || plugin.isDisabling()) return;
        for (UUID uuid : player.getPlayerUUIDs()) {
            ListenerView listener = listeners.get(uuid);
            if (listener == null) continue;
            AsyncRadioPlayback.withListener(listener.eye(), listener.volume(),
                (location, sound, category, volume, pitch, distance) -> sendSound(listener, location, sound, category, volume, pitch, distance),
                () -> player.playTick(listener.player(), tick));
        }
    }

    private void sendSound(ListenerView listener, Location location, Object sound, SoundCategory category,
                           float volume, float pitch, float distance) {
        if (!running || plugin.isDisabling() || volume <= 0 || !Float.isFinite(volume)
            || pitch <= 0 || !Float.isFinite(pitch)) return;
        String name = sound instanceof String ? (String) sound : builtinNames.get(sound);
        if (name == null) throw new IllegalArgumentException("Sound not prepared for async playback");
        Sound packetSound = sounds.computeIfAbsent(name, key -> new StaticSound(new ResourceLocation(key), null));
        Location at = MathUtils.stereoPan(location.clone(), distance);
        Vector3i position = new Vector3i((int) Math.floor(at.getX() * 8), (int) Math.floor(at.getY() * 8), (int) Math.floor(at.getZ() * 8));
        var packetCategory = com.github.retrooper.packetevents.protocol.sound.SoundCategory.fromId(category.ordinal());
        listener.user().sendPacket(new WrapperPlayServerSoundEffect(packetSound, packetCategory, position, volume, pitch,
            ThreadLocalRandom.current().nextLong()));
    }

    private void refreshListeners() {
        Map<UUID, ListenerView> next = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user != null && player.isOnline()) next.put(player.getUniqueId(),
                new ListenerView(player, user, player.getEyeLocation(), NoteBlockAPI.getPlayerVolume(player)));
        }
        listeners = Map.copyOf(next);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onQuit(PlayerQuitEvent event) {
        ListenerView listener = listeners.get(event.getPlayer().getUniqueId());
        if (listener == null || listener.player() != event.getPlayer()) return;
        Map<UUID, ListenerView> next = new HashMap<>(listeners);
        next.remove(event.getPlayer().getUniqueId());
        listeners = Map.copyOf(next);
    }

    @Override
    public void close() {
        running = false;
        listeners = Map.of();
        sampler.cancel();
        HandlerList.unregisterAll(this);
        workers.values().forEach(Thread::interrupt);
        workers.keySet().forEach(AsyncRadioPlayback::forget);
    }
}

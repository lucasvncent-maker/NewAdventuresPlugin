package fr.loual.newadventure.commands;

import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import net.kyori.adventure.title.Title;

/**
 * Commande /chlorine pour jouer "Chlorine" de Twenty One Pilots en jeu.
 * - Séquenceur NoteBlock polyphonique ultra-réaliste :
 *     * Voix de chant réaliste (FLUTE + PLING + GUITAR) reproduisant les intonations de Tyler Joseph.
 *     * Vraie walking bassline de basse électrique (BASS).
 *     * Guitare funk en contre-temps (GUITAR + HARP).
 *     * Batterie complète de Josh Dun (BASEDRUM, SNARE avec ghost notes, HAT accentués, rimshots IRON_XYLOPHONE).
 *     * Synthétiseur vintage (BIT + CHIME).
 * - Support direct du fichier studio "music.chlorine" (ogg streamé) via le pack de ressources.
 * - Paroles synchronisées dans l'ActionBar et particules de musique.
 */
public class ChlorineCommand implements CommandExecutor, TabCompleter, Listener {

    private final NewAdventurePlugin plugin;
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();

    private final List<NoteEvent> songNotes = new ArrayList<>();
    private final Map<Integer, Component> lyrics = new HashMap<>();
    private static final int TOTAL_STEPS = 480; // 480 steps * 3 ticks = 1440 ticks (~72s)
    private static final int STEP_TICKS = 3;

    public record NoteEvent(int step, Sound sound, float pitch, float volume) {}

    public ChlorineCommand(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        initSongData();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Cette commande est réservée aux joueurs.", NamedTextColor.RED));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("stop")) {
            if (stopMusic(player)) {
                player.sendMessage(Component.text("[♫] ", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .append(Component.text("Musique arrêtée.", NamedTextColor.GRAY)));
            } else {
                player.sendMessage(Component.text("[♫] ", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .append(Component.text("Aucune musique n'est en cours. Tapez ", NamedTextColor.RED))
                        .append(Component.text("/chlorine", NamedTextColor.YELLOW))
                        .append(Component.text(" pour lancer Chlorine !", NamedTextColor.RED)));
            }
            return true;
        }

        boolean askedStudio = false;
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("studio") || args[0].equalsIgnoreCase("real") || args[0].equalsIgnoreCase("original")) {
                askedStudio = true;
                player.sendMessage(Component.text("[♫] ", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .append(Component.text("Note : La version studio requiert un pack de ressources avec ", NamedTextColor.GRAY))
                        .append(Component.text("chlorine.ogg", NamedTextColor.YELLOW))
                        .append(Component.text(". La version NoteBlock intégrée est lancée en accompagnement !", NamedTextColor.GRAY)));
            }
        }

        // Si déjà en train de jouer, relance depuis le début
        stopMusic(player);
        startMusic(player, true, askedStudio);
        return true;
    }

    private void startMusic(Player player, boolean playNoteBlocks, boolean playStudioAudio) {
        player.showTitle(Title.title(
                Component.text("CHLORINE", NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text("Twenty One Pilots ♫", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2250), Duration.ofMillis(500))
        ));
        player.sendMessage(
                Component.text("------------------------------------------------", NamedTextColor.DARK_AQUA)
                        .append(Component.newline())
                        .append(Component.text(" ♫ Lecture de ", NamedTextColor.WHITE))
                        .append(Component.text("Twenty One Pilots - Chlorine", NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(" ♫", NamedTextColor.WHITE))
                        .append(Component.newline())
                        .append(Component.text(" Tapez ", NamedTextColor.GRAY))
                        .append(Component.text("/chlorine stop", NamedTextColor.YELLOW, TextDecoration.UNDERLINED))
                        .append(Component.text(" pour arrêter la musique à tout moment.", NamedTextColor.GRAY))
                        .append(Component.newline())
                        .append(Component.text("------------------------------------------------", NamedTextColor.DARK_AQUA))
        );

        // Déclencher le son audio studio sous toutes ses déclinaisons
        if (playStudioAudio) {
            Location loc = player.getLocation();
            String[] soundKeys = {
                    "music.chlorine", "minecraft:music.chlorine",
                    "custom.chlorine", "minecraft:custom.chlorine",
                    "chlorine", "minecraft:chlorine"
            };
            for (String key : soundKeys) {
                try {
                    player.playSound(loc, key, SoundCategory.RECORDS, 1.0f, 1.0f);
                } catch (Exception ignored) {}
            }
        }

        // Indexation par step pour accès O(1)
        Map<Integer, List<NoteEvent>> noteMap = new HashMap<>();
        if (playNoteBlocks) {
            for (NoteEvent ne : songNotes) {
                noteMap.computeIfAbsent(ne.step(), k -> new ArrayList<>()).add(ne);
            }
        }

        BukkitTask task = new BukkitRunnable() {
            int currentStep = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    activeTasks.remove(player.getUniqueId());
                    return;
                }

                if (currentStep >= TOTAL_STEPS) {
                    player.sendMessage(Component.text("[♫] ", NamedTextColor.AQUA, TextDecoration.BOLD)
                            .append(Component.text("Fin de ", NamedTextColor.WHITE))
                            .append(Component.text("Twenty One Pilots - Chlorine", NamedTextColor.AQUA, TextDecoration.BOLD))
                            .append(Component.text(". Tapez ", NamedTextColor.WHITE))
                            .append(Component.text("/chlorine", NamedTextColor.YELLOW))
                            .append(Component.text(" pour réécouter !", NamedTextColor.WHITE)));
                    activeTasks.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                // Jouer les notes de cet instant si noteblocks activés
                if (playNoteBlocks) {
                    List<NoteEvent> stepNotes = noteMap.get(currentStep);
                    if (stepNotes != null) {
                        Location loc = player.getLocation();
                        for (NoteEvent note : stepNotes) {
                            player.playSound(loc, note.sound(), SoundCategory.RECORDS, note.volume(), note.pitch());
                        }
                    }
                }

                // Paroles synchronisées
                Component lyric = lyrics.get(currentStep);
                if (lyric != null) {
                    player.sendActionBar(lyric);
                }

                // Particules de notes
                if (currentStep % 4 == 0) {
                    Location noteLoc = player.getLocation().add(0, 1.8, 0);
                    double noteColor = (currentStep % 24) / 24.0;
                    player.getWorld().spawnParticle(Particle.NOTE, noteLoc.getX(), noteLoc.getY(), noteLoc.getZ(), 0, noteColor, 0, 0, 1);
                }

                currentStep++;
            }
        }.runTaskTimer(plugin, 0L, STEP_TICKS);

        activeTasks.put(player.getUniqueId(), task);
    }

    public boolean stopMusic(Player player) {
        BukkitTask task = activeTasks.remove(player.getUniqueId());
        boolean stopped = false;
        if (task != null) {
            task.cancel();
            stopped = true;
        }
        String[] soundKeys = {
                "music.chlorine", "minecraft:music.chlorine",
                "custom.chlorine", "minecraft:custom.chlorine",
                "chlorine", "minecraft:chlorine"
        };
        for (String key : soundKeys) {
            try {
                player.stopSound(key, SoundCategory.RECORDS);
            } catch (Exception ignored) {}
        }
        return stopped;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        stopMusic(event.getPlayer());
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> sub = List.of("play", "studio", "noteblock", "stop");
            List<String> res = new ArrayList<>();
            for (String s : sub) {
                if (s.startsWith(args[0].toLowerCase())) {
                    res.add(s);
                }
            }
            return res;
        }
        return Collections.emptyList();
    }

    // ==========================================
    // Orchestration & Séquençage musical
    // ==========================================

    private static float pitch(int semitone) {
        int clamped = Math.max(0, Math.min(24, semitone));
        return (float) Math.pow(2.0, (clamped - 12) / 12.0);
    }

    private void addNote(int step, Sound sound, int semitone, float volume) {
        songNotes.add(new NoteEvent(step, sound, pitch(semitone), volume));
    }

    /**
     * Voix de chant réaliste (Mélodie de chant de Tyler Joseph) :
     * Flûte douce pour le souffle vocal + Pling net pour l'attaque des consonnes + Guitare pour la résonance de poitrine.
     */
    private void addVocal(int step, int semitone, float volume) {
        addNote(step, Sound.BLOCK_NOTE_BLOCK_FLUTE, semitone, volume * 1.0f);
        addNote(step, Sound.BLOCK_NOTE_BLOCK_PLING, semitone, volume * 0.85f);
        if (semitone >= 12) {
            addNote(step, Sound.BLOCK_NOTE_BLOCK_GUITAR, semitone - 12, volume * 0.45f);
        } else {
            addNote(step, Sound.BLOCK_NOTE_BLOCK_GUITAR, semitone, volume * 0.4f);
        }
    }

    /**
     * Basse ambulante (Walking Bass) réaliste et syncopée de Chlorine.
     */
    private void addWalkingBass(int start, int root, int third, int fifth, int octave) {
        addNote(start + 0, Sound.BLOCK_NOTE_BLOCK_BASS, root, 1.0f);
        addNote(start + 4, Sound.BLOCK_NOTE_BLOCK_BASS, third, 0.85f);
        addNote(start + 6, Sound.BLOCK_NOTE_BLOCK_BASS, fifth, 0.8f);
        addNote(start + 8, Sound.BLOCK_NOTE_BLOCK_BASS, (octave <= 24 ? octave : root), 0.95f);
        addNote(start + 12, Sound.BLOCK_NOTE_BLOCK_BASS, fifth, 0.8f);
        addNote(start + 14, Sound.BLOCK_NOTE_BLOCK_BASS, third, 0.75f);
    }

    /**
     * Batterie complète (Kick, Snare, Hi-hats avec variations d'ouverture, Rimshot).
     */
    private void addDrums(int start, boolean fill) {
        // Kick punchy (Grosse caisse)
        addNote(start + 0, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 6, 1.0f);
        addNote(start + 6, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 6, 0.75f);
        addNote(start + 8, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 6, 0.95f);
        addNote(start + 10, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 6, 0.6f);

        // Caisse claire (Snare) + Rimshot (Iron Xylophone) sur 2 et 4
        addNote(start + 4, Sound.BLOCK_NOTE_BLOCK_SNARE, 12, 1.0f);
        addNote(start + 4, Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 18, 0.5f);
        addNote(start + 12, Sound.BLOCK_NOTE_BLOCK_SNARE, 12, 1.0f);
        addNote(start + 12, Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 18, 0.5f);

        // Ghost note caisse claire
        addNote(start + 10, Sound.BLOCK_NOTE_BLOCK_SNARE, 10, 0.35f);

        // Hi-hats (Charleston) avec accents en contre-temps
        for (int s = 0; s < 16; s += 2) {
            int p = (s == 2 || s == 6 || s == 10 || s == 14) ? 20 : 16;
            float vol = (s == 2 || s == 6 || s == 10 || s == 14) ? 0.65f : 0.4f;
            addNote(start + s, Sound.BLOCK_NOTE_BLOCK_HAT, p, vol);
        }

        if (fill) {
            addNote(start + 13, Sound.BLOCK_NOTE_BLOCK_SNARE, 14, 0.75f);
            addNote(start + 14, Sound.BLOCK_NOTE_BLOCK_SNARE, 16, 0.9f);
            addNote(start + 15, Sound.BLOCK_NOTE_BLOCK_SNARE, 18, 1.0f);
        }
    }

    /**
     * Accords de guitare / Rhodes en contre-temps (chops funk).
     */
    private void addGuitarChords(int start, int root, int third, int fifth) {
        int[] chops = {2, 6, 10, 14};
        for (int s : chops) {
            addNote(start + s, Sound.BLOCK_NOTE_BLOCK_GUITAR, root, 0.5f);
            addNote(start + s, Sound.BLOCK_NOTE_BLOCK_GUITAR, third, 0.5f);
            addNote(start + s, Sound.BLOCK_NOTE_BLOCK_HARP, fifth, 0.4f);
        }
        // Nappes douces sur les temps
        addNote(start + 0, Sound.BLOCK_NOTE_BLOCK_HARP, root, 0.45f);
        addNote(start + 0, Sound.BLOCK_NOTE_BLOCK_HARP, third, 0.45f);
        addNote(start + 0, Sound.BLOCK_NOTE_BLOCK_HARP, fifth, 0.45f);
        addNote(start + 8, Sound.BLOCK_NOTE_BLOCK_HARP, root, 0.4f);
        addNote(start + 8, Sound.BLOCK_NOTE_BLOCK_HARP, third, 0.4f);
    }

    private void initSongData() {
        // Tonalité : Si bémol mineur (Bb minor) / Ré bémol majeur (Db major)
        // Gamme : Bb3 = 4, C4 = 6, Db4 = 7, Eb4 = 9, F4 = 11, Gb4 = 12, Ab4 = 14
        //         Bb4 = 16, C5 = 18, Db5 = 19, Eb5 = 21, F5 = 23, Gb5 = 24
        final int Bb3 = 4, C4 = 6, Db4 = 7, Eb4 = 9, F4 = 11, Gb4 = 12, Ab4 = 14;
        final int Bb4 = 16, C5 = 18, Db5 = 19, Eb5 = 21, F5 = 23;

        // -------------------------------------------------------------------
        // SECTION 1 : INTRO (Mesures 0 à 3, steps 0 à 63)
        // -------------------------------------------------------------------
        lyrics.put(0, Component.text("♫ Twenty One Pilots - Chlorine (Intro) ♫", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));

        // Mesure 0 : Bbm arpège au piano doux
        addGuitarChords(0, Bb3, Db4, F4);
        addNote(0, Sound.BLOCK_NOTE_BLOCK_HARP, Bb4, 0.75f);
        addNote(3, Sound.BLOCK_NOTE_BLOCK_HARP, Db5, 0.75f);
        addNote(6, Sound.BLOCK_NOTE_BLOCK_HARP, F5, 0.85f);
        addNote(9, Sound.BLOCK_NOTE_BLOCK_HARP, Db5, 0.75f);
        addNote(12, Sound.BLOCK_NOTE_BLOCK_HARP, C5, 0.7f);
        addNote(14, Sound.BLOCK_NOTE_BLOCK_HARP, Bb4, 0.7f);

        // Mesure 1 : Gb arpège
        addGuitarChords(16, Gb4, Bb4, Db5);
        addNote(16, Sound.BLOCK_NOTE_BLOCK_HARP, Gb4, 0.75f);
        addNote(19, Sound.BLOCK_NOTE_BLOCK_HARP, Bb4, 0.75f);
        addNote(22, Sound.BLOCK_NOTE_BLOCK_HARP, Db5, 0.85f);
        addNote(25, Sound.BLOCK_NOTE_BLOCK_HARP, Bb4, 0.75f);
        addNote(28, Sound.BLOCK_NOTE_BLOCK_HARP, Ab4, 0.7f);
        addNote(30, Sound.BLOCK_NOTE_BLOCK_HARP, Gb4, 0.7f);

        // Mesure 2 : Db + Entrée de la Walking Bass
        lyrics.put(32, Component.text("♫ (Groovy Walking Bass) ♫", NamedTextColor.GRAY));
        addGuitarChords(32, Db4, F4, Ab4);
        addWalkingBass(32, Db4, F4, Ab4, Db5);
        addNote(32, Sound.BLOCK_NOTE_BLOCK_HARP, F4, 0.75f);
        addNote(35, Sound.BLOCK_NOTE_BLOCK_HARP, Ab4, 0.75f);
        addNote(38, Sound.BLOCK_NOTE_BLOCK_HARP, Db5, 0.85f);
        addNote(41, Sound.BLOCK_NOTE_BLOCK_HARP, Ab4, 0.75f);
        addNote(44, Sound.BLOCK_NOTE_BLOCK_HARP, Gb4, 0.7f);
        addNote(46, Sound.BLOCK_NOTE_BLOCK_HARP, F4, 0.7f);

        // Mesure 3 : Ab + Hi-hats d'ambiance
        addGuitarChords(48, Ab4, C5, Eb5);
        addWalkingBass(48, Ab4, C5, Eb5, 24);
        for (int s = 48; s < 64; s += 2) {
            addNote(s, Sound.BLOCK_NOTE_BLOCK_HAT, 18, 0.55f);
        }
        addNote(48, Sound.BLOCK_NOTE_BLOCK_HARP, Eb4, 0.75f);
        addNote(51, Sound.BLOCK_NOTE_BLOCK_HARP, Ab4, 0.75f);
        addNote(54, Sound.BLOCK_NOTE_BLOCK_HARP, C5, 0.85f);
        addNote(57, Sound.BLOCK_NOTE_BLOCK_HARP, Bb4, 0.75f);
        addNote(60, Sound.BLOCK_NOTE_BLOCK_HARP, Ab4, 0.7f);
        addNote(62, Sound.BLOCK_NOTE_BLOCK_HARP, F4, 0.7f);

        // -------------------------------------------------------------------
        // SECTION 2 : COUPLET 1 (Mesures 4 à 7, steps 64 à 127)
        // -------------------------------------------------------------------
        // Rythme complet : Walking Bass + Guitare funk + Batterie
        addWalkingBass(64, Bb3, Db4, F4, Bb4);
        addGuitarChords(64, Bb3, Db4, F4);
        addDrums(64, false);

        addWalkingBass(80, Gb4, Bb4, Db5, Gb4);
        addGuitarChords(80, Gb4, Bb4, Db5);
        addDrums(80, false);

        addWalkingBass(96, Db4, F4, Ab4, Db5);
        addGuitarChords(96, Db4, F4, Ab4);
        addDrums(96, false);

        addWalkingBass(112, Ab4, C5, Eb5, 24);
        addGuitarChords(112, Ab4, C5, Eb5);
        addDrums(112, true);

        // Mélodie de chant réaliste (Tyler Joseph)
        lyrics.put(64, Component.text("♫ So where are you? It's been a little while... ♫", NamedTextColor.YELLOW));
        // "So where are you?" (rythme syncopé)
        addVocal(64, Bb3, 0.8f);
        addVocal(66, Db4, 0.85f);
        addVocal(68, F4, 1.0f);
        addVocal(72, Eb4, 0.9f);
        addVocal(74, Db4, 0.85f);
        // "It's been a little while"
        addVocal(78, Db4, 0.85f);
        addVocal(80, Eb4, 0.9f);
        addVocal(82, F4, 1.0f);
        addVocal(84, Eb4, 0.9f);
        addVocal(86, Db4, 0.85f);
        addVocal(88, Bb3, 0.8f);

        lyrics.put(96, Component.text("♫ That's how it goes, I guess... ♫", NamedTextColor.YELLOW));
        // "That's how it goes, I guess"
        addVocal(96, Db4, 0.85f);
        addVocal(98, Eb4, 0.9f);
        addVocal(100, F4, 1.0f);
        addVocal(102, F4, 0.95f);
        addVocal(104, Eb4, 0.9f);
        addVocal(106, Db4, 0.85f);

        // "Fuck it, now you're tripping on my wires"
        lyrics.put(112, Component.text("♫ Now you're tripping on my wires... ♫", NamedTextColor.YELLOW));
        addVocal(112, Db4, 0.85f);
        addVocal(114, F4, 0.95f);
        addVocal(116, F4, 1.0f);
        addVocal(118, F4, 0.95f);
        addVocal(120, Eb4, 0.9f);
        addVocal(122, Db4, 0.85f);
        addVocal(124, C4, 0.8f);
        addVocal(126, Bb3, 0.8f);

        // -------------------------------------------------------------------
        // SECTION 3 : PRÉ-REFRAIN (Mesures 8 à 11, steps 128 à 191)
        // -------------------------------------------------------------------
        addWalkingBass(128, Bb3, Db4, F4, Bb4);
        addGuitarChords(128, Bb3, Db4, F4);
        addDrums(128, false);

        addWalkingBass(144, Gb4, Bb4, Db5, Gb4);
        addGuitarChords(144, Gb4, Bb4, Db5);
        addDrums(144, false);

        addWalkingBass(160, Db4, F4, Ab4, Db5);
        addGuitarChords(160, Db4, F4, Ab4);
        addDrums(160, false);

        // Bar 11 : Montée en puissance
        addWalkingBass(176, Ab4, C5, Eb5, 24);
        addGuitarChords(176, Ab4, C5, Eb5);

        lyrics.put(128, Component.text("♫ Fallin' out of line, oh, trippin' on my wires... ♫", NamedTextColor.GOLD));
        // "Fallin' out of line"
        addVocal(128, Db5, 1.0f);
        addVocal(131, C5, 0.95f);
        addVocal(134, Bb4, 0.9f);
        addVocal(136, Ab4, 0.85f);
        addVocal(138, Bb4, 0.9f);

        // "Oh, trippin' on my wires"
        addVocal(144, Db5, 1.0f);
        addVocal(148, Db5, 1.0f);
        addVocal(150, C5, 0.95f);
        addVocal(152, Bb4, 0.9f);
        addVocal(154, Ab4, 0.85f);
        addVocal(156, Bb4, 0.9f);

        lyrics.put(160, Component.text("♫ Now you're runnin' out of time... ♫", NamedTextColor.GOLD));
        // "Now you're runnin' out of time"
        addVocal(160, Db5, 1.0f);
        addVocal(162, Db5, 1.0f);
        addVocal(164, Eb5, 1.0f);
        addVocal(166, F5, 1.05f);
        addVocal(168, Eb5, 1.0f);
        addVocal(170, Db5, 0.95f);
        addVocal(172, Bb4, 0.9f);

        // Roulement de batterie réaliste vers le refrain
        addNote(180, Sound.BLOCK_NOTE_BLOCK_SNARE, 12, 0.7f);
        addNote(182, Sound.BLOCK_NOTE_BLOCK_SNARE, 12, 0.8f);
        addNote(184, Sound.BLOCK_NOTE_BLOCK_SNARE, 14, 0.85f);
        addNote(186, Sound.BLOCK_NOTE_BLOCK_SNARE, 14, 0.9f);
        addNote(188, Sound.BLOCK_NOTE_BLOCK_SNARE, 16, 0.95f);
        addNote(189, Sound.BLOCK_NOTE_BLOCK_SNARE, 17, 1.0f);
        addNote(190, Sound.BLOCK_NOTE_BLOCK_SNARE, 18, 1.0f);
        addNote(191, Sound.BLOCK_NOTE_BLOCK_SNARE, 20, 1.0f);
        addNote(191, Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 22, 0.9f);

        // -------------------------------------------------------------------
        // SECTION 4 : LE REFRAIN ICONIQUE (Mesures 12 à 19, steps 192 à 319)
        // -------------------------------------------------------------------
        for (int b = 12; b <= 19; b++) {
            int step = b * 16;
            int idx = (b - 12) % 4;
            int r = idx == 0 ? Bb3 : (idx == 1 ? Gb4 : (idx == 2 ? Db4 : Ab4));
            int t = idx == 0 ? Db4 : (idx == 1 ? Bb4 : (idx == 2 ? F4 : C5));
            int f = idx == 0 ? F4 : (idx == 1 ? Db5 : (idx == 2 ? Ab4 : Eb5));
            int o = idx == 0 ? Bb4 : (idx == 1 ? Gb4 : (idx == 2 ? Db5 : 24));

            addWalkingBass(step, r, t, f, o);
            addGuitarChords(step, r, t, f);
            addDrums(step, (b == 15 || b == 19));

            // Couche de synthétiseur riche (Bit) sur le refrain
            addNote(step + 0, Sound.BLOCK_NOTE_BLOCK_BIT, r, 0.45f);
            addNote(step + 4, Sound.BLOCK_NOTE_BLOCK_BIT, t, 0.45f);
            addNote(step + 8, Sound.BLOCK_NOTE_BLOCK_BIT, f, 0.45f);
        }

        // --- Refrain Partie 1 ---
        lyrics.put(192, Component.text("♫ Sippin' on straight chlorine, let the vibes slide over me ♫", NamedTextColor.AQUA, TextDecoration.BOLD));
        // "Sip-pin' on straight chlo-rine"
        addVocal(192, Db5, 1.05f);
        addVocal(194, Db5, 1.05f);
        addVocal(196, Db5, 1.05f);
        addVocal(198, C5, 1.0f);
        addVocal(200, Bb4, 1.05f);
        addVocal(204, F4, 0.85f);
        addVocal(206, Bb4, 0.95f);

        // "Let the vibes slide over me"
        addVocal(208, Db5, 1.05f);
        addVocal(210, Db5, 1.05f);
        addVocal(212, Db5, 1.05f);
        addVocal(214, Eb5, 1.05f);
        addVocal(216, F5, 1.1f);
        addVocal(218, Eb5, 1.05f);
        addVocal(220, Db5, 1.0f);
        addVocal(222, Bb4, 0.9f);

        lyrics.put(224, Component.text("♫ This beat is a chemical, beat is a chemical ♫", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        // "This beat is a che-mi-cal"
        addVocal(224, F5, 1.1f);
        addVocal(226, F5, 1.1f);
        addVocal(228, F5, 1.05f);
        addVocal(230, Eb5, 1.0f);
        addVocal(232, Db5, 1.0f);
        addVocal(234, Bb4, 0.95f);
        addVocal(236, Bb4, 0.95f);

        // "Beat is a che-mi-cal"
        addVocal(240, F5, 1.1f);
        addVocal(242, F5, 1.1f);
        addVocal(244, Eb5, 1.0f);
        addVocal(246, Db5, 1.0f);
        addVocal(248, Bb4, 0.95f);
        addVocal(250, Bb4, 0.95f);

        // --- Refrain Partie 2 ---
        lyrics.put(256, Component.text("♫ When I leave don't save my seat, I'll be back when it's all complete ♫", NamedTextColor.AQUA, TextDecoration.BOLD));
        // "When I leave don't save my seat"
        addVocal(256, Db5, 1.05f);
        addVocal(258, Db5, 1.05f);
        addVocal(260, Db5, 1.05f);
        addVocal(262, C5, 1.0f);
        addVocal(264, Bb4, 1.05f);
        addVocal(268, F4, 0.85f);
        addVocal(270, Bb4, 0.95f);

        // "I'll be back when it's all com-plete"
        addVocal(272, Db5, 1.05f);
        addVocal(274, Db5, 1.05f);
        addVocal(276, Db5, 1.05f);
        addVocal(278, Eb5, 1.05f);
        addVocal(280, F5, 1.1f);
        addVocal(282, Eb5, 1.05f);
        addVocal(284, Db5, 1.0f);
        addVocal(286, Db5, 1.0f);

        lyrics.put(288, Component.text("♫ The moment is medical, moment is medical ♫", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        // "The mo-ment is me-di-cal"
        addVocal(288, F5, 1.1f);
        addVocal(290, F5, 1.1f);
        addVocal(292, F5, 1.05f);
        addVocal(294, Eb5, 1.0f);
        addVocal(296, Db5, 1.0f);
        addVocal(298, Bb4, 0.95f);
        addVocal(300, Bb4, 0.95f);

        // "Mo-ment is me-di-cal"
        addVocal(304, F5, 1.1f);
        addVocal(306, F5, 1.1f);
        addVocal(308, Eb5, 1.0f);
        addVocal(310, Db5, 1.0f);
        addVocal(312, Bb4, 0.95f);
        addVocal(314, Bb4, 0.95f);

        // "Sip-pin' on straight chlo-rine..."
        lyrics.put(316, Component.text("♫ Sippin' on straight chlorine... ♫", NamedTextColor.AQUA, TextDecoration.BOLD));
        addVocal(316, Db5, 1.05f);
        addVocal(318, C5, 1.0f);

        // -------------------------------------------------------------------
        // SECTION 5 : POST-REFRAIN / SYNTH RIFF (Mesures 20 à 23, steps 320 à 383)
        // -------------------------------------------------------------------
        lyrics.put(320, Component.text("♫ (Electro Synth Solo & Walking Bass) ♫", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        addWalkingBass(320, Bb3, Db4, F4, Bb4);
        addGuitarChords(320, Bb3, Db4, F4);
        addDrums(320, false);

        addWalkingBass(336, Gb4, Bb4, Db5, Gb4);
        addGuitarChords(336, Gb4, Bb4, Db5);
        addDrums(336, false);

        addWalkingBass(352, Db4, F4, Ab4, Db5);
        addGuitarChords(352, Db4, F4, Ab4);
        addDrums(352, false);

        addWalkingBass(368, Ab4, C5, Eb5, 24);
        addGuitarChords(368, Ab4, C5, Eb5);
        addDrums(368, true);

        // Lead synth bondissant (Bit + Chime)
        int[] riffSteps = {320, 324, 326, 328, 330, 332, 334,
                           336, 338, 340, 342, 344, 346, 348, 350,
                           352, 354, 356, 358, 360, 362, 364,
                           368, 370, 372, 374, 376, 380, 382};
        int[] riffNotes = {Bb4, Db5, F5,  Db5, Bb4, Ab4, Bb4,
                           Gb4, Bb4, Db5, Eb5, F5,  Eb5, Db5, Bb4,
                           Db5, F5,  Ab4, Db5, F5,  Eb5, Db5,
                           C5,  Eb5, Db5, C5,  Bb4, Ab4, F4};

        for (int i = 0; i < riffSteps.length; i++) {
            addNote(riffSteps[i], Sound.BLOCK_NOTE_BLOCK_BIT, riffNotes[i], 0.85f);
            addNote(riffSteps[i], Sound.BLOCK_NOTE_BLOCK_CHIME, riffNotes[i], 0.75f);
        }

        // -------------------------------------------------------------------
        // SECTION 6 : OUTRO / NED'S BALLAD (Mesures 24 à 29, steps 384 à 479)
        // -------------------------------------------------------------------
        // Ambiance piano mélancolique (Tyler au piano / Ned)
        lyrics.put(384, Component.text("♫ Can you build my heart with pieces? ♫", NamedTextColor.WHITE, TextDecoration.BOLD));
        addGuitarChords(384, Bb3, Db4, F4);
        addGuitarChords(400, Gb4, Bb4, Db5);
        addGuitarChords(416, Db4, F4, Ab4);
        addGuitarChords(432, Ab4, C5, Eb5);
        addGuitarChords(448, Bb3, Db4, F4);

        // Basse douce
        addNote(384, Sound.BLOCK_NOTE_BLOCK_BASS, Bb3, 0.8f);
        addNote(400, Sound.BLOCK_NOTE_BLOCK_BASS, Gb4, 0.8f);
        addNote(416, Sound.BLOCK_NOTE_BLOCK_BASS, Db4, 0.8f);
        addNote(432, Sound.BLOCK_NOTE_BLOCK_BASS, Ab4, 0.8f);
        addNote(448, Sound.BLOCK_NOTE_BLOCK_BASS, Bb3, 0.85f);

        // "Can you build my heart with pieces?"
        addVocal(388, F4, 0.95f);
        addVocal(391, Eb4, 0.95f);
        addVocal(394, Db4, 0.95f);
        addVocal(397, Bb3, 0.9f);
        addVocal(400, Db4, 0.95f);
        addVocal(403, F4, 1.0f);
        addVocal(406, Eb4, 0.95f);
        addVocal(409, Db4, 0.9f);

        lyrics.put(416, Component.text("♫ I am just a chemical... ♫", NamedTextColor.GRAY, TextDecoration.BOLD));
        // "I am just a chemical..."
        addVocal(418, Db4, 0.95f);
        addVocal(421, C4, 0.9f);
        addVocal(424, Bb3, 0.9f);
        addVocal(427, F4, 0.85f);
        addVocal(430, Bb3, 0.9f);
        addVocal(433, C4, 0.9f);
        addVocal(436, Bb3, 0.95f);

        lyrics.put(448, Component.text("♫ Sippin' on straight chlorine... ♫", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        // "Sippin' on straight chlorine..."
        addVocal(448, Db4, 0.9f);
        addVocal(451, C4, 0.85f);
        addVocal(454, Bb3, 0.9f);
        addVocal(458, F4, 0.8f);
        addVocal(462, Bb3, 0.95f);

        // Accord final Bbm majestueux avec carillon
        addNote(470, Sound.BLOCK_NOTE_BLOCK_HARP, Bb3, 1.0f);
        addNote(470, Sound.BLOCK_NOTE_BLOCK_HARP, Db4, 1.0f);
        addNote(470, Sound.BLOCK_NOTE_BLOCK_HARP, F4, 1.0f);
        addNote(470, Sound.BLOCK_NOTE_BLOCK_HARP, Bb4, 1.0f);
        addNote(470, Sound.BLOCK_NOTE_BLOCK_BELL, Bb4, 0.85f);
        addNote(470, Sound.BLOCK_NOTE_BLOCK_CHIME, F4, 0.8f);
        addNote(470, Sound.BLOCK_NOTE_BLOCK_GUITAR, Bb3, 0.7f);
    }
}

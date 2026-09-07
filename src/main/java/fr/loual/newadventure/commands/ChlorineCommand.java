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

import java.util.*;

/**
 * Commande /chlorine pour jouer "Chlorine" de Twenty One Pilots en jeu.
 * - Séquenceur NoteBlock polyphonique fidèle (intro, basse, couplet, pré-refrain, refrain, pont synthé, outro).
 * - Déclenchement simultané de la source sonore personnalisée "music.chlorine" / "custom.chlorine" si pack de texture.
 * - Paroles synchronisées dans l'ActionBar.
 * - Particules de notes de musique autour du joueur.
 * - Gestion du /chlorine stop et nettoyage à la déconnexion.
 */
public class ChlorineCommand implements CommandExecutor, TabCompleter, Listener {

    private final NewAdventurePlugin plugin;
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();

    // Événements de notes et paroles pré-calculés
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

        // Si déjà en train de jouer, relance depuis le début
        stopMusic(player);
        startMusic(player);
        return true;
    }

    private void startMusic(Player player) {
        player.sendTitle("§b§lCHLORINE", "§7Twenty One Pilots ♫", 10, 45, 10);
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

        // Déclencher le son ressource pack au cas où un ogg est configuré
        try {
            player.playSound(player.getLocation(), "music.chlorine", SoundCategory.RECORDS, 1.0f, 1.0f);
            player.playSound(player.getLocation(), "custom.chlorine", SoundCategory.RECORDS, 1.0f, 1.0f);
        } catch (Exception ignored) {}

        // Organiser les notes par step pour un lookup O(1) à chaque tick
        Map<Integer, List<NoteEvent>> noteMap = new HashMap<>();
        for (NoteEvent ne : songNotes) {
            noteMap.computeIfAbsent(ne.step(), k -> new ArrayList<>()).add(ne);
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

                // Jouer les notes du step
                List<NoteEvent> stepNotes = noteMap.get(currentStep);
                if (stepNotes != null) {
                    Location loc = player.getLocation();
                    for (NoteEvent note : stepNotes) {
                        player.playSound(loc, note.sound(), SoundCategory.RECORDS, note.volume(), note.pitch());
                    }
                }

                // Afficher les paroles si présentes pour ce step
                Component lyric = lyrics.get(currentStep);
                if (lyric != null) {
                    player.sendActionBar(lyric);
                }

                // Particules de notes de musique
                if (currentStep % 4 == 0) {
                    Location noteLoc = player.getLocation().add(0, 1.8, 0);
                    // Décalage pour couleur aléatoire de note Minecraft
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
        try {
            player.stopSound("music.chlorine", SoundCategory.RECORDS);
            player.stopSound("custom.chlorine", SoundCategory.RECORDS);
        } catch (Exception ignored) {}
        return stopped;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        stopMusic(event.getPlayer());
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> sub = List.of("play", "stop");
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
    // Séquençage musical de Chlorine
    // ==========================================

    private static float pitch(int semitone) {
        // Formule standard Minecraft NoteBlock : 0 = F#3 (0.5), 12 = F#4 (1.0), 24 = F#5 (2.0)
        int clamped = Math.max(0, Math.min(24, semitone));
        return (float) Math.pow(2.0, (clamped - 12) / 12.0);
    }

    private void addNote(int step, Sound sound, int semitone, float volume) {
        songNotes.add(new NoteEvent(step, sound, pitch(semitone), volume));
    }

    private void addMelody(int step, int semitone) {
        // Voix principale : Harp + Bit synthétique pour le son électro/pop
        addNote(step, Sound.BLOCK_NOTE_BLOCK_HARP, semitone, 1.0f);
        addNote(step, Sound.BLOCK_NOTE_BLOCK_BIT, semitone, 0.6f);
    }

    private void addRhythmBar(int barIndex, int root, int third, int fifth, boolean drums, boolean bass, boolean chords) {
        int start = barIndex * 16;

        // Basse rythmée caractéristique de Chlorine
        if (bass) {
            addNote(start + 0, Sound.BLOCK_NOTE_BLOCK_BASS, root, 1.0f);
            addNote(start + 4, Sound.BLOCK_NOTE_BLOCK_BASS, root, 0.8f);
            addNote(start + 6, Sound.BLOCK_NOTE_BLOCK_BASS, (root + 12 <= 24 ? root + 12 : root), 0.7f);
            addNote(start + 8, Sound.BLOCK_NOTE_BLOCK_BASS, root, 0.9f);
            addNote(start + 12, Sound.BLOCK_NOTE_BLOCK_BASS, third, 0.8f);
            addNote(start + 14, Sound.BLOCK_NOTE_BLOCK_BASS, fifth, 0.7f);
        }

        // Batterie : Kick, Snare, Hi-hats
        if (drums) {
            // Kick sur 1, 2.5 et 3
            addNote(start + 0, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 6, 1.0f);
            addNote(start + 6, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 6, 0.7f);
            addNote(start + 8, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 6, 0.9f);
            addNote(start + 10, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 6, 0.5f);

            // Snare sur 2 et 4
            addNote(start + 4, Sound.BLOCK_NOTE_BLOCK_SNARE, 12, 1.0f);
            addNote(start + 12, Sound.BLOCK_NOTE_BLOCK_SNARE, 12, 1.0f);

            // Hi-hats (croches)
            for (int s = 0; s < 16; s += 2) {
                addNote(start + s, Sound.BLOCK_NOTE_BLOCK_HAT, 18, (s % 4 == 0) ? 0.6f : 0.4f);
            }
        }

        // Accords synthé doux
        if (chords) {
            addNote(start + 0, Sound.BLOCK_NOTE_BLOCK_HARP, root, 0.5f);
            addNote(start + 0, Sound.BLOCK_NOTE_BLOCK_HARP, third, 0.5f);
            addNote(start + 0, Sound.BLOCK_NOTE_BLOCK_HARP, fifth, 0.5f);

            addNote(start + 8, Sound.BLOCK_NOTE_BLOCK_HARP, root, 0.4f);
            addNote(start + 8, Sound.BLOCK_NOTE_BLOCK_HARP, third, 0.4f);
            addNote(start + 8, Sound.BLOCK_NOTE_BLOCK_HARP, fifth, 0.4f);

            // Nappe Flute pour enrichir l'harmonie
            addNote(start + 0, Sound.BLOCK_NOTE_BLOCK_FLUTE, root, 0.35f);
            addNote(start + 0, Sound.BLOCK_NOTE_BLOCK_FLUTE, third, 0.35f);
        }
    }

    private void initSongData() {
        // Gamme B-flat minor (Bb minor) / Db major :
        // Bb3 = 4, C4 = 6, Db4 = 7, Eb4 = 9, F4 = 11, Gb4 = 12, Ab4 = 14
        // Bb4 = 16, C5 = 18, Db5 = 19, Eb5 = 21, F5 = 23, Gb5 = 24
        final int Bb3 = 4, C4 = 6, Db4 = 7, Eb4 = 9, F4 = 11, Gb4 = 12, Ab4 = 14;
        final int Bb4 = 16, C5 = 18, Db5 = 19, Eb5 = 21, F5 = 23;

        // Progression d'accords récurrente par bloc de 4 mesures :
        // Bar 0 : Bbm (Bb3, Db4, F4)
        // Bar 1 : Gb  (Gb4/Gb3, Bb4/Bb3, Db4)
        // Bar 2 : Db  (Db4, F4, Ab4)
        // Bar 3 : Ab  (Ab3/Ab4, C4/C5, Eb4)

        // -------------------------------------------------------------------
        // SECTION 1 : INTRO (Mesures 0 à 3, steps 0 à 63)
        // -------------------------------------------------------------------
        lyrics.put(0, Component.text("♫ Twenty One Pilots - Chlorine ♫", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));

        // Mesure 0 : Bbm arpège
        addRhythmBar(0, Bb3, Db4, F4, false, false, true);
        addNote(0, Sound.BLOCK_NOTE_BLOCK_HARP, Bb4, 0.7f);
        addNote(3, Sound.BLOCK_NOTE_BLOCK_HARP, Db5, 0.7f);
        addNote(6, Sound.BLOCK_NOTE_BLOCK_HARP, F5, 0.8f);
        addNote(9, Sound.BLOCK_NOTE_BLOCK_HARP, Db5, 0.7f);
        addNote(12, Sound.BLOCK_NOTE_BLOCK_HARP, C5, 0.6f);
        addNote(14, Sound.BLOCK_NOTE_BLOCK_HARP, Bb4, 0.6f);

        // Mesure 1 : Gb arpège
        addRhythmBar(1, Gb4, Bb4, Db5, false, false, true);
        addNote(16, Sound.BLOCK_NOTE_BLOCK_HARP, Gb4, 0.7f);
        addNote(19, Sound.BLOCK_NOTE_BLOCK_HARP, Bb4, 0.7f);
        addNote(22, Sound.BLOCK_NOTE_BLOCK_HARP, Db5, 0.8f);
        addNote(25, Sound.BLOCK_NOTE_BLOCK_HARP, Bb4, 0.7f);
        addNote(28, Sound.BLOCK_NOTE_BLOCK_HARP, Ab4, 0.6f);
        addNote(30, Sound.BLOCK_NOTE_BLOCK_HARP, Gb4, 0.6f);

        // Mesure 2 : Db + Entrée de la Basse
        lyrics.put(32, Component.text("♫ (Bassline) ♫", NamedTextColor.GRAY));
        addRhythmBar(2, Db4, F4, Ab4, false, true, true);
        addNote(32, Sound.BLOCK_NOTE_BLOCK_HARP, F4, 0.7f);
        addNote(35, Sound.BLOCK_NOTE_BLOCK_HARP, Ab4, 0.7f);
        addNote(38, Sound.BLOCK_NOTE_BLOCK_HARP, Db5, 0.8f);
        addNote(41, Sound.BLOCK_NOTE_BLOCK_HARP, Ab4, 0.7f);
        addNote(44, Sound.BLOCK_NOTE_BLOCK_HARP, Gb4, 0.6f);
        addNote(46, Sound.BLOCK_NOTE_BLOCK_HARP, F4, 0.6f);

        // Mesure 3 : Ab + Hi-hats
        addRhythmBar(3, Ab4, C5, Eb5, false, true, true);
        for (int s = 48; s < 64; s += 2) {
            addNote(s, Sound.BLOCK_NOTE_BLOCK_HAT, 18, 0.5f);
        }
        addNote(48, Sound.BLOCK_NOTE_BLOCK_HARP, Eb4, 0.7f);
        addNote(51, Sound.BLOCK_NOTE_BLOCK_HARP, Ab4, 0.7f);
        addNote(54, Sound.BLOCK_NOTE_BLOCK_HARP, C5, 0.8f);
        addNote(57, Sound.BLOCK_NOTE_BLOCK_HARP, Bb4, 0.7f);
        addNote(60, Sound.BLOCK_NOTE_BLOCK_HARP, Ab4, 0.6f);
        addNote(62, Sound.BLOCK_NOTE_BLOCK_HARP, F4, 0.6f);

        // -------------------------------------------------------------------
        // SECTION 2 : COUPLET 1 (Mesures 4 à 7, steps 64 à 127)
        // -------------------------------------------------------------------
        addRhythmBar(4, Bb3, Db4, F4, true, true, true);
        addRhythmBar(5, Gb4, Bb4, Db5, true, true, true);
        addRhythmBar(6, Db4, F4, Ab4, true, true, true);
        addRhythmBar(7, Ab4, C5, Eb5, true, true, true);

        lyrics.put(64, Component.text("♫ So where are you? It's been a little while... ♫", NamedTextColor.YELLOW));
        // "So where are you?"
        addMelody(64, F4);
        addMelody(67, F4);
        addMelody(70, Eb4);
        addMelody(72, Db4);
        // "It's been a little while"
        addMelody(76, Db4);
        addMelody(78, Eb4);
        addMelody(80, F4);
        addMelody(82, Eb4);
        addMelody(84, Db4);
        addMelody(86, Bb3);

        lyrics.put(96, Component.text("♫ That's how it goes, I guess... ♫", NamedTextColor.YELLOW));
        // "That's how it goes, I guess"
        addMelody(96, F4);
        addMelody(98, F4);
        addMelody(100, Eb4);
        addMelody(102, Db4);
        addMelody(106, Db4);
        addMelody(108, C4);
        addMelody(110, Bb3);

        // Transition
        addMelody(116, Bb3);
        addMelody(118, Db4);
        addMelody(120, Eb4);
        addMelody(122, F4);

        // -------------------------------------------------------------------
        // SECTION 3 : PRÉ-REFRAIN (Mesures 8 à 11, steps 128 à 191)
        // -------------------------------------------------------------------
        addRhythmBar(8, Bb3, Db4, F4, true, true, true);
        addRhythmBar(9, Gb4, Bb4, Db5, true, true, true);
        addRhythmBar(10, Db4, F4, Ab4, true, true, true);
        // Bar 11 : Montée en puissance de batterie
        addRhythmBar(11, Ab4, C5, Eb5, false, true, true);

        lyrics.put(128, Component.text("♫ Fallin' out of line, oh, trippin' on my wires... ♫", NamedTextColor.GOLD));
        // "Fallin' out of line"
        addMelody(128, Db5);
        addMelody(131, C5);
        addMelody(134, Bb4);
        addMelody(136, Ab4);
        addMelody(138, Bb4);

        // "Oh, trippin' on my wires"
        addMelody(144, Db5);
        addMelody(148, Db5);
        addMelody(150, C5);
        addMelody(152, Bb4);
        addMelody(154, Ab4);
        addMelody(156, Bb4);

        lyrics.put(160, Component.text("♫ Now you're runnin' out of time... ♫", NamedTextColor.GOLD));
        // "Now you're runnin' out of time"
        addMelody(160, Db5);
        addMelody(162, Db5);
        addMelody(164, Eb5);
        addMelody(166, F5);
        addMelody(168, Eb5);
        addMelody(170, Db5);
        addMelody(172, Bb4);

        // Drum build-up final vers le refrain
        addNote(180, Sound.BLOCK_NOTE_BLOCK_SNARE, 12, 0.7f);
        addNote(182, Sound.BLOCK_NOTE_BLOCK_SNARE, 12, 0.8f);
        addNote(184, Sound.BLOCK_NOTE_BLOCK_SNARE, 14, 0.85f);
        addNote(186, Sound.BLOCK_NOTE_BLOCK_SNARE, 14, 0.9f);
        addNote(188, Sound.BLOCK_NOTE_BLOCK_SNARE, 16, 0.95f);
        addNote(189, Sound.BLOCK_NOTE_BLOCK_SNARE, 17, 1.0f);
        addNote(190, Sound.BLOCK_NOTE_BLOCK_SNARE, 18, 1.0f);
        addNote(191, Sound.BLOCK_NOTE_BLOCK_SNARE, 20, 1.0f);

        // -------------------------------------------------------------------
        // SECTION 4 : LE REFRAIN ICONIQUE (Mesures 12 à 19, steps 192 à 319)
        // -------------------------------------------------------------------
        for (int b = 12; b <= 19; b++) {
            int chordIdx = (b - 12) % 4;
            int r = chordIdx == 0 ? Bb3 : (chordIdx == 1 ? Gb4 : (chordIdx == 2 ? Db4 : Ab4));
            int t = chordIdx == 0 ? Db4 : (chordIdx == 1 ? Bb4 : (chordIdx == 2 ? F4 : C5));
            int f = chordIdx == 0 ? F4 : (chordIdx == 1 ? Db5 : (chordIdx == 2 ? Ab4 : Eb5));
            addRhythmBar(b, r, t, f, true, true, true);
        }

        // --- Refrain Partie 1 ---
        lyrics.put(192, Component.text("♫ Sippin' on straight chlorine, let the vibes slide over me ♫", NamedTextColor.AQUA, TextDecoration.BOLD));
        // "Sippin' on straight chlorine"
        addMelody(192, Db5);
        addMelody(194, Db5);
        addMelody(196, Db5);
        addMelody(198, C5);
        addMelody(200, Bb4);
        addMelody(204, Bb4);

        // "Let the vibes slide over me"
        addMelody(208, Db5);
        addMelody(210, Db5);
        addMelody(212, Db5);
        addMelody(214, Eb5);
        addMelody(216, F5);
        addMelody(218, Eb5);
        addMelody(220, Db5);

        lyrics.put(224, Component.text("♫ This beat is a chemical, beat is a chemical ♫", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        // "This beat is a chemical"
        addMelody(224, F5);
        addMelody(226, F5);
        addMelody(228, F5);
        addMelody(230, Eb5);
        addMelody(232, Db5);
        addMelody(234, Bb4);
        addMelody(236, Bb4);

        // "Beat is a chemical"
        addMelody(240, F5);
        addMelody(242, F5);
        addMelody(244, Eb5);
        addMelody(246, Db5);
        addMelody(248, Bb4);
        addMelody(250, Bb4);

        // --- Refrain Partie 2 ---
        lyrics.put(256, Component.text("♫ When I leave don't save my seat, I'll be back when it's all complete ♫", NamedTextColor.AQUA, TextDecoration.BOLD));
        // "When I leave don't save my seat"
        addMelody(256, Db5);
        addMelody(258, Db5);
        addMelody(260, Db5);
        addMelody(262, C5);
        addMelody(264, Bb4);
        addMelody(266, Bb4);

        // "I'll be back when it's all complete"
        addMelody(272, Db5);
        addMelody(274, Db5);
        addMelody(276, Db5);
        addMelody(278, Eb5);
        addMelody(280, F5);
        addMelody(282, Eb5);
        addMelody(284, Db5);
        addMelody(286, Db5);

        lyrics.put(288, Component.text("♫ The moment is medical, moment is medical ♫", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        // "The moment is medical"
        addMelody(288, F5);
        addMelody(290, F5);
        addMelody(292, F5);
        addMelody(294, Eb5);
        addMelody(296, Db5);
        addMelody(298, Bb4);
        addMelody(300, Bb4);

        // "Moment is medical"
        addMelody(304, F5);
        addMelody(306, F5);
        addMelody(308, Eb5);
        addMelody(310, Db5);
        addMelody(312, Bb4);
        addMelody(314, Bb4);

        // "Sippin' on straight chlorine..."
        lyrics.put(316, Component.text("♫ Sippin' on straight chlorine... ♫", NamedTextColor.AQUA, TextDecoration.BOLD));
        addMelody(316, Db5);
        addMelody(318, C5);

        // -------------------------------------------------------------------
        // SECTION 5 : POST-REFRAIN / SYNTH HOOK (Mesures 20 à 23, steps 320 à 383)
        // -------------------------------------------------------------------
        lyrics.put(320, Component.text("♫ (Electro Synth Riff) ♫", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        addRhythmBar(20, Bb3, Db4, F4, true, true, true);
        addRhythmBar(21, Gb4, Bb4, Db5, true, true, true);
        addRhythmBar(22, Db4, F4, Ab4, true, true, true);
        addRhythmBar(23, Ab4, C5, Eb5, true, true, true);

        // Riff mélodique synthé bondissant
        addMelody(320, Bb4);
        addMelody(324, Db5);
        addMelody(326, F5);
        addMelody(328, Db5);
        addMelody(330, Bb4);
        addMelody(332, Ab4);
        addMelody(334, Bb4);

        addMelody(336, Gb4);
        addMelody(338, Bb4);
        addMelody(340, Db5);
        addMelody(342, Eb5);
        addMelody(344, F5);
        addMelody(346, Eb5);
        addMelody(348, Db5);
        addMelody(350, Bb4);

        addMelody(352, Db5);
        addMelody(354, F5);
        addMelody(356, Ab4);
        addMelody(358, Db5);
        addMelody(360, F5);
        addMelody(362, Eb5);
        addMelody(364, Db5);

        addMelody(368, C5);
        addMelody(370, Eb5);
        addMelody(372, Db5);
        addMelody(374, C5);
        addMelody(376, Bb4);
        addMelody(380, Ab4);
        addMelody(382, F4);

        // -------------------------------------------------------------------
        // SECTION 6 : OUTRO / PARTIE DE NED (Mesures 24 à 29, steps 384 à 479)
        // -------------------------------------------------------------------
        // Piano doux (Harp & Chime) sans batterie
        lyrics.put(384, Component.text("♫ Can you build my heart with pieces? ♫", NamedTextColor.WHITE, TextDecoration.BOLD));
        addRhythmBar(24, Bb3, Db4, F4, false, false, true);
        addRhythmBar(25, Gb4, Bb4, Db5, false, false, true);
        addRhythmBar(26, Db4, F4, Ab4, false, false, true);
        addRhythmBar(27, Ab4, C5, Eb5, false, false, true);
        addRhythmBar(28, Bb3, Db4, F4, false, false, true);

        // "Can you build my heart with pieces?"
        addNote(388, Sound.BLOCK_NOTE_BLOCK_HARP, F4, 0.9f);
        addNote(391, Sound.BLOCK_NOTE_BLOCK_HARP, Eb4, 0.9f);
        addNote(394, Sound.BLOCK_NOTE_BLOCK_HARP, Db4, 0.9f);
        addNote(397, Sound.BLOCK_NOTE_BLOCK_HARP, Bb3, 0.8f);
        addNote(400, Sound.BLOCK_NOTE_BLOCK_HARP, Db4, 0.9f);
        addNote(403, Sound.BLOCK_NOTE_BLOCK_HARP, F4, 0.9f);
        addNote(406, Sound.BLOCK_NOTE_BLOCK_HARP, Eb4, 0.85f);
        addNote(409, Sound.BLOCK_NOTE_BLOCK_HARP, Db4, 0.85f);

        lyrics.put(416, Component.text("♫ I am just a chemical... ♫", NamedTextColor.GRAY, TextDecoration.BOLD));
        // "I am just a chemical..."
        addNote(418, Sound.BLOCK_NOTE_BLOCK_HARP, Db4, 0.9f);
        addNote(421, Sound.BLOCK_NOTE_BLOCK_HARP, C4, 0.85f);
        addNote(424, Sound.BLOCK_NOTE_BLOCK_HARP, Bb3, 0.8f);
        addNote(427, Sound.BLOCK_NOTE_BLOCK_HARP, F4, 0.8f);
        addNote(430, Sound.BLOCK_NOTE_BLOCK_HARP, Bb3, 0.8f);
        addNote(433, Sound.BLOCK_NOTE_BLOCK_HARP, C4, 0.85f);
        addNote(436, Sound.BLOCK_NOTE_BLOCK_HARP, Bb3, 0.9f);

        lyrics.put(448, Component.text("♫ Sippin' on straight chlorine... ♫", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        // "Sippin' on straight chlorine..."
        addNote(448, Sound.BLOCK_NOTE_BLOCK_HARP, Db4, 0.85f);
        addNote(451, Sound.BLOCK_NOTE_BLOCK_HARP, C4, 0.8f);
        addNote(454, Sound.BLOCK_NOTE_BLOCK_HARP, Bb3, 0.85f);
        addNote(458, Sound.BLOCK_NOTE_BLOCK_HARP, F4, 0.75f);
        addNote(462, Sound.BLOCK_NOTE_BLOCK_HARP, Bb3, 0.9f);

        // Accord final apaisant Bbm + Bell Chime
        addNote(470, Sound.BLOCK_NOTE_BLOCK_HARP, Bb3, 0.9f);
        addNote(470, Sound.BLOCK_NOTE_BLOCK_HARP, Db4, 0.9f);
        addNote(470, Sound.BLOCK_NOTE_BLOCK_HARP, F4, 0.9f);
        addNote(470, Sound.BLOCK_NOTE_BLOCK_HARP, Bb4, 0.9f);
        addNote(470, Sound.BLOCK_NOTE_BLOCK_BELL, Bb4, 0.8f);
        addNote(470, Sound.BLOCK_NOTE_BLOCK_CHIME, F4, 0.7f);
    }
}

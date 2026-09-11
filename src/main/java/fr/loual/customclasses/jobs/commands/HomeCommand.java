package fr.loual.customclasses.jobs.commands;

import fr.loual.customclasses.jobs.JobManager;
import fr.loual.customclasses.jobs.PlayerJob;
import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class HomeCommand implements CommandExecutor, TabCompleter {

    private final NewAdventurePlugin plugin;
    private final JobManager jobManager;

    public HomeCommand(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Cette commande est réservée aux joueurs.", NamedTextColor.RED));
            return true;
        }

        boolean isAventurierM1 = jobManager.getPlayerJob(player) == PlayerJob.AVENTURIER && jobManager.getJobLevel(player, PlayerJob.AVENTURIER) >= 1;
        if (!isAventurierM1) {
            player.sendMessage(
                    Component.text("[Aventurier] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Vous devez être Aventurier de niveau 1 (accomplir la Mission 1) pour utiliser /" + label + " !", NamedTextColor.RED))
            );
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return true;
        }

        int slot = 1;
        if (args.length > 0) {
            if (args[0].equals("2")) {
                slot = 2;
            } else if (args[0].equals("1")) {
                slot = 1;
            }
        }

        if (slot == 2) {
            boolean isAventurierM4 = jobManager.getPlayerJob(player) == PlayerJob.AVENTURIER && jobManager.getJobLevel(player, PlayerJob.AVENTURIER) >= 4;
            if (!isAventurierM4) {
                player.sendMessage(
                        Component.text("[Aventurier] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                                .append(Component.text("Vous devez être Aventurier de niveau 4 pour utiliser le 2e Home (/sethome 2 & /home 2) !", NamedTextColor.RED))
                );
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return true;
            }
        }

        if (label.equalsIgnoreCase("sethome") || label.equalsIgnoreCase("set_home") || label.equalsIgnoreCase("sh")) {
            jobManager.setHomeLocation(player, slot, player.getLocation());
            player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 1.2f);
            player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().clone().add(0, 1, 0), 30, 0.4, 0.5, 0.4, 0.1);
            player.sendMessage(
                    Component.text("[Aventurier] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("✦ Point de Home #" + slot + " défini avec succès à votre position actuelle !", NamedTextColor.GREEN, TextDecoration.BOLD))
            );
            return true;
        }

        // Commande /home
        Location home = jobManager.getHomeLocation(player, slot);
        if (home == null) {
            player.sendMessage(
                    Component.text("[Aventurier] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Vous n'avez pas encore défini de point de Home #" + slot + " ! Utilisez ", NamedTextColor.RED))
                            .append(Component.text("/sethome " + slot, NamedTextColor.YELLOW, TextDecoration.BOLD))
                            .append(Component.text(" d'abord.", NamedTextColor.RED))
            );
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return true;
        }

        // Détection des compagnons très proches (rayon de 3.0 blocs maximum)
        double radius = 3.0;
        double radiusSquared = radius * radius;
        Location playerLoc = player.getLocation();
        List<Player> companions = new java.util.ArrayList<>();

        for (Player other : player.getWorld().getPlayers()) {
            if (!other.equals(player) && !other.isDead() && other.getLocation().distanceSquared(playerLoc) <= radiusSquared) {
                companions.add(other);
            }
        }

        // Particules et sons au point de départ
        player.getWorld().spawnParticle(Particle.PORTAL, playerLoc.clone().add(0, 1, 0), 30, 0.4, 0.5, 0.4, 0.1);
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, playerLoc.clone().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.05);
        player.playSound(playerLoc, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.0f, 1.0f);

        for (Player comp : companions) {
            Location cLoc = comp.getLocation();
            cLoc.getWorld().spawnParticle(Particle.PORTAL, cLoc.clone().add(0, 1, 0), 30, 0.4, 0.5, 0.4, 0.1);
            cLoc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, cLoc.clone().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.05);
            comp.playSound(cLoc, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.0f, 1.0f);
        }

        // Téléportation de l'aventurier et de ses compagnons
        player.teleport(home);
        for (Player comp : companions) {
            comp.teleport(home);
        }

        // Particules et sons à l'arrivée
        home.getWorld().spawnParticle(Particle.PORTAL, home.clone().add(0, 1, 0), 40, 0.5, 0.7, 0.5, 0.1);
        home.getWorld().spawnParticle(Particle.REVERSE_PORTAL, home.clone().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.05);
        home.getWorld().playSound(home, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.0f, 1.2f);
        home.getWorld().playSound(home, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);

        // Notifications
        if (companions.isEmpty()) {
            player.sendMessage(
                    Component.text("[Aventurier] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("✦ Téléportation à votre Home #" + slot + " réussie !", NamedTextColor.GREEN, TextDecoration.BOLD))
            );
        } else {
            String compNames = companions.stream().map(Player::getName).reduce((a, b) -> a + ", " + b).orElse("");
            player.sendMessage(
                    Component.text("[Aventurier] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("✦ Téléportation de groupe à votre Home #" + slot + " réussie avec ", NamedTextColor.GREEN, TextDecoration.BOLD))
                            .append(Component.text(companions.size() + " compagnon(s)", NamedTextColor.YELLOW, TextDecoration.BOLD))
                            .append(Component.text(" (" + compNames + ") !", NamedTextColor.GREEN))
            );

            for (Player comp : companions) {
                comp.sendMessage(
                        Component.text("[Aventurier] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                                .append(Component.text("✦ Vous avez été téléporté au Home de l'Aventurier ", NamedTextColor.AQUA))
                                .append(Component.text(player.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                                .append(Component.text(" !", NamedTextColor.AQUA))
                );
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            boolean isM4 = jobManager.getPlayerJob(player) == PlayerJob.AVENTURIER && jobManager.getJobLevel(player, PlayerJob.AVENTURIER) >= 4;
            if (isM4) {
                return List.of("1", "2");
            }
            return List.of("1");
        }
        return Collections.emptyList();
    }
}

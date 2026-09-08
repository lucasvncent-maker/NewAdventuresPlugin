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

        if (label.equalsIgnoreCase("sethome") || label.equalsIgnoreCase("set_home") || label.equalsIgnoreCase("sh")) {
            jobManager.setHomeLocation(player, player.getLocation());
            player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 1.2f);
            player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().clone().add(0, 1, 0), 30, 0.4, 0.5, 0.4, 0.1);
            player.sendMessage(
                    Component.text("[Aventurier] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("✦ Point de Home défini avec succès à votre position actuelle !", NamedTextColor.GREEN, TextDecoration.BOLD))
            );
            return true;
        }

        // Commande /home
        Location home = jobManager.getHomeLocation(player);
        if (home == null) {
            player.sendMessage(
                    Component.text("[Aventurier] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Vous n'avez pas encore défini de point de Home ! Utilisez ", NamedTextColor.RED))
                            .append(Component.text("/sethome", NamedTextColor.YELLOW, TextDecoration.BOLD))
                            .append(Component.text(" d'abord.", NamedTextColor.RED))
            );
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return true;
        }

        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().clone().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.1);
        player.teleport(home);
        player.getWorld().spawnParticle(Particle.PORTAL, home.clone().add(0, 1, 0), 35, 0.4, 0.6, 0.4, 0.1);
        player.getWorld().spawnParticle(Particle.DRAGON_BREATH, home.clone().add(0, 1, 0), 20, 0.3, 0.4, 0.3, 0.05);
        player.playSound(home, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.0f, 1.2f);
        player.sendMessage(
                Component.text("[Aventurier] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("✦ Téléportation à votre Home réussie !", NamedTextColor.GREEN, TextDecoration.BOLD))
        );
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

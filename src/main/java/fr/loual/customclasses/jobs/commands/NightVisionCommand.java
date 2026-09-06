package fr.loual.customclasses.jobs.commands;

import fr.loual.newadventure.NewAdventurePlugin;
import fr.loual.customclasses.jobs.JobManager;
import fr.loual.customclasses.jobs.PlayerJob;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

public class NightVisionCommand implements CommandExecutor, TabCompleter {

    private final NewAdventurePlugin plugin;
    private final JobManager jobManager;

    public NightVisionCommand(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Cette commande est réservée aux joueurs.", NamedTextColor.RED));
            return true;
        }

        if (jobManager.getPlayerJob(player) != PlayerJob.MINEUR || jobManager.getJobLevel(player, PlayerJob.MINEUR) < 1) {
            player.sendMessage(
                    Component.text("[Mineur] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Vous devez être Mineur de niveau 1 minimum pour utiliser /nv !", NamedTextColor.RED))
            );
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return true;
        }

        boolean newState = jobManager.toggleNightVision(player);
        if (newState) {
            player.sendMessage(
                    Component.text("[Mineur] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("✦ Vision Nocturne activée !", NamedTextColor.GREEN, TextDecoration.BOLD))
            );
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.5f);
        } else {
            player.sendMessage(
                    Component.text("[Mineur] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("✦ Vision Nocturne désactivée.", NamedTextColor.GRAY))
            );
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.2f);
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

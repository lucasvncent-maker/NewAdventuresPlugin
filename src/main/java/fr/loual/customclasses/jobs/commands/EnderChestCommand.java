package fr.loual.customclasses.jobs.commands;

import fr.loual.customclasses.jobs.JobManager;
import fr.loual.customclasses.jobs.PlayerJob;
import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

public class EnderChestCommand implements CommandExecutor, TabCompleter {

    private final NewAdventurePlugin plugin;
    private final JobManager jobManager;

    public EnderChestCommand(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Cette commande est réservée aux joueurs.", NamedTextColor.RED));
            return true;
        }

        boolean isAventurierM4 = jobManager.getPlayerJob(player) == PlayerJob.AVENTURIER
                && jobManager.getJobLevel(player, PlayerJob.AVENTURIER) >= 4;

        if (!isAventurierM4) {
            player.sendMessage(
                    Component.text("[Aventurier] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Vous devez être Aventurier de niveau 4 (Mission 4) pour utiliser /" + label + " !", NamedTextColor.RED))
            );
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return true;
        }

        player.openInventory(player.getEnderChest());
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1.0f);
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().clone().add(0, 1, 0), 20, 0.3, 0.4, 0.3, 0.1);
        player.sendMessage(
                Component.text("[Aventurier] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("✦ Coffre du Néant ouvert !", NamedTextColor.AQUA, TextDecoration.BOLD))
        );
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

package fr.loual.customclasses.jobs.commands;

import fr.loual.newadventure.NewAdventurePlugin;
import fr.loual.customclasses.jobs.CustomJobItems;
import fr.loual.customclasses.jobs.JobManager;
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

public class JumpBoostCommand implements CommandExecutor, TabCompleter {

    private final NewAdventurePlugin plugin;
    private final JobManager jobManager;

    public JumpBoostCommand(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Cette commande est réservée aux joueurs.", NamedTextColor.RED));
            return true;
        }

        boolean hasBoots = CustomJobItems.isJobItem(player.getInventory().getBoots(), CustomJobItems.ID_ARCHITECT_BOOTS);
        boolean isAdmin = player.hasPermission("customclasses.admin");

        if (!hasBoots && !isAdmin) {
            player.sendMessage(
                    Component.text("[Architecte] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Vous devez équiper les Chaussures de l'Architecte pour utiliser /" + label + " !", NamedTextColor.RED))
            );
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return true;
        }

        boolean newState = jobManager.toggleJumpBoost(player);
        if (newState) {
            player.sendMessage(
                    Component.text("[Architecte] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("✦ Saut Amélioré II activé !", NamedTextColor.GREEN, TextDecoration.BOLD))
            );
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.5f);
        } else {
            player.sendMessage(
                    Component.text("[Architecte] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("✦ Saut Amélioré II désactivé.", NamedTextColor.GRAY))
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

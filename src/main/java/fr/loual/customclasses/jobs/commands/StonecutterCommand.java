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

public class StonecutterCommand implements CommandExecutor, TabCompleter {

    private final NewAdventurePlugin plugin;
    private final JobManager jobManager;

    public StonecutterCommand(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Cette commande est réservée aux joueurs.", NamedTextColor.RED));
            return true;
        }

        boolean isArchitect = jobManager.getPlayerJob(player) == PlayerJob.ARCHITECTE && jobManager.getJobLevel(player, PlayerJob.ARCHITECTE) >= 2;

        if (!isArchitect) {
            player.sendMessage(
                    Component.text("[Architecte] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Vous devez être Architecte de niveau 2 minimum pour utiliser /" + label + " !", NamedTextColor.RED))
            );
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return true;
        }

        player.openStonecutter(player.getLocation(), true);
        player.playSound(player.getLocation(), Sound.UI_STONECUTTER_TAKE_RESULT, 0.8f, 1.0f);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

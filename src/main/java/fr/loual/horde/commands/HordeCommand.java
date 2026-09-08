package fr.loual.horde.commands;

import fr.loual.horde.HordeManager;
import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class HordeCommand implements CommandExecutor, TabCompleter {

    private final NewAdventurePlugin plugin;
    private final HordeManager hordeManager;

    public HordeCommand(NewAdventurePlugin plugin, HordeManager hordeManager) {
        this.plugin = plugin;
        this.hordeManager = hordeManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("horde.admin")) {
            sender.sendMessage(Component.text("Vous n'avez pas la permission d'utiliser cette commande.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("==== [ Commandes Horde ] ====", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            sender.sendMessage(Component.text("/" + label + " start ", NamedTextColor.YELLOW)
                    .append(Component.text("- Déclencher l'invasion de la Horde", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/" + label + " stop ", NamedTextColor.YELLOW)
                    .append(Component.text("- Arrêter l'invasion en cours", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/" + label + " status ", NamedTextColor.YELLOW)
                    .append(Component.text("- Voir le statut de la Horde", NamedTextColor.GRAY)));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "start" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Cette commande doit être exécutée par un joueur en jeu.", NamedTextColor.RED));
                    return true;
                }
                if (hordeManager.isHordeActive()) {
                    player.sendMessage(Component.text("Une Horde est déjà active !", NamedTextColor.RED));
                    return true;
                }
                hordeManager.startHorde(player, player.getLocation());
                player.sendMessage(Component.text("✦ Invasion de la Horde lancée à votre position !", NamedTextColor.GREEN));
                return true;
            }
            case "stop" -> {
                if (!hordeManager.isHordeActive()) {
                    sender.sendMessage(Component.text("Aucune Horde n'est active actuellement.", NamedTextColor.YELLOW));
                    return true;
                }
                hordeManager.stopHorde(true);
                sender.sendMessage(Component.text("✦ L'événement de la Horde a été arrêté.", NamedTextColor.RED));
                return true;
            }
            case "status" -> {
                sender.sendMessage(Component.text("Statut Horde : " + (hordeManager.isHordeActive() ? "ACTIVE (Vague " + hordeManager.getCurrentWave() + "/4)" : "INACTIVE"), NamedTextColor.GOLD));
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Sous-commande inconnue. Utilisez /" + label + " pour l'aide.", NamedTextColor.RED));
                return true;
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            for (String s : List.of("start", "stop", "status")) {
                if (s.startsWith(args[0].toLowerCase())) list.add(s);
            }
            return list;
        }
        return List.of();
    }
}

package fr.loual.customclasses.commands;

import fr.loual.newadventure.NewAdventurePlugin;
import fr.loual.customclasses.classes.ClassManager;
import fr.loual.customclasses.classes.PlayerClass;
import fr.loual.customclasses.gui.ClassSelectionGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ClassCommand implements CommandExecutor, TabCompleter {

    private final NewAdventurePlugin plugin;
    private final ClassManager classManager;

    private static final List<String> CLASS_NAMES = List.of(
            "humain",
            "assassin",
            "guerrier",
            "sauterelle",
            "sirene",
            "diable",
            "necromancien",
            "archer",
            "none"
    );

    public ClassCommand(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.classManager = plugin.getClassManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                if (classManager.hasClass(player) && !player.hasPermission("customclasses.admin")) {
                    sendClassStatus(player);
                    return true;
                }
                ClassSelectionGui.open(plugin, player);
            } else {
                sendHelp(sender, label);
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "choose", "gui", "menu" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Cette commande ne peut être exécutée que par un joueur.", NamedTextColor.RED));
                    return true;
                }
                if (classManager.hasClass(player) && !player.hasPermission("customclasses.admin")) {
                    sendClassStatus(player);
                    return true;
                }
                ClassSelectionGui.open(plugin, player);
                return true;
            }

            case "reset" -> {
                if (!sender.hasPermission("customclasses.admin")) {
                    sender.sendMessage(Component.text("✦ [Classes] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Pour changer de classe, vous devez fabriquer et utiliser l'objet mystique : ", NamedTextColor.RED))
                            .append(Component.text("✦ Nouvelle Âme ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)));
                    sender.sendMessage(Component.text("  • Recette : 1 Bloc de Diamant, 1 Éclat d'Améthyste, 1 Perle de l'Ender, 1 Éclat d'Écho, 1 Larme de Ghast.", NamedTextColor.DARK_AQUA));
                    return true;
                }

                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage(Component.text("Joueur introuvable : " + args[1], NamedTextColor.RED));
                        return true;
                    }
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    sender.sendMessage(Component.text("Usage console: /" + label + " reset <joueur>", NamedTextColor.RED));
                    return true;
                }

                classManager.resetPlayerClass(target);
                if (!sender.equals(target)) {
                    sender.sendMessage(Component.text("Classe de " + target.getName() + " réinitialisée avec succès.", NamedTextColor.GREEN));
                }
                return true;
            }

            case "set" -> {
                if (!sender.hasPermission("customclasses.admin")) {
                    sender.sendMessage(Component.text("Vous n'avez pas la permission de définir une classe.", NamedTextColor.RED));
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /" + label + " set <joueur> <classe>", NamedTextColor.RED));
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Joueur introuvable : " + args[1], NamedTextColor.RED));
                    return true;
                }

                PlayerClass pc = PlayerClass.fromId(args[2]);
                if (pc == PlayerClass.NONE && !args[2].equalsIgnoreCase("none")) {
                    sender.sendMessage(Component.text("Classe invalide : " + args[2] + ". Options: " + String.join(", ", CLASS_NAMES), NamedTextColor.RED));
                    return true;
                }

                classManager.setPlayerClass(target, pc);
                sender.sendMessage(Component.text("Classe de " + target.getName() + " changée pour " + pc.getDisplayName() + ".", NamedTextColor.GREEN));
                target.sendMessage(Component.text("Votre classe a été définie sur " + pc.getDisplayName() + " par un administrateur.", NamedTextColor.GOLD));
                return true;
            }

            case "get", "info" -> {
                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage(Component.text("Joueur introuvable : " + args[1], NamedTextColor.RED));
                        return true;
                    }
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    sender.sendMessage(Component.text("Usage console: /" + label + " info <joueur>", NamedTextColor.RED));
                    return true;
                }

                PlayerClass pc = classManager.getPlayerClass(target);
                sender.sendMessage(Component.text("==== [ Classe de " + target.getName() + " ] ====", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("Classe actuelle : ", NamedTextColor.GRAY)
                        .append(Component.text(pc.getDisplayName(), NamedTextColor.AQUA, TextDecoration.BOLD)));
                for (Component comp : pc.getDescription()) {
                    sender.sendMessage(comp);
                }
                return true;
            }

            case "giveame", "ame" -> {
                if (!sender.hasPermission("customclasses.admin")) {
                    sender.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
                    return true;
                }
                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage(Component.text("Joueur introuvable : " + args[1], NamedTextColor.RED));
                        return true;
                    }
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    sender.sendMessage(Component.text("Usage console: /" + label + " giveame <joueur>", NamedTextColor.RED));
                    return true;
                }

                target.getInventory().addItem(fr.loual.customclasses.items.NouvelleAme.create(plugin, 1));
                sender.sendMessage(Component.text("✦ Nouvelle Âme donnée à " + target.getName() + " !", NamedTextColor.GREEN));
                return true;
            }

            case "help" -> {
                sendHelp(sender, label);
                return true;
            }

            default -> {
                sendHelp(sender, label);
                return true;
            }
        }
    }

    private void sendClassStatus(Player player) {
        PlayerClass currentClass = classManager.getPlayerClass(player);
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("✦ ======================================== ✦", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("  Classe active : ", NamedTextColor.YELLOW)
                .append(Component.text(currentClass.getDisplayName(), NamedTextColor.AQUA, TextDecoration.BOLD)));
        player.sendMessage(Component.text("  Pour changer de classe, vous devez fabriquer et utiliser :", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  ➜ ", NamedTextColor.GOLD)
                .append(Component.text("✦ Nouvelle Âme ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)));
        player.sendMessage(Component.text("  Recette : 1 Bloc de Diamant, 1 Éclat d'Améthyste, 1 Perle de l'Ender, 1 Éclat d'Écho, 1 Larme de Ghast.", NamedTextColor.DARK_AQUA));
        player.sendMessage(Component.text("✦ ======================================== ✦", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.empty());
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("==== [ Système de Classes (8 classes) ] ====", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("/" + label + " choose ", NamedTextColor.YELLOW)
                .append(Component.text("- Ouvrir le menu de sélection de classe", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " reset [joueur] ", NamedTextColor.YELLOW)
                .append(Component.text("- Réinitialiser sa classe ou celle d'un joueur", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " info [joueur] ", NamedTextColor.YELLOW)
                .append(Component.text("- Voir les détails de la classe", NamedTextColor.GRAY)));
        if (sender.hasPermission("customclasses.admin")) {
            sender.sendMessage(Component.text("/" + label + " set <joueur> <classe> ", NamedTextColor.YELLOW)
                    .append(Component.text("- Définir directement la classe d'un joueur", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/" + label + " giveame [joueur] ", NamedTextColor.YELLOW)
                    .append(Component.text("- Donner un item Nouvelle Âme", NamedTextColor.GRAY)));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("choose", "reset", "info", "help"));
            if (sender.hasPermission("customclasses.admin")) {
                subs.add("set");
                subs.add("giveame");
            }
            for (String s : subs) {
                if (s.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(s);
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("reset") || sub.equals("set") || sub.equals("info") || sub.equals("giveame") || sub.equals("ame")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set") && sender.hasPermission("customclasses.admin")) {
            for (String c : CLASS_NAMES) {
                if (c.toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(c);
                }
            }
        }

        return completions;
    }
}

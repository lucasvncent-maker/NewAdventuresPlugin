package fr.loual.customclasses.jobs.commands;

import fr.loual.customclasses.CustomClasses;
import fr.loual.customclasses.jobs.AgriculteurMissions;
import fr.loual.customclasses.jobs.JobManager;
import fr.loual.customclasses.jobs.JobMission;
import fr.loual.customclasses.jobs.PlayerJob;
import fr.loual.customclasses.jobs.gui.JobRecipeGui;
import fr.loual.customclasses.jobs.gui.JobSelectionGui;
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

public class JobCommand implements CommandExecutor, TabCompleter {

    private final CustomClasses plugin;
    private final JobManager jobManager;

    private static final List<String> JOB_NAMES = List.of(
            "agriculteur",
            "none"
    );

    public JobCommand(CustomClasses plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                JobSelectionGui.open(plugin, player);
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
                JobSelectionGui.open(plugin, player);
                return true;
            }

            case "recipes", "recipe", "recettes", "recette" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Cette commande ne peut être exécutée que par un joueur.", NamedTextColor.RED));
                    return true;
                }
                String rName = (args.length >= 2) ? args[1] : JobRecipeGui.RECIPE_FARMER_SOUP;
                JobRecipeGui.open(plugin, player, rName);
                return true;
            }

            case "reset" -> {
                Player target;
                if (args.length >= 2) {
                    if (!sender.hasPermission("customclasses.admin")) {
                        sender.sendMessage(Component.text("Vous n'avez pas la permission de réinitialiser le métier d'un autre joueur.", NamedTextColor.RED));
                        return true;
                    }
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

                jobManager.resetPlayerJob(target);
                if (!sender.equals(target)) {
                    sender.sendMessage(Component.text("Métier de " + target.getName() + " réinitialisé avec succès.", NamedTextColor.GREEN));
                }
                return true;
            }

            case "set" -> {
                if (!sender.hasPermission("customclasses.admin")) {
                    sender.sendMessage(Component.text("Vous n'avez pas la permission de définir un métier.", NamedTextColor.RED));
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /" + label + " set <joueur> <métier> [niveau]", NamedTextColor.RED));
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Joueur introuvable : " + args[1], NamedTextColor.RED));
                    return true;
                }

                PlayerJob pj = PlayerJob.fromId(args[2]);
                if (pj == PlayerJob.NONE && !args[2].equalsIgnoreCase("none")) {
                    sender.sendMessage(Component.text("Métier invalide : " + args[2] + ". Options: " + String.join(", ", JOB_NAMES), NamedTextColor.RED));
                    return true;
                }

                jobManager.setPlayerJob(target, pj);

                if (args.length >= 4) {
                    try {
                        int level = Integer.parseInt(args[3]);
                        jobManager.setJobLevel(target, pj, Math.max(0, Math.min(4, level)));
                    } catch (NumberFormatException ignored) {}
                }

                sender.sendMessage(Component.text("Métier de " + target.getName() + " changé pour " + pj.getDisplayName() + ".", NamedTextColor.GREEN));
                target.sendMessage(Component.text("Votre métier a été défini sur " + pj.getDisplayName() + " par un administrateur.", NamedTextColor.GOLD));
                return true;
            }

            case "get", "info", "stats" -> {
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

                PlayerJob pj = jobManager.getPlayerJob(target);
                sender.sendMessage(Component.text("==== [ Métier de " + target.getName() + " ] ====", NamedTextColor.GOLD, TextDecoration.BOLD));
                sender.sendMessage(Component.text("Métier actuel : ", NamedTextColor.GRAY)
                        .append(Component.text(pj.getDisplayName(), NamedTextColor.AQUA, TextDecoration.BOLD)));

                if (pj == PlayerJob.AGRICULTEUR) {
                    int level = jobManager.getJobLevel(target, pj);
                    sender.sendMessage(Component.text("Niveau de mission complété : " + level + " / 4", NamedTextColor.YELLOW));

                    if (level < 4) {
                        JobMission current = AgriculteurMissions.getMission(level + 1);
                        if (current != null) {
                            sender.sendMessage(Component.empty());
                            sender.sendMessage(Component.text("✦ En cours : " + current.getTitle(), NamedTextColor.GOLD, TextDecoration.BOLD));
                            for (JobMission.Requirement req : current.getRequirements()) {
                                int p = jobManager.getRequirementProgress(target, pj, level + 1, req.key());
                                NamedTextColor col = (p >= req.requiredAmount()) ? NamedTextColor.GREEN : NamedTextColor.WHITE;
                                sender.sendMessage(Component.text("  • " + req.displayName() + " : " + p + " / " + req.requiredAmount(), col));
                            }
                            sender.sendMessage(Component.text("✦ Récompense : ", NamedTextColor.AQUA)
                                    .append(Component.text(current.getRewardDescription(), NamedTextColor.GRAY)));
                        }
                    } else {
                        sender.sendMessage(Component.text("★ Félicitations ! Toutes les missions du métier sont accomplies !", NamedTextColor.GREEN, TextDecoration.BOLD));
                    }

                    if (level >= 1) {
                        sender.sendMessage(Component.empty());
                        sender.sendMessage(Component.text("✦ Recettes débloquées :", NamedTextColor.GOLD, TextDecoration.BOLD));
                        sender.sendMessage(Component.text("  • Soupe de l'Agriculteur : 1x Bol, 1x Carotte, 1x Patate, 1x Blé", NamedTextColor.YELLOW));
                        if (level >= 3) {
                            sender.sendMessage(Component.text("  • Space Cookie : 1x Cookie, 1x Baie Lumineuse", NamedTextColor.YELLOW));
                        }
                        if (level >= 4) {
                            sender.sendMessage(Component.text("  • Soupe Merveilleuse : 9 ingrédients (Bol + récoltes variées)", NamedTextColor.YELLOW));
                            sender.sendMessage(Component.text("  • Houe Merveilleuse : 2x Cuivres, 2x Bâtons", NamedTextColor.YELLOW));
                        }
                        sender.sendMessage(Component.text("➜ Tapez /" + label + " recipes pour afficher la table de craft interactive !", NamedTextColor.AQUA));
                    }
                }
                return true;
            }

            default -> {
                sendHelp(sender, label);
                return true;
            }
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("==== [ Système de Métiers ] ====", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("/" + label + " ", NamedTextColor.YELLOW)
                .append(Component.text("- Ouvrir le menu des métiers et des missions", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " recipes [nom] ", NamedTextColor.YELLOW)
                .append(Component.text("- Consulter les recettes de craft exclusives dans l'établi", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " info [joueur] ", NamedTextColor.YELLOW)
                .append(Component.text("- Voir sa progression détaillée et ses recettes", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " reset [joueur] ", NamedTextColor.YELLOW)
                .append(Component.text("- Réinitialiser son métier", NamedTextColor.GRAY)));
        if (sender.hasPermission("customclasses.admin")) {
            sender.sendMessage(Component.text("/" + label + " set <joueur> <métier> [niveau] ", NamedTextColor.YELLOW)
                    .append(Component.text("- Définir directement le métier et niveau", NamedTextColor.GRAY)));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("choose", "menu", "info", "reset", "recipes", "recettes"));
            if (sender.hasPermission("customclasses.admin")) {
                subs.add("set");
            }
            for (String s : subs) {
                if (s.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(s);
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("reset") || sub.equals("set") || sub.equals("info")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            } else if (sub.equals("recipes") || sub.equals("recipe") || sub.equals("recettes") || sub.equals("recette")) {
                List<String> recipeNames = List.of("farmer_soup", "space_cookie", "wonderful_soup", "wonderful_hoe");
                for (String r : recipeNames) {
                    if (r.toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(r);
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set") && sender.hasPermission("customclasses.admin")) {
            for (String j : JOB_NAMES) {
                if (j.toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(j);
                }
            }
        }

        return completions;
    }
}

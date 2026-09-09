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
        if (args.length == 0) {
            sender.sendMessage(Component.text("==== [ Commandes Horde ] ====", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            sender.sendMessage(Component.text("/" + label + " join ", NamedTextColor.YELLOW)
                    .append(Component.text("- Rejoindre le combat dans l'Arène", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/" + label + " leave ", NamedTextColor.YELLOW)
                    .append(Component.text("- Quitter l'Arène et revenir à son point de départ", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/" + label + " status ", NamedTextColor.YELLOW)
                    .append(Component.text("- Voir le statut de la Horde", NamedTextColor.GRAY)));
            if (sender.hasPermission("horde.admin")) {
                sender.sendMessage(Component.text("/" + label + " start ", NamedTextColor.YELLOW)
                        .append(Component.text("- Déclencher l'invasion manuellement (Admin)", NamedTextColor.GRAY)));
                sender.sendMessage(Component.text("/" + label + " stop ", NamedTextColor.YELLOW)
                        .append(Component.text("- Arrêter l'invasion en cours (Admin)", NamedTextColor.GRAY)));
                sender.sendMessage(Component.text("/" + label + " arena ", NamedTextColor.YELLOW)
                        .append(Component.text("- Se téléporter dans l'arène en mode créatif pour construire (Admin)", NamedTextColor.GRAY)));
                sender.sendMessage(Component.text("/" + label + " customarena [on|off] ", NamedTextColor.YELLOW)
                        .append(Component.text("- Activer le mode arène personnalisée pour protéger vos constructions (Admin)", NamedTextColor.GRAY)));
                sender.sendMessage(Component.text("/" + label + " resetarena ", NamedTextColor.YELLOW)
                        .append(Component.text("- Régénérer l'arène par défaut en blackstone (Admin)", NamedTextColor.GRAY)));
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "join" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Cette commande doit être exécutée par un joueur en jeu.", NamedTextColor.RED));
                    return true;
                }
                hordeManager.joinArena(player);
                return true;
            }
            case "leave" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Cette commande doit être exécutée par un joueur en jeu.", NamedTextColor.RED));
                    return true;
                }
                if (!hordeManager.isInArena(player.getLocation()) && hordeManager.getReturnLocation(player.getUniqueId()) == null) {
                    player.sendMessage(Component.text("Vous n'êtes pas dans l'Arène de la Horde.", NamedTextColor.YELLOW));
                    return true;
                }
                hordeManager.leaveArena(player);
                return true;
            }
            case "status" -> {
                if (hordeManager.isHordeActive()) {
                    sender.sendMessage(Component.text("Statut Horde : ", NamedTextColor.GOLD)
                            .append(Component.text("ACTIVE [" + hordeManager.getCurrentTier().getDisplayName() + "] (Vague " + hordeManager.getCurrentWave() + "/4)", hordeManager.getCurrentTier().getColor(), TextDecoration.BOLD)));
                } else {
                    sender.sendMessage(Component.text("Statut Horde : INACTIVE", NamedTextColor.GRAY));
                }
                boolean custom = hordeManager.isCustomArena();
                sender.sendMessage(Component.text("Mode Arène Personnalisée : " + (custom ? "ACTIVÉ (protégée)" : "DÉSACTIVÉ (arène par défaut)"), custom ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                return true;
            }
            case "start" -> {
                if (!sender.hasPermission("horde.admin")) {
                    sender.sendMessage(Component.text("Vous n'avez pas la permission d'utiliser cette commande.", NamedTextColor.RED));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Cette commande doit être exécutée par un joueur en jeu.", NamedTextColor.RED));
                    return true;
                }
                if (hordeManager.isHordeActive()) {
                    player.sendMessage(Component.text("Une Horde est déjà active !", NamedTextColor.RED));
                    return true;
                }

                fr.loual.horde.HordeTier tier = fr.loual.horde.HordeTier.INGOT;
                if (args.length > 1) {
                    String param = args[1].toLowerCase();
                    if (param.contains("reinf") || param.contains("renforc") || param.contains("apocal") || param.contains("extreme")) {
                        tier = fr.loual.horde.HordeTier.REINFORCED_BLOCK;
                    } else if (param.contains("bloc") || param.contains("block") || param.contains("hero")) {
                        tier = fr.loual.horde.HordeTier.BLOCK;
                    }
                }

                hordeManager.startHorde(player, player.getLocation(), tier);
                player.sendMessage(Component.text("✦ Invasion de la Horde [" + tier.getDisplayName() + "] lancée dans l'Arène !", tier.getColor(), TextDecoration.BOLD));
                return true;
            }
            case "stop" -> {
                if (!sender.hasPermission("horde.admin")) {
                    sender.sendMessage(Component.text("Vous n'avez pas la permission d'utiliser cette commande.", NamedTextColor.RED));
                    return true;
                }
                if (!hordeManager.isHordeActive()) {
                    sender.sendMessage(Component.text("Aucune Horde n'est active actuellement.", NamedTextColor.YELLOW));
                    return true;
                }
                hordeManager.stopHorde(true);
                sender.sendMessage(Component.text("✦ L'événement de la Horde a été arrêté.", NamedTextColor.RED));
                return true;
            }
            case "arena", "build" -> {
                if (!sender.hasPermission("horde.admin")) {
                    sender.sendMessage(Component.text("Vous n'avez pas la permission d'utiliser cette commande.", NamedTextColor.RED));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Cette commande doit être exécutée par un joueur en jeu.", NamedTextColor.RED));
                    return true;
                }
                org.bukkit.World hordeWorld = hordeManager.getOrCreateHordeWorld();
                if (hordeWorld == null) {
                    player.sendMessage(Component.text("Impossible de charger le monde de l'Arène.", NamedTextColor.RED));
                    return true;
                }
                hordeManager.ensureArenaBuilt(hordeWorld);
                org.bukkit.Location loc = hordeManager.getPlayerSpawnLocation();
                player.teleportAsync(loc).thenAccept(s -> {
                    if (s && player.isOnline()) {
                        player.setGameMode(org.bukkit.GameMode.CREATIVE);
                        player.sendMessage(Component.text("✦ Bienvenue dans l'Arène de la Horde en Mode Créatif !", NamedTextColor.GREEN, TextDecoration.BOLD));
                        player.sendMessage(Component.text("Centre de l'Arène : X=0, Y=100, Z=0 (Rayon de combat: 24 blocs).", NamedTextColor.YELLOW));
                        player.sendMessage(Component.text("Bouton de départ au centre : X=0, Y=102, Z=0 sur socle Y=101.", NamedTextColor.YELLOW));
                        player.sendMessage(Component.text("Pour protéger vos constructions contre la régénération automatique, tapez : ", NamedTextColor.AQUA)
                                .append(Component.text("/horde customarena on", NamedTextColor.GOLD, TextDecoration.BOLD)));
                    }
                });
                return true;
            }
            case "customarena" -> {
                if (!sender.hasPermission("horde.admin")) {
                    sender.sendMessage(Component.text("Vous n'avez pas la permission d'utiliser cette commande.", NamedTextColor.RED));
                    return true;
                }
                if (args.length > 1) {
                    String mode = args[1].toLowerCase();
                    if (mode.equals("on") || mode.equals("true") || mode.equals("enable") || mode.equals("1")) {
                        hordeManager.setCustomArena(true);
                        sender.sendMessage(Component.text("✦ Mode Arène Personnalisée : ACTIVÉ ! Vos constructions sont désormais protégées, le plugin ne touchera à aucun de vos blocs.", NamedTextColor.GREEN, TextDecoration.BOLD));
                    } else if (mode.equals("off") || mode.equals("false") || mode.equals("disable") || mode.equals("0")) {
                        hordeManager.setCustomArena(false);
                        sender.sendMessage(Component.text("✦ Mode Arène Personnalisée : DÉSACTIVÉ.", NamedTextColor.YELLOW));
                    } else {
                        sender.sendMessage(Component.text("Utilisation : /" + label + " customarena [on|off]", NamedTextColor.RED));
                    }
                } else {
                    boolean cur = hordeManager.isCustomArena();
                    sender.sendMessage(Component.text("✦ Mode Arène Personnalisée actuel : " + (cur ? "ACTIVÉ (vos blocs sont protégés)" : "DÉSACTIVÉ (arène par défaut)"), cur ? NamedTextColor.GREEN : NamedTextColor.YELLOW, TextDecoration.BOLD));
                }
                return true;
            }
            case "resetarena" -> {
                if (!sender.hasPermission("horde.admin")) {
                    sender.sendMessage(Component.text("Vous n'avez pas la permission d'utiliser cette commande.", NamedTextColor.RED));
                    return true;
                }
                org.bukkit.World hordeWorld = hordeManager.getOrCreateHordeWorld();
                if (hordeWorld == null) {
                    sender.sendMessage(Component.text("Impossible de charger le monde de l'Arène.", NamedTextColor.RED));
                    return true;
                }
                hordeManager.resetDefaultArena(hordeWorld);
                sender.sendMessage(Component.text("✦ L'Arène par défaut en blackstone a été reconstruite à zéro (X=0, Y=100, Z=0) !", NamedTextColor.GREEN, TextDecoration.BOLD));
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
            List<String> options = new ArrayList<>(List.of("join", "leave", "status"));
            if (sender.hasPermission("horde.admin")) {
                options.add("start");
                options.add("stop");
                options.add("arena");
                options.add("customarena");
                options.add("resetarena");
            }
            for (String s : options) {
                if (s.startsWith(args[0].toLowerCase())) list.add(s);
            }
            return list;
        } else if (args.length == 2 && "start".equalsIgnoreCase(args[0]) && sender.hasPermission("horde.admin")) {
            List<String> list = new ArrayList<>();
            List<String> tiers = List.of("standard", "heroique", "apocalypse");
            for (String t : tiers) {
                if (t.startsWith(args[1].toLowerCase())) list.add(t);
            }
            return list;
        } else if (args.length == 2 && "customarena".equalsIgnoreCase(args[0]) && sender.hasPermission("horde.admin")) {
            List<String> list = new ArrayList<>();
            List<String> modes = List.of("on", "off");
            for (String m : modes) {
                if (m.startsWith(args[1].toLowerCase())) list.add(m);
            }
            return list;
        }
        return List.of();
    }
}

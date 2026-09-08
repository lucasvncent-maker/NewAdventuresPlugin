package fr.loual.casino.commands;

import fr.loual.casino.spawner.VillageCroupierSpawner;
import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CasinoCommand implements CommandExecutor, TabCompleter {

    private final NewAdventurePlugin plugin;
    private final VillageCroupierSpawner spawner;

    public CasinoCommand(NewAdventurePlugin plugin, VillageCroupierSpawner spawner) {
        this.plugin = plugin;
        this.spawner = spawner;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Cette commande doit être exécutée par un joueur.", NamedTextColor.RED));
            return true;
        }

        // Sous-commandes publiques : stats & top/leaderboard
        String sub = args.length > 0 ? args[0].toLowerCase() : (label.equalsIgnoreCase("croupier") ? "spawn" : "help");

        if (sub.equals("stats")) {
            Player target = player;
            if (args.length > 1) {
                Player found = Bukkit.getPlayer(args[1]);
                if (found != null) {
                    target = found;
                }
            }

            var stats = plugin.getCasinoStatsManager().getStats(target.getUniqueId(), target.getName());
            player.sendMessage(Component.text("★ ========================================= ★", NamedTextColor.GOLD, TextDecoration.BOLD));
            player.sendMessage(Component.text("        ♠ STATISTIQUES CASINO : " + stats.name().toUpperCase() + " ♠", NamedTextColor.YELLOW, TextDecoration.BOLD));
            player.sendMessage(Component.text("  • Mains jouées : ", NamedTextColor.GRAY).append(Component.text(stats.handsPlayed(), NamedTextColor.WHITE, TextDecoration.BOLD)));
            player.sendMessage(Component.text("  • Victoires : ", NamedTextColor.GRAY)
                    .append(Component.text(stats.handsWon(), NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text(String.format(" (%.1f%%)", stats.getWinRate()), NamedTextColor.DARK_GREEN)));
            player.sendMessage(Component.text("  • Blackjacks naturels (x3) : ", NamedTextColor.GRAY).append(Component.text(stats.naturalBlackjacks(), NamedTextColor.GOLD, TextDecoration.BOLD)));
            player.sendMessage(Component.text("  • Record Jetons Défi : ", NamedTextColor.GRAY).append(Component.text(stats.highestChips() + " Jetons", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)));
            player.sendMessage(Component.text("  • Cuprites remportées : ", NamedTextColor.GRAY).append(Component.text(stats.cupritesWon() + " Lingot(s)", NamedTextColor.AQUA, TextDecoration.BOLD)));
            player.sendMessage(Component.text("★ ========================================= ★", NamedTextColor.GOLD, TextDecoration.BOLD));
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
            return true;
        }

        if (sub.equals("top") || sub.equals("leaderboard") || sub.equals("classement")) {
            var topCuprite = plugin.getCasinoStatsManager().getTopByCuprites(5);
            var topChips = plugin.getCasinoStatsManager().getTopByHighestChips(5);

            player.sendMessage(Component.text("★ ========================================= ★", NamedTextColor.GOLD, TextDecoration.BOLD));
            player.sendMessage(Component.text("     🏆 LEADERBOARD CASINO & DÉFI CUPRITE 🏆", NamedTextColor.YELLOW, TextDecoration.BOLD));
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("✦ Top Lingots de Cuprite Gagnés :", NamedTextColor.AQUA, TextDecoration.BOLD));
            if (topCuprite.isEmpty()) {
                player.sendMessage(Component.text("  (Aucune cuprite remportée pour le moment)", NamedTextColor.GRAY));
            } else {
                for (int i = 0; i < topCuprite.size(); i++) {
                    var s = topCuprite.get(i);
                    player.sendMessage(Component.text("  " + (i + 1) + ". ", NamedTextColor.YELLOW)
                            .append(Component.text(s.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                            .append(Component.text(" ➔ " + s.cupritesWon() + " Cuprite(s)", NamedTextColor.AQUA)));
                }
            }

            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("✦ Top Record de Jetons de Défi :", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            if (topChips.isEmpty()) {
                player.sendMessage(Component.text("  (Aucune manche enregistrée pour le moment)", NamedTextColor.GRAY));
            } else {
                for (int i = 0; i < topChips.size(); i++) {
                    var s = topChips.get(i);
                    player.sendMessage(Component.text("  " + (i + 1) + ". ", NamedTextColor.YELLOW)
                            .append(Component.text(s.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                            .append(Component.text(" ➔ " + s.highestChips() + " Jetons", NamedTextColor.LIGHT_PURPLE)));
                }
            }
            player.sendMessage(Component.text("★ ========================================= ★", NamedTextColor.GOLD, TextDecoration.BOLD));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.2f);
            return true;
        }

        // Commandes Admin (spawn & remove)
        if (!player.hasPermission("casino.admin") && !player.isOp()) {
            player.sendMessage(Component.text("Vous n'avez pas la permission d'utiliser cette commande d'administration.", NamedTextColor.RED));
            return true;
        }

        switch (sub) {
            case "spawn", "summon", "create" -> {
                Location loc = player.getLocation();
                Villager croupier = VillageCroupierSpawner.spawnCroupier(loc, plugin);
                if (croupier != null) {
                    player.sendMessage(Component.text("♠ Croupier de Blackjack créé avec succès à votre position !", NamedTextColor.GOLD, TextDecoration.BOLD));
                    player.playSound(loc, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
                } else {
                    player.sendMessage(Component.text("Erreur lors de la création du croupier.", NamedTextColor.RED));
                }
            }
            case "remove", "delete", "kill" -> {
                Location loc = player.getLocation();
                Collection<Entity> nearby = loc.getWorld().getNearbyEntities(loc, 5, 5, 5, spawner::isCroupier);
                if (nearby.isEmpty()) {
                    player.sendMessage(Component.text("Aucun Croupier trouvé à moins de 5 blocs.", NamedTextColor.RED));
                } else {
                    int count = 0;
                    for (Entity ent : nearby) {
                        ent.remove();
                        count++;
                    }
                    player.sendMessage(Component.text(count + " Croupier(s) supprimé(s).", NamedTextColor.GREEN));
                    player.playSound(loc, Sound.ENTITY_ITEM_BREAK, 1.0f, 1.2f);
                }
            }
            default -> {
                player.sendMessage(Component.text("=== Commandes Casino / Blackjack ===", NamedTextColor.GOLD, TextDecoration.BOLD));
                player.sendMessage(Component.text("/casino stats [joueur] §7- Consulter vos statistiques ou celles d'un joueur", NamedTextColor.YELLOW));
                player.sendMessage(Component.text("/casino top §7- Afficher le classement des meilleurs joueurs", NamedTextColor.YELLOW));
                if (player.hasPermission("casino.admin") || player.isOp()) {
                    player.sendMessage(Component.text("/casino spawn §7- Faire apparaître un Croupier de Blackjack", NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("/casino remove §7- Supprimer le Croupier le plus proche (5 blocs)", NamedTextColor.YELLOW));
                }
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("stats", "top", "leaderboard"));
            if (sender.hasPermission("casino.admin") || sender.isOp()) {
                subs.add("spawn");
                subs.add("remove");
            }
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) list.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    list.add(p.getName());
                }
            }
        }
        return list;
    }
}

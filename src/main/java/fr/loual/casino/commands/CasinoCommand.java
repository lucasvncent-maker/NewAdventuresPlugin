package fr.loual.casino.commands;

import fr.loual.casino.spawner.VillageCroupierSpawner;
import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

        if (!player.hasPermission("casino.admin") && !player.isOp()) {
            player.sendMessage(Component.text("Vous n'avez pas la permission d'utiliser cette commande.", NamedTextColor.RED));
            return true;
        }

        // Si la commande utilisée est directement /croupier sans argument, ou /casino spawn
        String sub = args.length > 0 ? args[0].toLowerCase() : (label.equalsIgnoreCase("croupier") ? "spawn" : "help");

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
                player.sendMessage(Component.text("/casino spawn §7- Faire apparaître un Croupier de Blackjack", NamedTextColor.YELLOW));
                player.sendMessage(Component.text("/casino remove §7- Supprimer le Croupier le plus proche (5 blocs)", NamedTextColor.YELLOW));
                player.sendMessage(Component.text("/croupier §7- Raccourci pour faire apparaître un Croupier", NamedTextColor.YELLOW));
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            if ("spawn".startsWith(args[0].toLowerCase())) list.add("spawn");
            if ("remove".startsWith(args[0].toLowerCase())) list.add("remove");
            if ("help".startsWith(args[0].toLowerCase())) list.add("help");
        }
        return list;
    }
}

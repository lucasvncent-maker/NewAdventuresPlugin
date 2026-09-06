package fr.loual.customminerals.commands;

import fr.loual.newadventure.NewAdventurePlugin;
import fr.loual.customminerals.items.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CustomMineralsCommand implements CommandExecutor, TabCompleter {

    private final NewAdventurePlugin plugin;

    private static final List<String> ITEM_TYPES = List.of(
            "cuprite",
            "hammer",
            "hammer2",
            "hammer3",
            "pickaxe",
            "axe",
            "chest",
            "block",
            "reinforced_block"
    );

    public CustomMineralsCommand(NewAdventurePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("customminerals.admin")) {
            sender.sendMessage(Component.text("Vous n'avez pas la permission d'utiliser cette commande.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(Component.text("[CustomMinerals] Configuration rechargée avec succès !", NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            Player targetPlayer;
            String itemType = "cuprite";
            int amount = 1;

            if (args.length >= 2) {
                targetPlayer = Bukkit.getPlayer(args[1]);
                if (targetPlayer == null) {
                    sender.sendMessage(Component.text("Joueur introuvable : " + args[1], NamedTextColor.RED));
                    return true;
                }
            } else if (sender instanceof Player player) {
                targetPlayer = player;
            } else {
                sender.sendMessage(Component.text("Veuillez spécifier un joueur depuis la console : /" + label + " give <joueur> [item] [quantité]", NamedTextColor.RED));
                return true;
            }

            if (args.length >= 3) {
                itemType = args[2].toLowerCase();
            }

            if (args.length >= 4) {
                try {
                    amount = Integer.parseInt(args[3]);
                    if (amount <= 0) {
                        sender.sendMessage(Component.text("La quantité doit être supérieure à 0.", NamedTextColor.RED));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Quantité invalide : " + args[3], NamedTextColor.RED));
                    return true;
                }
            }

            ItemStack itemToGive = resolveItem(itemType, amount);
            if (itemToGive == null) {
                sender.sendMessage(Component.text("Item inconnu : " + itemType + ". Options: " + String.join(", ", ITEM_TYPES), NamedTextColor.RED));
                return true;
            }

            targetPlayer.getInventory().addItem(itemToGive);

            String itemName = itemToGive.hasItemMeta() && itemToGive.getItemMeta().hasDisplayName()
                    ? itemType
                    : itemToGive.getType().name();

            sender.sendMessage(
                    Component.text("Donné ", NamedTextColor.GREEN)
                            .append(Component.text(amount + "x ", NamedTextColor.YELLOW))
                            .append(Component.text(itemName, NamedTextColor.GOLD))
                            .append(Component.text(" à " + targetPlayer.getName() + ".", NamedTextColor.GREEN))
            );

            if (!sender.equals(targetPlayer)) {
                targetPlayer.sendMessage(
                        Component.text("Vous avez reçu ", NamedTextColor.GREEN)
                                .append(Component.text(amount + "x ", NamedTextColor.YELLOW))
                                .append(Component.text(itemName, NamedTextColor.GOLD))
                                .append(Component.text(" !", NamedTextColor.GREEN))
                );
            }

            return true;
        }

        sendHelp(sender, label);
        return true;
    }

    private ItemStack resolveItem(String type, int amount) {
        return switch (type.toLowerCase()) {
            case "cuprite", "ingot" -> Cuprite.create(plugin, amount);
            case "hammer", "hammer1" -> CupriteHammer.create(plugin, 1);
            case "hammer2", "hammer_improved" -> CupriteHammer.create(plugin, 2);
            case "hammer3", "hammer_reinforced" -> CupriteHammer.create(plugin, 3);
            case "pickaxe" -> CupritePickaxe.create(plugin);
            case "axe" -> CupriteAxe.create(plugin);
            case "chest" -> CupriteChest.create(plugin, amount);
            case "block" -> CupriteBlock.create(plugin, amount);
            case "reinforced_block" -> ReinforcedCupriteBlock.create(plugin, amount);
            default -> null;
        };
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("==== [ CustomMinerals ] ====", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/" + label + " give [joueur] [item] [quantité] ", NamedTextColor.YELLOW)
                .append(Component.text("- Se donner ou donner un item personnalisé", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  Items: cuprite, hammer, hammer2, hammer3, pickaxe, axe, chest, block, reinforced_block", NamedTextColor.DARK_AQUA));
        sender.sendMessage(Component.text("/" + label + " reload ", NamedTextColor.YELLOW)
                .append(Component.text("- Recharger la configuration", NamedTextColor.GRAY)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("customminerals.admin")) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = List.of("give", "reload", "help");
            for (String sub : subcommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (String it : ITEM_TYPES) {
                if (it.toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(it);
                }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            List<String> amounts = List.of("1", "16", "32", "64");
            for (String a : amounts) {
                if (a.startsWith(args[3])) {
                    completions.add(a);
                }
            }
        }

        return completions;
    }
}

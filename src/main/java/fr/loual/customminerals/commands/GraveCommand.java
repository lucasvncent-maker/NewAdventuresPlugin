package fr.loual.customminerals.commands;

import fr.loual.customminerals.listeners.DeathChestListener;
import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class GraveCommand implements CommandExecutor, TabCompleter {

    private final NewAdventurePlugin plugin;
    private final DeathChestListener deathChestListener;

    public GraveCommand(NewAdventurePlugin plugin, DeathChestListener deathChestListener) {
        this.plugin = plugin;
        this.deathChestListener = deathChestListener;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Cette commande ne peut être exécutée que par un joueur.", NamedTextColor.RED));
            return true;
        }

        // 1. Recherche d'une tombe posée dans un rayon de 15 blocs
        Block chestBlock = deathChestListener.findNearbyDeathChest(player, 15);
        if (chestBlock != null) {
            boolean claimed = deathChestListener.claimDeathChest(player, chestBlock);
            if (claimed) {
                return true;
            }
        }

        // 2. Si aucune tombe physique n'est trouvée, vérification de la sauvegarde de sécurité
        String locStr = player.getPersistentDataContainer().get(deathChestListener.getBackupLocKey(), PersistentDataType.STRING);
        boolean hasBackupItems = player.getPersistentDataContainer().has(deathChestListener.getBackupItemsKey(), PersistentDataType.BYTE_ARRAY);

        if (hasBackupItems && locStr != null) {
            String[] parts = locStr.split(";");
            if (parts.length >= 4) {
                String worldName = parts[0];
                try {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);

                    boolean isSameWorld = player.getWorld().getName().equalsIgnoreCase(worldName);
                    double dist = isSameWorld ? player.getLocation().distance(new Location(player.getWorld(), x, y, z)) : Double.MAX_VALUE;

                    boolean forceRestore = args.length > 0 && (args[0].equalsIgnoreCase("restore") || args[0].equalsIgnoreCase("force"));

                    if (forceRestore || (isSameWorld && dist <= 25.0)) {
                        boolean restored = deathChestListener.restoreFromPlayerBackup(player);
                        if (restored) {
                            return true;
                        }
                    }

                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("✦ Aucune tombe à portée immédiate (15 blocs).", NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("✦ Vos dernières coordonnées de mort : ", NamedTextColor.GRAY)
                            .append(Component.text("X: " + x + "  Y: " + y + "  Z: " + z + " (" + worldName + ")", NamedTextColor.AQUA, TextDecoration.BOLD)));
                    if (isSameWorld) {
                        player.sendMessage(Component.text("✦ Distance : " + (int) dist + " blocs. Rapprochez-vous de votre tombe pour la récupérer !", NamedTextColor.GRAY));
                    } else {
                        player.sendMessage(Component.text("✦ Vous devez être dans le monde '" + worldName + "' pour la récupérer !", NamedTextColor.GRAY));
                    }
                    player.sendMessage(Component.text("✦ Si votre tombe a été détruite par un bug : ", NamedTextColor.GRAY)
                            .append(Component.text("/tombe restore", NamedTextColor.GOLD, TextDecoration.BOLD))
                            .append(Component.text(" pour forcer la restauration.", NamedTextColor.GRAY)));
                    player.sendMessage(Component.empty());
                    return true;
                } catch (NumberFormatException ignored) {}
            }
        }

        player.sendMessage(Component.text("✦ Aucune tombe trouvée dans un rayon de 15 blocs et aucune sauvegarde disponible.", NamedTextColor.RED));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = List.of("restore");
            for (String s : subs) {
                if (s.toLowerCase().startsWith(args[0].toLowerCase())) {
                    list.add(s);
                }
            }
        }
        return list;
    }
}

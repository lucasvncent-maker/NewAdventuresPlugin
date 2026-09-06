package fr.loual.customminerals.listeners;

import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DeathChestListener implements Listener {

    private final NewAdventurePlugin plugin;
    private final NamespacedKey deathChestKey;
    private final NamespacedKey itemsKey;
    private final NamespacedKey ownerUuidKey;
    private final NamespacedKey ownerNameKey;

    public DeathChestListener(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.deathChestKey = new NamespacedKey(plugin, "is_death_chest");
        this.itemsKey = new NamespacedKey(plugin, "death_chest_items");
        this.ownerUuidKey = new NamespacedKey(plugin, "death_chest_owner_uuid");
        this.ownerNameKey = new NamespacedKey(plugin, "death_chest_owner_name");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        // Si keepInventory est activé ou que le joueur n'a aucun drop, on ne crée pas de coffre
        Boolean keepInv = world.getGameRuleValue(GameRule.KEEP_INVENTORY);
        if (Boolean.TRUE.equals(keepInv) || event.getDrops().isEmpty()) {
            return;
        }

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear(); // Empêche les items de se disperser au sol

        Location deathLoc = player.getLocation();
        Block chestBlock = findSafeAirBlock(deathLoc);

        chestBlock.setType(Material.PLAYER_HEAD);
        if (chestBlock.getState() instanceof org.bukkit.block.Skull skull) {
            skull.setOwningPlayer(player);
            skull.customName(Component.text("Tombe de " + player.getName(), NamedTextColor.RED, TextDecoration.BOLD));

            PersistentDataContainer pdc = skull.getPersistentDataContainer();
            pdc.set(deathChestKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(ownerUuidKey, PersistentDataType.STRING, player.getUniqueId().toString());
            pdc.set(ownerNameKey, PersistentDataType.STRING, player.getName());

            byte[] serialized = ItemStack.serializeItemsAsBytes(drops);
            pdc.set(itemsKey, PersistentDataType.BYTE_ARRAY, serialized);
            skull.update();
        }

        // Notification au joueur mort
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("☠ VOUS ÊTES MORT !", NamedTextColor.RED, TextDecoration.BOLD));
        player.sendMessage(Component.text("✦ Vos affaires sont en sécurité dans votre ", NamedTextColor.GRAY)
                .append(Component.text("Tombe Funéraire", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" aux coordonnées :", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  X: " + chestBlock.getX() + "  Y: " + chestBlock.getY() + "  Z: " + chestBlock.getZ(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("✦ Faites simplement un ", NamedTextColor.GRAY)
                .append(Component.text("Clic Droit", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(" sur la tête pour tout récupérer instantanément !", NamedTextColor.GRAY)));
        player.sendMessage(Component.empty());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || !isDeathChestMaterial(clicked.getType())) {
            return;
        }

        if (!(clicked.getState() instanceof TileState tileState)) {
            return;
        }

        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        if (!pdc.has(deathChestKey, PersistentDataType.BYTE)) {
            return;
        }

        // C'est un coffre/tombe de mort : on annule l'interaction vanilla
        event.setCancelled(true);

        Player player = event.getPlayer();
        byte[] serialized = pdc.get(itemsKey, PersistentDataType.BYTE_ARRAY);
        if (serialized == null) {
            clicked.setType(Material.AIR);
            return;
        }

        ItemStack[] items = ItemStack.deserializeItemsFromBytes(serialized);

        // Donner tous les items au joueur
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(items);
        for (ItemStack leftover : overflow.values()) {
            if (leftover != null && !leftover.getType().isAir()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }

        // Sons et effets
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 1.2f);
        player.sendMessage(Component.text("✦ Vous avez récupéré l'intégralité de vos affaires !", NamedTextColor.GREEN, TextDecoration.BOLD));

        // Supprimer le bloc de mort
        clicked.setType(Material.AIR);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isDeathChestMaterial(block.getType())) {
            return;
        }

        if (!(block.getState() instanceof TileState tileState)) {
            return;
        }

        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        if (!pdc.has(deathChestKey, PersistentDataType.BYTE)) {
            return;
        }

        byte[] serialized = pdc.get(itemsKey, PersistentDataType.BYTE_ARRAY);
        if (serialized != null) {
            ItemStack[] items = ItemStack.deserializeItemsFromBytes(serialized);
            for (ItemStack item : items) {
                if (item != null && !item.getType().isAir()) {
                    block.getWorld().dropItemNaturally(block.getLocation(), item);
                }
            }
        }

        event.setDropItems(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isDeathChest);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isDeathChest);
    }

    private boolean isDeathChestMaterial(Material material) {
        return material == Material.PLAYER_HEAD || material == Material.PLAYER_WALL_HEAD || material == Material.CHEST;
    }

    private boolean isDeathChest(Block block) {
        if (!isDeathChestMaterial(block.getType())) return false;
        if (block.getState() instanceof TileState tileState) {
            return tileState.getPersistentDataContainer().has(deathChestKey, PersistentDataType.BYTE);
        }
        return false;
    }

    private Block findSafeAirBlock(Location loc) {
        World world = loc.getWorld();
        if (world == null) return loc.getBlock();

        int minHeight = world.getMinHeight();
        int maxHeight = world.getMaxHeight();

        int targetY = Math.clamp(loc.getBlockY(), minHeight + 1, maxHeight - 2);
        Location clampedLoc = loc.clone();
        clampedLoc.setY(targetY);

        Block original = clampedLoc.getBlock();
        if (original.getType().isAir()) {
            return original;
        }

        // Recherche du bloc d'air le plus proche dans une boîte 7x7x7
        Block best = null;
        double bestDistSq = Double.MAX_VALUE;

        int bx = clampedLoc.getBlockX();
        int by = clampedLoc.getBlockY();
        int bz = clampedLoc.getBlockZ();

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int y = by + dy;
                    if (y < minHeight || y >= maxHeight) continue;
                    Block candidate = world.getBlockAt(bx + dx, y, bz + dz);
                    if (candidate.getType().isAir()) {
                        double distSq = (dx * dx) + (dy * dy) + (dz * dz);
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            best = candidate;
                        }
                    }
                }
            }
        }

        // Si aucun bloc d'air n'est trouvé autour, on remplace le bloc à la position du joueur
        return best != null ? best : original;
    }
}

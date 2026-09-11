package fr.loual.customminerals.listeners;

import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DeathChestListener implements Listener {

    private final NewAdventurePlugin plugin;
    private final NamespacedKey deathChestKey;
    private final NamespacedKey itemsKey;
    private final NamespacedKey ownerUuidKey;
    private final NamespacedKey ownerNameKey;
    private final NamespacedKey backupItemsKey;
    private final NamespacedKey backupLocKey;

    public DeathChestListener(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.deathChestKey = new NamespacedKey(plugin, "is_death_chest");
        this.itemsKey = new NamespacedKey(plugin, "death_chest_items");
        this.ownerUuidKey = new NamespacedKey(plugin, "death_chest_owner_uuid");
        this.ownerNameKey = new NamespacedKey(plugin, "death_chest_owner_name");
        this.backupItemsKey = new NamespacedKey(plugin, "last_death_backup_items");
        this.backupLocKey = new NamespacedKey(plugin, "last_death_backup_loc");
    }

    public NamespacedKey getBackupItemsKey() {
        return backupItemsKey;
    }

    public NamespacedKey getBackupLocKey() {
        return backupLocKey;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        // Si keepInventory est activé ou que le joueur n'a aucun drop, on ne crée pas de coffre
        Boolean keepInv = world.getGameRuleValue(GameRule.KEEP_INVENTORY);
        if (Boolean.TRUE.equals(keepInv) || event.getKeepInventory() || event.getDrops().isEmpty()) {
            return;
        }

        // Sécurité absolue en mode Horde : le joueur conserve tout son équipement sans tombe
        if (plugin.getHordeManager() != null && 
                (plugin.getHordeManager().isParticipant(player.getUniqueId()) 
                || plugin.getHordeManager().isInArena(player.getLocation()) 
                || plugin.getHordeManager().isHordeWorld(world))) {
            return;
        }

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear(); // Empêche les items de se disperser au sol

        Location deathLoc = player.getLocation();
        Block chestBlock = findSafeAirBlock(deathLoc);

        chestBlock.setType(Material.PLAYER_HEAD);
        byte[] serialized = ItemStack.serializeItemsAsBytes(drops);

        if (chestBlock.getState() instanceof org.bukkit.block.Skull skull) {
            skull.setOwningPlayer(player);
            skull.customName(Component.text("Tombe de " + player.getName(), NamedTextColor.RED, TextDecoration.BOLD));

            PersistentDataContainer pdc = skull.getPersistentDataContainer();
            pdc.set(deathChestKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(ownerUuidKey, PersistentDataType.STRING, player.getUniqueId().toString());
            pdc.set(ownerNameKey, PersistentDataType.STRING, player.getName());
            pdc.set(itemsKey, PersistentDataType.BYTE_ARRAY, serialized);
            skull.update();
        }

        // Sauvegarde de sécurité dans les données du joueur (au cas où un bug détruirait le bloc)
        PersistentDataContainer playerPdc = player.getPersistentDataContainer();
        playerPdc.set(backupItemsKey, PersistentDataType.BYTE_ARRAY, serialized);
        playerPdc.set(backupLocKey, PersistentDataType.STRING, world.getName() + ";" + chestBlock.getX() + ";" + chestBlock.getY() + ";" + chestBlock.getZ());

        // Notification au joueur mort
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("☠ VOUS ÊTES MORT !", NamedTextColor.RED, TextDecoration.BOLD));
        player.sendMessage(Component.text("✦ Vos affaires sont en sécurité dans votre ", NamedTextColor.GRAY)
                .append(Component.text("Tombe Funéraire", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" aux coordonnées :", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  X: " + chestBlock.getX() + "  Y: " + chestBlock.getY() + "  Z: " + chestBlock.getZ(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("✦ Faites un ", NamedTextColor.GRAY)
                .append(Component.text("Clic Droit / Clic Gauche", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(" sur la tête (ou tapez ", NamedTextColor.GRAY))
                .append(Component.text("/tombe", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" à proximité) pour tout récupérer !", NamedTextColor.GRAY)));
        player.sendMessage(Component.empty());
    }

    // Empêche l'eau ou la lave de s'écouler dans la tombe et de la détruire
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (isDeathChest(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    // Empêche la physique vanilla de faire pop la tête en item lorsqu'elle est recouverte de liquide
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (isDeathChest(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    // Empêche les pistons de pousser ou détruire la tombe
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (isDeathChest(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (isDeathChest(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_AIR
                && action != Action.LEFT_CLICK_AIR) {
            return;
        }

        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();

        // 1. Clic direct sur la tombe
        if (clicked != null && isDeathChest(clicked)) {
            if (claimDeathChest(player, clicked)) {
                event.setCancelled(true);
            }
            return;
        }

        // 2. RayTrace à travers les fluides (eau / lave) avec FluidCollisionMode.NEVER
        RayTraceResult ray = player.rayTraceBlocks(5.5, FluidCollisionMode.NEVER);
        if (ray != null && ray.getHitBlock() != null && isDeathChest(ray.getHitBlock())) {
            if (claimDeathChest(player, ray.getHitBlock())) {
                event.setCancelled(true);
            }
            return;
        }

        // 3. Si le joueur a cliqué sur un bloc d'eau ou adjacent, vérification des blocs voisins directs
        if (clicked != null) {
            for (BlockFace face : BlockFace.values()) {
                if (!face.isCartesian()) continue;
                Block rel = clicked.getRelative(face);
                if (isDeathChest(rel)) {
                    if (claimDeathChest(player, rel)) {
                        event.setCancelled(true);
                    }
                    return;
                }
            }
        }

        // 4. Détection par proximité immédiate si le joueur clique dans l'eau / l'air à côté de sa tombe
        Block nearbyChest = findNearbyDeathChest(player, 3);
        if (nearbyChest != null) {
            if (claimDeathChest(player, nearbyChest)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isDeathChest(block)) {
            return;
        }

        // Si le joueur casse sa tombe (au clic gauche), on la récupère proprement dans l'inventaire
        event.setCancelled(true);
        claimDeathChest(event.getPlayer(), block);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isDeathChest);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isDeathChest);
    }

    public boolean claimDeathChest(Player player, Block block) {
        if (block == null || !isDeathChest(block)) {
            return false;
        }

        if (!(block.getState() instanceof TileState tileState)) {
            return false;
        }

        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        if (!pdc.has(deathChestKey, PersistentDataType.BYTE)) {
            return false;
        }

        String ownerUuid = pdc.get(ownerUuidKey, PersistentDataType.STRING);
        String ownerName = pdc.get(ownerNameKey, PersistentDataType.STRING);

        // Seul le propriétaire ou un opérateur peut récupérer la tombe
        if (ownerUuid != null && !ownerUuid.equals(player.getUniqueId().toString()) && !player.isOp()) {
            player.sendMessage(Component.text("✦ Cette tombe appartient à " + (ownerName != null ? ownerName : "un autre joueur") + " !", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return false;
        }

        byte[] serialized = pdc.get(itemsKey, PersistentDataType.BYTE_ARRAY);
        if (serialized == null) {
            block.setType(Material.AIR);
            return true;
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

        // Nettoyage de la sauvegarde de secours du joueur
        player.getPersistentDataContainer().remove(backupItemsKey);
        player.getPersistentDataContainer().remove(backupLocKey);

        // Supprimer le bloc de mort
        block.setType(Material.AIR);
        return true;
    }

    public Block findNearbyDeathChest(Player player, int radius) {
        if (player == null) return null;
        World world = player.getWorld();
        int px = player.getLocation().getBlockX();
        int py = player.getLocation().getBlockY();
        int pz = player.getLocation().getBlockZ();
        String pUuid = player.getUniqueId().toString();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block b = world.getBlockAt(px + dx, py + dy, pz + dz);
                    if (isDeathChest(b)) {
                        if (b.getState() instanceof TileState ts) {
                            String owner = ts.getPersistentDataContainer().get(ownerUuidKey, PersistentDataType.STRING);
                            if (owner == null || owner.equals(pUuid) || player.isOp()) {
                                return b;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public boolean restoreFromPlayerBackup(Player player) {
        if (player == null) return false;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        byte[] serialized = pdc.get(backupItemsKey, PersistentDataType.BYTE_ARRAY);
        if (serialized == null) return false;

        ItemStack[] items = ItemStack.deserializeItemsFromBytes(serialized);
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(items);
        for (ItemStack leftover : overflow.values()) {
            if (leftover != null && !leftover.getType().isAir()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 1.2f);
        player.sendMessage(Component.text("✦ Vos affaires ont été restaurées depuis la sauvegarde de sécurité !", NamedTextColor.GREEN, TextDecoration.BOLD));

        pdc.remove(backupItemsKey);
        pdc.remove(backupLocKey);
        return true;
    }

    public boolean isDeathChestMaterial(Material material) {
        return material == Material.PLAYER_HEAD || material == Material.PLAYER_WALL_HEAD || material == Material.CHEST;
    }

    public boolean isDeathChest(Block block) {
        if (block == null) return false;
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
        if (original.getType().isAir() || original.getType() == Material.WATER) {
            return original;
        }

        // Recherche du bloc d'air ou d'eau le plus proche dans une boîte 7x7x7
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
                    if (candidate.getType().isAir() || candidate.getType() == Material.WATER) {
                        double distSq = (dx * dx) + (dy * dy) + (dz * dz);
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            best = candidate;
                        }
                    }
                }
            }
        }

        return best != null ? best : original;
    }
}

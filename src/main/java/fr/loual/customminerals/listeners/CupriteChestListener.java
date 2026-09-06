package fr.loual.customminerals.listeners;

import fr.loual.newadventure.NewAdventurePlugin;
import fr.loual.customminerals.items.CupriteChest;
import fr.loual.customminerals.items.CupriteChestHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class CupriteChestListener implements Listener {

    private final NewAdventurePlugin plugin;
    private final NamespacedKey cupriteChestKey;
    private final NamespacedKey itemsKey;

    private static final int STORAGE_SLOTS_PER_PAGE = 45;
    private static final int TOTAL_STORAGE_SLOTS = 90;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 53;

    public CupriteChestListener(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.cupriteChestKey = new NamespacedKey(plugin, CupriteChest.ITEM_ID);
        this.itemsKey = new NamespacedKey(plugin, "cuprite_chest_items");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!CupriteChest.isCupriteChest(plugin, item)) {
            return;
        }

        Block block = event.getBlockPlaced();
        if (block.getState() instanceof TileState tileState) {
            tileState.getPersistentDataContainer().set(cupriteChestKey, PersistentDataType.BYTE, (byte) 1);
            tileState.update();
        }

        // Empêcher la fusion avec les coffres voisins (reste un bloc simple)
        if (block.getBlockData() instanceof Chest chestData) {
            chestData.setType(Chest.Type.SINGLE);
            block.setBlockData(chestData, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.TRAPPED_CHEST) {
            return;
        }

        if (!(clicked.getState() instanceof TileState tileState)) {
            return;
        }

        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        if (!pdc.has(cupriteChestKey, PersistentDataType.BYTE)) {
            return;
        }

        // Annuler l'ouverture et le signal redstone vanilla du trapped_chest
        event.setCancelled(true);

        Player player = event.getPlayer();
        openCupriteChestPage(player, clicked, 0);
        player.playSound(clicked.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f);
    }

    private void openCupriteChestPage(Player player, Block chestBlock, int page) {
        if (!(chestBlock.getState() instanceof TileState tileState)) {
            return;
        }

        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        ItemStack[] allItems = new ItemStack[TOTAL_STORAGE_SLOTS];
        byte[] serialized = pdc.get(itemsKey, PersistentDataType.BYTE_ARRAY);
        if (serialized != null) {
            ItemStack[] stored = ItemStack.deserializeItemsFromBytes(serialized);
            System.arraycopy(stored, 0, allItems, 0, Math.min(stored.length, TOTAL_STORAGE_SLOTS));
        }

        CupriteChestHolder holder = new CupriteChestHolder(chestBlock.getLocation(), page);
        Inventory inv = Bukkit.createInventory(
                holder,
                54,
                Component.text("✦ Coffre en Cuprite (Page " + (page + 1) + "/2)", NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        holder.setInventory(inv);

        // Copier les items de la page demandée dans les 45 premiers slots
        int offset = page * STORAGE_SLOTS_PER_PAGE;
        for (int i = 0; i < STORAGE_SLOTS_PER_PAGE; i++) {
            inv.setItem(i, allItems[offset + i]);
        }

        // Décoration / Barre d'outils (ligne 6: slots 45 à 53)
        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ", NamedTextColor.GRAY));
        for (int s = 45; s < 54; s++) {
            inv.setItem(s, filler);
        }

        if (page > 0) {
            ItemStack prevBtn = createGuiItem(
                    Material.ARROW,
                    Component.text("◀ Page précédente (1/2)", NamedTextColor.YELLOW, TextDecoration.BOLD)
            );
            inv.setItem(PREV_PAGE_SLOT, prevBtn);
        }

        ItemStack infoItem = createGuiItem(
                Material.COPPER_INGOT,
                Component.text("Page " + (page + 1) + " / 2", NamedTextColor.AQUA, TextDecoration.BOLD),
                List.of(
                        Component.text("Capacité totale : 90 slots", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("45 slots par page", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                )
        );
        inv.setItem(INFO_SLOT, infoItem);

        if (page < 1) {
            ItemStack nextBtn = createGuiItem(
                    Material.ARROW,
                    Component.text("Page suivante (2/2) ▶", NamedTextColor.YELLOW, TextDecoration.BOLD)
            );
            inv.setItem(NEXT_PAGE_SLOT, nextBtn);
        }

        player.openInventory(inv);
    }

    private ItemStack createGuiItem(Material material, Component name) {
        return createGuiItem(material, name, List.of());
    }

    private ItemStack createGuiItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CupriteChestHolder holder)) {
            return;
        }

        int slot = event.getRawSlot();
        // Clic sur la barre de navigation du bas (slots 45 à 53)
        if (slot >= 45 && slot < 54) {
            event.setCancelled(true);

            if (slot == PREV_PAGE_SLOT && holder.getCurrentPage() > 0) {
                switchPage((Player) event.getWhoClicked(), holder, 0);
            } else if (slot == NEXT_PAGE_SLOT && holder.getCurrentPage() < 1) {
                switchPage((Player) event.getWhoClicked(), holder, 1);
            }
        }
    }

    private void switchPage(Player player, CupriteChestHolder holder, int targetPage) {
        Location loc = holder.getLocation();
        if (loc.getWorld() == null) return;
        Block block = loc.getBlock();
        if (!(block.getState() instanceof TileState tileState)) return;

        // Sauvegarder la page actuelle avant de changer
        savePageContent(tileState, holder.getCurrentPage(), holder.getInventory());

        // Changer de page
        player.playSound(loc, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
        openCupriteChestPage(player, block, targetPage);
    }

    private void savePageContent(TileState tileState, int page, Inventory inv) {
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        ItemStack[] allItems = new ItemStack[TOTAL_STORAGE_SLOTS];
        byte[] serialized = pdc.get(itemsKey, PersistentDataType.BYTE_ARRAY);
        if (serialized != null) {
            ItemStack[] stored = ItemStack.deserializeItemsFromBytes(serialized);
            System.arraycopy(stored, 0, allItems, 0, Math.min(stored.length, TOTAL_STORAGE_SLOTS));
        }

        int offset = page * STORAGE_SLOTS_PER_PAGE;
        for (int i = 0; i < STORAGE_SLOTS_PER_PAGE; i++) {
            allItems[offset + i] = inv.getItem(i);
        }

        pdc.set(itemsKey, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(allItems));
        tileState.update();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CupriteChestHolder holder)) {
            return;
        }

        Location loc = holder.getLocation();
        if (loc.getWorld() == null) {
            return;
        }

        Block block = loc.getBlock();
        if (block.getState() instanceof TileState tileState) {
            savePageContent(tileState, holder.getCurrentPage(), event.getInventory());
        }

        if (event.getPlayer() instanceof Player player) {
            player.playSound(loc, Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.TRAPPED_CHEST) {
            return;
        }

        if (!(block.getState() instanceof TileState tileState)) {
            return;
        }

        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        if (!pdc.has(cupriteChestKey, PersistentDataType.BYTE)) {
            return;
        }

        // Vider et faire tomber les 90 items stockés
        byte[] serialized = pdc.get(itemsKey, PersistentDataType.BYTE_ARRAY);
        if (serialized != null) {
            ItemStack[] items = ItemStack.deserializeItemsFromBytes(serialized);
            for (ItemStack item : items) {
                if (item != null && !item.getType().isAir()) {
                    block.getWorld().dropItemNaturally(block.getLocation(), item);
                }
            }
        }

        // Annuler le drop vanilla et donner l'item Coffre en Cuprite
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation(), CupriteChest.create(plugin));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(java.util.List<Block> blocks) {
        for (Block block : new java.util.ArrayList<>(blocks)) {
            if (block.getType() == Material.TRAPPED_CHEST && block.getState() instanceof TileState tileState) {
                PersistentDataContainer pdc = tileState.getPersistentDataContainer();
                if (pdc.has(cupriteChestKey, PersistentDataType.BYTE)) {
                    byte[] serialized = pdc.get(itemsKey, PersistentDataType.BYTE_ARRAY);
                    if (serialized != null) {
                        ItemStack[] items = ItemStack.deserializeItemsFromBytes(serialized);
                        for (ItemStack item : items) {
                            if (item != null && !item.getType().isAir()) {
                                block.getWorld().dropItemNaturally(block.getLocation(), item);
                            }
                        }
                    }
                    block.getWorld().dropItemNaturally(block.getLocation(), CupriteChest.create(plugin));
                    block.setType(Material.AIR);
                    blocks.remove(block);
                }
            }
        }
    }
}

package fr.loual.customclasses.jobs;

import fr.loual.customminerals.items.Cuprite;
import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

public class MineurPouchManager {

    private static final NamespacedKey POUCH_KEY = new NamespacedKey("customclasses", "ore_pouch_data");
    private static final Map<UUID, Inventory> activeInventories = new HashMap<>();

    public static class MineurPouchHolder implements InventoryHolder {
        private Inventory inventory;

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static Inventory getPouchInventory(Player player) {
        if (player == null) return null;
        UUID uuid = player.getUniqueId();
        if (activeInventories.containsKey(uuid)) {
            return activeInventories.get(uuid);
        }

        MineurPouchHolder holder = new MineurPouchHolder();
        Inventory inv = Bukkit.createInventory(
                holder,
                27,
                Component.text("⛏ Sacoche de Minage ⛏", NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        holder.setInventory(inv);

        // Charger depuis le PDC
        String base64 = player.getPersistentDataContainer().get(POUCH_KEY, PersistentDataType.STRING);
        if (base64 != null && !base64.isBlank()) {
            ItemStack[] items = deserializeItemArray(base64);
            if (items != null) {
                for (int i = 0; i < Math.min(items.length, 27); i++) {
                    inv.setItem(i, items[i]);
                }
            }
        }

        activeInventories.put(uuid, inv);
        return inv;
    }

    public static void savePouch(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        Inventory inv = activeInventories.get(uuid);
        if (inv == null) return;

        String base64 = serializeItemArray(inv.getContents());
        player.getPersistentDataContainer().set(POUCH_KEY, PersistentDataType.STRING, base64);
    }

    public static void openPouch(Player player) {
        Inventory inv = getPouchInventory(player);
        if (inv != null) {
            player.openInventory(inv);
            player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 1.0f, 1.0f);
        }
    }

    public static boolean isAbsorbableOre(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        Material mat = item.getType();
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("NewAdventurePlugin");
        if (plugin != null && Cuprite.isCuprite(plugin, item)) return true;

        return mat == Material.COAL
                || mat == Material.RAW_IRON
                || mat == Material.RAW_COPPER
                || mat == Material.RAW_GOLD
                || mat == Material.REDSTONE
                || mat == Material.LAPIS_LAZULI
                || mat == Material.DIAMOND
                || mat == Material.EMERALD
                || mat == Material.QUARTZ
                || mat == Material.AMETHYST_SHARD
                || mat == Material.ANCIENT_DEBRIS;
    }

    /**
     * Tente d'absorber l'item dans la sacoche du joueur.
     * @return true si l'item a été totalement absorbé, false si partiellement ou non absorbé
     */
    public static boolean tryAbsorb(Player player, ItemStack item) {
        if (player == null || item == null || item.getAmount() <= 0) return false;
        if (!isAbsorbableOre(item)) return false;

        Inventory pouch = getPouchInventory(player);
        if (pouch == null) return false;

        HashMap<Integer, ItemStack> leftover = pouch.addItem(item);
        if (leftover.isEmpty()) {
            item.setAmount(0);
            savePouch(player);
            return true;
        } else {
            int remaining = 0;
            for (ItemStack rem : leftover.values()) {
                remaining += rem.getAmount();
            }
            item.setAmount(remaining);
            savePouch(player);
            return remaining <= 0;
        }
    }

    public static boolean hasPouchInInventory(Player player) {
        if (player == null) return false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && CustomJobItems.isJobItem(item, CustomJobItems.ID_MINEUR_ORE_POUCH)) {
                return true;
            }
        }
        return false;
    }

    private static String serializeItemArray(ItemStack[] items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private static ItemStack[] deserializeItemArray(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            int size = dataInput.readInt();
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            dataInput.close();
            return items;
        } catch (Exception e) {
            return new ItemStack[0];
        }
    }
}

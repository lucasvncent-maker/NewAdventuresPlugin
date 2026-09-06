package fr.loual.customminerals.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class CupriteChest {

    public static final String ITEM_ID = "cuprite_chest";
    public static final Material BASE_MATERIAL = Material.TRAPPED_CHEST;

    private CupriteChest() {
    }

    public static ItemStack create(Plugin plugin) {
        return create(plugin, 1);
    }

    public static ItemStack create(Plugin plugin, int amount) {
        ItemStack item = new ItemStack(BASE_MATERIAL, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(
                    Component.text("Coffre en Cuprite", NamedTextColor.GOLD)
                            .decoration(TextDecoration.BOLD, true)
                            .decoration(TextDecoration.ITALIC, false)
            );

            meta.lore(List.of(
                    Component.text("Coffre compact renforcé aux minerais de Cuprite.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("✦ Capacité : ", NamedTextColor.YELLOW)
                            .append(Component.text("90 cases (2 pages)", NamedTextColor.AQUA))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("  Stockage colossal (plus qu'un double coffre) !", NamedTextColor.DARK_AQUA)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("✦ Propriété : ", NamedTextColor.YELLOW)
                            .append(Component.text("Conserve ou déverse son contenu", NamedTextColor.LIGHT_PURPLE))
                            .decoration(TextDecoration.ITALIC, false)
            ));

            meta.setEnchantmentGlintOverride(true);

            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of(ITEM_ID));
            meta.setCustomModelDataComponent(cmd);
            meta.setItemModel(new NamespacedKey(NamespacedKey.MINECRAFT, ITEM_ID));

            NamespacedKey itemKey = new NamespacedKey(plugin, ITEM_ID);
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);

            NamespacedKey typeKey = new NamespacedKey(plugin, "custom_material_id");
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, ITEM_ID);

            item.setItemMeta(meta);
        }

        return item;
    }

    public static boolean isCupriteChest(Plugin plugin, ItemStack item) {
        if (item == null || item.getType() != BASE_MATERIAL) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        NamespacedKey itemKey = new NamespacedKey(plugin, ITEM_ID);
        return meta.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }
}

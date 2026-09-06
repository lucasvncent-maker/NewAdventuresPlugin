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

public final class CupriteBlock {

    public static final String ITEM_ID = "cuprite_block";
    public static final Material BASE_MATERIAL = Material.COPPER_BLOCK;

    private CupriteBlock() {
    }

    public static ItemStack create(Plugin plugin) {
        return create(plugin, 1);
    }

    public static ItemStack create(Plugin plugin, int amount) {
        ItemStack item = new ItemStack(BASE_MATERIAL, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(
                    Component.text("Bloc de Cuprite", NamedTextColor.GOLD)
                            .decoration(TextDecoration.BOLD, true)
                            .decoration(TextDecoration.ITALIC, false)
            );

            meta.lore(List.of(
                    Component.text("Composé de 4 unités de Cuprite pure.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Utilisé pour améliorer le Marteau en Cuprite.", NamedTextColor.DARK_AQUA)
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

    public static boolean isCupriteBlock(Plugin plugin, ItemStack item) {
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

package fr.loual.customminerals.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class CupritePickaxe {

    public static final String ITEM_ID = "cuprite_pickaxe";
    public static final Material BASE_MATERIAL = Material.GOLDEN_PICKAXE;

    private CupritePickaxe() {
    }

    public static ItemStack create(Plugin plugin) {
        ItemStack item = new ItemStack(BASE_MATERIAL);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(
                    Component.text("Pioche en Cuprite", NamedTextColor.GOLD)
                            .decoration(TextDecoration.BOLD, true)
                            .decoration(TextDecoration.ITALIC, false)
            );

            meta.lore(List.of(
                    Component.text("Forgée dans la Cuprite la plus pure.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("✦ Propriété unique : ", NamedTextColor.YELLOW)
                            .append(Component.text("Récupère les Spawners", NamedTextColor.GREEN))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("✦ Durabilité : ", NamedTextColor.YELLOW)
                            .append(Component.text("1 utilisation", NamedTextColor.RED))
                            .decoration(TextDecoration.ITALIC, false)
            ));

            meta.setEnchantmentGlintOverride(true);

            // 1 utilisation restante affichée sur la barre de durabilité
            if (meta instanceof Damageable damageable) {
                damageable.setDamage(BASE_MATERIAL.getMaxDurability() - 1);
            }

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

    public static boolean isCupritePickaxe(Plugin plugin, ItemStack item) {
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

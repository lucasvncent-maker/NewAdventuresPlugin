package fr.loual.customminerals.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class CupriteAxe {

    public static final String ITEM_ID = "cuprite_axe";
    public static final Material BASE_MATERIAL = Material.NETHERITE_AXE;

    private CupriteAxe() {
    }

    public static ItemStack create(Plugin plugin) {
        ItemStack item = new ItemStack(BASE_MATERIAL);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(
                    Component.text("Hache en Cuprite", NamedTextColor.GOLD)
                            .decoration(TextDecoration.BOLD, true)
                            .decoration(TextDecoration.ITALIC, false)
            );

            meta.lore(List.of(
                    Component.text("Forgée avec la puissance tellurique de la Cuprite.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("✦ Propriété unique : ", NamedTextColor.YELLOW)
                            .append(Component.text("Abattage Automatique", NamedTextColor.GREEN))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("  Coupe l'intégralité du tronc et des feuilles d'un seul coup !", NamedTextColor.DARK_AQUA)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("✦ Sneak (Accroupi) : ", NamedTextColor.YELLOW)
                            .append(Component.text("Coupe un seul bloc", NamedTextColor.GRAY))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("✦ Propriété : ", NamedTextColor.YELLOW)
                            .append(Component.text("Incassable", NamedTextColor.LIGHT_PURPLE))
                            .decoration(TextDecoration.ITALIC, false)
            ));

            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.EFFICIENCY, 5, true);

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

    public static boolean isCupriteAxe(Plugin plugin, ItemStack item) {
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

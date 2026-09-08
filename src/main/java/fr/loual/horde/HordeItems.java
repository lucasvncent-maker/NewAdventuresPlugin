package fr.loual.horde;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class HordeItems {

    public static final NamespacedKey HORDE_ITEM_KEY = new NamespacedKey("horde", "item_id");
    public static final String ID_TITAN_SOUL_SLICER = "titan_soul_slicer";

    public static ItemStack getTitanSoulSlicer() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✦ Tranche-Âme du Titan ✦", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Lame forgée dans les flammes corrompues de la Horde.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("✦ Tranchant VI & Butin IV", NamedTextColor.YELLOW),
                    Component.text("✦ Drain Vital : Les coups critiques absorbent 1 cœur de vie", NamedTextColor.LIGHT_PURPLE),
                    Component.text("  à la cible pour soigner son porteur !", NamedTextColor.LIGHT_PURPLE),
                    Component.empty(),
                    Component.text("★ Trophée Légendaire de l'Invasion des Damnés", NamedTextColor.GOLD, TextDecoration.ITALIC)
            ));
            meta.addEnchant(Enchantment.SHARPNESS, 6, true);
            meta.addEnchant(Enchantment.LOOTING, 4, true);
            meta.addEnchant(Enchantment.UNBREAKING, 5, true);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(HORDE_ITEM_KEY, PersistentDataType.STRING, ID_TITAN_SOUL_SLICER);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isHordeItem(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta()) return false;
        String val = item.getItemMeta().getPersistentDataContainer().get(HORDE_ITEM_KEY, PersistentDataType.STRING);
        return id != null && id.equalsIgnoreCase(val);
    }
}

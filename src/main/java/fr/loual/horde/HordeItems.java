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
    public static final String ID_INQUISITOR_WRATH_BLADE = "inquisitor_wrath_blade";
    public static final String ID_APOCALYPSE_CLAYMORE = "apocalypse_claymore";

    public static ItemStack getTitanSoulSlicer() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✦ Tranche-Âme du Titan ✦", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Lame forgée dans les flammes corrompues de la Horde.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("✦ Tranchant VI & Butin IV", NamedTextColor.YELLOW),
                    Component.text("✦ Drain Vital : Chaque coup critique absorbe 1 cœur de vie", NamedTextColor.LIGHT_PURPLE),
                    Component.text("  à la cible pour régénérer son porteur !", NamedTextColor.LIGHT_PURPLE),
                    Component.empty(),
                    Component.text("★ Trophée Légendaire de l'Invasion Standard", NamedTextColor.GOLD, TextDecoration.ITALIC)
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

    public static ItemStack getInquisitorWrathBlade() {
        ItemStack item = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✦ Jugement de l'Inquisiteur ✦", NamedTextColor.RED, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Hache de guerre imprégnée de l'énergie brute de la Cuprite.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("✦ Tranchant VII & Châtiment V", NamedTextColor.YELLOW),
                    Component.text("✦ Onde Tellurique : Frappe les ennemis avec une onde de choc", NamedTextColor.GOLD),
                    Component.text("  qui étourdit et repousse les cibles proches !", NamedTextColor.GOLD),
                    Component.empty(),
                    Component.text("★ Trophée Héroïque de l'Invasion de Cuprite", NamedTextColor.RED, TextDecoration.ITALIC)
            ));
            meta.addEnchant(Enchantment.SHARPNESS, 7, true);
            meta.addEnchant(Enchantment.SMITE, 5, true);
            meta.addEnchant(Enchantment.UNBREAKING, 6, true);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(HORDE_ITEM_KEY, PersistentDataType.STRING, ID_INQUISITOR_WRATH_BLADE);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack getApocalypseClaymore() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✦ L'Espadon de l'Apocalypse ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Lame forgée au cœur d'une étoile effondrée de Cuprite Renforcée.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("✦ Tranchant VIII & Butin V & Affilage V", NamedTextColor.YELLOW),
                    Component.text("✦ Fléau Stellaire : Déclenche des explosions pyrotechniques", NamedTextColor.LIGHT_PURPLE),
                    Component.text("  solaires et embrase tous les monstres aux alentours !", NamedTextColor.LIGHT_PURPLE),
                    Component.empty(),
                    Component.text("★ Relique Suprême de l'Apocalypse de Cuprite", NamedTextColor.LIGHT_PURPLE, TextDecoration.ITALIC)
            ));
            meta.addEnchant(Enchantment.SHARPNESS, 8, true);
            meta.addEnchant(Enchantment.FIRE_ASPECT, 3, true);
            meta.addEnchant(Enchantment.SWEEPING_EDGE, 4, true);
            meta.addEnchant(Enchantment.LOOTING, 5, true);
            meta.addEnchant(Enchantment.UNBREAKING, 8, true);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(HORDE_ITEM_KEY, PersistentDataType.STRING, ID_APOCALYPSE_CLAYMORE);
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

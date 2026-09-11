package fr.loual.customclasses.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class NouvelleAme {

    public static final String ITEM_ID = "nouvelle_ame";
    public static final Material BASE_MATERIAL = Material.NETHER_STAR;

    private NouvelleAme() {
    }

    public static ItemStack create(Plugin plugin) {
        return create(plugin, 1);
    }

    public static ItemStack create(Plugin plugin, int amount) {
        ItemStack item = new ItemStack(BASE_MATERIAL, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text("✦ Nouvelle Âme ✦", NamedTextColor.AQUA)
                            .decoration(TextDecoration.BOLD, true)
                            .decoration(TextDecoration.ITALIC, false)
            );

            meta.lore(List.of(
                    Component.text("Une essence primordiale pulsant d'énergie mystique.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("✦ Utilisation :", NamedTextColor.YELLOW, TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("  • Clic Droit pour briser vos liens et", NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("    choisir une toute NOUVELLE CLASSE !", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("⚠ Cet objet est consumé lors du rituel.", NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            meta.setEnchantmentGlintOverride(true);

            NamespacedKey key = new NamespacedKey(plugin, ITEM_ID);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isNouvelleAme(Plugin plugin, ItemStack item) {
        if (item == null || item.getType() != BASE_MATERIAL) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        NamespacedKey key = new NamespacedKey(plugin, ITEM_ID);
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}

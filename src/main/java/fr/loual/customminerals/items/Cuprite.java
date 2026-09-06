package fr.loual.customminerals.items;

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

/**
 * Représentation et utilitaire pour l'item custom "Cuprite".
 */
public final class Cuprite {

    public static final String ITEM_ID = "cuprite";
    public static final Material BASE_MATERIAL = Material.RAW_COPPER;

    private Cuprite() {
        // Constructeur privé pour classe utilitaire
    }

    /**
     * Crée une unité de Cuprite.
     *
     * @param plugin L'instance du plugin pour la NamespacedKey
     * @return L'ItemStack représentant la Cuprite
     */
    public static ItemStack create(Plugin plugin) {
        return create(plugin, 1);
    }

    /**
     * Crée une pile de Cuprite avec la quantité spécifiée.
     *
     * @param plugin L'instance du plugin pour la NamespacedKey
     * @param amount Quantité d'items
     * @return L'ItemStack représentant la Cuprite
     */
    public static ItemStack create(Plugin plugin, int amount) {
        ItemStack item = new ItemStack(BASE_MATERIAL, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Nom doré sans italique par défaut
            meta.displayName(
                    Component.text("Cuprite", NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false)
            );

            // Description / Lore thématique
            meta.lore(List.of(
                    Component.text("Minéral précieux issu d'un filon de cuivre.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            // Allure enchantée (brillance / glint sans enchantement fictif)
            meta.setEnchantmentGlintOverride(true);

            // Custom model data & Item Model
            org.bukkit.inventory.meta.components.CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of(ITEM_ID));
            meta.setCustomModelDataComponent(cmd);
            meta.setItemModel(new NamespacedKey(NamespacedKey.MINECRAFT, ITEM_ID));

            // Data tags persistants (PDC)
            NamespacedKey itemKey = new NamespacedKey(plugin, ITEM_ID);
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);

            NamespacedKey typeKey = new NamespacedKey(plugin, "custom_material_id");
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, ITEM_ID);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Vérifie si un ItemStack est de la Cuprite valide.
     *
     * @param plugin L'instance du plugin
     * @param item L'ItemStack à vérifier
     * @return true si l'item est de la Cuprite
     */
    public static boolean isCuprite(Plugin plugin, ItemStack item) {
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

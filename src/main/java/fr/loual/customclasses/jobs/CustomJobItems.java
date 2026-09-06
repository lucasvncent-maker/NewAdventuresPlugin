package fr.loual.customclasses.jobs;

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

public class CustomJobItems {

    public static final NamespacedKey ITEM_KEY = new NamespacedKey("customclasses", "job_item");

    public static final String ID_FARMER_SOUP = "farmer_soup";
    public static final String ID_SPACE_COOKIE = "space_cookie";
    public static final String ID_WONDERFUL_SOUP = "wonderful_soup";
    public static final String ID_WONDERFUL_HOE = "wonderful_hoe";

    public static ItemStack getFarmerSoup() {
        ItemStack item = new ItemStack(Material.BEETROOT_SOUP);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Soupe de l'Agriculteur", NamedTextColor.GOLD, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Une soupe riche et concentrée en nutriments.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("✦ Se mange instantanément !", NamedTextColor.YELLOW),
                    Component.text("✦ Saturation équivalente à une carotte dorée", NamedTextColor.YELLOW),
                    Component.text("✦ Confère Régénération II pendant 10s", NamedTextColor.GREEN),
                    Component.text("✦ Empilable par 64", NamedTextColor.AQUA)
            ));
            meta.setMaxStackSize(64);
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, ID_FARMER_SOUP);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack getSpaceCookie() {
        ItemStack item = new ItemStack(Material.COOKIE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Space Cookie", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Un biscuit aux épices psychotropes d'un autre monde.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("✦ Force III & Vitesse II (30s)", NamedTextColor.GREEN),
                    Component.text("✦ Saturation Maximale (20.0)", NamedTextColor.GREEN),
                    Component.text("✖ Effet secondaire : Nausée pendant 10s après 30 secondes !", NamedTextColor.RED)
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, ID_SPACE_COOKIE);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack getWonderfulSoup() {
        ItemStack item = new ItemStack(Material.RABBIT_STEW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Soupe Merveilleuse", NamedTextColor.AQUA, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("L'apogée de la gastronomie végétale et magique.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("✦ Se mange quasi instantanément !", NamedTextColor.YELLOW),
                    Component.text("✦ Rend 1 cœur (2 HP)", NamedTextColor.GREEN),
                    Component.text("✦ Saturation Maximale (20.0)", NamedTextColor.GREEN),
                    Component.text("✦ Empilable par 64", NamedTextColor.AQUA)
            ));
            meta.setMaxStackSize(64);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, ID_WONDERFUL_SOUP);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack getWonderfulHoe() {
        ItemStack item = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Houe Merveilleuse", NamedTextColor.GOLD, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Façonnée dans le cuivre et animée d'une force fertile.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("✦ Carottes -> Carottes Dorées", NamedTextColor.YELLOW),
                    Component.text("✦ Blé -> Blocs de Paille", NamedTextColor.YELLOW),
                    Component.text("✦ Pastèques -> Blocs entiers de Pastèque", NamedTextColor.YELLOW),
                    Component.text("✦ Citrouilles -> Citrouilles bonus", NamedTextColor.YELLOW),
                    Component.text("✦ Betteraves -> Récoltes abondantes", NamedTextColor.YELLOW)
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addEnchant(Enchantment.FORTUNE, 3, true);
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, ID_WONDERFUL_HOE);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isJobItem(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta()) return false;
        String val = item.getItemMeta().getPersistentDataContainer().get(ITEM_KEY, PersistentDataType.STRING);
        return id.equalsIgnoreCase(val);
    }
}

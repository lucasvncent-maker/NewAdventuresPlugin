package fr.loual.customclasses.jobs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class CustomJobItems {

    public static final NamespacedKey ITEM_KEY = new NamespacedKey("customclasses", "job_item");

    public static final String ID_FARMER_SOUP = "farmer_soup";
    public static final String ID_SPACE_COOKIE = "space_cookie";
    public static final String ID_WONDERFUL_SOUP = "wonderful_soup";
    public static final String ID_WONDERFUL_HOE = "wonderful_hoe";

    public static final String ID_ARCHITECT_HELMET = "architect_helmet";
    public static final String ID_ARCHITECT_CHESTPLATE = "architect_chestplate";
    public static final String ID_ARCHITECT_LEGGINGS = "architect_leggings";
    public static final String ID_ARCHITECT_BOOTS = "architect_boots";
    public static final String ID_ARCHITECT_FEATHER = "architect_feather";

    public static final String ID_AVENTURIER_GOLDEN_SWORD = "aventurier_golden_sword";
    public static final String ID_AVENTURIER_INFINITE_PEARL = "aventurier_infinite_pearl";
    public static final String ID_AVENTURIER_UNBREAKABLE_ELYTRA = "aventurier_unbreakable_elytra";
    public static final String ID_AVENTURIER_INFINITE_FIREWORK = "aventurier_infinite_firework";

    public static final Color ARCHITECT_COLOR = Color.fromRGB(235, 180, 50);

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
                    Component.text("✦ Se mange instantanément (même sans avoir faim) !", NamedTextColor.YELLOW),
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

    public static ItemStack getArchitectHelmet() {
        ItemStack item = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(ARCHITECT_COLOR);
            meta.displayName(Component.text("Chapeau de l'Architecte", NamedTextColor.GOLD, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Coiffe d'apparat du maître d'œuvre.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("✦ Protection V & Incassable", NamedTextColor.YELLOW),
                    Component.text("✦ Effet passif : Vitesse II", NamedTextColor.GREEN),
                    Component.text("✦ Ensemble (4 pièces) : Active le Vol avec la Plume !", NamedTextColor.AQUA)
            ));
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.PROTECTION, 5, true);
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, ID_ARCHITECT_HELMET);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack getArchitectChestplate() {
        ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(ARCHITECT_COLOR);
            meta.displayName(Component.text("Chemise de l'Architecte", NamedTextColor.GOLD, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Vêtement souple et renforcé taillé pour le chantier.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("✦ Protection V & Incassable", NamedTextColor.YELLOW),
                    Component.text("✦ Effet passif : Célérité II", NamedTextColor.GREEN),
                    Component.text("✦ Ensemble (4 pièces) : Active le Vol avec la Plume !", NamedTextColor.AQUA)
            ));
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.PROTECTION, 5, true);
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, ID_ARCHITECT_CHESTPLATE);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack getArchitectLeggings() {
        ItemStack item = new ItemStack(Material.LEATHER_LEGGINGS);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(ARCHITECT_COLOR);
            meta.displayName(Component.text("Pantalon de l'Architecte", NamedTextColor.GOLD, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Pantalon de travail aux poches profondes.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("✦ Protection V & Incassable", NamedTextColor.YELLOW),
                    Component.text("✦ Effet : Vision Nocturne (/nv pour activer)", NamedTextColor.GREEN),
                    Component.text("✦ Ensemble (4 pièces) : Active le Vol avec la Plume !", NamedTextColor.AQUA)
            ));
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.PROTECTION, 5, true);
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, ID_ARCHITECT_LEGGINGS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack getArchitectBoots() {
        ItemStack item = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(ARCHITECT_COLOR);
            meta.displayName(Component.text("Chaussures de l'Architecte", NamedTextColor.GOLD, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Bottes renforcées offrant une grande agilité sur les échafaudages.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("✦ Protection V & Incassable", NamedTextColor.YELLOW),
                    Component.text("✦ Effet : Saut Amélioré II (/jb pour activer)", NamedTextColor.GREEN),
                    Component.text("✦ Ensemble (4 pièces) : Active le Vol avec la Plume !", NamedTextColor.AQUA)
            ));
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.PROTECTION, 5, true);
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, ID_ARCHITECT_BOOTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack getArchitectFeather() {
        ItemStack item = new ItemStack(Material.FEATHER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Plume de l'Architecte", NamedTextColor.GOLD, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Plume mystique vibrant au rythme de la créativité.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("✦ Clic Droit : Vol Créatif pendant 30 secondes !", NamedTextColor.YELLOW),
                    Component.text("✦ Nécessite la tenue complète de l'Architecte (4 pièces)", NamedTextColor.AQUA),
                    Component.text("✦ Temps de recharge : 5 minutes", NamedTextColor.RED),
                    Component.text("✦ Protège des dégâts de chute à l'atterrissage", NamedTextColor.GREEN)
            ));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, ID_ARCHITECT_FEATHER);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack getAventurierGoldenSword() {
        ItemStack item = new ItemStack(Material.GOLDEN_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✦ Épée Dorée de l'Aventurier ✦", NamedTextColor.GOLD, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Trésor ancestral déniché dans une structure antique.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("✦ Tranchant VII (Sharpness VII)", NamedTextColor.YELLOW, TextDecoration.BOLD),
                    Component.text("✦ Butin IV (Looting IV)", NamedTextColor.YELLOW, TextDecoration.BOLD),
                    Component.text("✦ Solidité V & Raccommodage", NamedTextColor.AQUA),
                    Component.empty(),
                    Component.text("✦ Butin Exclusif de l'Aventurier ✦", NamedTextColor.GOLD)
            ));
            meta.addEnchant(Enchantment.SHARPNESS, 7, true);
            meta.addEnchant(Enchantment.LOOTING, 4, true);
            meta.addEnchant(Enchantment.UNBREAKING, 5, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, ID_AVENTURIER_GOLDEN_SWORD);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack getAventurierInfinitePearl() {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✦ Perle Infinie de l'Aventurier ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Une perle magique imprégnée par l'exploration des 5 biomes du Nether.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("✔ Ne s'épuise JAMAIS (Utilisations infinies) !", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text("✔ AUCUN dégât de chute à l'atterrissage !", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text("✦ Temps de recharge léger : 1.5s", NamedTextColor.YELLOW),
                    Component.empty(),
                    Component.text("✦ Récompense Mission 2 de l'Aventurier ✦", NamedTextColor.DARK_PURPLE)
            ));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, ID_AVENTURIER_INFINITE_PEARL);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack getAventurierUnbreakableElytra() {
        ItemStack item = new ItemStack(Material.ELYTRA);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✦ Élytres Incassables de l'Aventurier ✦", NamedTextColor.AQUA, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Ailes légendaires forgées par le souffle des explorateurs des cieux.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("✔ INCASSABLE (Durabilité infinie) !", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text("✦ Protection V & Raccommodage", NamedTextColor.YELLOW),
                    Component.empty(),
                    Component.text("✦ Récompense Suprême Mission 3 de l'Aventurier ✦", NamedTextColor.GOLD)
            ));
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.PROTECTION, 5, true);
            meta.addEnchant(Enchantment.UNBREAKING, 10, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, ID_AVENTURIER_UNBREAKABLE_ELYTRA);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack getAventurierInfiniteFirework() {
        ItemStack item = new ItemStack(Material.FIREWORK_ROCKET);
        org.bukkit.inventory.meta.FireworkMeta meta = (org.bukkit.inventory.meta.FireworkMeta) item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✦ Fusée Infinie de l'Aventurier ✦", NamedTextColor.GOLD, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Une fusée pyrotechnique magique alimentée par la flamme des pionniers.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("✔ Ne s'épuise JAMAIS (Propulsion infinie) !", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text("✦ Utilisez en vol avec vos Élytres pour vous propulser !", NamedTextColor.YELLOW),
                    Component.empty(),
                    Component.text("✦ Récompense Suprême Mission 3 de l'Aventurier ✦", NamedTextColor.GOLD)
            ));
            meta.setPower(2);
            meta.addEffect(org.bukkit.FireworkEffect.builder()
                    .with(org.bukkit.FireworkEffect.Type.BALL)
                    .withColor(Color.YELLOW, Color.ORANGE, Color.AQUA)
                    .withFade(Color.WHITE)
                    .trail(true)
                    .build());
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, ID_AVENTURIER_INFINITE_FIREWORK);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean hasFullArchitectSet(Player player) {
        if (player == null) return false;
        var inv = player.getInventory();
        return isJobItem(inv.getHelmet(), ID_ARCHITECT_HELMET)
                && isJobItem(inv.getChestplate(), ID_ARCHITECT_CHESTPLATE)
                && isJobItem(inv.getLeggings(), ID_ARCHITECT_LEGGINGS)
                && isJobItem(inv.getBoots(), ID_ARCHITECT_BOOTS);
    }

    public static String getJobItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(ITEM_KEY, PersistentDataType.STRING);
    }

    public static boolean isJobItem(ItemStack item, String id) {
        String val = getJobItemId(item);
        return id != null && id.equalsIgnoreCase(val);
    }

    public static ItemStack getItemById(String id) {
        if (id == null) return null;
        return switch (id.toLowerCase()) {
            case ID_FARMER_SOUP -> getFarmerSoup();
            case ID_SPACE_COOKIE -> getSpaceCookie();
            case ID_WONDERFUL_SOUP -> getWonderfulSoup();
            case ID_WONDERFUL_HOE -> getWonderfulHoe();
            case ID_ARCHITECT_HELMET -> getArchitectHelmet();
            case ID_ARCHITECT_CHESTPLATE -> getArchitectChestplate();
            case ID_ARCHITECT_LEGGINGS -> getArchitectLeggings();
            case ID_ARCHITECT_BOOTS -> getArchitectBoots();
            case ID_ARCHITECT_FEATHER -> getArchitectFeather();
            case ID_AVENTURIER_GOLDEN_SWORD -> getAventurierGoldenSword();
            case ID_AVENTURIER_INFINITE_PEARL -> getAventurierInfinitePearl();
            case ID_AVENTURIER_UNBREAKABLE_ELYTRA -> getAventurierUnbreakableElytra();
            case ID_AVENTURIER_INFINITE_FIREWORK -> getAventurierInfiniteFirework();
            default -> null;
        };
    }
}

package fr.loual.customclasses.jobs.gui;

import fr.loual.customclasses.CustomClasses;
import fr.loual.customclasses.jobs.CustomJobItems;
import fr.loual.customclasses.jobs.JobManager;
import fr.loual.customclasses.jobs.PlayerJob;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class JobRecipeGui {

    public static final String RECIPE_FARMER_SOUP = "farmer_soup";
    public static final String RECIPE_SPACE_COOKIE = "space_cookie";
    public static final String RECIPE_WONDERFUL_SOUP = "wonderful_soup";
    public static final String RECIPE_WONDERFUL_HOE = "wonderful_hoe";

    public static void open(CustomClasses plugin, Player player, String recipeKey) {
        if (recipeKey == null) recipeKey = RECIPE_FARMER_SOUP;
        String finalKey = switch (recipeKey.toLowerCase()) {
            case "space_cookie", "cookie" -> RECIPE_SPACE_COOKIE;
            case "wonderful_soup", "soupe_merveilleuse" -> RECIPE_WONDERFUL_SOUP;
            case "wonderful_hoe", "houe_merveilleuse", "houe" -> RECIPE_WONDERFUL_HOE;
            default -> RECIPE_FARMER_SOUP;
        };

        JobRecipeGuiHolder holder = new JobRecipeGuiHolder(finalKey);
        String recipeTitle = getRecipeTitle(finalKey);

        Inventory inv = Bukkit.createInventory(
                holder,
                45,
                Component.text("✦ Recette : " + recipeTitle, NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        holder.setInventory(inv);

        // Fond vitres grises
        ItemStack glass = createNamedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, glass);
        }

        JobManager jm = plugin.getJobManager();
        int playerLevel = (jm.getPlayerJob(player) == PlayerJob.AGRICULTEUR) ? jm.getJobLevel(player, PlayerJob.AGRICULTEUR) : 0;

        // Onglets en haut (Ligne 0 : slots 1, 3, 5, 7)
        inv.setItem(1, createTabItem("Soupe de l'Agriculteur", Material.MUSHROOM_STEW, RECIPE_FARMER_SOUP, finalKey, 1, playerLevel));
        inv.setItem(3, createTabItem("Space Cookie", Material.COOKIE, RECIPE_SPACE_COOKIE, finalKey, 3, playerLevel));
        inv.setItem(5, createTabItem("Soupe Merveilleuse", Material.BEETROOT_SOUP, RECIPE_WONDERFUL_SOUP, finalKey, 4, playerLevel));
        inv.setItem(7, createTabItem("Houe Merveilleuse", Material.GOLDEN_HOE, RECIPE_WONDERFUL_HOE, finalKey, 4, playerLevel));

        // Grille de craft 3x3 :
        // Ligne 1 : 11, 12, 13
        // Ligne 2 : 20, 21, 22
        // Ligne 3 : 29, 30, 31
        int[] gridSlots = { 11, 12, 13, 20, 21, 22, 29, 30, 31 };
        ItemStack emptySlot = createNamedItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§8(Vide)");
        for (int s : gridSlots) {
            inv.setItem(s, emptySlot);
        }

        // Configuration des recettes
        ItemStack resultItem;
        ItemStack indicator = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta indMeta = indicator.getItemMeta();

        int reqLevel = getRequiredLevel(finalKey);
        boolean isUnlocked = playerLevel >= reqLevel;

        switch (finalKey) {
            case RECIPE_SPACE_COOKIE -> {
                inv.setItem(11, new ItemStack(Material.COOKIE));
                inv.setItem(12, new ItemStack(Material.GLOW_BERRIES));
                resultItem = CustomJobItems.getSpaceCookie();
                if (indMeta != null) {
                    indMeta.displayName(Component.text("➜ Fabrication : Space Cookie", NamedTextColor.YELLOW, TextDecoration.BOLD));
                    indMeta.lore(List.of(
                            Component.text("Type : Recette Informe (n'importe où)", NamedTextColor.GRAY),
                            Component.text("Ingrédients nécessaires :", NamedTextColor.GOLD),
                            Component.text("  • 1x Cookie", NamedTextColor.WHITE),
                            Component.text("  • 1x Baie lumineuse", NamedTextColor.WHITE)
                    ));
                    indicator.setItemMeta(indMeta);
                }
            }
            case RECIPE_WONDERFUL_SOUP -> {
                inv.setItem(11, new ItemStack(Material.PUMPKIN));
                inv.setItem(12, new ItemStack(Material.MELON_SLICE));
                inv.setItem(13, new ItemStack(Material.GOLDEN_CARROT));
                inv.setItem(20, new ItemStack(Material.POTATO));
                inv.setItem(21, new ItemStack(Material.WHEAT));
                inv.setItem(22, new ItemStack(Material.SUGAR_CANE));
                inv.setItem(29, new ItemStack(Material.EGG));
                inv.setItem(30, new ItemStack(Material.HONEY_BOTTLE));
                inv.setItem(31, new ItemStack(Material.BEETROOT));
                resultItem = CustomJobItems.getWonderfulSoup();
                if (indMeta != null) {
                    indMeta.displayName(Component.text("➜ Fabrication : Soupe Merveilleuse", NamedTextColor.YELLOW, TextDecoration.BOLD));
                    indMeta.lore(List.of(
                            Component.text("Type : Recette Informe (remplit les 9 cases)", NamedTextColor.GRAY),
                            Component.text("Ingrédients nécessaires (9) :", NamedTextColor.GOLD),
                            Component.text("  • Citrouille + Tranche de pastèque", NamedTextColor.WHITE),
                            Component.text("  • Carotte dorée + Patate + Blé", NamedTextColor.WHITE),
                            Component.text("  • Canne à sucre + Œuf", NamedTextColor.WHITE),
                            Component.text("  • Fiole de miel + Betterave", NamedTextColor.WHITE)
                    ));
                    indicator.setItemMeta(indMeta);
                }
            }
            case RECIPE_WONDERFUL_HOE -> {
                inv.setItem(11, new ItemStack(Material.COPPER_INGOT));
                inv.setItem(12, new ItemStack(Material.COPPER_INGOT));
                inv.setItem(21, new ItemStack(Material.STICK));
                inv.setItem(30, new ItemStack(Material.STICK));
                resultItem = CustomJobItems.getWonderfulHoe();
                if (indMeta != null) {
                    indMeta.displayName(Component.text("➜ Fabrication : Houe Merveilleuse", NamedTextColor.YELLOW, TextDecoration.BOLD));
                    indMeta.lore(List.of(
                            Component.text("Type : Recette Façonnée (forme de houe)", NamedTextColor.GRAY),
                            Component.text("Ingrédients nécessaires :", NamedTextColor.GOLD),
                            Component.text("  • 2x Lingots de cuivre (ou cuivre brut)", NamedTextColor.WHITE),
                            Component.text("  • 2x Bâtons", NamedTextColor.WHITE)
                    ));
                    indicator.setItemMeta(indMeta);
                }
            }
            default -> { // FARMER_SOUP
                inv.setItem(11, new ItemStack(Material.BOWL));
                inv.setItem(12, new ItemStack(Material.CARROT));
                inv.setItem(20, new ItemStack(Material.POTATO));
                inv.setItem(21, new ItemStack(Material.WHEAT));
                resultItem = CustomJobItems.getFarmerSoup();
                if (indMeta != null) {
                    indMeta.displayName(Component.text("➜ Fabrication : Soupe de l'Agriculteur", NamedTextColor.YELLOW, TextDecoration.BOLD));
                    indMeta.lore(List.of(
                            Component.text("Type : Recette Informe (n'importe où)", NamedTextColor.GRAY),
                            Component.text("Ingrédients nécessaires :", NamedTextColor.GOLD),
                            Component.text("  • 1x Bol", NamedTextColor.WHITE),
                            Component.text("  • 1x Carotte", NamedTextColor.WHITE),
                            Component.text("  • 1x Pomme de terre", NamedTextColor.WHITE),
                            Component.text("  • 1x Blé", NamedTextColor.WHITE)
                    ));
                    indicator.setItemMeta(indMeta);
                }
            }
        }

        // Flèche / Établi au slot 23
        inv.setItem(23, indicator);

        // Résultat au slot 25
        inv.setItem(25, resultItem);

        // Indicateur de statut au slot 36
        ItemStack status = new ItemStack(isUnlocked ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta sMeta = status.getItemMeta();
        if (sMeta != null) {
            if (isUnlocked) {
                sMeta.displayName(Component.text("✔ Recette Débloquée !", NamedTextColor.GREEN, TextDecoration.BOLD));
                sMeta.lore(List.of(
                        Component.text("Vous possédez le niveau requis.", NamedTextColor.GRAY),
                        Component.text("Vous pouvez fabriquer cet objet", NamedTextColor.GRAY),
                        Component.text("directement dans un établi !", NamedTextColor.GRAY)
                ));
            } else {
                sMeta.displayName(Component.text("🔒 Recette Verrouillée", NamedTextColor.RED, TextDecoration.BOLD));
                sMeta.lore(List.of(
                        Component.text("Niveau de mission requis : " + reqLevel, NamedTextColor.YELLOW),
                        Component.text("Accomplissez les missions du métier", NamedTextColor.GRAY),
                        Component.text("pour débloquer ce craft !", NamedTextColor.GRAY)
                ));
            }
            status.setItemMeta(sMeta);
        }
        inv.setItem(36, status);

        // Bouton retour au slot 40
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bMeta = back.getItemMeta();
        if (bMeta != null) {
            bMeta.displayName(Component.text("← Retour au menu des Métiers", NamedTextColor.RED, TextDecoration.BOLD));
            bMeta.lore(List.of(Component.text("Cliquez pour revenir au menu principal", NamedTextColor.GRAY)));
            back.setItemMeta(bMeta);
        }
        inv.setItem(40, back);

        player.openInventory(inv);
    }

    private static String getRecipeTitle(String key) {
        return switch (key) {
            case RECIPE_SPACE_COOKIE -> "Space Cookie";
            case RECIPE_WONDERFUL_SOUP -> "Soupe Merveilleuse";
            case RECIPE_WONDERFUL_HOE -> "Houe Merveilleuse";
            default -> "Soupe de l'Agriculteur";
        };
    }

    private static int getRequiredLevel(String key) {
        return switch (key) {
            case RECIPE_SPACE_COOKIE -> 3;
            case RECIPE_WONDERFUL_SOUP, RECIPE_WONDERFUL_HOE -> 4;
            default -> 1;
        };
    }

    private static ItemStack createTabItem(String name, Material mat, String tabKey, String activeKey, int reqLevel, int playerLevel) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            boolean isActive = tabKey.equalsIgnoreCase(activeKey);
            boolean isUnlocked = playerLevel >= reqLevel;

            NamedTextColor col = isActive ? NamedTextColor.GREEN : (isUnlocked ? NamedTextColor.GOLD : NamedTextColor.GRAY);
            meta.displayName(Component.text(name, col, TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Niveau requis : Mission " + reqLevel, NamedTextColor.YELLOW));
            if (isUnlocked) {
                lore.add(Component.text("✔ Débloqué", NamedTextColor.GREEN));
            } else {
                lore.add(Component.text("🔒 Verrouillé", NamedTextColor.RED));
            }
            lore.add(Component.empty());
            if (isActive) {
                lore.add(Component.text("▶ Recette actuellement affichée", NamedTextColor.AQUA));
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                lore.add(Component.text("➜ Cliquez pour voir ce craft", NamedTextColor.YELLOW));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createNamedItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            item.setItemMeta(meta);
        }
        return item;
    }
}

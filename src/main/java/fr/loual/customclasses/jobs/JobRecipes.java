package fr.loual.customclasses.jobs;

import fr.loual.customclasses.CustomClasses;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.List;

public class JobRecipes {

    public static final String KEY_FARMER_SOUP = "recipe_farmer_soup";
    public static final String KEY_SPACE_COOKIE = "recipe_space_cookie";
    public static final String KEY_WONDERFUL_SOUP = "recipe_wonderful_soup";
    public static final String KEY_WONDERFUL_HOE = "recipe_wonderful_hoe";
    public static final String KEY_WONDERFUL_HOE_MIRROR = "recipe_wonderful_hoe_mirror";

    public static NamespacedKey getKey(CustomClasses plugin, String name) {
        return new NamespacedKey(plugin, name);
    }

    public static void syncDiscoveredRecipes(CustomClasses plugin, org.bukkit.entity.Player player) {
        if (player == null || !player.isOnline()) return;

        PlayerJob pj = plugin.getJobManager().getPlayerJob(player);
        int level = plugin.getJobManager().getJobLevel(player, PlayerJob.AGRICULTEUR);

        NamespacedKey kFarmer = getKey(plugin, KEY_FARMER_SOUP);
        NamespacedKey kCookie = getKey(plugin, KEY_SPACE_COOKIE);
        NamespacedKey kWonderfulSoup = getKey(plugin, KEY_WONDERFUL_SOUP);
        NamespacedKey kWonderfulHoe = getKey(plugin, KEY_WONDERFUL_HOE);
        NamespacedKey kWonderfulHoeMirror = getKey(plugin, KEY_WONDERFUL_HOE_MIRROR);

        if (pj == PlayerJob.AGRICULTEUR) {
            if (level >= 1) {
                if (!player.hasDiscoveredRecipe(kFarmer)) player.discoverRecipe(kFarmer);
            } else {
                if (player.hasDiscoveredRecipe(kFarmer)) player.undiscoverRecipe(kFarmer);
            }

            if (level >= 3) {
                if (!player.hasDiscoveredRecipe(kCookie)) player.discoverRecipe(kCookie);
            } else {
                if (player.hasDiscoveredRecipe(kCookie)) player.undiscoverRecipe(kCookie);
            }

            if (level >= 4) {
                if (!player.hasDiscoveredRecipe(kWonderfulSoup)) player.discoverRecipe(kWonderfulSoup);
                if (!player.hasDiscoveredRecipe(kWonderfulHoe)) player.discoverRecipe(kWonderfulHoe);
                if (!player.hasDiscoveredRecipe(kWonderfulHoeMirror)) player.discoverRecipe(kWonderfulHoeMirror);
            } else {
                if (player.hasDiscoveredRecipe(kWonderfulSoup)) player.undiscoverRecipe(kWonderfulSoup);
                if (player.hasDiscoveredRecipe(kWonderfulHoe)) player.undiscoverRecipe(kWonderfulHoe);
                if (player.hasDiscoveredRecipe(kWonderfulHoeMirror)) player.undiscoverRecipe(kWonderfulHoeMirror);
            }
        } else {
            if (player.hasDiscoveredRecipe(kFarmer)) player.undiscoverRecipe(kFarmer);
            if (player.hasDiscoveredRecipe(kCookie)) player.undiscoverRecipe(kCookie);
            if (player.hasDiscoveredRecipe(kWonderfulSoup)) player.undiscoverRecipe(kWonderfulSoup);
            if (player.hasDiscoveredRecipe(kWonderfulHoe)) player.undiscoverRecipe(kWonderfulHoe);
            if (player.hasDiscoveredRecipe(kWonderfulHoeMirror)) player.undiscoverRecipe(kWonderfulHoeMirror);
        }
    }

    public static void registerRecipes(CustomClasses plugin) {
        registerFarmerSoupRecipe(plugin);
        registerSpaceCookieRecipe(plugin);
        registerWonderfulSoupRecipe(plugin);
        registerWonderfulHoeRecipe(plugin);
    }

    private static void registerFarmerSoupRecipe(CustomClasses plugin) {
        NamespacedKey key = getKey(plugin, KEY_FARMER_SOUP);
        Bukkit.removeRecipe(key);

        ShapelessRecipe recipe = new ShapelessRecipe(key, CustomJobItems.getFarmerSoup());
        recipe.addIngredient(Material.BOWL);
        recipe.addIngredient(Material.CARROT);
        recipe.addIngredient(Material.POTATO);
        recipe.addIngredient(Material.WHEAT);

        Bukkit.addRecipe(recipe);
    }

    private static void registerSpaceCookieRecipe(CustomClasses plugin) {
        NamespacedKey key = getKey(plugin, KEY_SPACE_COOKIE);
        Bukkit.removeRecipe(key);

        ShapelessRecipe recipe = new ShapelessRecipe(key, CustomJobItems.getSpaceCookie());
        recipe.addIngredient(Material.COOKIE);
        recipe.addIngredient(Material.GLOW_BERRIES);

        Bukkit.addRecipe(recipe);
    }

    private static void registerWonderfulSoupRecipe(CustomClasses plugin) {
        NamespacedKey key = getKey(plugin, KEY_WONDERFUL_SOUP);
        Bukkit.removeRecipe(key);

        ShapelessRecipe recipe = new ShapelessRecipe(key, CustomJobItems.getWonderfulSoup());
        // 9 ingrédients requis
        recipe.addIngredient(new RecipeChoice.MaterialChoice(List.of(Material.PUMPKIN, Material.CARVED_PUMPKIN)));
        recipe.addIngredient(new RecipeChoice.MaterialChoice(List.of(Material.MELON_SLICE, Material.MELON)));
        recipe.addIngredient(Material.GOLDEN_CARROT);
        recipe.addIngredient(Material.POTATO);
        recipe.addIngredient(Material.WHEAT);
        recipe.addIngredient(Material.SUGAR_CANE);
        recipe.addIngredient(Material.EGG);
        recipe.addIngredient(Material.HONEY_BOTTLE);
        recipe.addIngredient(Material.BEETROOT);

        Bukkit.addRecipe(recipe);
    }

    private static void registerWonderfulHoeRecipe(CustomClasses plugin) {
        NamespacedKey key = getKey(plugin, KEY_WONDERFUL_HOE);
        Bukkit.removeRecipe(key);

        // 2 sticks et 2 cuprite (lingots de cuivre)
        ShapedRecipe recipe = new ShapedRecipe(key, CustomJobItems.getWonderfulHoe());
        recipe.shape("CC ", " S ", " S ");
        recipe.setIngredient('C', new RecipeChoice.MaterialChoice(List.of(Material.COPPER_INGOT, Material.RAW_COPPER)));
        recipe.setIngredient('S', Material.STICK);

        Bukkit.addRecipe(recipe);

        // Version miroir
        NamespacedKey mirrorKey = getKey(plugin, KEY_WONDERFUL_HOE_MIRROR);
        Bukkit.removeRecipe(mirrorKey);
        ShapedRecipe mirrorRecipe = new ShapedRecipe(mirrorKey, CustomJobItems.getWonderfulHoe());
        mirrorRecipe.shape(" CC", " S ", " S ");
        mirrorRecipe.setIngredient('C', new RecipeChoice.MaterialChoice(List.of(Material.COPPER_INGOT, Material.RAW_COPPER)));
        mirrorRecipe.setIngredient('S', Material.STICK);

        Bukkit.addRecipe(mirrorRecipe);
    }
}

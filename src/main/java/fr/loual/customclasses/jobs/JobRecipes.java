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

    public static void registerRecipes(CustomClasses plugin) {
        registerFarmerSoupRecipe(plugin);
        registerSpaceCookieRecipe(plugin);
        registerWonderfulSoupRecipe(plugin);
        registerWonderfulHoeRecipe(plugin);
    }

    private static void registerFarmerSoupRecipe(CustomClasses plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "recipe_farmer_soup");
        Bukkit.removeRecipe(key);

        ShapelessRecipe recipe = new ShapelessRecipe(key, CustomJobItems.getFarmerSoup());
        recipe.addIngredient(Material.BOWL);
        recipe.addIngredient(Material.CARROT);
        recipe.addIngredient(Material.POTATO);
        recipe.addIngredient(Material.WHEAT);

        Bukkit.addRecipe(recipe);
    }

    private static void registerSpaceCookieRecipe(CustomClasses plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "recipe_space_cookie");
        Bukkit.removeRecipe(key);

        ShapelessRecipe recipe = new ShapelessRecipe(key, CustomJobItems.getSpaceCookie());
        recipe.addIngredient(Material.COOKIE);
        recipe.addIngredient(Material.GLOW_BERRIES);

        Bukkit.addRecipe(recipe);
    }

    private static void registerWonderfulSoupRecipe(CustomClasses plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "recipe_wonderful_soup");
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
        NamespacedKey key = new NamespacedKey(plugin, "recipe_wonderful_hoe");
        Bukkit.removeRecipe(key);

        // 2 sticks et 2 cuprite (lingots de cuivre)
        ShapedRecipe recipe = new ShapedRecipe(key, CustomJobItems.getWonderfulHoe());
        recipe.shape("CC ", " S ", " S ");
        recipe.setIngredient('C', new RecipeChoice.MaterialChoice(List.of(Material.COPPER_INGOT, Material.RAW_COPPER)));
        recipe.setIngredient('S', Material.STICK);

        Bukkit.addRecipe(recipe);

        // Version miroir
        NamespacedKey mirrorKey = new NamespacedKey(plugin, "recipe_wonderful_hoe_mirror");
        Bukkit.removeRecipe(mirrorKey);
        ShapedRecipe mirrorRecipe = new ShapedRecipe(mirrorKey, CustomJobItems.getWonderfulHoe());
        mirrorRecipe.shape(" CC", " S ", " S ");
        mirrorRecipe.setIngredient('C', new RecipeChoice.MaterialChoice(List.of(Material.COPPER_INGOT, Material.RAW_COPPER)));
        mirrorRecipe.setIngredient('S', Material.STICK);

        Bukkit.addRecipe(mirrorRecipe);
    }
}

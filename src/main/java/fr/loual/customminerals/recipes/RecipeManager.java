package fr.loual.customminerals.recipes;

import fr.loual.newadventure.NewAdventurePlugin;
import fr.loual.customminerals.items.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

public class RecipeManager implements Listener {

    private final NewAdventurePlugin plugin;

    private final NamespacedKey hammerKey;
    private final NamespacedKey pickaxeKey;
    private final NamespacedKey axeKey;
    private final NamespacedKey axeMirroredKey;
    private final NamespacedKey blockKey;
    private final NamespacedKey reinforcedBlockKey;
    private final NamespacedKey chestKey;
    private final NamespacedKey hammerTier2Key;
    private final NamespacedKey hammerTier3Key;
    private final NamespacedKey hoeKey;
    private final NamespacedKey hoeMirroredKey;
    private final NamespacedKey nouvelleAmeKey;

    public RecipeManager(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.hammerKey = new NamespacedKey(plugin, "cuprite_hammer_craft");
        this.pickaxeKey = new NamespacedKey(plugin, "cuprite_pickaxe_craft");
        this.axeKey = new NamespacedKey(plugin, "cuprite_axe_craft");
        this.axeMirroredKey = new NamespacedKey(plugin, "cuprite_axe_craft_mirrored");
        this.blockKey = new NamespacedKey(plugin, "cuprite_block_craft");
        this.reinforcedBlockKey = new NamespacedKey(plugin, "cuprite_reinforced_block_craft");
        this.chestKey = new NamespacedKey(plugin, "cuprite_chest_craft");
        this.hammerTier2Key = new NamespacedKey(plugin, "cuprite_hammer_upgrade_2");
        this.hammerTier3Key = new NamespacedKey(plugin, "cuprite_hammer_upgrade_3");
        this.hoeKey = new NamespacedKey(plugin, "cuprite_hoe_craft");
        this.hoeMirroredKey = new NamespacedKey(plugin, "cuprite_hoe_craft_mirrored");
        this.nouvelleAmeKey = new NamespacedKey(plugin, "nouvelle_ame_craft");
    }

    public void registerRecipes() {
        ItemStack cupriteItem = Cuprite.create(plugin, 1);
        RecipeChoice.ExactChoice cupriteChoice = new RecipeChoice.ExactChoice(cupriteItem);

        ItemStack cupriteBlockItem = CupriteBlock.create(plugin, 1);
        RecipeChoice.ExactChoice cupriteBlockChoice = new RecipeChoice.ExactChoice(cupriteBlockItem);

        // 1. Marteau en Cuprite ("ccc", "csc", " s ") -> Nécessite désormais des Blocs de Cuprite
        ShapedRecipe hammerRecipe = new ShapedRecipe(hammerKey, CupriteHammer.create(plugin, 1));
        hammerRecipe.shape("ccc", "csc", " s ");
        hammerRecipe.setIngredient('c', cupriteBlockChoice);
        hammerRecipe.setIngredient('s', Material.STICK);
        registerOrReplace(hammerRecipe);

        // 2. Pioche en Cuprite ("ccc", " s ", " s ") -> Nécessite désormais des Blocs de Cuprite
        ShapedRecipe pickaxeRecipe = new ShapedRecipe(pickaxeKey, CupritePickaxe.create(plugin));
        pickaxeRecipe.shape("ccc", " s ", " s ");
        pickaxeRecipe.setIngredient('c', cupriteBlockChoice);
        pickaxeRecipe.setIngredient('s', Material.STICK);
        registerOrReplace(pickaxeRecipe);

        // 3. Bloc de Cuprite (4 de Cuprite en 2x2)
        ShapedRecipe blockRecipe = new ShapedRecipe(blockKey, CupriteBlock.create(plugin, 1));
        blockRecipe.shape("cc", "cc");
        blockRecipe.setIngredient('c', cupriteChoice);
        registerOrReplace(blockRecipe);

        // 4. Bloc Renforcé de Cuprite (4 Blocs de Cuprite en 2x2)
        ShapedRecipe reinforcedRecipe = new ShapedRecipe(reinforcedBlockKey, ReinforcedCupriteBlock.create(plugin, 1));
        reinforcedRecipe.shape("bb", "bb");
        reinforcedRecipe.setIngredient('b', cupriteBlockChoice);
        registerOrReplace(reinforcedRecipe);

        // 5. Hache en Cuprite ("cc ", "cs ", " s ") & Miroir -> Nécessite désormais des Blocs de Cuprite
        ShapedRecipe axeRecipe = new ShapedRecipe(axeKey, CupriteAxe.create(plugin));
        axeRecipe.shape("cc ", "cs ", " s ");
        axeRecipe.setIngredient('c', cupriteBlockChoice);
        axeRecipe.setIngredient('s', Material.STICK);
        registerOrReplace(axeRecipe);

        ShapedRecipe axeMirroredRecipe = new ShapedRecipe(axeMirroredKey, CupriteAxe.create(plugin));
        axeMirroredRecipe.shape(" cc", " sc", " s ");
        axeMirroredRecipe.setIngredient('c', cupriteBlockChoice);
        axeMirroredRecipe.setIngredient('s', Material.STICK);
        registerOrReplace(axeMirroredRecipe);

        // 6. Désactivation du craft vanilla du coffre piégé et définition du Coffre en Cuprite (avec Blocs de Cuprite)
        try {
            Bukkit.removeRecipe(NamespacedKey.minecraft("trapped_chest"));
        } catch (Exception ignored) {
        }

        ShapedRecipe chestRecipe = new ShapedRecipe(chestKey, CupriteChest.create(plugin));
        chestRecipe.shape(" c ", "cCc", " c ");
        chestRecipe.setIngredient('c', cupriteBlockChoice);
        chestRecipe.setIngredient('C', Material.CHEST);
        registerOrReplace(chestRecipe);

        // 7. Amélioration Marteau Palier II (Table de Forge : Marteau T1 + Bloc Renforcé de Cuprite)
        org.bukkit.inventory.SmithingTransformRecipe hammerTier2Recipe = new org.bukkit.inventory.SmithingTransformRecipe(
                hammerTier2Key,
                CupriteHammer.create(plugin, 2),
                RecipeChoice.empty(),
                new RecipeChoice.MaterialChoice(CupriteHammer.BASE_MATERIAL),
                new RecipeChoice.MaterialChoice(ReinforcedCupriteBlock.BASE_MATERIAL),
                false
        );
        registerOrReplace(hammerTier2Recipe);

        // 8. Amélioration Marteau Palier III (Table de Forge : Marteau T2 + Bloc Renforcé de Cuprite)
        org.bukkit.inventory.SmithingTransformRecipe hammerTier3Recipe = new org.bukkit.inventory.SmithingTransformRecipe(
                hammerTier3Key,
                CupriteHammer.create(plugin, 3),
                RecipeChoice.empty(),
                new RecipeChoice.MaterialChoice(CupriteHammer.BASE_MATERIAL),
                new RecipeChoice.MaterialChoice(ReinforcedCupriteBlock.BASE_MATERIAL),
                false
        );
        registerOrReplace(hammerTier3Recipe);

        // 9. Houe en Cuprite ("cc ", " s ", " s ") & Miroir -> Nécessite désormais des Blocs de Cuprite
        ShapedRecipe hoeRecipe = new ShapedRecipe(hoeKey, CupriteHoe.create(plugin));
        hoeRecipe.shape("cc ", " s ", " s ");
        hoeRecipe.setIngredient('c', cupriteBlockChoice);
        hoeRecipe.setIngredient('s', Material.STICK);
        registerOrReplace(hoeRecipe);

        ShapedRecipe hoeMirroredRecipe = new ShapedRecipe(hoeMirroredKey, CupriteHoe.create(plugin));
        hoeMirroredRecipe.shape(" cc", " s ", " s ");
        hoeMirroredRecipe.setIngredient('c', cupriteBlockChoice);
        hoeMirroredRecipe.setIngredient('s', Material.STICK);
        registerOrReplace(hoeMirroredRecipe);

        // 10. Nouvelle Âme (Changement de classe) : 1 Bloc de diamant, 1 éclat d'améthyste, 1 perle de l'ender, 1 éclat d'écho, 1 larme de ghast
        org.bukkit.inventory.ShapelessRecipe nouvelleAmeRecipe = new org.bukkit.inventory.ShapelessRecipe(nouvelleAmeKey, fr.loual.customclasses.items.NouvelleAme.create(plugin, 1));
        nouvelleAmeRecipe.addIngredient(Material.DIAMOND_BLOCK);
        nouvelleAmeRecipe.addIngredient(Material.AMETHYST_SHARD);
        nouvelleAmeRecipe.addIngredient(Material.ENDER_PEARL);
        nouvelleAmeRecipe.addIngredient(Material.ECHO_SHARD);
        nouvelleAmeRecipe.addIngredient(Material.GHAST_TEAR);
        registerOrReplace(nouvelleAmeRecipe);
    }

    private void registerOrReplace(org.bukkit.inventory.Recipe recipe) {
        if (recipe instanceof org.bukkit.Keyed keyed) {
            try {
                Bukkit.removeRecipe(keyed.getKey());
            } catch (Exception ignored) {
            }
        }
        Bukkit.addRecipe(recipe);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) {
            return;
        }

        if (!(event.getRecipe() instanceof ShapedRecipe recipe)) {
            return;
        }

        NamespacedKey key = recipe.getKey();
        CraftingInventory inv = event.getInventory();

        // Bloquer tout craft produisant un TRAPPED_CHEST autre que le coffre en cuprite
        if (event.getRecipe().getResult().getType() == Material.TRAPPED_CHEST && !key.equals(chestKey)) {
            inv.setResult(null);
            return;
        }

        if (key.equals(hammerKey) || key.equals(pickaxeKey) || key.equals(axeKey) || key.equals(axeMirroredKey) || key.equals(chestKey) || key.equals(hoeKey) || key.equals(hoeMirroredKey)) {
            for (ItemStack item : inv.getMatrix()) {
                if (item != null && item.getType() == CupriteBlock.BASE_MATERIAL && !CupriteBlock.isCupriteBlock(plugin, item)) {
                    inv.setResult(null);
                    return;
                }
            }
        } else if (key.equals(blockKey)) {
            for (ItemStack item : inv.getMatrix()) {
                if (item != null && !Cuprite.isCuprite(plugin, item)) {
                    inv.setResult(null);
                    return;
                }
            }
        } else if (key.equals(reinforcedBlockKey)) {
            for (ItemStack item : inv.getMatrix()) {
                if (item != null && !CupriteBlock.isCupriteBlock(plugin, item)) {
                    inv.setResult(null);
                    return;
                }
            }
        }
    }
}
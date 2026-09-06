package fr.loual.customclasses.jobs.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class JobRecipeGuiHolder implements InventoryHolder {

    private Inventory inventory;
    private String currentRecipeKey;

    public JobRecipeGuiHolder(String currentRecipeKey) {
        this.currentRecipeKey = currentRecipeKey;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public String getCurrentRecipeKey() {
        return currentRecipeKey;
    }

    public void setCurrentRecipeKey(String currentRecipeKey) {
        this.currentRecipeKey = currentRecipeKey;
    }
}

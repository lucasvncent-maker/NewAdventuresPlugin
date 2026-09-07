package fr.loual.casino.gui;

import fr.loual.casino.BlackjackGame;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class BlackjackGuiHolder implements InventoryHolder {

    private final BlackjackGame game;
    private Inventory inventory;

    public BlackjackGuiHolder(BlackjackGame game) {
        this.game = game;
    }

    public BlackjackGame getGame() {
        return game;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

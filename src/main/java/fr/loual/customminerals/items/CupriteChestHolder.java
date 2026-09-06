package fr.loual.customminerals.items;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class CupriteChestHolder implements InventoryHolder {

    private final Location location;
    private Inventory inventory;
    private int currentPage;

    public CupriteChestHolder(Location location) {
        this(location, 0);
    }

    public CupriteChestHolder(Location location, int currentPage) {
        this.location = location;
        this.currentPage = currentPage;
    }

    public Location getLocation() {
        return location;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

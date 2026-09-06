package fr.loual.customclasses.gui;

import fr.loual.newadventure.NewAdventurePlugin;
import fr.loual.customclasses.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ClassSelectionGui {

    public static final NamespacedKey CLASS_ICON_KEY = new NamespacedKey("customclasses", "class_choice");

    // Disposition des 8 classes dans l'inventaire 27 slots
    // Ligne 1 : slots 10, 12, 14, 16
    // Ligne 2 : slots 19, 21, 23, 25
    private static final int[] SLOTS = { 10, 12, 14, 16, 19, 21, 23, 25 };
    private static final PlayerClass[] CLASSES = {
            PlayerClass.HUMAIN,
            PlayerClass.ASSASSIN,
            PlayerClass.GUERRIER,
            PlayerClass.SAUTERELLE,
            PlayerClass.SIRENE,
            PlayerClass.DIABLE,
            PlayerClass.NECROMANCIEN,
            PlayerClass.ARCHER
    };

    public static void open(NewAdventurePlugin plugin, Player player) {
        ClassGuiHolder holder = new ClassGuiHolder();
        Inventory inv = Bukkit.createInventory(
                holder,
                36,
                Component.text("✦ Choisissez votre Classe ✦", NamedTextColor.DARK_AQUA, TextDecoration.BOLD)
        );
        holder.setInventory(inv);

        // Fond vitres grisées
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.displayName(Component.text(" "));
            glass.setItemMeta(glassMeta);
        }
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, glass);
        }

        PlayerClass currentClass = plugin.getClassManager().getPlayerClass(player);

        // Placer les 8 classes sur les slots centraux
        for (int i = 0; i < CLASSES.length; i++) {
            PlayerClass pc = CLASSES[i];
            int slot = SLOTS[i];
            ItemStack icon = createClassIcon(player, pc, currentClass);
            inv.setItem(slot, icon);
        }

        player.openInventory(inv);
    }

    private static ItemStack createClassIcon(Player player, PlayerClass pc, PlayerClass currentClass) {
        ItemStack item = new ItemStack(pc.getIcon());
        ItemMeta meta = item.getItemMeta();

        if (meta instanceof SkullMeta skullMeta && pc == PlayerClass.HUMAIN) {
            skullMeta.setOwningPlayer(player);
        }

        if (meta != null) {
            boolean isCurrent = (pc == currentClass);
            NamedTextColor titleColor = isCurrent ? NamedTextColor.GREEN : getClassColor(pc);

            meta.displayName(
                    Component.text(pc.getDisplayName(), titleColor, TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false)
            );

            List<Component> lore = new ArrayList<>(pc.getDescription());
            lore.add(Component.empty());
            if (isCurrent) {
                lore.add(Component.text("✔ Classe actuellement active", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("➜ Cliquez pour sélectionner cette classe !", NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);

            meta.getPersistentDataContainer().set(CLASS_ICON_KEY, PersistentDataType.STRING, pc.getId());
            item.setItemMeta(meta);
        }

        return item;
    }

    private static NamedTextColor getClassColor(PlayerClass pc) {
        return switch (pc) {
            case HUMAIN -> NamedTextColor.GOLD;
            case ASSASSIN -> NamedTextColor.DARK_PURPLE;
            case GUERRIER -> NamedTextColor.RED;
            case SAUTERELLE -> NamedTextColor.GREEN;
            case SIRENE -> NamedTextColor.AQUA;
            case DIABLE -> NamedTextColor.DARK_RED;
            case NECROMANCIEN -> NamedTextColor.DARK_GREEN;
            case ARCHER -> NamedTextColor.YELLOW;
            default -> NamedTextColor.WHITE;
        };
    }
}

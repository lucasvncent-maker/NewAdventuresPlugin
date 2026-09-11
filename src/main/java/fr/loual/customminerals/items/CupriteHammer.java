package fr.loual.customminerals.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public final class CupriteHammer {

    public static final String ITEM_ID = "cuprite_hammer";
    public static final Material BASE_MATERIAL = Material.NETHERITE_PICKAXE;

    private CupriteHammer() {
    }

    public static ItemStack create(Plugin plugin) {
        return create(plugin, 1);
    }

    public static ItemStack upgrade(Plugin plugin, ItemStack oldHammer, int tier) {
        ItemStack upgraded = create(plugin, tier);
        if (oldHammer != null && oldHammer.hasItemMeta()) {
            ItemMeta oldMeta = oldHammer.getItemMeta();
            ItemMeta newMeta = upgraded.getItemMeta();
            if (oldMeta != null && newMeta != null) {
                for (var entry : oldMeta.getEnchants().entrySet()) {
                    if (!newMeta.hasEnchant(entry.getKey())) {
                        newMeta.addEnchant(entry.getKey(), entry.getValue(), true);
                    } else {
                        int level = Math.max(entry.getValue(), newMeta.getEnchantLevel(entry.getKey()));
                        newMeta.addEnchant(entry.getKey(), level, true);
                    }
                }
                upgraded.setItemMeta(newMeta);
            }
        }
        return upgraded;
    }

    public static ItemStack create(Plugin plugin, int tier) {
        tier = Math.clamp(tier, 1, 3);
        ItemStack item = new ItemStack(BASE_MATERIAL);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Unbreakable de base pour tous les paliers
            meta.setUnbreakable(true);

            // Fortune III appliquée sur tous les marteaux
            meta.addEnchant(Enchantment.FORTUNE, 3, true);

            // Custom Model Data & Item Model
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of(ITEM_ID));
            meta.setCustomModelDataComponent(cmd);
            meta.setItemModel(new NamespacedKey(NamespacedKey.MINECRAFT, ITEM_ID));

            // PDC Keys
            NamespacedKey itemKey = new NamespacedKey(plugin, ITEM_ID);
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);

            NamespacedKey tierKey = new NamespacedKey(plugin, "hammer_tier");
            meta.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);

            NamespacedKey radiusKey = new NamespacedKey(plugin, "mine_radius");
            NamespacedKey depthKey = new NamespacedKey(plugin, "mine_depth");
            NamespacedKey autoSmeltKey = new NamespacedKey(plugin, "auto_smelt");

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Marteau d'excavation massif en Cuprite.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));

            if (tier == 1) {
                meta.displayName(
                        Component.text("Marteau en Cuprite", NamedTextColor.GOLD)
                                .decoration(TextDecoration.BOLD, true)
                                .decoration(TextDecoration.ITALIC, false)
                );

                meta.getPersistentDataContainer().set(radiusKey, PersistentDataType.INTEGER, 1); // 3x3
                meta.getPersistentDataContainer().set(depthKey, PersistentDataType.INTEGER, 3);  // 3 de profondeur
                meta.getPersistentDataContainer().set(autoSmeltKey, PersistentDataType.BYTE, (byte) 0);

                lore.add(Component.empty());
                lore.add(Component.text("✦ Zone de minage : ", NamedTextColor.YELLOW)
                        .append(Component.text("3x3x3", NamedTextColor.GREEN))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("✦ Effet : ", NamedTextColor.YELLOW)
                        .append(Component.text("Fortune III", NamedTextColor.AQUA))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("✦ Propriété : ", NamedTextColor.YELLOW)
                        .append(Component.text("Incassable", NamedTextColor.LIGHT_PURPLE))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(Component.text("Améliorable à la table de forge avec un ", NamedTextColor.DARK_GRAY)
                        .append(Component.text("Bloc Renforcé de Cuprite", NamedTextColor.LIGHT_PURPLE))
                        .decoration(TextDecoration.ITALIC, false));

            } else if (tier == 2) {
                meta.displayName(
                        Component.text("Marteau en Cuprite Amélioré", NamedTextColor.YELLOW)
                                .decoration(TextDecoration.BOLD, true)
                                .decoration(TextDecoration.ITALIC, false)
                );

                meta.addEnchant(Enchantment.EFFICIENCY, 7, true);

                meta.getPersistentDataContainer().set(radiusKey, PersistentDataType.INTEGER, 1); // 3x3
                meta.getPersistentDataContainer().set(depthKey, PersistentDataType.INTEGER, 3);  // 3 de profondeur
                meta.getPersistentDataContainer().set(autoSmeltKey, PersistentDataType.BYTE, (byte) 1);

                lore.add(Component.empty());
                lore.add(Component.text("✦ Zone de minage : ", NamedTextColor.YELLOW)
                        .append(Component.text("3x3x3", NamedTextColor.GREEN))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("✦ Enchantement : ", NamedTextColor.YELLOW)
                        .append(Component.text("Efficacité VII & Fortune III", NamedTextColor.AQUA))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("✦ Spécial : ", NamedTextColor.YELLOW)
                        .append(Component.text("Fonte automatique des minerais", NamedTextColor.GOLD))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("✦ Propriété : ", NamedTextColor.YELLOW)
                        .append(Component.text("Incassable", NamedTextColor.LIGHT_PURPLE))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(Component.text("Améliorable à la table de forge avec un ", NamedTextColor.DARK_GRAY)
                        .append(Component.text("Bloc Renforcé de Cuprite", NamedTextColor.LIGHT_PURPLE))
                        .decoration(TextDecoration.ITALIC, false));

            } else {
                meta.displayName(
                        Component.text("Marteau en Cuprite Renforcé", NamedTextColor.RED)
                                .decoration(TextDecoration.BOLD, true)
                                .decoration(TextDecoration.ITALIC, false)
                );

                meta.addEnchant(Enchantment.EFFICIENCY, 7, true);

                meta.getPersistentDataContainer().set(radiusKey, PersistentDataType.INTEGER, 2); // 5x5
                meta.getPersistentDataContainer().set(depthKey, PersistentDataType.INTEGER, 5);  // 5 de profondeur
                meta.getPersistentDataContainer().set(autoSmeltKey, PersistentDataType.BYTE, (byte) 1);

                lore.add(Component.empty());
                lore.add(Component.text("✦ Zone de minage : ", NamedTextColor.YELLOW)
                        .append(Component.text("5x5x5 (Titanesque)", NamedTextColor.RED))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("✦ Enchantement : ", NamedTextColor.YELLOW)
                        .append(Component.text("Efficacité VII & Fortune III", NamedTextColor.AQUA))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("✦ Spécial : ", NamedTextColor.YELLOW)
                        .append(Component.text("Fonte automatique des minerais", NamedTextColor.GOLD))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("✦ Propriété : ", NamedTextColor.YELLOW)
                        .append(Component.text("Incassable", NamedTextColor.LIGHT_PURPLE))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(Component.text("★ Niveau Maximum ★", NamedTextColor.DARK_RED)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    public static boolean isCupriteHammer(Plugin plugin, ItemStack item) {
        if (item == null || item.getType() != BASE_MATERIAL) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        NamespacedKey itemKey = new NamespacedKey(plugin, ITEM_ID);
        return meta.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    public static int getTier(Plugin plugin, ItemStack item) {
        if (!isCupriteHammer(plugin, item)) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        NamespacedKey key = new NamespacedKey(plugin, "hammer_tier");
        return meta.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 1);
    }

    public static boolean hasAutoSmelt(Plugin plugin, ItemStack item) {
        if (!isCupriteHammer(plugin, item)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        NamespacedKey key = new NamespacedKey(plugin, "auto_smelt");
        Byte b = meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    public static int getRadius(Plugin plugin, ItemStack item) {
        if (!isCupriteHammer(plugin, item)) {
            return 1;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 1;
        NamespacedKey key = new NamespacedKey(plugin, "mine_radius");
        return meta.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 1);
    }

    public static int getDepth(Plugin plugin, ItemStack item) {
        if (!isCupriteHammer(plugin, item)) {
            return 3;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 3;
        NamespacedKey key = new NamespacedKey(plugin, "mine_depth");
        return meta.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 3);
    }
}

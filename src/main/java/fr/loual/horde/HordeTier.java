package fr.loual.horde;

import net.kyori.adventure.text.format.NamedTextColor;

public enum HordeTier {
    INGOT("Standard", "Lingot de Cuprite", NamedTextColor.GOLD, 1),
    BLOCK("Héroïque", "Bloc de Cuprite", NamedTextColor.RED, 2),
    REINFORCED_BLOCK("Apocalypse", "Bloc de Cuprite Renforcé", NamedTextColor.LIGHT_PURPLE, 3);

    private final String displayName;
    private final String requiredItemName;
    private final NamedTextColor color;
    private final int level;

    HordeTier(String displayName, String requiredItemName, NamedTextColor color, int level) {
        this.displayName = displayName;
        this.requiredItemName = requiredItemName;
        this.color = color;
        this.level = level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRequiredItemName() {
        return requiredItemName;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public int getLevel() {
        return level;
    }

    public static HordeTier fromString(String str) {
        if (str == null) return INGOT;
        try {
            return valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return INGOT;
        }
    }
}

package fr.loual.customclasses.jobs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;

import java.util.List;

public enum PlayerJob {
    NONE("Aucun", "none", Material.BARRIER, List.of(
            Component.text("Aucun métier sélectionné.", NamedTextColor.GRAY)
    )),

    AGRICULTEUR("Agriculteur", "agriculteur", Material.GOLDEN_HOE, List.of(
            Component.text("Le Maître des Récoltes", NamedTextColor.GREEN, TextDecoration.ITALIC),
            Component.text("Cultivateur chevronné capable de multiplier les récoltes et automatiser les champs.", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("✦ Passif :", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.text("  • Augmentation des drops de toutes les cultures récoltées.", NamedTextColor.YELLOW),
            Component.empty(),
            Component.text("✦ Progression (4 Missions) :", NamedTextColor.AQUA, TextDecoration.BOLD),
            Component.text("  • M1 : Soupe nutritive instantanée (Regen II)", NamedTextColor.DARK_AQUA),
            Component.text("  • M2 : Plantation automatique en zone (rayon 3, pousse moyenne)", NamedTextColor.DARK_AQUA),
            Component.text("  • M3 : Space Cookie (Force III, Speed II) + Zone rayon 6 (pousse avancée)", NamedTextColor.DARK_AQUA),
            Component.text("  • M4 : Soupe Merveilleuse + Houe Merveilleuse (drops dorés & blocs)", NamedTextColor.DARK_AQUA)
    ));

    private final String displayName;
    private final String id;
    private final Material icon;
    private final List<Component> description;

    PlayerJob(String displayName, String id, Material icon, List<Component> description) {
        this.displayName = displayName;
        this.id = id;
        this.icon = icon;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getId() {
        return id;
    }

    public Material getIcon() {
        return icon;
    }

    public List<Component> getDescription() {
        return description;
    }

    public static PlayerJob fromId(String id) {
        if (id == null) return NONE;
        for (PlayerJob pj : values()) {
            if (pj.id.equalsIgnoreCase(id) || pj.name().equalsIgnoreCase(id)) {
                return pj;
            }
        }
        return NONE;
    }
}

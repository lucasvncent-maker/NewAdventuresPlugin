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
    )),

    MINEUR("Mineur", "mineur", Material.DIAMOND_PICKAXE, List.of(
            Component.text("L'Expert des Tréfonds", NamedTextColor.BLUE, TextDecoration.ITALIC),
            Component.text("Maître de l'extraction minière capable de maximiser les filons et de prospérer sous terre.", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("✦ Passif :", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.text("  • 25% de chances d'appliquer un effet Fortune supplémentaire lors du minage d'un minerai.", NamedTextColor.YELLOW),
            Component.empty(),
            Component.text("✦ Progression (3 Missions) :", NamedTextColor.AQUA, TextDecoration.BOLD),
            Component.text("  • M1 : Célérité I permanent + Vision nocturne activable (/nv)", NamedTextColor.DARK_AQUA),
            Component.text("  • M2 : 5% de chances d'obtenir de la Cuprite + 1 niveau de Fortune supplémentaire", NamedTextColor.DARK_AQUA),
            Component.text("  • M3 : Célérité II permanent + Régénération, Résistance et Résistance au Feu sous la couche Y=30", NamedTextColor.DARK_AQUA)
    )),

    ARCHITECTE("Architecte", "architecte", Material.SCAFFOLDING, List.of(
            Component.text("Le Maître Bâtisseur", NamedTextColor.GOLD, TextDecoration.ITALIC),
            Component.text("Artisan et bâtisseur hors pair capable d'ériger des édifices grandioses et d'accéder à ses ateliers n'importe où.", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("✦ Équipements & Pouvoirs :", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.text("  • Outils portatifs : Établi (/craft) & Tailleur de pierre (/sc) nomades.", NamedTextColor.YELLOW),
            Component.text("  • Tenue d'Architecte : Protection V, Incassable avec Vitesse II, Célérité II, /nv et /jb.", NamedTextColor.YELLOW),
            Component.text("  • Plume de l'Architecte : Vol créatif temporaire (30s) avec l'armure complète !", NamedTextColor.YELLOW),
            Component.empty(),
            Component.text("✦ Progression (5 Missions) :", NamedTextColor.AQUA, TextDecoration.BOLD),
            Component.text("  • M1 : Déblocage de la commande /craft (/wb)", NamedTextColor.DARK_AQUA),
            Component.text("  • M2 : Déblocage de /stonecutter (/sc) + Chapeau de l'Architecte (Vitesse II)", NamedTextColor.DARK_AQUA),
            Component.text("  • M3 : Chemise de l'Architecte (Célérité II)", NamedTextColor.DARK_AQUA),
            Component.text("  • M4 : Pantalon de l'Architecte (Vision Nocturne activable avec /nv)", NamedTextColor.DARK_AQUA),
            Component.text("  • M5 : Chaussures de l'Architecte (Saut II avec /jb) + Plume de l'Architecte (Vol 30s)", NamedTextColor.DARK_AQUA)
    )),

    AVENTURIER("Aventurier", "aventurier", Material.COMPASS, List.of(
            Component.text("L'Explorateur Légendaire", NamedTextColor.GOLD, TextDecoration.ITALIC),
            Component.text("Voyageur intrépide arpentant les contrées à la recherche de trésors oubliés.", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("✦ Passif :", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.text("  • Vitesse I permanente pour parcourir le monde sans faiblir.", NamedTextColor.YELLOW),
            Component.text("  • Coffres de structures enrichis : minerais précieux, cuprite, épée en or Sharpness VII Looting IV !", NamedTextColor.YELLOW),
            Component.empty(),
            Component.text("✦ Progression (3 Missions) :", NamedTextColor.AQUA, TextDecoration.BOLD),
            Component.text("  • M1 : Explorer 5 structures Overworld ➔ Meilleurs loots + /sethome & /home", NamedTextColor.DARK_AQUA),
            Component.text("  • M2 : Explorer les 5 biomes du Nether ➔ Perle infinie sans dégât de chute", NamedTextColor.DARK_AQUA),
            Component.text("  • M3 : 3 Pommes Cheat, 3 Élytres, 8 Éponges ➔ Élytres Incassables & Feu d'artifice infini", NamedTextColor.DARK_AQUA)
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

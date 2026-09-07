package fr.loual.customclasses.classes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;

import java.util.List;

public enum PlayerClass {
    NONE("Aucune", "none", Material.BARRIER, List.of(
            Component.text("Aucune classe sélectionnée.", NamedTextColor.GRAY)
    )),

    // 1. Humain
    HUMAIN("Humain", "humain", Material.PLAYER_HEAD, List.of(
            Component.text("Le Polyvalent", NamedTextColor.YELLOW, TextDecoration.ITALIC),
            Component.text("L'expérience de jeu vanilla classique, idéale pour progresser vite.", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("✦ Bonus :", NamedTextColor.GREEN, TextDecoration.BOLD),
            Component.text("  • +25% de points d'expérience (XP) sur toutes les sources.", NamedTextColor.DARK_GREEN),
            Component.empty(),
            Component.text("✖ Malus :", NamedTextColor.RED, TextDecoration.BOLD),
            Component.text("  • Aucun malus.", NamedTextColor.GRAY)
    )),

    // 2. Assassin
    ASSASSIN("Assassin", "assassin", Material.WITHER_SKELETON_SKULL, List.of(
            Component.text("Furtivité & Burst", NamedTextColor.DARK_PURPLE, TextDecoration.ITALIC),
            Component.text("Un chasseur rapide capable d'exécuter des cibles isolées en un éclair.", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("✦ Capacité [F] : Pas de l'Ombre", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.text("  • Téléporte 15 blocs en avant dans un nuage de fumée.", NamedTextColor.LIGHT_PURPLE),
            Component.text("  • Prochain coup porté dans le dos inflige x2 DÉGÂTS !", NamedTextColor.LIGHT_PURPLE),
            Component.empty(),
            Component.text("✦ Bonus :", NamedTextColor.GREEN, TextDecoration.BOLD),
            Component.text("  • Vitesse I permanente.", NamedTextColor.AQUA),
            Component.text("  • Frapper un monstre accroupi confère Force II pendant 5s.", NamedTextColor.AQUA),
            Component.text("  • Courte invisibilité (3s) après une attaque accroupie.", NamedTextColor.AQUA),
            Component.empty(),
            Component.text("✖ Malus :", NamedTextColor.RED, TextDecoration.BOLD),
            Component.text("  • Santé max réduite à 7 cœurs (-3 cœurs).", NamedTextColor.RED)
    )),

    // 3. Guerrier
    GUERRIER("Guerrier", "guerrier", Material.NETHERITE_CHESTPLATE, List.of(
            Component.text("Le Mastodonte", NamedTextColor.GOLD, TextDecoration.ITALIC),
            Component.text("La première ligne au corps-à-corps, capable d'encaisser de lourds assauts.", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("✦ Capacité [F] : Choc Tellurique", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.text("  • Frappe violemment le sol de son arme.", NamedTextColor.YELLOW),
            Component.text("  • Projette dans les airs puis étourdit (3s) les monstres dans un cône.", NamedTextColor.YELLOW),
            Component.empty(),
            Component.text("✦ Bonus :", NamedTextColor.GREEN, TextDecoration.BOLD),
            Component.text("  • 12 cœurs max (+2 cœurs).", NamedTextColor.AQUA),
            Component.text("  • Résistance I permanente.", NamedTextColor.AQUA),
            Component.empty(),
            Component.text("✖ Malus :", NamedTextColor.RED, TextDecoration.BOLD),
            Component.text("  • Vitesse d'attaque au corps-à-corps réduite de 15%.", NamedTextColor.RED)
    )),

    // 4. Sauterelle
    SAUTERELLE("Sauterelle", "sauterelle", Material.SLIME_BALL, List.of(
            Component.text("L'Acrobate", NamedTextColor.GREEN, TextDecoration.ITALIC),
            Component.text("Une classe aérienne qui joue avec la verticalité et le contrôle des foules.", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("✦ Capacité [F] : Catapulte Aérienne", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.text("  • Catapulté à 15 blocs de haut dans les airs.", NamedTextColor.GREEN),
            Component.text("  • Déclenche une lourde onde de choc dévastatrice à l'atterrissage !", NamedTextColor.GREEN),
            Component.empty(),
            Component.text("✦ Bonus :", NamedTextColor.GREEN, TextDecoration.BOLD),
            Component.text("  • Jump Boost II permanent.", NamedTextColor.AQUA),
            Component.text("  • Coups portés propulsent les monstres dans les airs (Knockback vertical).", NamedTextColor.AQUA),
            Component.text("  • Résistance aux chutes : dégâts de chute réduits de 50%.", NamedTextColor.AQUA),
            Component.text("  • Onde de choc lors d'une chute élevée infligeant des dégâts de zone.", NamedTextColor.AQUA)
    )),

    // 5. Sirène
    SIRENE("Sirène", "sirene", Material.HEART_OF_THE_SEA, List.of(
            Component.text("Reine des Océans", NamedTextColor.DARK_AQUA, TextDecoration.ITALIC),
            Component.text("Intouchable dans l'eau avec un contrôle de zone redoutable à terre.", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("✦ Capacité [F] : Cri Sonique", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.text("  • Onde de choc sonique : Lenteur III & Faiblesse II (8 blocs, 5s).", NamedTextColor.DARK_AQUA),
            Component.empty(),
            Component.text("✦ Bonus :", NamedTextColor.GREEN, TextDecoration.BOLD),
            Component.text("  • Respiration aquatique & Grâce du dauphin permanentes.", NamedTextColor.AQUA),
            Component.text("  • Vision nocturne active uniquement dans l'eau.", NamedTextColor.AQUA),
            Component.empty(),
            Component.text("✖ Malus :", NamedTextColor.RED, TextDecoration.BOLD),
            Component.text("  • Après 15 min hors de l'eau : Faim I et Lenteur I (réinitialisable avec de l'eau).", NamedTextColor.RED)
    )),

    // 6. Diable
    DIABLE("Diable", "diable", Material.BLAZE_POWDER, List.of(
            Component.text("Le Pyromancien", NamedTextColor.RED, TextDecoration.ITALIC),
            Component.text("Le maître absolu du Nether et du feu.", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("✦ Capacité [F] : Flaque de Braises", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.text("  • Se liquéfie au sol en flaque incandescente invincible et invisible (10s) !", NamedTextColor.RED),
            Component.text("  • Enflamme et brûle toutes les créatures qui marchent dessus !", NamedTextColor.RED),
            Component.empty(),
            Component.text("✦ Bonus :", NamedTextColor.GREEN, TextDecoration.BOLD),
            Component.text("  • Immunité totale au feu et à la lave (nage rapide dans la lave).", NamedTextColor.AQUA),
            Component.text("  • Tous les coups au corps-à-corps enflamment automatiquement les créatures.", NamedTextColor.AQUA),
            Component.empty(),
            Component.text("✖ Malus :", NamedTextColor.RED, TextDecoration.BOLD),
            Component.text("  • Se noie 3x plus vite sous l'eau et subit des dégâts sous la pluie.", NamedTextColor.RED)
    )),

    // 7. Nécromancien
    NECROMANCIEN("Nécromancien", "necromancien", Material.NETHERITE_HOE, List.of(
            Component.text("Le Moissonneur de Peste", NamedTextColor.DARK_GREEN, TextDecoration.ITALIC),
            Component.text("Affaiblit les monstres à l'usure et lève une armée de morts-vivants avec une houe.", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("✦ Capacité [F] : Détonation Putride", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.text("  • Fait exploser un serviteur : TURBO dégâts de zone (35 dégâts) !", NamedTextColor.DARK_PURPLE),
            Component.empty(),
            Component.text("✦ Bonus :", NamedTextColor.GREEN, TextDecoration.BOLD),
            Component.text("  • 40% de chances de réanimer un Zombie/Squelette serviteur éphémère (25s).", NamedTextColor.AQUA),
            Component.text("  • Coup à la houe (+10 dégâts) inflige Poison virulent IV (8s), Wither III et Lenteur II.", NamedTextColor.AQUA),
            Component.empty(),
            Component.text("✖ Malus :", NamedTextColor.RED, TextDecoration.BOLD),
            Component.text("  • Épées, haches, arcs et arbalètes inefficaces (-80% dégâts, tir bloqué).", NamedTextColor.RED)
    )),

    // 8. Archer
    ARCHER("Archer", "archer", Material.BOW, List.of(
            Component.text("Le Tireur d'Élite", NamedTextColor.YELLOW, TextDecoration.ITALIC),
            Component.text("Le maître incontesté du combat à longue distance.", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("✦ Capacité [F] : Flèche Explosive", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.text("  • Arme la prochaine flèche : Explosion massive dévastatrice (30 dégâts) !", NamedTextColor.YELLOW),
            Component.empty(),
            Component.text("✦ Bonus :", NamedTextColor.GREEN, TextDecoration.BOLD),
            Component.text("  • +30% de dégâts avec les arcs et arbalètes.", NamedTextColor.AQUA),
            Component.text("  • Vitesse normale conservée pendant la visée + Vitesse I avec arme à distance.", NamedTextColor.AQUA),
            Component.text("  • Tir à +20 blocs : Critique garanti + Knockback II.", NamedTextColor.AQUA),
            Component.text("  • Révèle les cibles touchées (Surbrillance 5s).", NamedTextColor.AQUA),
            Component.text("  • 35% de chances d'économiser la flèche & ramassage des flèches de squelettes.", NamedTextColor.AQUA),
            Component.empty(),
            Component.text("✖ Malus :", NamedTextColor.RED, TextDecoration.BOLD),
            Component.text("  • Dégâts réduits de moitié (-50%) avec épées et haches.", NamedTextColor.RED)
    ));

    private final String displayName;
    private final String id;
    private final Material icon;
    private final List<Component> description;

    PlayerClass(String displayName, String id, Material icon, List<Component> description) {
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

    public static PlayerClass fromId(String id) {
        if (id == null) return NONE;
        for (PlayerClass pc : values()) {
            if (pc.id.equalsIgnoreCase(id) || pc.name().equalsIgnoreCase(id)) {
                return pc;
            }
        }
        return NONE;
    }
}

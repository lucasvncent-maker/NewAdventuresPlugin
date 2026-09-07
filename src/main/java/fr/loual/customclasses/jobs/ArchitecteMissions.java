package fr.loual.customclasses.jobs;

import org.bukkit.Material;

import java.util.List;

public class ArchitecteMissions {

    public static final List<JobMission> MISSIONS = List.of(
            // Mission 1
            new JobMission(
                    1,
                    "Mission 1 : Les Fondations du Bâtisseur",
                    "Déblocage de la commande /craft (/workbench, /wb) pour ouvrir un établi n'importe où !",
                    List.of(
                            new JobMission.Requirement("WOOD", "Bois (Bûches ou Planches)", Material.OAK_LOG, 64),
                            new JobMission.Requirement("STONE_BRICKS", "Pierres taillées (Stonebricks)", Material.STONE_BRICKS, 64),
                            new JobMission.Requirement("SAND", "Sable", Material.SAND, 64)
                    )
            ),

            // Mission 2
            new JobMission(
                    2,
                    "Mission 2 : La Palette de Couleurs",
                    "Déblocage de /stonecutter (/sc) + Chapeau de l'Architecte (Protection V, Vitesse II permanent) !",
                    List.of(
                            new JobMission.Requirement("WOOL", "Laine (toutes couleurs)", Material.WHITE_WOOL, 64),
                            new JobMission.Requirement("RED_DYE", "Colorant Rouge", Material.RED_DYE, 8),
                            new JobMission.Requirement("BLUE_DYE", "Colorant Bleu", Material.BLUE_DYE, 8),
                            new JobMission.Requirement("YELLOW_DYE", "Colorant Jaune", Material.YELLOW_DYE, 8),
                            new JobMission.Requirement("GREEN_DYE", "Colorant Vert", Material.GREEN_DYE, 8)
                    )
            ),

            // Mission 3
            new JobMission(
                    3,
                    "Mission 3 : Le Savoir et le Rangement",
                    "Chemise de l'Architecte (Protection V, Célérité II permanent) !",
                    List.of(
                            new JobMission.Requirement("CHEST", "Coffres", Material.CHEST, 12),
                            new JobMission.Requirement("ENCHANTING_TABLE", "Table d'enchantement", Material.ENCHANTING_TABLE, 1),
                            new JobMission.Requirement("BOOKSHELF", "Bibliothèques", Material.BOOKSHELF, 15),
                            new JobMission.Requirement("ENDER_CHEST", "Ender Chest", Material.ENDER_CHEST, 1)
                    )
            ),

            // Mission 4
            new JobMission(
                    4,
                    "Mission 4 : Les Détails et la Finition",
                    "Pantalon de l'Architecte (Protection V, Vision Nocturne activable via /nv) !",
                    List.of(
                            new JobMission.Requirement("GLASS", "Verre", Material.GLASS, 64),
                            new JobMission.Requirement("LANTERN", "Lanternes", Material.LANTERN, 64),
                            new JobMission.Requirement("TRAPDOOR", "Trappes", Material.OAK_TRAPDOOR, 64),
                            new JobMission.Requirement("LEAVES", "Feuilles", Material.OAK_LEAVES, 64)
                    )
            ),

            // Mission 5
            new JobMission(
                    5,
                    "Mission 5 : Les Lumières du Génie",
                    "Chaussures de l'Architecte (Saut II via /jb) + Plume de l'Architecte (Vol Créatif 30s) !",
                    List.of(
                            new JobMission.Requirement("SEA_LANTERN", "Lanternes aquatiques", Material.SEA_LANTERN, 64),
                            new JobMission.Requirement("CALCITE", "Calcite", Material.CALCITE, 64),
                            new JobMission.Requirement("QUARTZ_BLOCK", "Blocs de Quartz", Material.QUARTZ_BLOCK, 64),
                            new JobMission.Requirement("END_ROD", "End Rods", Material.END_ROD, 64),
                            new JobMission.Requirement("FROGLIGHT", "Grelampes (Froglights)", Material.OCHRE_FROGLIGHT, 64)
                    )
            )
    );

    public static JobMission getMission(int level) {
        if (level < 1 || level > MISSIONS.size()) return null;
        return MISSIONS.get(level - 1);
    }
}

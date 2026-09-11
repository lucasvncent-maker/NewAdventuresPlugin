package fr.loual.customclasses.jobs;

import org.bukkit.Material;

import java.util.List;

public class AventurierMissions {

    public static final List<JobMission> MISSIONS = List.of(
            // Mission 1
            new JobMission(
                    1,
                    "Mission 1 : L'Explorateur de l'Overworld",
                    "Meilleurs butins dans les coffres de structures + Déblocage des commandes /sethome et /home de groupe (téléporte les alliés proches).",
                    List.of(
                            new JobMission.Requirement("OVERWORLD_STRUCTURES", "Structures explorées (Overworld)", Material.FILLED_MAP, 5)
                    )
            ),

            // Mission 2
            new JobMission(
                    2,
                    "Mission 2 : Les Tréfonds du Nether",
                    "Perle Infinie (aucun dégât de chute) + Boussole Antique de Découverte (détecte les structures proches).",
                    List.of(
                            new JobMission.Requirement("NETHER_BIOMES", "Biomes explorés (Nether)", Material.NETHERRACK, 5)
                    )
            ),

            // Mission 3
            new JobMission(
                    3,
                    "Mission 3 : Le Butin Suprême",
                    "Élytres Incassables de l'Aventurier + Fusée de Feu d'artifice infinie.",
                    List.of(
                            new JobMission.Requirement("ENCHANTED_GOLDEN_APPLE", "Pommes cheat (dorées enchantées)", Material.ENCHANTED_GOLDEN_APPLE, 3),
                            new JobMission.Requirement("ELYTRA", "Élytres", Material.ELYTRA, 3),
                            new JobMission.Requirement("SPONGE", "Éponges", Material.SPONGE, 8)
                    )
            ),

            // Mission 4
            new JobMission(
                    4,
                    "Mission 4 : L'Explorateur Légendaire des Mondes",
                    "Grappin d'Exploration (projection dynamique) + Commande /enderchest (/ec) + Déblocage du 2e Home (/sethome 2 & /home 2) !",
                    List.of(
                            new JobMission.Requirement("TOTEM_OF_UNDYING", "Totems d'Immortalité", Material.TOTEM_OF_UNDYING, 4),
                            new JobMission.Requirement("SHULKER_SHELL", "Carapaces de Shulker", Material.SHULKER_SHELL, 16),
                            new JobMission.Requirement("HEART_OF_THE_SEA", "Cœurs de la Mer", Material.HEART_OF_THE_SEA, 2)
                    )
            )
    );

    public static JobMission getMission(int level) {
        if (level < 1 || level > MISSIONS.size()) return null;
        return MISSIONS.get(level - 1);
    }
}

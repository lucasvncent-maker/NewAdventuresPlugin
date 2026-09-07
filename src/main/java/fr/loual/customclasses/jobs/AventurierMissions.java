package fr.loual.customclasses.jobs;

import org.bukkit.Material;

import java.util.List;

public class AventurierMissions {

    public static final List<JobMission> MISSIONS = List.of(
            // Mission 1
            new JobMission(
                    1,
                    "Mission 1 : L'Explorateur de l'Overworld",
                    "Meilleurs butins dans les coffres de structures + Déblocage des commandes /sethome et /home.",
                    List.of(
                            new JobMission.Requirement("OVERWORLD_STRUCTURES", "Structures explorées (Overworld)", Material.FILLED_MAP, 5)
                    )
            ),

            // Mission 2
            new JobMission(
                    2,
                    "Mission 2 : Les Tréfonds du Nether",
                    "Perle de l'Aventurier : Ender Pearl infinie qui n'inflige aucun dégât de chute.",
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
            )
    );

    public static JobMission getMission(int level) {
        if (level < 1 || level > MISSIONS.size()) return null;
        return MISSIONS.get(level - 1);
    }
}

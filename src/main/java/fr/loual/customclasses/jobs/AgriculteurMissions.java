package fr.loual.customclasses.jobs;

import org.bukkit.Material;

import java.util.List;

public class AgriculteurMissions {

    public static final List<JobMission> MISSIONS = List.of(
            // Mission 1
            new JobMission(
                    1,
                    "Mission 1 : Les Fondations Agricoles",
                    "Débloque le craft de la Soupe de l'Agriculteur (instantanée, regen 2 pendant 10s, stats carotte dorée).",
                    List.of(
                            new JobMission.Requirement("CARROT", "Carottes", Material.CARROT, 64),
                            new JobMission.Requirement("WHEAT", "Blés", Material.WHEAT, 64),
                            new JobMission.Requirement("POTATO", "Pommes de terre", Material.POTATO, 64),
                            new JobMission.Requirement("DANDELION", "Pissenlits", Material.DANDELION, 32)
                    )
            ),

            // Mission 2
            new JobMission(
                    2,
                    "Mission 2 : L'Expansion des Terres",
                    "Plantation automatique en zone (rayon 5) avec croissance intermédiaire.",
                    List.of(
                            new JobMission.Requirement("MELON", "Pastèques", Material.MELON_SLICE, 64),
                            new JobMission.Requirement("PUMPKIN", "Citrouilles", Material.PUMPKIN, 64),
                            new JobMission.Requirement("BEETROOT", "Betteraves", Material.BEETROOT, 64),
                            new JobMission.Requirement("POPPY", "Coquelicots", Material.POPPY, 32)
                    )
            ),

            // Mission 3
            new JobMission(
                    3,
                    "Mission 3 : La Pâtisserie Cosmique",
                    "Débloque le Space Cookie + Rayon de plantation étendu à 10 avec croissance avancée.",
                    List.of(
                            new JobMission.Requirement("COOKIE", "Cookies", Material.COOKIE, 64),
                            new JobMission.Requirement("CAKE", "Gâteaux", Material.CAKE, 16),
                            new JobMission.Requirement("GLOW_BERRIES", "Baies lumineuses", Material.GLOW_BERRIES, 64)
                    )
            ),

            // Mission 4
            new JobMission(
                    4,
                    "Mission 4 : La Botanique Suprême",
                    "Débloque la Soupe Merveilleuse (+1 cœur, saturation max) et la Houe Merveilleuse.",
                    List.of(
                            new JobMission.Requirement("CHORUS_FLOWER", "Fleurs de Chorus", Material.CHORUS_FLOWER, 64),
                            new JobMission.Requirement("HONEYCOMB", "Rayons de miel", Material.HONEYCOMB, 64),
                            new JobMission.Requirement("PITCHER_PLANT", "Planturnes", Material.PITCHER_PLANT, 12)
                    )
            )
    );

    public static JobMission getMission(int level) {
        if (level < 1 || level > MISSIONS.size()) return null;
        return MISSIONS.get(level - 1);
    }
}

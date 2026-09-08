package fr.loual.customclasses.jobs;

import org.bukkit.Material;

import java.util.List;

public class MineurMissions {

    public static final List<JobMission> MISSIONS = List.of(
            // Mission 1
            new JobMission(
                    1,
                    "Mission 1 : La Triade Minérale",
                    "Vision nocturne permanente (activable/désactivable avec /nv) + Effet Célérité I permanent.",
                    List.of(
                            new JobMission.Requirement("COAL", "Charbons", Material.COAL, 64),
                            new JobMission.Requirement("IRON", "Fers", Material.RAW_IRON, 64),
                            new JobMission.Requirement("GOLD", "Ors", Material.RAW_GOLD, 64)
                    )
            ),

            // Mission 2
            new JobMission(
                    2,
                    "Mission 2 : L'Artisanat Minier & la Sacoche",
                    "Sacoche de Minage aspirante reçue + 1 niveau de Fortune supplémentaire garanti (+1).",
                    List.of(
                            new JobMission.Requirement("DIAMOND", "Diamants", Material.DIAMOND, 64),
                            new JobMission.Requirement("EMERALD", "Émeraudes", Material.EMERALD, 64),
                            new JobMission.Requirement("CUPRITE", "Cuprites", Material.RAW_COPPER, 16)
                    )
            ),

            // Mission 3
            new JobMission(
                    3,
                    "Mission 3 : La Maîtrise des Tréfonds",
                    "Célérité II permanent + 5% de chances de drop de Cuprite supplémentaire sur tous les minerais.",
                    List.of(
                            new JobMission.Requirement("AMETHYST", "Améthystes", Material.AMETHYST_SHARD, 64),
                            new JobMission.Requirement("SCULK_SENSOR", "Capteurs Sculk", Material.SCULK_SENSOR, 64),
                            new JobMission.Requirement("SPAWNER", "Générateurs (Spawners)", Material.SPAWNER, 3)
                    )
            ),

            // Mission 4
            new JobMission(
                    4,
                    "Mission 4 : Le Maître des Abysses & de la Netherite",
                    "Bénédiction sous la couche Y=30 (Régénération I, Résistance I, Résistance au Feu I) + 1 niveau de Fortune supplémentaire ultime (Fortune +2 total) !",
                    List.of(
                            new JobMission.Requirement("ANCIENT_DEBRIS", "Débris Antiques", Material.ANCIENT_DEBRIS, 16),
                            new JobMission.Requirement("CUPRITE", "Cuprites", Material.RAW_COPPER, 32),
                            new JobMission.Requirement("ECHO_SHARD", "Éclats d'Écho", Material.ECHO_SHARD, 16)
                    )
            )
    );

    public static JobMission getMission(int level) {
        if (level < 1 || level > MISSIONS.size()) return null;
        return MISSIONS.get(level - 1);
    }
}

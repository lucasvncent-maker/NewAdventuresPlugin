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
                    "Mission 2 : Les Gemmes Rares et la Cuprite",
                    "5% de chances d'obtenir de la Cuprite en minant des minerais + 1 niveau de Fortune supplémentaire.",
                    List.of(
                            new JobMission.Requirement("DIAMOND", "Diamants", Material.DIAMOND, 64),
                            new JobMission.Requirement("EMERALD", "Émeraudes", Material.EMERALD, 64),
                            new JobMission.Requirement("CUPRITE", "Cuprites", Material.RAW_COPPER, 16)
                    )
            ),

            // Mission 3
            new JobMission(
                    3,
                    "Mission 3 : Les Richesses des Profondeurs",
                    "Célérité II permanent + Régénération I, Résistance I et Résistance au Feu I sous la couche 30.",
                    List.of(
                            new JobMission.Requirement("AMETHYST", "Améthystes", Material.AMETHYST_SHARD, 64),
                            new JobMission.Requirement("SCULK_SENSOR", "Capteurs Sculk", Material.SCULK_SENSOR, 64),
                            new JobMission.Requirement("SPAWNER", "Générateurs (Spawners)", Material.SPAWNER, 3)
                    )
            )
    );

    public static JobMission getMission(int level) {
        if (level < 1 || level > MISSIONS.size()) return null;
        return MISSIONS.get(level - 1);
    }
}

package fr.loual.customclasses.jobs;

import fr.loual.customclasses.CustomClasses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JobManager {

    private final CustomClasses plugin;
    private final NamespacedKey jobKey;
    private final Map<UUID, PlayerJob> cache = new HashMap<>();

    public JobManager(CustomClasses plugin) {
        this.plugin = plugin;
        this.jobKey = new NamespacedKey(plugin, "player_job");
    }

    public PlayerJob getPlayerJob(Player player) {
        if (cache.containsKey(player.getUniqueId())) {
            return cache.get(player.getUniqueId());
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String id = pdc.get(jobKey, PersistentDataType.STRING);
        PlayerJob pj = PlayerJob.fromId(id);
        cache.put(player.getUniqueId(), pj);
        return pj;
    }

    public boolean hasJob(Player player) {
        return getPlayerJob(player) != PlayerJob.NONE;
    }

    public void setPlayerJob(Player player, PlayerJob pj) {
        cache.put(player.getUniqueId(), pj);
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pj == PlayerJob.NONE) {
            pdc.remove(jobKey);
        } else {
            pdc.set(jobKey, PersistentDataType.STRING, pj.getId());
        }
    }

    public void resetPlayerJob(Player player) {
        setPlayerJob(player, PlayerJob.NONE);
        player.sendMessage(
                Component.text("[Métiers] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("Votre métier a été réinitialisé !", NamedTextColor.YELLOW))
        );
    }

    public int getJobLevel(Player player, PlayerJob job) {
        NamespacedKey key = new NamespacedKey(plugin, "job_" + job.getId() + "_level");
        Integer level = player.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return level != null ? level : 0;
    }

    public void setJobLevel(Player player, PlayerJob job, int level) {
        NamespacedKey key = new NamespacedKey(plugin, "job_" + job.getId() + "_level");
        player.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, level);
    }

    public int getRequirementProgress(Player player, PlayerJob job, int missionLevel, String reqKey) {
        NamespacedKey key = new NamespacedKey(plugin, "job_" + job.getId() + "_m" + missionLevel + "_" + reqKey.toLowerCase());
        Integer val = player.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return val != null ? val : 0;
    }

    public void setRequirementProgress(Player player, PlayerJob job, int missionLevel, String reqKey, int amount) {
        NamespacedKey key = new NamespacedKey(plugin, "job_" + job.getId() + "_m" + missionLevel + "_" + reqKey.toLowerCase());
        player.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, amount);
    }

    /**
     * Ajoute de la progression sur un objectif de la mission courante du joueur.
     */
    public void addProgress(Player player, PlayerJob job, String reqKey, int amount) {
        if (job != getPlayerJob(player)) return;

        int currentLevel = getJobLevel(player, job);
        int missionNumber = currentLevel + 1;

        JobMission mission = AgriculteurMissions.getMission(missionNumber);
        if (mission == null) return; // Déjà au niveau max

        JobMission.Requirement req = mission.getRequirement(reqKey);
        if (req == null) return;

        int currentAmount = getRequirementProgress(player, job, missionNumber, reqKey);
        if (currentAmount >= req.requiredAmount()) return;

        int newAmount = Math.min(req.requiredAmount(), currentAmount + amount);
        setRequirementProgress(player, job, missionNumber, reqKey, newAmount);

        // Feedback discret dans l'action bar
        player.sendActionBar(Component.text(
                "[" + job.getDisplayName() + "] " + req.displayName() + " : " + newAmount + " / " + req.requiredAmount(),
                newAmount >= req.requiredAmount() ? NamedTextColor.GREEN : NamedTextColor.GOLD
        ));

        // Vérifier si la mission entière est terminée
        checkMissionCompletion(player, job, mission);
    }

    private void checkMissionCompletion(Player player, PlayerJob job, JobMission mission) {
        int missionNumber = mission.getLevel();
        for (JobMission.Requirement req : mission.getRequirements()) {
            int progress = getRequirementProgress(player, job, missionNumber, req.key());
            if (progress < req.requiredAmount()) {
                return; // Mission non finie
            }
        }

        // Toutes les exigences sont remplies !
        setJobLevel(player, job, missionNumber);

        // Célébration
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.2);

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("★ ========================================= ★", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("✦ MISSION ACCOMPLIE : ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text(mission.getTitle(), NamedTextColor.YELLOW, TextDecoration.BOLD)));
        player.sendMessage(Component.text("✦ Récompense débloquée : ", NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.text(mission.getRewardDescription(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("★ ========================================= ★", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.empty());
    }

    public void unloadPlayer(Player player) {
        cache.remove(player.getUniqueId());
    }
}

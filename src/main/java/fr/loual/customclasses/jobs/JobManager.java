package fr.loual.customclasses.jobs;

import fr.loual.newadventure.NewAdventurePlugin;
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

    private final NewAdventurePlugin plugin;
    private final NamespacedKey jobKey;
    private final Map<UUID, PlayerJob> cache = new HashMap<>();

    public JobManager(NewAdventurePlugin plugin) {
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
        setJobLevel(player, PlayerJob.AGRICULTEUR, 0);
        JobRecipes.syncDiscoveredRecipes(plugin, player);
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
        JobRecipes.syncDiscoveredRecipes(plugin, player);
    }

    /**
     * Mappe un matériau au code d'exigence (Requirement Key).
     */
    public String getRequirementKeyForMaterial(org.bukkit.Material mat) {
        if (mat == null) return null;
        return switch (mat) {
            case CARROT -> "CARROT";
            case WHEAT, HAY_BLOCK -> "WHEAT";
            case POTATO -> "POTATO";
            case DANDELION -> "DANDELION";
            case MELON_SLICE, MELON -> "MELON";
            case PUMPKIN, CARVED_PUMPKIN -> "PUMPKIN";
            case BEETROOT -> "BEETROOT";
            case POPPY -> "POPPY";
            case COOKIE -> "COOKIE";
            case CAKE -> "CAKE";
            case GLOW_BERRIES -> "GLOW_BERRIES";
            case CHORUS_FLOWER -> "CHORUS_FLOWER";
            case HONEYCOMB, HONEYCOMB_BLOCK -> "HONEYCOMB";
            case PITCHER_PLANT -> "PITCHER_PLANT";
            default -> null;
        };
    }

    /**
     * Compte la quantité réelle de l'objet présent dans l'inventaire du joueur.
     */
    public int getInventoryItemCount(Player player, String reqKey) {
        if (player == null || reqKey == null) return 0;
        int count = 0;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            org.bukkit.Material type = item.getType();
            switch (reqKey) {
                case "CARROT" -> {
                    if (type == org.bukkit.Material.CARROT) count += item.getAmount();
                }
                case "WHEAT" -> {
                    if (type == org.bukkit.Material.WHEAT) count += item.getAmount();
                    else if (type == org.bukkit.Material.HAY_BLOCK) count += item.getAmount() * 9;
                }
                case "POTATO" -> {
                    if (type == org.bukkit.Material.POTATO) count += item.getAmount();
                }
                case "DANDELION" -> {
                    if (type == org.bukkit.Material.DANDELION) count += item.getAmount();
                }
                case "MELON" -> {
                    if (type == org.bukkit.Material.MELON_SLICE) count += item.getAmount();
                    else if (type == org.bukkit.Material.MELON) count += item.getAmount() * 9;
                }
                case "PUMPKIN" -> {
                    if (type == org.bukkit.Material.PUMPKIN || type == org.bukkit.Material.CARVED_PUMPKIN) count += item.getAmount();
                }
                case "BEETROOT" -> {
                    if (type == org.bukkit.Material.BEETROOT) count += item.getAmount();
                }
                case "POPPY" -> {
                    if (type == org.bukkit.Material.POPPY) count += item.getAmount();
                }
                case "COOKIE" -> {
                    if (type == org.bukkit.Material.COOKIE) count += item.getAmount();
                }
                case "CAKE" -> {
                    if (type == org.bukkit.Material.CAKE) count += item.getAmount();
                }
                case "GLOW_BERRIES" -> {
                    if (type == org.bukkit.Material.GLOW_BERRIES) count += item.getAmount();
                }
                case "CHORUS_FLOWER" -> {
                    if (type == org.bukkit.Material.CHORUS_FLOWER) count += item.getAmount();
                }
                case "HONEYCOMB" -> {
                    if (type == org.bukkit.Material.HONEYCOMB) count += item.getAmount();
                    else if (type == org.bukkit.Material.HONEYCOMB_BLOCK) count += item.getAmount() * 4;
                }
                case "PITCHER_PLANT" -> {
                    if (type == org.bukkit.Material.PITCHER_PLANT) count += item.getAmount();
                }
                default -> {}
            }
        }
        return count;
    }

    /**
     * Retourne la progression sur un objectif donné :
     * - Si la mission est déjà complétée : quantité requise max.
     * - Si la mission est en cours : nombre d'objets possédés dans l'inventaire (plafonné à requiredAmount).
     * - Si la mission est future : 0.
     */
    public int getRequirementProgress(Player player, PlayerJob job, int missionLevel, String reqKey) {
        int completedLevel = getJobLevel(player, job);
        JobMission mission = AgriculteurMissions.getMission(missionLevel);
        if (mission == null) return 0;
        JobMission.Requirement req = mission.getRequirement(reqKey);
        if (req == null) return 0;

        if (missionLevel <= completedLevel) {
            return req.requiredAmount();
        } else if (missionLevel == completedLevel + 1) {
            int inInv = getInventoryItemCount(player, reqKey);
            return Math.min(req.requiredAmount(), inInv);
        } else {
            return 0;
        }
    }

    /**
     * Affichage de la progression dans l'action bar lors du ramassage ou craft d'un objet,
     * puis vérification de complétion de mission.
     */
    public void checkAndNotifyProgress(Player player, PlayerJob job, String reqKey) {
        if (job != getPlayerJob(player)) return;

        int currentLevel = getJobLevel(player, job);
        int missionNumber = currentLevel + 1;

        JobMission mission = AgriculteurMissions.getMission(missionNumber);
        if (mission == null) return; // Déjà au niveau max

        JobMission.Requirement req = mission.getRequirement(reqKey);
        if (req == null) return; // Ne fait pas partie de la mission actuelle

        int count = getInventoryItemCount(player, reqKey);
        int displayCount = Math.min(count, req.requiredAmount());

        // Feedback discret dans l'action bar lorsqu'on ramasse un item requis
        player.sendActionBar(Component.text(
                "[" + job.getDisplayName() + "] " + req.displayName() + " : " + displayCount + " / " + req.requiredAmount(),
                displayCount >= req.requiredAmount() ? NamedTextColor.GREEN : NamedTextColor.GOLD
        ));

        // Vérifier si la mission entière est terminée
        checkCurrentMissionCompletion(player, job);
    }

    /**
     * Méthode de compatibilité si appelée ailleurs.
     */
    public void addProgress(Player player, PlayerJob job, String reqKey, int amount) {
        checkAndNotifyProgress(player, job, reqKey);
    }

    /**
     * Vérifie si le joueur possède tous les objets demandés par sa mission en cours.
     * Si oui, monte de niveau et célèbre !
     */
    public boolean checkCurrentMissionCompletion(Player player, PlayerJob job) {
        if (job != getPlayerJob(player)) return false;

        boolean completedAny = false;
        while (true) {
            int currentLevel = getJobLevel(player, job);
            int missionNumber = currentLevel + 1;

            JobMission mission = AgriculteurMissions.getMission(missionNumber);
            if (mission == null) break; // Déjà au niveau max

            boolean allMet = true;
            for (JobMission.Requirement req : mission.getRequirements()) {
                int count = getInventoryItemCount(player, req.key());
                if (count < req.requiredAmount()) {
                    allMet = false;
                    break;
                }
            }

            if (!allMet) break;

            // Toutes les exigences de cette mission sont remplies dans l'inventaire !
            setJobLevel(player, job, missionNumber);
            completedAny = true;

            // Débloquer la recette dans le livre de craft Minecraft (vanilla)
            JobRecipes.syncDiscoveredRecipes(plugin, player);

            // Célébration
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.2);

            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("★ ========================================= ★", NamedTextColor.GOLD, TextDecoration.BOLD));
            player.sendMessage(Component.text("✦ MISSION ACCOMPLIE : ", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .append(Component.text(mission.getTitle(), NamedTextColor.YELLOW, TextDecoration.BOLD)));
            player.sendMessage(Component.text("✦ Récompense débloquée : ", NamedTextColor.AQUA, TextDecoration.BOLD)
                    .append(Component.text(mission.getRewardDescription(), NamedTextColor.WHITE)));

            if (missionNumber == 1) {
                player.sendMessage(Component.text("✦ Ingrédients Soupe de l'Agriculteur : ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("1x Bol, 1x Carotte, 1x Pomme de terre, 1x Blé", NamedTextColor.YELLOW)));
                player.sendMessage(Component.text("➜ Tapez /job recipes ou consultez votre établi pour voir le craft !", NamedTextColor.GRAY));
            } else if (missionNumber == 3) {
                player.sendMessage(Component.text("✦ Ingrédients Space Cookie : ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("1x Cookie, 1x Baie lumineuse", NamedTextColor.YELLOW)));
                player.sendMessage(Component.text("➜ Tapez /job recipes ou consultez votre établi pour voir le craft !", NamedTextColor.GRAY));
            } else if (missionNumber == 4) {
                player.sendMessage(Component.text("✦ Crafts Suprêmes débloqués : ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("Soupe Merveilleuse (9 ingrédients) & Houe Merveilleuse (2 Cuivres, 2 Bâtons)", NamedTextColor.YELLOW)));
                player.sendMessage(Component.text("➜ Tapez /job recipes ou consultez votre établi pour voir les crafts !", NamedTextColor.GRAY));
            }

            player.sendMessage(Component.text("★ ========================================= ★", NamedTextColor.GOLD, TextDecoration.BOLD));
            player.sendMessage(Component.empty());
        }

        return completedAny;
    }

    public void unloadPlayer(Player player) {
        cache.remove(player.getUniqueId());
    }
}

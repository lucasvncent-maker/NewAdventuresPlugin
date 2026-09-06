package fr.loual.customclasses.jobs;

import fr.loual.customclasses.classes.PlayerClass;
import fr.loual.customminerals.items.Cuprite;
import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JobManager {

    private final NewAdventurePlugin plugin;
    private final NamespacedKey jobKey;
    private final NamespacedKey nvKey;
    private final Map<UUID, PlayerJob> cache = new HashMap<>();

    public JobManager(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.jobKey = new NamespacedKey(plugin, "player_job");
        this.nvKey = new NamespacedKey(plugin, "mineur_nv_enabled");
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
        applyJobEffects(player);
    }

    public void resetPlayerJob(Player player) {
        setPlayerJob(player, PlayerJob.NONE);
        setJobLevel(player, PlayerJob.AGRICULTEUR, 0);
        setJobLevel(player, PlayerJob.MINEUR, 0);
        setNightVisionEnabled(player, false);
        applyJobEffects(player);
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
        applyJobEffects(player);
    }

    public JobMission getMission(PlayerJob job, int level) {
        if (job == PlayerJob.AGRICULTEUR) {
            return AgriculteurMissions.getMission(level);
        } else if (job == PlayerJob.MINEUR) {
            return MineurMissions.getMission(level);
        }
        return null;
    }

    public boolean isNightVisionEnabled(Player player) {
        Byte b = player.getPersistentDataContainer().get(nvKey, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    public void setNightVisionEnabled(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(nvKey, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
        applyJobEffects(player);
    }

    public boolean toggleNightVision(Player player) {
        boolean next = !isNightVisionEnabled(player);
        setNightVisionEnabled(player, next);
        return next;
    }

    /**
     * Applique les effets permanents selon le métier et le palier de mission.
     */
    public void applyJobEffects(Player player) {
        PlayerJob job = getPlayerJob(player);
        int level = getJobLevel(player, job);

        boolean isSirene = false;
        try {
            isSirene = (plugin.getClassManager().getPlayerClass(player) == PlayerClass.SIRENE);
        } catch (Exception ignored) {}

        if (job == PlayerJob.MINEUR) {
            // Haste permanent
            if (level >= 3) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, PotionEffect.INFINITE_DURATION, 1, false, false, true));
            } else if (level >= 1) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, PotionEffect.INFINITE_DURATION, 0, false, false, true));
            } else {
                player.removePotionEffect(PotionEffectType.HASTE);
            }

            // Night Vision
            if (level >= 1 && isNightVisionEnabled(player)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, true));
            } else if (!isSirene) {
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
        } else {
            // Nettoyage si le joueur n'est pas mineur
            player.removePotionEffect(PotionEffectType.HASTE);
            if (!isSirene) {
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
        }
    }

    /**
     * Tâche périodique (toutes les secondes) pour gérer les auras de profondeur sous la couche 30.
     */
    public void tickLayerEffects() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (getPlayerJob(player) == PlayerJob.MINEUR && getJobLevel(player, PlayerJob.MINEUR) >= 3) {
                if (player.getLocation().getY() <= 30.0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 50, 0, false, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 50, 0, false, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 50, 0, false, false, true));
                }
            }
        }
    }

    /**
     * Mappe un matériau au code d'exigence (Requirement Key).
     */
    public String getRequirementKeyForMaterial(Material mat) {
        if (mat == null) return null;
        return switch (mat) {
            // Agriculteur
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

            // Mineur
            case COAL, COAL_BLOCK -> "COAL";
            case RAW_IRON, IRON_INGOT, IRON_BLOCK -> "IRON";
            case RAW_GOLD, GOLD_INGOT, GOLD_BLOCK -> "GOLD";
            case DIAMOND, DIAMOND_BLOCK -> "DIAMOND";
            case EMERALD, EMERALD_BLOCK -> "EMERALD";
            case RAW_COPPER, COPPER_INGOT, COPPER_BLOCK -> "CUPRITE";
            case AMETHYST_SHARD, AMETHYST_BLOCK -> "AMETHYST";
            case SCULK_SENSOR -> "SCULK_SENSOR";
            case SPAWNER -> "SPAWNER";
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
            Material type = item.getType();
            switch (reqKey) {
                // --- AGRICULTEUR ---
                case "CARROT" -> {
                    if (type == Material.CARROT) count += item.getAmount();
                }
                case "WHEAT" -> {
                    if (type == Material.WHEAT) count += item.getAmount();
                    else if (type == Material.HAY_BLOCK) count += item.getAmount() * 9;
                }
                case "POTATO" -> {
                    if (type == Material.POTATO) count += item.getAmount();
                }
                case "DANDELION" -> {
                    if (type == Material.DANDELION) count += item.getAmount();
                }
                case "MELON" -> {
                    if (type == Material.MELON_SLICE) count += item.getAmount();
                    else if (type == Material.MELON) count += item.getAmount() * 9;
                }
                case "PUMPKIN" -> {
                    if (type == Material.PUMPKIN || type == Material.CARVED_PUMPKIN) count += item.getAmount();
                }
                case "BEETROOT" -> {
                    if (type == Material.BEETROOT) count += item.getAmount();
                }
                case "POPPY" -> {
                    if (type == Material.POPPY) count += item.getAmount();
                }
                case "COOKIE" -> {
                    if (type == Material.COOKIE) count += item.getAmount();
                }
                case "CAKE" -> {
                    if (type == Material.CAKE) count += item.getAmount();
                }
                case "GLOW_BERRIES" -> {
                    if (type == Material.GLOW_BERRIES) count += item.getAmount();
                }
                case "CHORUS_FLOWER" -> {
                    if (type == Material.CHORUS_FLOWER) count += item.getAmount();
                }
                case "HONEYCOMB" -> {
                    if (type == Material.HONEYCOMB) count += item.getAmount();
                    else if (type == Material.HONEYCOMB_BLOCK) count += item.getAmount() * 4;
                }
                case "PITCHER_PLANT" -> {
                    if (type == Material.PITCHER_PLANT) count += item.getAmount();
                }

                // --- MINEUR ---
                case "COAL" -> {
                    if (type == Material.COAL) count += item.getAmount();
                    else if (type == Material.COAL_BLOCK) count += item.getAmount() * 9;
                }
                case "IRON" -> {
                    if (type == Material.RAW_IRON || type == Material.IRON_INGOT) count += item.getAmount();
                    else if (type == Material.IRON_BLOCK) count += item.getAmount() * 9;
                }
                case "GOLD" -> {
                    if (type == Material.RAW_GOLD || type == Material.GOLD_INGOT) count += item.getAmount();
                    else if (type == Material.GOLD_BLOCK) count += item.getAmount() * 9;
                }
                case "DIAMOND" -> {
                    if (type == Material.DIAMOND) count += item.getAmount();
                    else if (type == Material.DIAMOND_BLOCK) count += item.getAmount() * 9;
                }
                case "EMERALD" -> {
                    if (type == Material.EMERALD) count += item.getAmount();
                    else if (type == Material.EMERALD_BLOCK) count += item.getAmount() * 9;
                }
                case "CUPRITE" -> {
                    if (Cuprite.isCuprite(plugin, item)) count += item.getAmount();
                }
                case "AMETHYST" -> {
                    if (type == Material.AMETHYST_SHARD) count += item.getAmount();
                    else if (type == Material.AMETHYST_BLOCK) count += item.getAmount() * 4;
                }
                case "SCULK_SENSOR" -> {
                    if (type == Material.SCULK_SENSOR) count += item.getAmount();
                }
                case "SPAWNER" -> {
                    if (type == Material.SPAWNER) count += item.getAmount();
                }
                default -> {}
            }
        }
        return count;
    }

    /**
     * Retourne la progression sur un objectif donné.
     */
    public int getRequirementProgress(Player player, PlayerJob job, int missionLevel, String reqKey) {
        int completedLevel = getJobLevel(player, job);
        JobMission mission = getMission(job, missionLevel);
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
     * Affichage de la progression dans l'action bar lors du ramassage ou minage d'un objet.
     */
    public void checkAndNotifyProgress(Player player, PlayerJob job, String reqKey) {
        if (job != getPlayerJob(player)) return;

        int currentLevel = getJobLevel(player, job);
        int missionNumber = currentLevel + 1;

        JobMission mission = getMission(job, missionNumber);
        if (mission == null) return; // Déjà au niveau max

        JobMission.Requirement req = mission.getRequirement(reqKey);
        if (req == null) return; // Ne fait pas partie de la mission actuelle

        int count = getInventoryItemCount(player, reqKey);
        int displayCount = Math.min(count, req.requiredAmount());

        player.sendActionBar(Component.text(
                "[" + job.getDisplayName() + "] " + req.displayName() + " : " + displayCount + " / " + req.requiredAmount(),
                displayCount >= req.requiredAmount() ? NamedTextColor.GREEN : NamedTextColor.GOLD
        ));

        checkCurrentMissionCompletion(player, job);
    }

    public void addProgress(Player player, PlayerJob job, String reqKey, int amount) {
        checkAndNotifyProgress(player, job, reqKey);
    }

    /**
     * Vérifie si le joueur possède tous les objets demandés par sa mission en cours.
     */
    public boolean checkCurrentMissionCompletion(Player player, PlayerJob job) {
        if (job != getPlayerJob(player)) return false;

        boolean completedAny = false;
        while (true) {
            int currentLevel = getJobLevel(player, job);
            int missionNumber = currentLevel + 1;

            JobMission mission = getMission(job, missionNumber);
            if (mission == null) break;

            boolean allMet = true;
            for (JobMission.Requirement req : mission.getRequirements()) {
                int count = getInventoryItemCount(player, req.key());
                if (count < req.requiredAmount()) {
                    allMet = false;
                    break;
                }
            }

            if (!allMet) break;

            setJobLevel(player, job, missionNumber);
            completedAny = true;

            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.2);

            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("★ ========================================= ★", NamedTextColor.GOLD, TextDecoration.BOLD));
            player.sendMessage(Component.text("✦ MISSION ACCOMPLIE : ", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .append(Component.text(mission.getTitle(), NamedTextColor.YELLOW, TextDecoration.BOLD)));
            player.sendMessage(Component.text("✦ Récompense débloquée : ", NamedTextColor.AQUA, TextDecoration.BOLD)
                    .append(Component.text(mission.getRewardDescription(), NamedTextColor.WHITE)));

            if (job == PlayerJob.AGRICULTEUR) {
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
                            .append(Component.text("Soupe Merveilleuse & Houe Merveilleuse", NamedTextColor.YELLOW)));
                    player.sendMessage(Component.text("➜ Tapez /job recipes ou consultez votre établi pour voir les crafts !", NamedTextColor.GRAY));
                }
            } else if (job == PlayerJob.MINEUR) {
                if (missionNumber == 1) {
                    player.sendMessage(Component.text("✦ Récompenses Mineur M1 : ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Effet Célérité I permanent + /nv pour activer la Vision Nocturne !", NamedTextColor.YELLOW)));
                } else if (missionNumber == 2) {
                    player.sendMessage(Component.text("✦ Récompenses Mineur M2 : ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("5% de chance de drop de la Cuprite sur les minerais + Fortune supplémentaire (+1) !", NamedTextColor.YELLOW)));
                } else if (missionNumber == 3) {
                    player.sendMessage(Component.text("✦ Récompenses Mineur M3 : ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Célérité II permanent + Régénération, Résistance & Résistance au Feu sous la couche 30 !", NamedTextColor.YELLOW)));
                }
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

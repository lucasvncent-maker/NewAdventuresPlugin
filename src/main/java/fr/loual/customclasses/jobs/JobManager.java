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
    private final NamespacedKey jbKey;
    private final Map<UUID, PlayerJob> cache = new HashMap<>();

    public JobManager(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.jobKey = new NamespacedKey(plugin, "player_job");
        this.nvKey = new NamespacedKey(plugin, "mineur_nv_enabled");
        this.jbKey = new NamespacedKey(plugin, "architect_jb_enabled");
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
        setJobLevel(player, PlayerJob.ARCHITECTE, 0);
        setNightVisionEnabled(player, false);
        setJumpBoostEnabled(player, false);
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
        } else if (job == PlayerJob.ARCHITECTE) {
            return ArchitecteMissions.getMission(level);
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

    public boolean isJumpBoostEnabled(Player player) {
        Byte b = player.getPersistentDataContainer().get(jbKey, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    public void setJumpBoostEnabled(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(jbKey, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
        applyJobEffects(player);
    }

    public boolean toggleJumpBoost(Player player) {
        boolean next = !isJumpBoostEnabled(player);
        setJumpBoostEnabled(player, next);
        return next;
    }

    /**
     * Applique les effets permanents selon le métier, les paliers de mission et les pièces d'armure de l'Architecte.
     */
    public void applyJobEffects(Player player) {
        PlayerJob job = getPlayerJob(player);
        int level = getJobLevel(player, job);

        boolean isSirene = false;
        boolean isSauterelle = false;
        try {
            PlayerClass pc = plugin.getClassManager().getPlayerClass(player);
            isSirene = (pc == PlayerClass.SIRENE);
            isSauterelle = (pc == PlayerClass.SAUTERELLE);
        } catch (Exception ignored) {}

        var inv = player.getInventory();

        // 1. Célérité (Haste)
        boolean hasHaste = false;
        int hasteAmp = 0;
        if (job == PlayerJob.MINEUR) {
            if (level >= 3) {
                hasHaste = true;
                hasteAmp = 1; // Haste II
            } else if (level >= 1) {
                hasHaste = true;
                hasteAmp = 0; // Haste I
            }
        }
        if (CustomJobItems.isJobItem(inv.getChestplate(), CustomJobItems.ID_ARCHITECT_CHESTPLATE)) {
            hasHaste = true;
            hasteAmp = Math.max(hasteAmp, 1); // Chemise donne Haste II
        }

        if (hasHaste) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, PotionEffect.INFINITE_DURATION, hasteAmp, false, false, true));
        } else {
            player.removePotionEffect(PotionEffectType.HASTE);
        }

        // 2. Vitesse (Speed II avec Chapeau de l'Architecte)
        if (CustomJobItems.isJobItem(inv.getHelmet(), CustomJobItems.ID_ARCHITECT_HELMET)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false, true));
        } else {
            if (player.hasPotionEffect(PotionEffectType.SPEED)) {
                PotionEffect pe = player.getPotionEffect(PotionEffectType.SPEED);
                if (pe != null && pe.getDuration() > 3600 * 20) {
                    player.removePotionEffect(PotionEffectType.SPEED);
                }
            }
        }

        // 3. Vision Nocturne (Mineur M1+ ou Pantalon de l'Architecte)
        boolean hasLeggings = CustomJobItems.isJobItem(inv.getLeggings(), CustomJobItems.ID_ARCHITECT_LEGGINGS);
        boolean canHaveNv = (job == PlayerJob.MINEUR && level >= 1) || hasLeggings;
        boolean isSireneInWater = isSirene && (player.isInWater() || player.getEyeLocation().getBlock().getType() == Material.WATER);
        if (canHaveNv && isNightVisionEnabled(player)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, true));
        } else if (!isSireneInWater) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }

        // 4. Saut Amélioré (Jump Boost II avec Chaussures de l'Architecte via /jb)
        boolean hasBoots = CustomJobItems.isJobItem(inv.getBoots(), CustomJobItems.ID_ARCHITECT_BOOTS);
        if (hasBoots && isJumpBoostEnabled(player)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, 1, false, false, true));
        } else if (!isSauterelle) {
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        }
    }

    /**
     * Tâche périodique (toutes les secondes) pour actualiser les effets de l'armure de l'Architecte.
     */
    public void tickArmorEffects() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            applyJobEffects(player);
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
        String name = mat.name();

        // --- ARCHITECTE ---
        if (name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_PLANKS")
                || mat == Material.BAMBOO_BLOCK || mat == Material.STRIPPED_BAMBOO_BLOCK || mat == Material.BAMBOO_PLANKS) {
            return "WOOD";
        }
        if (mat == Material.STONE_BRICKS || mat == Material.MOSSY_STONE_BRICKS
                || mat == Material.CRACKED_STONE_BRICKS || mat == Material.CHISELED_STONE_BRICKS) {
            return "STONE_BRICKS";
        }
        if (mat == Material.SAND || mat == Material.RED_SAND) {
            return "SAND";
        }
        if (name.endsWith("_WOOL")) {
            return "WOOL";
        }
        if (mat == Material.RED_DYE) return "RED_DYE";
        if (mat == Material.BLUE_DYE) return "BLUE_DYE";
        if (mat == Material.YELLOW_DYE) return "YELLOW_DYE";
        if (mat == Material.GREEN_DYE) return "GREEN_DYE";

        if (mat == Material.CHEST || mat == Material.TRAPPED_CHEST || mat == Material.BARREL) {
            return "CHEST";
        }
        if (mat == Material.ENCHANTING_TABLE) return "ENCHANTING_TABLE";
        if (mat == Material.BOOKSHELF || mat == Material.CHISELED_BOOKSHELF) return "BOOKSHELF";
        if (mat == Material.ENDER_CHEST) return "ENDER_CHEST";

        if (mat == Material.GLASS || mat == Material.TINTED_GLASS || name.endsWith("_STAINED_GLASS") || (name.endsWith("_GLASS") && !name.contains("BOTTLE") && !name.contains("SPYGLASS"))) {
            return "GLASS";
        }
        if (mat == Material.LANTERN || mat == Material.SOUL_LANTERN) return "LANTERN";
        if (name.endsWith("_TRAPDOOR")) return "TRAPDOOR";
        if (name.endsWith("_LEAVES")) return "LEAVES";

        if (mat == Material.SEA_LANTERN) return "SEA_LANTERN";
        if (mat == Material.CALCITE) return "CALCITE";
        if (mat == Material.QUARTZ_BLOCK || mat == Material.SMOOTH_QUARTZ || mat == Material.CHISELED_QUARTZ_BLOCK || mat == Material.QUARTZ_PILLAR || mat == Material.QUARTZ_BRICKS) {
            return "QUARTZ_BLOCK";
        }
        if (mat == Material.END_ROD) return "END_ROD";
        if (mat == Material.OCHRE_FROGLIGHT || mat == Material.VERDANT_FROGLIGHT || mat == Material.PEARLESCENT_FROGLIGHT) {
            return "FROGLIGHT";
        }

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

                // --- ARCHITECTE ---
                case "WOOD" -> {
                    String n = type.name();
                    if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_PLANKS")
                            || type == Material.BAMBOO_BLOCK || type == Material.STRIPPED_BAMBOO_BLOCK || type == Material.BAMBOO_PLANKS) {
                        count += item.getAmount();
                    }
                }
                case "STONE_BRICKS" -> {
                    if (type == Material.STONE_BRICKS || type == Material.MOSSY_STONE_BRICKS
                            || type == Material.CRACKED_STONE_BRICKS || type == Material.CHISELED_STONE_BRICKS) {
                        count += item.getAmount();
                    }
                }
                case "SAND" -> {
                    if (type == Material.SAND || type == Material.RED_SAND) count += item.getAmount();
                }
                case "WOOL" -> {
                    if (type.name().endsWith("_WOOL")) count += item.getAmount();
                }
                case "RED_DYE" -> {
                    if (type == Material.RED_DYE) count += item.getAmount();
                }
                case "BLUE_DYE" -> {
                    if (type == Material.BLUE_DYE) count += item.getAmount();
                }
                case "YELLOW_DYE" -> {
                    if (type == Material.YELLOW_DYE) count += item.getAmount();
                }
                case "GREEN_DYE" -> {
                    if (type == Material.GREEN_DYE) count += item.getAmount();
                }
                case "CHEST" -> {
                    if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL) count += item.getAmount();
                }
                case "ENCHANTING_TABLE" -> {
                    if (type == Material.ENCHANTING_TABLE) count += item.getAmount();
                }
                case "BOOKSHELF" -> {
                    if (type == Material.BOOKSHELF || type == Material.CHISELED_BOOKSHELF) count += item.getAmount();
                }
                case "ENDER_CHEST" -> {
                    if (type == Material.ENDER_CHEST) count += item.getAmount();
                }
                case "GLASS" -> {
                    String n = type.name();
                    if (type == Material.GLASS || type == Material.TINTED_GLASS || n.endsWith("_STAINED_GLASS") || (n.endsWith("_GLASS") && !n.contains("BOTTLE") && !n.contains("SPYGLASS"))) {
                        count += item.getAmount();
                    }
                }
                case "LANTERN" -> {
                    if (type == Material.LANTERN || type == Material.SOUL_LANTERN) count += item.getAmount();
                }
                case "TRAPDOOR" -> {
                    if (type.name().endsWith("_TRAPDOOR")) count += item.getAmount();
                }
                case "LEAVES" -> {
                    if (type.name().endsWith("_LEAVES")) count += item.getAmount();
                }
                case "SEA_LANTERN" -> {
                    if (type == Material.SEA_LANTERN) count += item.getAmount();
                }
                case "CALCITE" -> {
                    if (type == Material.CALCITE) count += item.getAmount();
                }
                case "QUARTZ_BLOCK" -> {
                    if (type == Material.QUARTZ_BLOCK || type == Material.SMOOTH_QUARTZ || type == Material.CHISELED_QUARTZ_BLOCK || type == Material.QUARTZ_PILLAR || type == Material.QUARTZ_BRICKS) {
                        count += item.getAmount();
                    }
                }
                case "END_ROD" -> {
                    if (type == Material.END_ROD) count += item.getAmount();
                }
                case "FROGLIGHT" -> {
                    if (type == Material.OCHRE_FROGLIGHT || type == Material.VERDANT_FROGLIGHT || type == Material.PEARLESCENT_FROGLIGHT) {
                        count += item.getAmount();
                    }
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
            } else if (job == PlayerJob.ARCHITECTE) {
                if (missionNumber == 1) {
                    player.sendMessage(Component.text("✦ Récompense Architecte M1 : ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Accès instantané à l'établi via la commande /craft (/wb, /workbench) !", NamedTextColor.YELLOW)));
                } else if (missionNumber == 2) {
                    player.sendMessage(Component.text("✦ Récompenses Architecte M2 : ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Accès au tailleur de pierre (/sc, /stonecutter) + Chapeau de l'Architecte (Vitesse II) !", NamedTextColor.YELLOW)));
                    giveOrDropItem(player, CustomJobItems.getArchitectHelmet());
                } else if (missionNumber == 3) {
                    player.sendMessage(Component.text("✦ Récompense Architecte M3 : ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Chemise de l'Architecte reçue (Célérité II permanent) !", NamedTextColor.YELLOW)));
                    giveOrDropItem(player, CustomJobItems.getArchitectChestplate());
                } else if (missionNumber == 4) {
                    player.sendMessage(Component.text("✦ Récompense Architecte M4 : ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Pantalon de l'Architecte reçu (Vision Nocturne avec /nv) !", NamedTextColor.YELLOW)));
                    giveOrDropItem(player, CustomJobItems.getArchitectLeggings());
                } else if (missionNumber == 5) {
                    player.sendMessage(Component.text("✦ Récompenses Suprêmes Architecte M5 : ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Chaussures de l'Architecte (Saut II avec /jb) + Plume de l'Architecte (Vol Créatif 30s) !", NamedTextColor.YELLOW)));
                    giveOrDropItem(player, CustomJobItems.getArchitectBoots());
                    giveOrDropItem(player, CustomJobItems.getArchitectFeather());
                }
            }

            player.sendMessage(Component.text("★ ========================================= ★", NamedTextColor.GOLD, TextDecoration.BOLD));
            player.sendMessage(Component.empty());
        }

        return completedAny;
    }

    public void giveOrDropItem(Player player, org.bukkit.inventory.ItemStack item) {
        if (player == null || item == null) return;
        Map<Integer, org.bukkit.inventory.ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
            player.sendMessage(Component.text("Votre inventaire était plein, un objet a été déposé à vos pieds !", NamedTextColor.YELLOW));
        }
    }

    public void unloadPlayer(Player player) {
        cache.remove(player.getUniqueId());
    }
}

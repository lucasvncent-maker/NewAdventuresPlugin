package fr.loual.customclasses.jobs.listeners;

import fr.loual.customclasses.CustomClasses;
import fr.loual.customclasses.jobs.CustomJobItems;
import fr.loual.customclasses.jobs.JobManager;
import fr.loual.customclasses.jobs.PlayerJob;
import fr.loual.customclasses.jobs.gui.JobGuiHolder;
import fr.loual.customclasses.jobs.gui.JobSelectionGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Beehive;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class JobListener implements Listener {

    private final CustomClasses plugin;
    private final JobManager jobManager;

    // Cooldown pour éviter le double clic sur les soupes instantanées
    private final Set<UUID> instantEatCooldown = new HashSet<>();

    public JobListener(CustomClasses plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        jobManager.unloadPlayer(event.getPlayer());
        instantEatCooldown.remove(event.getPlayer().getUniqueId());
    }

    // ==========================================================
    // 1. CLIC DANS LE MENU DES MÉTIERS
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof JobGuiHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        String jobId = meta.getPersistentDataContainer().get(JobSelectionGui.JOB_ICON_KEY, PersistentDataType.STRING);
        if (jobId == null) return;

        PlayerJob pj = PlayerJob.fromId(jobId);
        if (pj != PlayerJob.NONE) {
            jobManager.setPlayerJob(player, pj);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
            player.sendMessage(
                    Component.text("✦ Vous avez choisi le métier : ", NamedTextColor.GREEN, TextDecoration.BOLD)
                            .append(Component.text(pj.getDisplayName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                            .append(Component.text(" !", NamedTextColor.GREEN))
            );
            // Réouvrir le menu actualisé
            JobSelectionGui.open(plugin, player);
        }
    }

    // ==========================================================
    // 2. RÉCOLTE DES CULTURES (Passif, Houe Merveilleuse, Missions)
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCropHarvest(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (jobManager.getPlayerJob(player) != PlayerJob.AGRICULTEUR) {
            return;
        }

        Block block = event.getBlock();
        Material type = block.getType();
        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean isWonderfulHoe = CustomJobItems.isJobItem(tool, CustomJobItems.ID_WONDERFUL_HOE);

        // --- HOUE MERVEILLEUSE : Drops surpuissants (or, blocs) ---
        if (isWonderfulHoe) {
            boolean replaced = handleWonderfulHoeDrops(block, type);
            if (replaced) {
                event.setDropItems(false);
                block.getWorld().spawnParticle(Particle.WAX_ON, block.getLocation().clone().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, 0.05);
                try {
                    player.playSound(block.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.8f);
                } catch (Exception ignored) {}
            }
        } else {
            // --- PASSIF DE BASE : +50% de chance d'obtenir +1 drop sur les cultures mûres ---
            if (isMatureCrop(block) || type == Material.MELON || type == Material.PUMPKIN) {
                if (Math.random() <= 0.50) {
                    ItemStack extra = getCropProduct(type);
                    if (extra != null) {
                        block.getWorld().dropItemNaturally(block.getLocation(), extra);
                    }
                }
            }
        }

        // --- PROGRESSION DES MISSIONS DE L'AGRICULTEUR ---
        trackHarvestMission(player, block, type);
    }

    private boolean isMatureCrop(Block block) {
        if (block.getBlockData() instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return false;
    }

    private ItemStack getCropProduct(Material crop) {
        return switch (crop) {
            case WHEAT -> new ItemStack(Material.WHEAT, 1);
            case CARROTS -> new ItemStack(Material.CARROT, 1);
            case POTATOES -> new ItemStack(Material.POTATO, 1);
            case BEETROOTS -> new ItemStack(Material.BEETROOT, 1);
            case MELON -> new ItemStack(Material.MELON_SLICE, 2);
            case PUMPKIN -> new ItemStack(Material.PUMPKIN, 1);
            default -> null;
        };
    }

    private boolean handleWonderfulHoeDrops(Block block, Material type) {
        Location loc = block.getLocation().add(0.5, 0.2, 0.5);
        World world = block.getWorld();

        if (type == Material.CARROTS && isMatureCrop(block)) {
            world.dropItemNaturally(loc, new ItemStack(Material.GOLDEN_CARROT, 2));
            world.dropItemNaturally(loc, new ItemStack(Material.CARROT, 1));
            return true;
        }

        if (type == Material.WHEAT && isMatureCrop(block)) {
            world.dropItemNaturally(loc, new ItemStack(Material.HAY_BLOCK, 1));
            world.dropItemNaturally(loc, new ItemStack(Material.WHEAT_SEEDS, 2));
            return true;
        }

        if (type == Material.MELON) {
            world.dropItemNaturally(loc, new ItemStack(Material.MELON, 1));
            return true;
        }

        if (type == Material.PUMPKIN || type == Material.CARVED_PUMPKIN) {
            world.dropItemNaturally(loc, new ItemStack(Material.PUMPKIN, 2));
            return true;
        }

        if (type == Material.BEETROOTS && isMatureCrop(block)) {
            world.dropItemNaturally(loc, new ItemStack(Material.BEETROOT, 4));
            world.dropItemNaturally(loc, new ItemStack(Material.BEETROOT_SEEDS, 2));
            return true;
        }

        return false;
    }

    private void trackHarvestMission(Player player, Block block, Material type) {
        // Mission 1 : Carottes, Blés, Patates, Pissenlits
        if (type == Material.CARROTS && isMatureCrop(block)) {
            jobManager.addProgress(player, PlayerJob.AGRICULTEUR, "CARROT", 1);
        } else if (type == Material.WHEAT && isMatureCrop(block)) {
            jobManager.addProgress(player, PlayerJob.AGRICULTEUR, "WHEAT", 1);
        } else if (type == Material.POTATOES && isMatureCrop(block)) {
            jobManager.addProgress(player, PlayerJob.AGRICULTEUR, "POTATO", 1);
        } else if (type == Material.DANDELION) {
            jobManager.addProgress(player, PlayerJob.AGRICULTEUR, "DANDELION", 1);
        }

        // Mission 2 : Pastèques, Citrouilles, Betteraves, Coquelicots
        else if (type == Material.MELON) {
            jobManager.addProgress(player, PlayerJob.AGRICULTEUR, "MELON", 1);
        } else if (type == Material.PUMPKIN || type == Material.CARVED_PUMPKIN) {
            jobManager.addProgress(player, PlayerJob.AGRICULTEUR, "PUMPKIN", 1);
        } else if (type == Material.BEETROOTS && isMatureCrop(block)) {
            jobManager.addProgress(player, PlayerJob.AGRICULTEUR, "BEETROOT", 1);
        } else if (type == Material.POPPY) {
            jobManager.addProgress(player, PlayerJob.AGRICULTEUR, "POPPY", 1);
        }

        // Mission 3 : Baies lumineuses
        else if (type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT) {
            jobManager.addProgress(player, PlayerJob.AGRICULTEUR, "GLOW_BERRIES", 1);
        }

        // Mission 4 : Fleur de chorus, Planturne
        else if (type == Material.CHORUS_FLOWER) {
            jobManager.addProgress(player, PlayerJob.AGRICULTEUR, "CHORUS_FLOWER", 1);
        } else if (type == Material.PITCHER_PLANT || type == Material.PITCHER_CROP) {
            jobManager.addProgress(player, PlayerJob.AGRICULTEUR, "PITCHER_PLANT", 1);
        }
    }

    // Récolte interactive (clic droit sur baies ou ruche)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        if (jobManager.getPlayerJob(player) != PlayerJob.AGRICULTEUR) return;

        Material harvested = event.getHarvestedBlock().getType();
        if (harvested == Material.CAVE_VINES || harvested == Material.CAVE_VINES_PLANT) {
            jobManager.addProgress(player, PlayerJob.AGRICULTEUR, "GLOW_BERRIES", 1);
        }
    }

    // Récolte des rayons de miel à la cisaille
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShearBeehive(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        if (jobManager.getPlayerJob(player) != PlayerJob.AGRICULTEUR) return;

        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.SHEARS) {
            if (block.getBlockData() instanceof Beehive beehive && beehive.getHoneyLevel() >= beehive.getMaximumHoneyLevel()) {
                jobManager.addProgress(player, PlayerJob.AGRICULTEUR, "HONEYCOMB", 3);
            }
        }
    }

    // ==========================================================
    // 3. CRAFT DES COOKIES ET GÂTEAUX (Mission 3)
    // ==========================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (jobManager.getPlayerJob(player) != PlayerJob.AGRICULTEUR) return;

        ItemStack result = event.getRecipe().getResult();
        int amount = result.getAmount();

        if (result.getType() == Material.COOKIE) {
            jobManager.addProgress(player, PlayerJob.AGRICULTEUR, "COOKIE", amount);
        } else if (result.getType() == Material.CAKE) {
            jobManager.addProgress(player, PlayerJob.AGRICULTEUR, "CAKE", amount);
        }
    }

    // =========================================================================
    // 4. PLANTATION AUTOMATIQUE EN ZONE (Récompense Mission 2 et 3)
    // =========================================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSeedPlant(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.FARMLAND) return;
        if (event.getBlockFace() != BlockFace.UP) return;

        Player player = event.getPlayer();
        if (jobManager.getPlayerJob(player) != PlayerJob.AGRICULTEUR) return;

        int level = jobManager.getJobLevel(player, PlayerJob.AGRICULTEUR);
        if (level < 2) return; // Débloqué à partir de la mission 2

        ItemStack item = event.getItem();
        if (item == null) return;

        Material seedType = item.getType();
        Material cropType = getCropFromSeed(seedType);
        if (cropType == null) return;

        // Paramètres de zone selon le niveau de mission
        int radius = (level >= 3) ? 10 : 5;
        int targetAge = (level >= 3) ? getAdvancedAge(cropType) : getIntermediateAge(cropType);

        // Exécuter la plantation de zone
        Bukkit.getScheduler().runTask(plugin, () -> {
            plantSurroundingFarmland(player, clicked.getLocation(), seedType, cropType, radius, targetAge);
        });
    }

    private void plantSurroundingFarmland(Player player, Location center, Material seedMat, Material cropMat, int radius, int age) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        int plantedCount = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) continue;

                for (int y = -1; y <= 1; y++) {
                    Block soil = world.getBlockAt(cx + x, cy + y, cz + z);
                    Block above = world.getBlockAt(cx + x, cy + y + 1, cz + z);

                    if (soil.getType() == Material.FARMLAND && above.getType().isAir()) {
                        // Vérifier que le joueur a encore des graines dans son inventaire
                        if (!player.getInventory().containsAtLeast(new ItemStack(seedMat), 1)) {
                            return; // Plus de graines
                        }

                        player.getInventory().removeItem(new ItemStack(seedMat, 1));
                        above.setType(cropMat);

                        if (above.getBlockData() instanceof Ageable ageable) {
                            ageable.setAge(Math.min(age, ageable.getMaximumAge()));
                            above.setBlockData(ageable);
                        }

                        plantedCount++;
                    }
                }
            }
        }

        if (plantedCount > 0) {
            world.spawnParticle(Particle.HAPPY_VILLAGER, center.clone().add(0.5, 1.0, 0.5), 15, radius / 2.0, 0.2, radius / 2.0, 0.1);
            try {
                player.playSound(center, Sound.ITEM_CROP_PLANT, 0.8f, 1.2f);
            } catch (Exception ignored) {}
            player.sendActionBar(Component.text("✦ Plantation en zone : " + plantedCount + " graines posées !", NamedTextColor.GREEN, TextDecoration.BOLD));
        }
    }

    private Material getCropFromSeed(Material seed) {
        return switch (seed) {
            case WHEAT_SEEDS -> Material.WHEAT;
            case CARROT -> Material.CARROTS;
            case POTATO -> Material.POTATOES;
            case BEETROOT_SEEDS -> Material.BEETROOTS;
            case MELON_SEEDS -> Material.MELON_STEM;
            case PUMPKIN_SEEDS -> Material.PUMPKIN_STEM;
            case TORCHFLOWER_SEEDS -> Material.TORCHFLOWER_CROP;
            case PITCHER_POD -> Material.PITCHER_CROP;
            default -> null;
        };
    }

    private int getIntermediateAge(Material crop) {
        return (crop == Material.BEETROOTS) ? 1 : 3;
    }

    private int getAdvancedAge(Material crop) {
        return (crop == Material.BEETROOTS) ? 2 : 5;
    }

    // ==========================================================
    // 5. CONSOMMATION DES ITEMS SPÉCIAUX (Soupes, Space Cookie)
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpecialItemInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        UUID uuid = player.getUniqueId();
        if (instantEatCooldown.contains(uuid)) return;

        // 1. Soupe de l'Agriculteur (instantanée, regen II 10s, stats carotte dorée)
        if (CustomJobItems.isJobItem(item, CustomJobItems.ID_FARMER_SOUP)) {
            event.setCancelled(true);
            consumeFarmerSoup(player, item);
            return;
        }

        // 2. Soupe Merveilleuse (quasi instantanée, +1 cœur, saturation max)
        if (CustomJobItems.isJobItem(item, CustomJobItems.ID_WONDERFUL_SOUP)) {
            event.setCancelled(true);
            consumeWonderfulSoup(player, item);
            return;
        }
    }

    private void consumeFarmerSoup(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        instantEatCooldown.add(uuid);

        // Retirer 1 soupe et donner un bol vide
        item.subtract(1);
        player.getInventory().addItem(new ItemStack(Material.BOWL));

        // Stats équivalentes carotte dorée (6 nourriture, 14.4 saturation)
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + 6));
        player.setSaturation(Math.min(20.0f, player.getSaturation() + 14.4f));

        // Régénération II pendant 10s (200 ticks, amplificateur 1)
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1, false, true, true));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.8f, 1.2f);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().clone().add(0, 1.5, 0), 5, 0.3, 0.3, 0.3, 0.05);
        player.sendActionBar(Component.text("✦ Soupe de l'Agriculteur consommée !", NamedTextColor.GOLD, TextDecoration.BOLD));

        Bukkit.getScheduler().runTaskLater(plugin, () -> instantEatCooldown.remove(uuid), 10L);
    }

    private void consumeWonderfulSoup(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        instantEatCooldown.add(uuid);

        item.subtract(1);
        player.getInventory().addItem(new ItemStack(Material.BOWL));
        player.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE));

        // Rend 1 cœur (2.0 HP)
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 2.0));

        // Saturation maximale
        player.setFoodLevel(20);
        player.setSaturation(20.0f);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.8f, 1.4f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().clone().add(0, 1.2, 0), 15, 0.3, 0.4, 0.3, 0.1);
        player.sendActionBar(Component.text("✦ Soupe Merveilleuse consommée : +1 Cœur & Saturation Max !", NamedTextColor.AQUA, TextDecoration.BOLD));

        Bukkit.getScheduler().runTaskLater(plugin, () -> instantEatCooldown.remove(uuid), 10L);
    }

    // Space Cookie (consommation via événement standard ou clic)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsumeSpaceCookie(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (CustomJobItems.isJobItem(item, CustomJobItems.ID_SPACE_COOKIE)) {
            Player player = event.getPlayer();

            // Force III (30s = 600t, amplificateur 2)
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 2, false, true, true));
            // Vitesse II (30s = 600t, amplificateur 1)
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1, false, true, true));
            // Saturation maximale
            player.setSaturation(20.0f);
            player.setFoodLevel(20);

            player.sendActionBar(Component.text("✦ Space Cookie absorbé : Force III & Vitesse II (30s) !", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.4f, 1.8f);

            // Effet secondaire : Nausée pendant 10s après 30 secondes
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0, false, true, true));
                    player.sendActionBar(Component.text("🌀 Le contre-coup du Space Cookie frappe votre esprit...", NamedTextColor.RED, TextDecoration.BOLD));
                    player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.4f);
                }
            }, 600L);
        }
    }

    // ==========================================================
    // 6. RESTRICTIONS DE CRAFT DES RECETTES DE MÉTIER
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || !result.hasItemMeta()) return;

        String id = result.getItemMeta().getPersistentDataContainer().get(CustomJobItems.ITEM_KEY, PersistentDataType.STRING);
        if (id == null) return;

        if (event.getView().getPlayer() instanceof Player player) {
            PlayerJob pj = jobManager.getPlayerJob(player);
            int level = jobManager.getJobLevel(player, PlayerJob.AGRICULTEUR);

            if (pj != PlayerJob.AGRICULTEUR) {
                event.getInventory().setResult(null);
                return;
            }

            boolean allowed = switch (id) {
                case CustomJobItems.ID_FARMER_SOUP -> level >= 1;
                case CustomJobItems.ID_SPACE_COOKIE -> level >= 3;
                case CustomJobItems.ID_WONDERFUL_SOUP, CustomJobItems.ID_WONDERFUL_HOE -> level >= 4;
                default -> true;
            };

            if (!allowed) {
                event.getInventory().setResult(null);
            }
        }
    }
}

package fr.loual.customclasses.jobs.listeners;

import fr.loual.newadventure.NewAdventurePlugin;
import fr.loual.customclasses.jobs.CustomJobItems;
import fr.loual.customclasses.jobs.JobManager;
import fr.loual.customclasses.jobs.JobRecipes;
import fr.loual.customclasses.jobs.PlayerJob;
import fr.loual.customclasses.jobs.gui.JobGuiHolder;
import fr.loual.customclasses.jobs.gui.JobRecipeGui;
import fr.loual.customclasses.jobs.gui.JobRecipeGuiHolder;
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
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.entity.EnderPearl;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.block.Container;
import org.bukkit.loot.Lootable;
import fr.loual.customminerals.items.Cuprite;
import org.bukkit.generator.structure.Structure;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.entity.Item;
import org.bukkit.entity.FishHook;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.meta.CompassMeta;
import fr.loual.customclasses.jobs.MineurPouchManager;
import org.bukkit.util.StructureSearchResult;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class JobListener implements Listener {

    private final NewAdventurePlugin plugin;
    private final JobManager jobManager;

    // Cooldown pour éviter le double clic sur les soupes instantanées
    private final Set<UUID> instantEatCooldown = new HashSet<>();

    // Vol de l'Architecte (Plume)
    private final Map<UUID, Long> featherCooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> activeFlightTasks = new HashMap<>();
    private final Set<UUID> fallImmunity = new HashSet<>();

    // Aventurier
    private final Map<UUID, Long> discoveryCompassCooldowns = new HashMap<>();
    private final Map<UUID, Long> pearlCooldowns = new HashMap<>();
    private final Map<UUID, Long> grapplingCooldowns = new HashMap<>();
    private final NamespacedKey chestBoostKey;
    private final NamespacedKey noFallPearlKey;

    public JobListener(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
        this.chestBoostKey = new NamespacedKey(plugin, "aventurier_boosted_chest");
        this.noFallPearlKey = new NamespacedKey(plugin, "no_fall_pearl");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        JobRecipes.syncDiscoveredRecipes(plugin, player);
        jobManager.applyJobEffects(player);

        // Envoi automatique du pack de ressources si activé dans config.yml
        if (plugin.getConfig().getBoolean("resource-pack.auto-send-on-join", false)) {
            String url = plugin.getConfig().getString("resource-pack.url", "");
            if (url != null && !url.isBlank()) {
                String sha1 = plugin.getConfig().getString("resource-pack.sha1", "");
                boolean required = plugin.getConfig().getBoolean("resource-pack.required", false);
                Component prompt = Component.text("Veuillez accepter le pack de ressources pour profiter des textures et items personnalisés !", NamedTextColor.GOLD);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    try {
                        if (sha1 != null && !sha1.isBlank()) {
                            player.setResourcePack(url, sha1, required, prompt);
                        } else {
                            player.setResourcePack(url);
                        }
                    } catch (Exception ignored) {
                        try {
                            player.setResourcePack(url);
                        } catch (Exception ignored2) {}
                    }
                }, 20L);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        jobManager.unloadPlayer(player);
        instantEatCooldown.remove(player.getUniqueId());

        BukkitTask ft = activeFlightTasks.remove(player.getUniqueId());
        if (ft != null) {
            ft.cancel();
        }
        fallImmunity.remove(player.getUniqueId());
        pearlCooldowns.remove(player.getUniqueId());
    }

    // ==========================================================
    // 1. CLIC DANS LE MENU DES MÉTIERS & RECETTES
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p && activeFlightTasks.containsKey(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && activeFlightTasks.containsKey(p.getUniqueId())) {
                    if (!CustomJobItems.hasFullArchitectSet(p)) {
                        BukkitTask t = activeFlightTasks.remove(p.getUniqueId());
                        if (t != null) t.cancel();
                        endFlight(p, true);
                    }
                }
            });
        }
        // --- 1.A. VISUALISEUR DE RECETTES (JobRecipeGuiHolder) ---
        if (event.getInventory().getHolder() instanceof JobRecipeGuiHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;

            int slot = event.getRawSlot();
            switch (slot) {
                case 1 -> {
                    JobRecipeGui.open(plugin, player, JobRecipeGui.RECIPE_FARMER_SOUP);
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
                }
                case 3 -> {
                    JobRecipeGui.open(plugin, player, JobRecipeGui.RECIPE_SPACE_COOKIE);
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
                }
                case 5 -> {
                    JobRecipeGui.open(plugin, player, JobRecipeGui.RECIPE_WONDERFUL_SOUP);
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
                }
                case 7 -> {
                    JobRecipeGui.open(plugin, player, JobRecipeGui.RECIPE_WONDERFUL_HOE);
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
                }
                case 40 -> {
                    JobSelectionGui.open(plugin, player);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
                }
            }
            return;
        }

        // --- 1.B. MENU PRINCIPAL DES MÉTIERS (JobGuiHolder) ---
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

        // Clic sur l'icône de métier
        String jobId = meta.getPersistentDataContainer().get(JobSelectionGui.JOB_ICON_KEY, PersistentDataType.STRING);
        if (jobId != null) {
            PlayerJob pj = PlayerJob.fromId(jobId);
            if (pj != PlayerJob.NONE) {
                jobManager.setPlayerJob(player, pj);
                jobManager.checkCurrentMissionCompletion(player, pj);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                player.sendMessage(
                        Component.text("✦ Vous avez choisi le métier : ", NamedTextColor.GREEN, TextDecoration.BOLD)
                                .append(Component.text(pj.getDisplayName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                                .append(Component.text(" !", NamedTextColor.GREEN))
                );
                // Réouvrir le menu actualisé
                JobSelectionGui.open(plugin, player);
            }
            return;
        }

        // Clic sur le Livre de Recettes (Slot 31)
        if (meta.getPersistentDataContainer().has(JobSelectionGui.RECIPE_BOOK_KEY, PersistentDataType.BYTE)) {
            JobRecipeGui.open(plugin, player, JobRecipeGui.RECIPE_FARMER_SOUP);
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            return;
        }

        // Clic sur le bouton de Récupération Globale (Slot 33)
        if (meta.getPersistentDataContainer().has(JobSelectionGui.RECLAIM_ALL_KEY, PersistentDataType.BYTE)) {
            int total = jobManager.reclaimAllUnlockedItems(player);
            if (total > 0) {
                player.sendMessage(Component.text("✦ [Métier] Vous avez récupéré " + total + " objet(s) exclusif(s) perdu(s) !", NamedTextColor.GREEN, TextDecoration.BOLD));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
            } else {
                player.sendMessage(Component.text("✦ Vous possédez déjà tous vos objets de métier débloqués (ou aucun objet à récupérer) !", NamedTextColor.YELLOW));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            }
            return;
        }

        // Clic sur une mission pour afficher directement ses détails ou récupérer ses items
        Integer missionNum = meta.getPersistentDataContainer().get(JobSelectionGui.MISSION_ITEM_KEY, PersistentDataType.INTEGER);
        if (missionNum != null) {
            PlayerJob pj = jobManager.getPlayerJob(player);
            int currentLevel = jobManager.getJobLevel(player, pj);
            if (currentLevel >= missionNum) {
                int res = jobManager.reclaimMissionItems(player, pj, missionNum);
                if (res > 0) {
                    player.sendMessage(Component.text("✦ [Métier] Vous avez récupéré " + res + " objet(s) exclusif(s) de cette mission !", NamedTextColor.GREEN, TextDecoration.BOLD));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                    return;
                } else if (res == 0) {
                    player.sendMessage(Component.text("✦ Vous possédez déjà tous les objets exclusifs de cette mission dans votre inventaire / armure / enderchest !", NamedTextColor.YELLOW));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    return;
                }
            }

            if (pj == PlayerJob.AGRICULTEUR) {
                switch (missionNum) {
                    case 1 -> {
                        JobRecipeGui.open(plugin, player, JobRecipeGui.RECIPE_FARMER_SOUP);
                        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    }
                    case 2 -> {
                        player.sendMessage(Component.text("✦ La Mission 2 débloque un passif de plantation en zone (aucun craft d'item).", NamedTextColor.YELLOW));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    }
                    case 3 -> {
                        JobRecipeGui.open(plugin, player, JobRecipeGui.RECIPE_SPACE_COOKIE);
                        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    }
                    case 4 -> {
                        JobRecipeGui.open(plugin, player, JobRecipeGui.RECIPE_WONDERFUL_SOUP);
                        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    }
                }
            } else if (pj == PlayerJob.MINEUR) {
                switch (missionNum) {
                    case 1 -> {
                        player.sendMessage(Component.text("✦ Mission 1 Mineur : Récoltez 64 charbons, 64 fers et 64 d'or pour débloquer Célérité I et /nv !", NamedTextColor.YELLOW));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                    }
                    case 2 -> {
                        player.sendMessage(Component.text("✦ Mission 2 Mineur : Récoltez 64 diamants, 64 émeraudes et 16 cuprites pour débloquer 5% de chance de Cuprite et Fortune +1 !", NamedTextColor.YELLOW));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                    }
                    case 3 -> {
                        player.sendMessage(Component.text("✦ Mission 3 Mineur : Récoltez 64 améthystes, 64 capteurs sculk et 3 spawners pour débloquer Célérité II et la bénédiction sous Y=30 !", NamedTextColor.YELLOW));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                    }
                }
            } else if (pj == PlayerJob.ARCHITECTE) {
                switch (missionNum) {
                    case 1 -> {
                        player.sendMessage(Component.text("✦ Mission 1 Architecte : 64 Bois, 64 Stonebricks, 64 Sable pour débloquer /craft (/wb) !", NamedTextColor.YELLOW));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                    }
                    case 2 -> {
                        player.sendMessage(Component.text("✦ Mission 2 Architecte : 64 Laines, 8 Colorants (Rouge, Bleu, Jaune, Vert) pour débloquer /sc et le Chapeau de l'Architecte !", NamedTextColor.YELLOW));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                    }
                    case 3 -> {
                        player.sendMessage(Component.text("✦ Mission 3 Architecte : 12 Coffres, 1 Table d'enchantement, 15 Bibliothèques, 1 Ender Chest pour la Chemise de l'Architecte !", NamedTextColor.YELLOW));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                    }
                    case 4 -> {
                        player.sendMessage(Component.text("✦ Mission 4 Architecte : 64 Verre, 64 Lanternes, 64 Trappes, 64 Feuilles pour le Pantalon de l'Architecte !", NamedTextColor.YELLOW));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                    }
                    case 5 -> {
                        player.sendMessage(Component.text("✦ Mission 5 Architecte : 64 Sea Lantern, 64 Calcite, 64 Blocs Quartz, 64 End Rod, 64 Grélampe pour les Chaussures et la Plume de l'Architecte !", NamedTextColor.YELLOW));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                    }
                }
            } else if (pj == PlayerJob.AVENTURIER) {
                switch (missionNum) {
                    case 1 -> {
                        player.sendMessage(Component.text("✦ Mission 1 Aventurier : Explorez 5 structures différentes de l'Overworld pour débloquer de meilleurs coffres et /sethome !", NamedTextColor.YELLOW));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                    }
                    case 2 -> {
                        player.sendMessage(Component.text("✦ Mission 2 Aventurier : Explorez les 5 biomes du Nether pour débloquer la Perle Infinie sans dégât de chute !", NamedTextColor.YELLOW));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                    }
                    case 3 -> {
                        player.sendMessage(Component.text("✦ Mission 3 Aventurier : Obtenez 3 pommes cheat, 3 élytres et 8 éponges pour débloquer les Élytres Incassables et le Feu d'artifice infini !", NamedTextColor.YELLOW));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                    }
                }
            }
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

    // Récolte interactive (clic droit sur baies lumineuses)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        if (jobManager.getPlayerJob(player) != PlayerJob.AGRICULTEUR) return;

        Material harvested = event.getHarvestedBlock().getType();
        if (harvested == Material.CAVE_VINES || harvested == Material.CAVE_VINES_PLANT) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                jobManager.checkAndNotifyProgress(player, PlayerJob.AGRICULTEUR, "GLOW_BERRIES");
            });
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
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    jobManager.checkAndNotifyProgress(player, PlayerJob.AGRICULTEUR, "HONEYCOMB");
                });
            }
        }
    }

    // ==========================================================
    // 2bis. MINAGE DES MINERAIS (Passif du Mineur, Fortune, Cuprite)
    // ==========================================================
    private static final Set<Material> MINER_ORES = Set.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS
    );

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOreMine(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (jobManager.getPlayerJob(player) != PlayerJob.MINEUR) return;

        Block block = event.getBlock();
        Material type = block.getType();
        if (!MINER_ORES.contains(type)) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        int level = jobManager.getJobLevel(player, PlayerJob.MINEUR);
        boolean hasSilkTouch = tool.containsEnchantment(Enchantment.SILK_TOUCH);

        // 1. Passif Fortune supplémentaire (si pas Toucher de Soie)
        if (!hasSilkTouch) {
            ItemStack extraDrop = getOreProduct(type);
            if (extraDrop != null) {
                int extraCount = 0;
                // Base passive : 25% de chance de drop supplémentaire
                if (Math.random() <= 0.25) {
                    extraCount++;
                }
                // Récompense Mission 2 : 1 niveau de Fortune supplémentaire garanti (+1)
                if (level >= 2) {
                    extraCount++;
                }
                // Récompense Mission 4 : 1 niveau de Fortune supplémentaire ultime (+2 au total)
                if (level >= 4) {
                    extraCount++;
                }

                if (extraCount > 0) {
                    ItemStack dropStack = extraDrop.clone();
                    dropStack.setAmount(extraCount);
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), dropStack);
                    block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, 0.05);
                    try {
                        player.playSound(block.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.8f);
                    } catch (Exception ignored) {}
                }
            }
        }

        // 2. Récompense Mission 3 : 5% de chance de drop de la Cuprite sur les minerais (8% à la Mission 4)
        if (level >= 3) {
            double chance = (level >= 4) ? 0.08 : 0.05;
            if (Math.random() <= chance) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), Cuprite.create(plugin, 1));
                block.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, block.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0.1);
                player.playSound(block.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9f, 1.5f);
                player.sendActionBar(Component.text("✦ [Mineur M" + level + "] Cuprite découverte !", NamedTextColor.GOLD, TextDecoration.BOLD));
            }
        }
    }

    private ItemStack getOreProduct(Material ore) {
        return switch (ore) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> new ItemStack(Material.COAL, 1);
            case IRON_ORE, DEEPSLATE_IRON_ORE -> new ItemStack(Material.RAW_IRON, 1);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> new ItemStack(Material.RAW_COPPER, 1);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> new ItemStack(Material.RAW_GOLD, 1);
            case NETHER_GOLD_ORE -> new ItemStack(Material.GOLD_NUGGET, 4);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> new ItemStack(Material.REDSTONE, 2);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> new ItemStack(Material.LAPIS_LAZULI, 4);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> new ItemStack(Material.DIAMOND, 1);
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> new ItemStack(Material.EMERALD, 1);
            case NETHER_QUARTZ_ORE -> new ItemStack(Material.QUARTZ, 1);
            case ANCIENT_DEBRIS -> new ItemStack(Material.ANCIENT_DEBRIS, 1);
            default -> null;
        };
    }

    // ==========================================================
    // 3. GESTION DES SOUPES CUSTOM : STACKING JUSQU'À 64
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCustomSoupPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        org.bukkit.entity.Item itemEntity = event.getItem();
        ItemStack groundStack = itemEntity.getItemStack();
        String itemId = CustomJobItems.getJobItemId(groundStack);
        if (itemId == null) return;
        if (!itemId.equals(CustomJobItems.ID_FARMER_SOUP) && !itemId.equals(CustomJobItems.ID_WONDERFUL_SOUP)) {
            return;
        }

        // Vanilla sépare les soupes par défaut car BEETROOT_SOUP / RABBIT_STEW a maxStack = 1
        // On fusionne manuellement avec les stacks existants du joueur
        int toAdd = groundStack.getAmount();
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();

        // 1. Chercher les slots contenant déjà cette même soupe avec quantité < 64
        for (int i = 0; i < 36; i++) {
            ItemStack slotItem = inv.getItem(i);
            if (slotItem != null && CustomJobItems.isJobItem(slotItem, itemId)) {
                int currentAmount = slotItem.getAmount();
                if (currentAmount < 64) {
                    int space = 64 - currentAmount;
                    int transfer = Math.min(space, toAdd);
                    slotItem.setAmount(currentAmount + transfer);
                    toAdd -= transfer;
                    if (toAdd <= 0) break;
                }
            }
        }

        // 2. S'il reste des soupes, trouver le premier slot vide
        if (toAdd > 0) {
            int emptySlot = inv.firstEmpty();
            if (emptySlot != -1 && emptySlot < 36) {
                ItemStack newStack = groundStack.clone();
                newStack.setAmount(Math.min(64, toAdd));
                inv.setItem(emptySlot, newStack);
                toAdd -= newStack.getAmount();
            }
        }

        int pickedUp = groundStack.getAmount() - toAdd;
        if (pickedUp > 0) {
            event.setCancelled(true);

            try {
                player.playPickupItemAnimation(itemEntity, pickedUp);
            } catch (Exception ignored) {}
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.8f);

            if (toAdd <= 0) {
                itemEntity.remove();
            } else {
                groundStack.setAmount(toAdd);
                itemEntity.setItemStack(groundStack);
            }
        }
    }

    // Fusion au sol entre soupes custom proches
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCustomSoupSpawn(ItemSpawnEvent event) {
        org.bukkit.entity.Item spawned = event.getEntity();
        ItemStack stack = spawned.getItemStack();
        String id = CustomJobItems.getJobItemId(stack);
        if (id == null) return;
        if (!id.equals(CustomJobItems.ID_FARMER_SOUP) && !id.equals(CustomJobItems.ID_WONDERFUL_SOUP)) return;

        for (org.bukkit.entity.Entity nearby : spawned.getNearbyEntities(2.0, 2.0, 2.0)) {
            if (nearby instanceof org.bukkit.entity.Item other && !other.isDead() && !other.equals(spawned)) {
                ItemStack otherStack = other.getItemStack();
                if (CustomJobItems.isJobItem(otherStack, id)) {
                    int total = stack.getAmount() + otherStack.getAmount();
                    if (total <= 64) {
                        stack.setAmount(total);
                        spawned.setItemStack(stack);
                        other.remove();
                    }
                }
            }
        }
    }

    // Empilage manuel dans l'inventaire au clic
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCustomSoupInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        String currentId = CustomJobItems.getJobItemId(current);
        String cursorId = CustomJobItems.getJobItemId(cursor);

        if (currentId != null && currentId.equals(cursorId)
                && (currentId.equals(CustomJobItems.ID_FARMER_SOUP) || currentId.equals(CustomJobItems.ID_WONDERFUL_SOUP))) {

            int currentAmount = current.getAmount();
            int cursorAmount = cursor.getAmount();

            if (event.isLeftClick()) {
                if (currentAmount < 64) {
                    event.setCancelled(true);
                    int space = 64 - currentAmount;
                    int transfer = Math.min(space, cursorAmount);

                    current.setAmount(currentAmount + transfer);
                    cursor.setAmount(cursorAmount - transfer);

                    event.setCurrentItem(current);
                    player.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
                }
            } else if (event.isRightClick()) {
                if (currentAmount < 64 && cursorAmount > 0) {
                    event.setCancelled(true);
                    current.setAmount(currentAmount + 1);
                    cursor.setAmount(cursorAmount - 1);

                    event.setCurrentItem(current);
                    player.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
                }
            }
        }
    }

    // Regroupe les soupes éparpillées en stacks complets de 64
    public void consolidateSoupStacks(Player player) {
        consolidateType(player, CustomJobItems.ID_FARMER_SOUP);
        consolidateType(player, CustomJobItems.ID_WONDERFUL_SOUP);
    }

    private void consolidateType(Player player, String itemId) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        int total = 0;
        java.util.List<Integer> slots = new java.util.ArrayList<>();

        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && CustomJobItems.isJobItem(item, itemId)) {
                total += item.getAmount();
                slots.add(i);
            }
        }

        if (slots.size() <= 1) return;

        for (int slot : slots) {
            inv.setItem(slot, null);
        }

        ItemStack template = CustomJobItems.getItemById(itemId);
        if (template == null) return;

        for (int slot : slots) {
            if (total <= 0) break;
            int count = Math.min(64, total);
            ItemStack stack = template.clone();
            stack.setAmount(count);
            inv.setItem(slot, stack);
            total -= count;
        }
    }

    // ==========================================================
    // 3bis. PROGRESSION DES MISSIONS
    // ==========================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        PlayerJob job = jobManager.getPlayerJob(player);
        if (job == PlayerJob.NONE) return;

        Material mat = event.getItem().getItemStack().getType();
        String reqKey = jobManager.getRequirementKeyForMaterial(mat);
        if (reqKey == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            jobManager.checkAndNotifyProgress(player, job, reqKey);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Si craft de soupes en shift-click, regrouper les stacks
        ItemStack result = event.getRecipe().getResult();
        if (CustomJobItems.isJobItem(result, CustomJobItems.ID_FARMER_SOUP) || CustomJobItems.isJobItem(result, CustomJobItems.ID_WONDERFUL_SOUP)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                consolidateSoupStacks(player);
            });
        }

        PlayerJob job = jobManager.getPlayerJob(player);
        if (job == PlayerJob.NONE) return;

        String reqKey = jobManager.getRequirementKeyForMaterial(result.getType());
        if (reqKey == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            jobManager.checkAndNotifyProgress(player, job, reqKey);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        consolidateSoupStacks(player);

        PlayerJob job = jobManager.getPlayerJob(player);
        if (job != PlayerJob.NONE) {
            jobManager.checkCurrentMissionCompletion(player, job);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                jobManager.applyJobEffects(event.getPlayer());
            }
        });
    }

    // =========================================================================
    // 3.5 AGRICULTEUR : PROTECTION DU PIÉTINEMENT DES CULTURES (FARMLAND TRAMPLE)
    // =========================================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFarmlandPhysicalInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.FARMLAND) return;

        Player player = event.getPlayer();
        if (jobManager.getPlayerJob(player) == PlayerJob.AGRICULTEUR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFarmlandTrample(EntityChangeBlockEvent event) {
        if (event.getBlock().getType() != Material.FARMLAND) return;
        if (event.getTo() != Material.DIRT) return;

        if (event.getEntity() instanceof Player player) {
            if (jobManager.getPlayerJob(player) == PlayerJob.AGRICULTEUR) {
                event.setCancelled(true);
            }
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
        int radius = (level >= 3) ? 6 : 3;
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

        // 3. Space Cookie (mangeable instantanément même sans avoir faim / à satiété)
        if (CustomJobItems.isJobItem(item, CustomJobItems.ID_SPACE_COOKIE)) {
            event.setCancelled(true);
            consumeSpaceCookie(player, item);
            return;
        }

        // 4. Plume de l'Architecte (Vol Créatif pendant 30s)
        if (CustomJobItems.isJobItem(item, CustomJobItems.ID_ARCHITECT_FEATHER)) {
            event.setCancelled(true);
            handleArchitectFeather(player);
            return;
        }

        // 5. Perle Infinie de l'Aventurier (quantité infinie, aucun dégât de chute)
        if (CustomJobItems.isJobItem(item, CustomJobItems.ID_AVENTURIER_INFINITE_PEARL)) {
            event.setCancelled(true);
            handleAventurierPearl(player, item);
            return;
        }

        // 6. Fusée Infinie de l'Aventurier (propulsion infinie sans s'épuiser)
        if (CustomJobItems.isJobItem(item, CustomJobItems.ID_AVENTURIER_INFINITE_FIREWORK)) {
            event.setCancelled(true);
            handleAventurierFirework(player);
            ItemStack fwBackup = item.clone();
            fwBackup.setAmount(1);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    if (!player.getInventory().containsAtLeast(fwBackup, 1)) {
                        player.getInventory().addItem(fwBackup);
                    }
                    player.updateInventory();
                }
            });
            return;
        }

        // 7. Boussole Antique de Découverte (Aventurier M2)
        if (CustomJobItems.isJobItem(item, CustomJobItems.ID_AVENTURIER_DISCOVERY_COMPASS)) {
            event.setCancelled(true);
            handleAventurierDiscoveryCompass(player, item);
            return;
        }

        // 8. Sacoche de Minage du Mineur (Mineur M2)
        if (CustomJobItems.isJobItem(item, CustomJobItems.ID_MINEUR_ORE_POUCH)) {
            event.setCancelled(true);
            if (jobManager.getPlayerJob(player) != PlayerJob.MINEUR || jobManager.getJobLevel(player, PlayerJob.MINEUR) < 2) {
                player.sendMessage(Component.text("[Mineur] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("Vous devez être Mineur de niveau 2 minimum pour utiliser la Sacoche de Minage !", NamedTextColor.RED)));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            MineurPouchManager.openPouch(player);
            return;
        }
    }

    private void consumeFarmerSoup(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        instantEatCooldown.add(uuid);

        // Retirer 1 soupe et donner un bol vide
        item.subtract(1);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(Material.BOWL));
        if (!leftover.isEmpty()) {
            leftover.values().forEach(b -> player.getWorld().dropItemNaturally(player.getLocation(), b));
        }

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
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(Material.BOWL), new ItemStack(Material.GLASS_BOTTLE));
        if (!leftover.isEmpty()) {
            leftover.values().forEach(b -> player.getWorld().dropItemNaturally(player.getLocation(), b));
        }

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

    private void consumeSpaceCookie(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        instantEatCooldown.add(uuid);

        // Retirer 1 cookie du stack
        item.subtract(1);

        // Force III (30s = 600t, amplificateur 2)
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 2, false, true, true));
        // Vitesse II (30s = 600t, amplificateur 1)
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1, false, true, true));
        // Saturation maximale
        player.setSaturation(20.0f);
        player.setFoodLevel(20);

        player.sendActionBar(Component.text("✦ Space Cookie absorbé : Force III & Vitesse II (30s) !", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.8f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.4f, 1.8f);
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().clone().add(0, 1.0, 0), 25, 0.4, 0.5, 0.4, 0.1);

        // Effet secondaire : Nausée pendant 10s après 30 secondes
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0, false, true, true));
                player.sendActionBar(Component.text("🌀 Le contre-coup du Space Cookie frappe votre esprit...", NamedTextColor.RED, TextDecoration.BOLD));
                player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.4f);
            }
        }, 600L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> instantEatCooldown.remove(uuid), 10L);
    }

    // Fallback au cas où le joueur le consomme via l'animation vanilla
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsumeSpaceCookie(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (CustomJobItems.isJobItem(item, CustomJobItems.ID_SPACE_COOKIE)) {
            consumeSpaceCookie(event.getPlayer(), item);
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

    // ==========================================================
    // 7. VOL DE L'ARCHITECTE (PLUME) & IMMUNITÉ DE CHUTE
    // ==========================================================
    private void handleArchitectFeather(Player player) {
        UUID uuid = player.getUniqueId();

        // 1. Vérifier l'armure complète de 4 pièces
        if (!CustomJobItems.hasFullArchitectSet(player)) {
            player.sendMessage(
                    Component.text("[Architecte] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Vous devez équiper la tenue complète de l'Architecte (4 pièces) pour utiliser la Plume !", NamedTextColor.RED))
            );
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        // 2. Vérifier si un vol est déjà en cours
        if (activeFlightTasks.containsKey(uuid)) {
            player.sendMessage(
                    Component.text("[Architecte] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Votre vol est déjà actif !", NamedTextColor.YELLOW))
            );
            return;
        }

        // 3. Vérifier le cooldown de 5 minutes (300 secondes)
        long now = System.currentTimeMillis();
        Long expireTime = featherCooldowns.get(uuid);
        if (expireTime != null && now < expireTime) {
            long remainingSeconds = (expireTime - now + 999) / 1000;
            long mins = remainingSeconds / 60;
            long secs = remainingSeconds % 60;
            player.sendMessage(
                    Component.text("[Architecte] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("La Plume est en recharge : " + mins + "m " + secs + "s restantes.", NamedTextColor.RED))
            );
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        // 4. Activer le vol
        featherCooldowns.put(uuid, now + 300_000L); // 5 minutes

        player.setAllowFlight(true);
        player.setFlying(true);

        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, 1.0f, 1.2f);
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().clone().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.1);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 25, 0.5, 0.2, 0.5, 0.1);

        player.sendMessage(
                Component.text("✦ Envol de l'Architecte déclenché ! Vol créatif accordé pendant 30 secondes.", NamedTextColor.GREEN, TextDecoration.BOLD)
        );

        // 5. Tâche de décompte (30 secondes)
        BukkitTask task = new BukkitRunnable() {
            int secondsRemaining = 30;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    activeFlightTasks.remove(uuid);
                    return;
                }

                // Si une pièce de l'armure est retirée, arrêt immédiat du vol
                if (!CustomJobItems.hasFullArchitectSet(player)) {
                    cancel();
                    activeFlightTasks.remove(uuid);
                    endFlight(player, true);
                    return;
                }

                secondsRemaining--;

                if (secondsRemaining <= 0) {
                    cancel();
                    activeFlightTasks.remove(uuid);
                    endFlight(player, false);
                    return;
                }

                NamedTextColor color = (secondsRemaining <= 5) ? NamedTextColor.RED : NamedTextColor.AQUA;
                player.sendActionBar(Component.text("✦ Vol de l'Architecte : " + secondsRemaining + "s restantes ✦", color, TextDecoration.BOLD));
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 3, 0.2, 0.05, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, 20L, 20L);

        activeFlightTasks.put(uuid, task);
    }

    private void endFlight(Player player, boolean cancelledEarly) {
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }

        UUID uuid = player.getUniqueId();
        // Protection anti-chute pendant 5 secondes
        fallImmunity.add(uuid);
        Bukkit.getScheduler().runTaskLater(plugin, () -> fallImmunity.remove(uuid), 100L);

        if (cancelledEarly) {
            player.sendMessage(
                    Component.text("✖ Vous avez retiré une pièce d'armure ! Le vol a été interrompu (Protection chute 5s active).", NamedTextColor.RED, TextDecoration.BOLD)
            );
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 0.8f);
        } else {
            player.sendMessage(
                    Component.text("✦ Le vol de l'Architecte a pris fin ! (Protection anti-chute 5s accordée).", NamedTextColor.YELLOW, TextDecoration.BOLD)
            );
            player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.0f);
        }
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().clone().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.05);
    }

    private void handleAventurierPearl(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = pearlCooldowns.get(uuid);
        if (last != null && now - last < 1500L) {
            player.updateInventory();
            return;
        }
        pearlCooldowns.put(uuid, now);

        EnderPearl pearl = player.launchProjectile(EnderPearl.class);
        pearl.getPersistentDataContainer().set(noFallPearlKey, PersistentDataType.BYTE, (byte) 1);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 1.0f, 1.2f);
        player.setCooldown(Material.ENDER_PEARL, 30);

        // Garantir que la perle infinie ne disparaisse jamais visuellement ni dans l'inventaire
        ItemStack pearlBackup = item != null ? item.clone() : CustomJobItems.getAventurierInfinitePearl();
        pearlBackup.setAmount(1);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                if (!player.getInventory().containsAtLeast(pearlBackup, 1)) {
                    player.getInventory().addItem(pearlBackup);
                }
                player.updateInventory();
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerLaunchProjectile(PlayerLaunchProjectileEvent event) {
        ItemStack item = event.getItemStack();
        if (CustomJobItems.isJobItem(item, CustomJobItems.ID_AVENTURIER_INFINITE_PEARL)) {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis();
            Long last = pearlCooldowns.get(uuid);
            if (last != null && now - last < 1500L) {
                event.setCancelled(true);
                player.updateInventory();
                return;
            }
            pearlCooldowns.put(uuid, now);
            event.setShouldConsume(false);
            if (event.getProjectile() instanceof EnderPearl pearl) {
                pearl.getPersistentDataContainer().set(noFallPearlKey, PersistentDataType.BYTE, (byte) 1);
            }
            player.setCooldown(Material.ENDER_PEARL, 30);

            ItemStack pearlBackup = item.clone();
            pearlBackup.setAmount(1);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    if (!player.getInventory().containsAtLeast(pearlBackup, 1)) {
                        player.getInventory().addItem(pearlBackup);
                    }
                    player.updateInventory();
                }
            });
        }
    }

    private void handleAventurierFirework(Player player) {
        if (player.isGliding()) {
            ItemStack fwItem = new ItemStack(Material.FIREWORK_ROCKET);
            FireworkMeta fwm = (FireworkMeta) fwItem.getItemMeta();
            if (fwm != null) {
                fwm.setPower(2);
                fwItem.setItemMeta(fwm);
            }
            Firework fw = player.boostElytra(fwItem);
            if (fw != null) {
                fw.setSilent(true);
            }
            player.stopSound(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH);
            player.stopSound(Sound.ENTITY_FIREWORK_ROCKET_BLAST);
        } else {
            Firework fw = player.getWorld().spawn(player.getLocation().add(0, 1, 0), Firework.class);
            FireworkMeta fwm = fw.getFireworkMeta();
            fwm.setPower(1);
            fw.setFireworkMeta(fwm);
            fw.setSilent(true);
            player.stopSound(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH);
            player.stopSound(Sound.ENTITY_FIREWORK_ROCKET_BLAST);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            Player player = event.getPlayer();
            player.getWorld().spawnParticle(Particle.PORTAL, event.getTo(), 25, 0.4, 0.5, 0.4, 0.1);
        }
    }

    // ==========================================================
    // 6. AVENTURIER : COFFRES DE STRUCTURES & EXPLORATION
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGH)
    public void onAventurierChestInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Material type = block.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST && type != Material.BARREL) return;

        Player player = event.getPlayer();
        if (jobManager.getPlayerJob(player) != PlayerJob.AVENTURIER) return;

        if (!(block.getState() instanceof Container container)) return;

        if (container.getPersistentDataContainer().has(chestBoostKey)) return;

        // Vérifier si le coffre est dans une structure ou a une LootTable
        boolean inStructure = false;
        if (container instanceof Lootable lootable && lootable.hasLootTable()) {
            inStructure = true;
        } else {
            try {
                for (GeneratedStructure struct : block.getChunk().getStructures()) {
                    if (struct != null && struct.getBoundingBox().contains(block.getX(), block.getY(), block.getZ())) {
                        inStructure = true;
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }

        if (!inStructure) return;

        container.getPersistentDataContainer().set(chestBoostKey, PersistentDataType.BYTE, (byte) 1);
        container.update();

        int level = jobManager.getJobLevel(player, PlayerJob.AVENTURIER);

        // Laisser 1 tick pour que Bukkit résolve la LootTable éventuelle lors de l'ouverture
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!container.isPlaced()) return;
            Inventory inv = container.getInventory();
            populateAventurierChestLoot(inv, level);

            Location loc = block.getLocation().add(0.5, 1.0, 0.5);
            loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 25, 0.4, 0.4, 0.4, 0.1);
            loc.getWorld().spawnParticle(Particle.WAX_ON, loc, 15, 0.3, 0.3, 0.3, 0.05);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);

            player.sendActionBar(Component.text("✦ Coffre de structure : Butin rare d'Aventurier découvert ! ✦", NamedTextColor.GOLD, TextDecoration.BOLD));
        }, 1L);
    }

    private void populateAventurierChestLoot(Inventory inv, int level) {
        java.util.Random rnd = new java.util.Random();

        // 1. Minerais précieux
        int diamondCount = 2 + rnd.nextInt(4); // 2-5 diamants
        inv.addItem(new ItemStack(Material.DIAMOND, diamondCount));

        int goldCount = 4 + rnd.nextInt(7); // 4-10 lingots d'or
        inv.addItem(new ItemStack(Material.GOLD_INGOT, goldCount));

        int ironCount = 5 + rnd.nextInt(9); // 5-13 fers
        inv.addItem(new ItemStack(Material.IRON_INGOT, ironCount));

        if (rnd.nextBoolean()) {
            inv.addItem(new ItemStack(Material.EMERALD, 3 + rnd.nextInt(6)));
        }

        // 2. Cuprite (Nerf : 15% niveau 0, 30% niveau 1+)
        int cupriteChance = level >= 1 ? 30 : 15;
        if (rnd.nextInt(100) < cupriteChance) {
            int amount = (level >= 1 && rnd.nextInt(100) < 25) ? 2 : 1;
            inv.addItem(Cuprite.create(plugin, amount));
        }

        // 3. Épée en or Sharpness 7 Looting 4 (40% niveau 0, 70% niveau 1+)
        int swordChance = level >= 1 ? 70 : 40;
        if (rnd.nextInt(100) < swordChance) {
            inv.addItem(CustomJobItems.getAventurierGoldenSword());
        }

        // 4. Pomme cheat (Pomme dorée enchantée)
        int appleChance = level >= 1 ? 40 : 20;
        if (rnd.nextInt(100) < appleChance) {
            inv.addItem(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
        }

        // 5. Totem d'immortalité
        int totemChance = level >= 1 ? 35 : 15;
        if (rnd.nextInt(100) < totemChance) {
            inv.addItem(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
        }

        // 6. Butins suprêmes débloqués avec Mission 1 (Meilleurs Loots)
        if (level >= 1) {
            if (rnd.nextInt(100) < 30) {
                inv.addItem(new ItemStack(Material.NETHERITE_INGOT, 1));
            }
            if (rnd.nextInt(100) < 25) {
                inv.addItem(new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1));
            }
        }
    }

    // ==========================================================
    // 7. EXPLORATION DES STRUCTURES (Overworld) & BIOMES (Nether)
    // ==========================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (jobManager.getPlayerJob(player) != PlayerJob.AVENTURIER) return;

        World world = player.getWorld();

        // Overworld (Mission 1 : 5 structures différentes)
        if (world.getEnvironment() == World.Environment.NORMAL) {
            int level = jobManager.getJobLevel(player, PlayerJob.AVENTURIER);
            if (level == 0) {
                try {
                    for (GeneratedStructure genStructure : player.getLocation().getChunk().getStructures()) {
                        if (genStructure == null || genStructure.getStructure() == null) continue;
                        if (genStructure.getBoundingBox().contains(player.getLocation().toVector())) {
                            org.bukkit.NamespacedKey key = org.bukkit.Registry.STRUCTURE.getKey(genStructure.getStructure());
                            if (key == null) continue;
                            String rawKey = key.getKey().toLowerCase();
                            String typeId = normalizeStructureKey(rawKey);
                            String displayName = getStructureDisplayName(typeId);
                            jobManager.addDiscoveredStructure(player, typeId, displayName);
                        }
                    }
                } catch (Exception ignored) {}
            }
        } else if (world.getEnvironment() == World.Environment.NETHER) {
            // Nether (Mission 2 : Explorer tous les biomes du Nether)
            int level = jobManager.getJobLevel(player, PlayerJob.AVENTURIER);
            if (level == 1) {
                org.bukkit.block.Biome b = player.getLocation().getBlock().getBiome();
                String key = b.getKey().value().toLowerCase();
                if (key.contains("wastes") || key.contains("crimson") || key.contains("warped") || key.contains("valley") || key.contains("deltas")) {
                    String displayName = switch (key) {
                        case "nether_wastes" -> "Nether Wastes";
                        case "crimson_forest" -> "Crimson Forest";
                        case "warped_forest" -> "Warped Forest";
                        case "soul_sand_valley" -> "Soul Sand Valley";
                        case "basalt_deltas" -> "Basalt Deltas";
                        default -> key;
                    };
                    jobManager.addDiscoveredBiome(player, key, displayName);
                }
            }
        }
    }

    private String normalizeStructureKey(String key) {
        if (key.contains("village")) return "village";
        if (key.contains("mineshaft")) return "mineshaft";
        if (key.contains("pyramid") || key.contains("desert")) return "desert_pyramid";
        if (key.contains("jungle")) return "jungle_temple";
        if (key.contains("shipwreck")) return "shipwreck";
        if (key.contains("ocean_ruin")) return "ocean_ruin";
        if (key.contains("monument")) return "monument";
        if (key.contains("outpost")) return "pillager_outpost";
        if (key.contains("mansion")) return "woodland_mansion";
        if (key.contains("stronghold")) return "stronghold";
        if (key.contains("ancient_city")) return "ancient_city";
        if (key.contains("swamp_hut")) return "swamp_hut";
        if (key.contains("igloo")) return "igloo";
        if (key.contains("trail_ruins")) return "trail_ruins";
        if (key.contains("trial_chambers")) return "trial_chambers";
        if (key.contains("buried_treasure")) return "buried_treasure";
        return key;
    }

    private String getStructureDisplayName(String key) {
        return switch (key) {
            case "village" -> "Village";
            case "mineshaft" -> "Mineshaft Abandonné";
            case "desert_pyramid" -> "Pyramide du Désert";
            case "jungle_temple" -> "Temple de la Jungle";
            case "shipwreck" -> "Épave de Navire";
            case "ocean_ruin" -> "Ruines Océaniques";
            case "monument" -> "Monument Sous-marin";
            case "pillager_outpost" -> "Avant-poste de Pillards";
            case "woodland_mansion" -> "Manoir des Bois";
            case "stronghold" -> "Forteresse (Stronghold)";
            case "ancient_city" -> "Cité Antique (Ancient City)";
            case "swamp_hut" -> "Hutte de Sorcière";
            case "igloo" -> "Igloo";
            case "trail_ruins" -> "Ruines du Sentier (Trail Ruins)";
            case "trial_chambers" -> "Chambres des Épreuves (Trial Chambers)";
            case "buried_treasure" -> "Trésor Enfoui";
            default -> "Structure Mystérieuse";
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // 1. Dégâts spécifiques causés par l'atterrissage d'une Ender Pearl (DamageType.ENDER_PEARL ou perle avec noFallPearlKey)
        boolean isEnderPearl = event.getDamageSource().getDamageType() == org.bukkit.damage.DamageType.ENDER_PEARL;
        if (!isEnderPearl && event.getDamageSource().getDirectEntity() != null) {
            if (event.getDamageSource().getDirectEntity().getPersistentDataContainer().has(noFallPearlKey, PersistentDataType.BYTE)) {
                isEnderPearl = true;
            }
        }

        if (isEnderPearl) {
            boolean isAventurierM2 = jobManager.getPlayerJob(player) == PlayerJob.AVENTURIER && jobManager.getJobLevel(player, PlayerJob.AVENTURIER) >= 2;
            boolean isNoFallPearl = event.getDamageSource().getDirectEntity() != null && event.getDamageSource().getDirectEntity().getPersistentDataContainer().has(noFallPearlKey, PersistentDataType.BYTE);
            if (isAventurierM2 || isNoFallPearl) {
                event.setCancelled(true);
                player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 20, 0.3, 0.2, 0.3, 0.05);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.4f);
                return;
            }
        }

        // 2. Fin de vol de l'Architecte : protection anti-chute temporaire (5s)
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && fallImmunity.contains(player.getUniqueId())) {
            event.setCancelled(true);
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 15, 0.3, 0.2, 0.3, 0.05);
            player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.2f);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.length() <= 1) return;
        String raw = message.substring(1).trim();
        String[] parts = raw.split("\\s+");
        if (parts.length == 0) return;

        String cmd = parts[0].toLowerCase();
        if (cmd.contains(":")) {
            cmd = cmd.substring(cmd.indexOf(':') + 1);
        }

        Player player = event.getPlayer();

        if (cmd.equals("craft") || cmd.equals("workbench") || cmd.equals("wb")) {
            boolean isArchitect = jobManager.getPlayerJob(player) == PlayerJob.ARCHITECTE && jobManager.getJobLevel(player, PlayerJob.ARCHITECTE) >= 1;
            if (!isArchitect) {
                event.setCancelled(true);
                player.sendMessage(
                        Component.text("[Architecte] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                                .append(Component.text("Vous devez être Architecte de niveau 1 minimum pour utiliser /" + parts[0] + " !", NamedTextColor.RED))
                );
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            }
        } else if (cmd.equals("stonecutter") || cmd.equals("sc") || cmd.equals("nc")) {
            boolean isArchitect = jobManager.getPlayerJob(player) == PlayerJob.ARCHITECTE && jobManager.getJobLevel(player, PlayerJob.ARCHITECTE) >= 2;
            if (!isArchitect) {
                event.setCancelled(true);
                player.sendMessage(
                        Component.text("[Architecte] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                                .append(Component.text("Vous devez être Architecte de niveau 2 minimum pour utiliser /" + parts[0] + " !", NamedTextColor.RED))
                );
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            }
        } else if (cmd.equals("nv") || cmd.equals("nightvision")) {
            boolean isMineur = jobManager.getPlayerJob(player) == PlayerJob.MINEUR && jobManager.getJobLevel(player, PlayerJob.MINEUR) >= 1;
            boolean hasLeggings = CustomJobItems.isJobItem(player.getInventory().getLeggings(), CustomJobItems.ID_ARCHITECT_LEGGINGS);
            if (!isMineur && !hasLeggings) {
                event.setCancelled(true);
                player.sendMessage(
                        Component.text("[Vision Nocturne] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                                .append(Component.text("Vous devez être Mineur de niveau 1 ou équiper le Pantalon de l'Architecte pour utiliser /" + parts[0] + " !", NamedTextColor.RED))
                );
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            }
        } else if (cmd.equals("jb") || cmd.equals("jumpboost")) {
            boolean hasBoots = CustomJobItems.isJobItem(player.getInventory().getBoots(), CustomJobItems.ID_ARCHITECT_BOOTS);
            if (!hasBoots) {
                event.setCancelled(true);
                player.sendMessage(
                        Component.text("[Architecte] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                                .append(Component.text("Vous devez équiper les Chaussures de l'Architecte pour utiliser /" + parts[0] + " !", NamedTextColor.RED))
                );
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            }
        } else if (cmd.equals("sethome") || cmd.equals("set_home") || cmd.equals("sh") || cmd.equals("home") || cmd.equals("h")) {
            boolean isAventurier = jobManager.getPlayerJob(player) == PlayerJob.AVENTURIER && jobManager.getJobLevel(player, PlayerJob.AVENTURIER) >= 1;
            if (!isAventurier) {
                event.setCancelled(true);
                player.sendMessage(
                        Component.text("[Aventurier] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                                .append(Component.text("Vous devez être Aventurier de niveau 1 minimum pour utiliser /" + parts[0] + " !", NamedTextColor.RED))
                );
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            }
        } else if (cmd.equals("enderchest") || cmd.equals("ec")) {
            boolean isAventurierM4 = jobManager.getPlayerJob(player) == PlayerJob.AVENTURIER && jobManager.getJobLevel(player, PlayerJob.AVENTURIER) >= 4;
            if (!isAventurierM4) {
                event.setCancelled(true);
                player.sendMessage(
                        Component.text("[Aventurier] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                                .append(Component.text("Vous devez être Aventurier de niveau 4 pour utiliser /" + parts[0] + " !", NamedTextColor.RED))
                );
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            }
        }
    }

    // ==========================================
    // Boussole Antique de Découverte (Aventurier M2)
    // ==========================================
    private void handleAventurierDiscoveryCompass(Player player, ItemStack item) {
        if (jobManager.getPlayerJob(player) != PlayerJob.AVENTURIER || jobManager.getJobLevel(player, PlayerJob.AVENTURIER) < 2) {
            player.sendMessage(Component.text("[Aventurier] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text("Vous devez être Aventurier de niveau 2 minimum pour utiliser la Boussole Antique !", NamedTextColor.RED)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            player.sendMessage(Component.text("[Aventurier] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text("La Boussole Antique ne résonne que dans l'Overworld !", NamedTextColor.RED)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (discoveryCompassCooldowns.getOrDefault(uuid, 0L) > now) {
            long remaining = (discoveryCompassCooldowns.get(uuid) - now) / 1000L;
            player.sendMessage(Component.text("⌛ La Boussole Antique se recharge... Attendez encore " + remaining + "s.", NamedTextColor.GRAY));
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.8f, 1.2f);
            return;
        }

        player.sendMessage(Component.text("🧭 Analyse des résonances cartographiques en cours...", NamedTextColor.YELLOW));
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        discoveryCompassCooldowns.put(uuid, now + 30_000L);

        Location origin = player.getLocation();
        final int initialRadiusChunks = 62; // 62 chunks = 992 blocs (~1000 blocs)

        final List<Structure> candidateStructures = List.of(
                Structure.VILLAGE_PLAINS,
                Structure.VILLAGE_DESERT,
                Structure.VILLAGE_SAVANNA,
                Structure.VILLAGE_TAIGA,
                Structure.VILLAGE_SNOWY,
                Structure.MINESHAFT,
                Structure.SHIPWRECK,
                Structure.PILLAGER_OUTPOST,
                Structure.DESERT_PYRAMID,
                Structure.TRAIL_RUINS,
                Structure.SWAMP_HUT,
                Structure.IGLOO,
                Structure.JUNGLE_PYRAMID,
                Structure.OCEAN_RUIN_COLD,
                Structure.OCEAN_RUIN_WARM,
                Structure.MINESHAFT_MESA,
                Structure.SHIPWRECK_BEACHED,
                Structure.TRIAL_CHAMBERS,
                Structure.MONUMENT,
                Structure.ANCIENT_CITY
        );

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Location bestLoc = null;
            Structure bestStruct = null;
            double bestDist = Double.MAX_VALUE;

            // 1. Scan unique de toutes les structures en une seule passe via NMS/CraftWorld (HolderSet)
            StructureSearchResult singlePass = locateNearestAnyStructure(world, origin, candidateStructures, initialRadiusChunks);

            if (singlePass != null && singlePass.getLocation() != null) {
                bestLoc = singlePass.getLocation();
                bestStruct = singlePass.getStructure();
                bestDist = origin.distance(bestLoc);
            } else if (!craftWorldLocateMethodAvailable) {
                // Repli séquentiel uniquement si la méthode interne CraftWorld n'est pas disponible
                int currentMaxRadius = initialRadiusChunks;
                for (Structure struct : candidateStructures) {
                    if (!player.isOnline()) return;

                    try {
                        var searchResult = world.locateNearestStructure(origin, struct, currentMaxRadius, true);
                        if (searchResult != null && searchResult.getLocation() != null) {
                            double d = origin.distance(searchResult.getLocation());
                            if (d < bestDist) {
                                bestDist = d;
                                bestLoc = searchResult.getLocation();
                                bestStruct = struct;
                                currentMaxRadius = Math.max(8, (int) Math.ceil(bestDist / 16.0));
                                if (bestDist <= 200.0) {
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Boussole Antique] Erreur lors de la recherche de " + struct + " : " + e.getMessage());
                    }
                }
            }

            final Location finalLoc = bestLoc;
            final Structure finalStruct = bestStruct;
            final double finalDistance = bestDist;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    onCompassSearchComplete(player, item, origin, world, finalLoc, finalStruct, finalDistance);
                }
            });
        });
    }

    private static Method craftWorldLocateMethod = null;
    private static boolean craftWorldLocateMethodChecked = false;
    private static boolean craftWorldLocateMethodAvailable = false;

    private StructureSearchResult locateNearestAnyStructure(World world, Location origin, List<Structure> structures, int radiusChunks) {
        try {
            if (!craftWorldLocateMethodChecked) {
                craftWorldLocateMethodChecked = true;
                try {
                    craftWorldLocateMethod = world.getClass().getDeclaredMethod("locateNearestStructure", Location.class, List.class, int.class, boolean.class);
                    craftWorldLocateMethod.setAccessible(true);
                    craftWorldLocateMethodAvailable = true;
                } catch (NoSuchMethodException e) {
                    craftWorldLocateMethodAvailable = false;
                }
            }

            if (craftWorldLocateMethod != null) {
                return (StructureSearchResult) craftWorldLocateMethod.invoke(world, origin, structures, radiusChunks, true);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[Boussole Antique] Erreur scan groupé : " + t.getMessage());
        }
        return null;
    }

    private void onCompassSearchComplete(Player player, ItemStack item, Location origin, World world, Location foundLoc, Structure foundStruct, double finalDist) {
        UUID uuid = player.getUniqueId();
        if (foundLoc == null) {
            player.sendMessage(Component.text("[Aventurier] Aucune structure inexplorée détectée dans un rayon de 1000 blocs.", NamedTextColor.GRAY));
            discoveryCompassCooldowns.put(uuid, System.currentTimeMillis() + 5_000L);
            return;
        }

        // Mettre à jour l'item et le CompassTarget
        if (item != null && item.getType() == Material.COMPASS) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof CompassMeta cm) {
                cm.setLodestone(foundLoc);
                cm.setLodestoneTracked(false);
                item.setItemMeta(cm);
            }
        }
        for (ItemStack invItem : player.getInventory().getContents()) {
            if (invItem != null && CustomJobItems.isJobItem(invItem, CustomJobItems.ID_AVENTURIER_DISCOVERY_COMPASS)) {
                ItemMeta meta = invItem.getItemMeta();
                if (meta instanceof CompassMeta cm) {
                    cm.setLodestone(foundLoc);
                    cm.setLodestoneTracked(false);
                    invItem.setItemMeta(cm);
                }
            }
        }
        player.setCompassTarget(foundLoc);
        player.updateInventory();

        String name = getStructureFriendlyName(foundStruct);
        String direction = getCardinalDirection(origin, foundLoc);
        int distInt = (int) finalDist;

        player.sendMessage(Component.text("★ ========================================= ★", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("     ✦ BOUSSOLE ANTIQUE DE DÉCOUVERTE ✦", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("  • Structure détectée : ", NamedTextColor.GRAY).append(Component.text(name, NamedTextColor.AQUA, TextDecoration.BOLD)));
        player.sendMessage(Component.text("  • Distance : ", NamedTextColor.GRAY).append(Component.text(distInt + " blocs", NamedTextColor.WHITE, TextDecoration.BOLD)));
        player.sendMessage(Component.text("  • Direction : ", NamedTextColor.GRAY).append(Component.text(direction, NamedTextColor.GOLD, TextDecoration.BOLD)));
        player.sendMessage(Component.text("  • Coordonnées : ", NamedTextColor.GRAY).append(Component.text("X=" + foundLoc.getBlockX() + ", Z=" + foundLoc.getBlockZ(), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("  ➜ L'aiguille de votre boussole pointe vers cette structure !", NamedTextColor.GREEN));
        player.sendMessage(Component.text("★ ========================================= ★", NamedTextColor.GOLD, TextDecoration.BOLD));

        player.sendActionBar(Component.text("✦ Découverte : " + name + " à " + distInt + "m (" + direction + ") ✦", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 1.0f, 1.1f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
        world.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.5, 0), 25, 0.4, 0.5, 0.4, 0.1);
    }

    private String getStructureFriendlyName(Structure structure) {
        if (structure == null) return "Structure Mystérieuse";
        org.bukkit.NamespacedKey nk = org.bukkit.Registry.STRUCTURE.getKey(structure);
        String key = (nk != null) ? nk.getKey().toLowerCase() : structure.toString().toLowerCase();
        if (key.contains("village")) return "Village";
        if (key.contains("ancient_city")) return "Cité des Abîmes (Ancient City)";
        if (key.contains("trial_chambers")) return "Chambre des Épreuves (Trial Chamber)";
        if (key.contains("monument")) return "Monument Océanique";
        if (key.contains("mansion")) return "Manoir des Bois";
        if (key.contains("pillager_outpost")) return "Avant-poste de Pillards";
        if (key.contains("mineshaft")) return "Mine Abandonnée";
        if (key.contains("desert_pyramid")) return "Temple du Désert";
        if (key.contains("jungle_pyramid")) return "Temple de la Jungle";
        if (key.contains("swamp_hut")) return "Hutte de Sorcière";
        if (key.contains("stronghold")) return "Fort de l'End (Stronghold)";
        if (key.contains("shipwreck")) return "Épave de Navire";
        if (key.contains("ocean_ruin")) return "Ruines Océaniques";
        if (key.contains("igloo")) return "Igloo";
        if (key.contains("trail_ruins")) return "Ruines des Sentiers";
        if (key.contains("buried_treasure")) return "Trésor Enfoui";
        return "Structure Inexplorée (" + key + ")";
    }

    private String getCardinalDirection(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        if (angle < 0) angle += 360;

        if (angle >= 337.5 || angle < 22.5) return "Sud (+Z)";
        if (angle >= 22.5 && angle < 67.5) return "Sud-Ouest (-X, +Z)";
        if (angle >= 67.5 && angle < 112.5) return "Ouest (-X)";
        if (angle >= 112.5 && angle < 157.5) return "Nord-Ouest (-X, -Z)";
        if (angle >= 157.5 && angle < 202.5) return "Nord (-Z)";
        if (angle >= 202.5 && angle < 247.5) return "Nord-Est (+X, -Z)";
        if (angle >= 247.5 && angle < 292.5) return "Est (+X)";
        return "Sud-Est (+X, +Z)";
    }

    // ==========================================
    // Sacoche de Minage aspirante (Mineur M2)
    // ==========================================
    @EventHandler
    public void onPouchInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MineurPouchManager.MineurPouchHolder) {
            if (event.getPlayer() instanceof Player p) {
                MineurPouchManager.savePouch(p);
                p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 0.8f, 1.2f);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (jobManager.getPlayerJob(player) != PlayerJob.MINEUR || jobManager.getJobLevel(player, PlayerJob.MINEUR) < 2) {
            return;
        }
        if (!MineurPouchManager.hasPouchInInventory(player)) {
            return;
        }

        Iterator<Item> iterator = event.getItems().iterator();
        int absorbedCount = 0;

        while (iterator.hasNext()) {
            Item itemEntity = iterator.next();
            ItemStack stack = itemEntity.getItemStack();
            if (MineurPouchManager.isAbsorbableOre(stack)) {
                int amountBefore = stack.getAmount();
                boolean fully = MineurPouchManager.tryAbsorb(player, stack);
                int absorbed = amountBefore - stack.getAmount();
                if (absorbed > 0) {
                    absorbedCount += absorbed;
                }
                if (fully || stack.getAmount() <= 0) {
                    iterator.remove();
                } else {
                    itemEntity.setItemStack(stack);
                }
            }
        }

        if (absorbedCount > 0) {
            player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.7f, 1.4f);
            player.sendActionBar(Component.text("⛏ " + absorbedCount + " minerais aspirés dans votre Sacoche de Minage !", NamedTextColor.GOLD, TextDecoration.BOLD));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (jobManager.getPlayerJob(player) != PlayerJob.MINEUR || jobManager.getJobLevel(player, PlayerJob.MINEUR) < 2) {
                return;
            }
            if (!MineurPouchManager.hasPouchInInventory(player)) {
                return;
            }

            Item itemEntity = event.getItem();
            ItemStack stack = itemEntity.getItemStack();
            if (MineurPouchManager.isAbsorbableOre(stack)) {
                int before = stack.getAmount();
                boolean fully = MineurPouchManager.tryAbsorb(player, stack);
                int absorbed = before - stack.getAmount();
                if (absorbed > 0) {
                    player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.6f, 1.5f);
                    player.sendActionBar(Component.text("⛏ " + absorbed + " minerais aspirés dans votre Sacoche !", NamedTextColor.GOLD));
                }
                if (fully || stack.getAmount() <= 0) {
                    event.setCancelled(true);
                    itemEntity.remove();
                } else {
                    itemEntity.setItemStack(stack);
                }
            }
        }
    }

    @EventHandler
    public void onJobPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        MineurPouchManager.savePouch(player);
        grapplingCooldowns.remove(player.getUniqueId());
        fallImmunity.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (!CustomJobItems.isJobItem(rod, CustomJobItems.ID_AVENTURIER_GRAPPLING_HOOK)) {
            rod = player.getInventory().getItemInOffHand();
            if (!CustomJobItems.isJobItem(rod, CustomJobItems.ID_AVENTURIER_GRAPPLING_HOOK)) return;
        }

        if (jobManager.getPlayerJob(player) != PlayerJob.AVENTURIER || jobManager.getJobLevel(player, PlayerJob.AVENTURIER) < 4) {
            player.sendMessage(Component.text("[Aventurier] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text("Vous devez être Aventurier de niveau 4 pour utiliser le Grappin d'Exploration !", NamedTextColor.RED)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            event.setCancelled(true);
            return;
        }

        PlayerFishEvent.State state = event.getState();
        if (state == PlayerFishEvent.State.IN_GROUND || state == PlayerFishEvent.State.CAUGHT_ENTITY || state == PlayerFishEvent.State.REEL_IN) {
            FishHook hook = event.getHook();
            if (hook == null) return;

            long now = System.currentTimeMillis();
            Long last = grapplingCooldowns.get(player.getUniqueId());
            if (last != null && now - last < 1500L) {
                return;
            }

            Location hookLoc = hook.getLocation();
            Location playerLoc = player.getLocation();
            double distance = hookLoc.distance(playerLoc);

            if (distance > 1.8) {
                grapplingCooldowns.put(player.getUniqueId(), now);
                player.setCooldown(Material.FISHING_ROD, 35);

                org.bukkit.util.Vector dir = hookLoc.toVector().subtract(playerLoc.toVector());
                dir.normalize();

                double speed = Math.min(2.4, 0.9 + distance * 0.08);
                org.bukkit.util.Vector velocity = dir.multiply(speed);
                velocity.setY(Math.min(1.4, velocity.getY() + 0.35));

                player.setVelocity(velocity);

                player.getWorld().spawnParticle(Particle.CLOUD, playerLoc.clone().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.08);
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, playerLoc.clone().add(0, 1.0, 0), 2, 0.2, 0.2, 0.2, 0.01);
                player.playSound(playerLoc, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.2f);
                player.playSound(playerLoc, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.4f);

                fallImmunity.add(player.getUniqueId());
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    fallImmunity.remove(player.getUniqueId());
                }, 100L); // 5 secondes de protection
            }
        }
    }
}

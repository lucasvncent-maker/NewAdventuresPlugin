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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.player.PlayerRespawnEvent;
import fr.loual.customminerals.items.Cuprite;
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

    public JobListener(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
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

        // Clic sur une mission pour afficher directement ses détails
        Integer missionNum = meta.getPersistentDataContainer().get(JobSelectionGui.MISSION_ITEM_KEY, PersistentDataType.INTEGER);
        if (missionNum != null) {
            PlayerJob pj = jobManager.getPlayerJob(player);
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

        // 2. Récompense Mission 2 : 5% de chance de drop de la Cuprite sur les minerais
        if (level >= 2) {
            if (Math.random() <= 0.05) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), Cuprite.create(plugin, 1));
                block.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, block.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0.1);
                player.playSound(block.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9f, 1.5f);
                player.sendActionBar(Component.text("✦ [Mineur M2] Cuprite découverte !", NamedTextColor.GOLD, TextDecoration.BOLD));
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (fallImmunity.contains(player.getUniqueId())) {
                event.setCancelled(true);
                player.getWorld().spawnParticle(Particle.POOF, player.getLocation(), 15, 0.3, 0.2, 0.3, 0.05);
                player.playSound(player.getLocation(), Sound.BLOCK_WOOL_FALL, 0.8f, 1.2f);
            }
        }
    }
}

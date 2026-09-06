package fr.loual.customclasses.listeners;

import fr.loual.customclasses.CustomClasses;
import fr.loual.customclasses.classes.ClassManager;
import fr.loual.customclasses.classes.PlayerClass;
import fr.loual.customclasses.gui.ClassGuiHolder;
import fr.loual.customclasses.gui.ClassSelectionGui;
import fr.loual.customclasses.jobs.gui.JobSelectionGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

import java.util.*;

public class ClassListener implements Listener {

    private final CustomClasses plugin;
    private final ClassManager classManager;

    // Sirène : temps passé hors de l'eau (UUID -> timestamp dernière immersion en ms)
    private final Map<UUID, Long> sirenLastWaterTime = new HashMap<>();

    // Sirène : temps de début de sneak pour le cri sonique (UUID -> timestamp début sneak en ms)
    private final Map<UUID, Long> sirenSneakStart = new HashMap<>();
    private final Map<UUID, Long> sirenLastShoutTime = new HashMap<>();

    // Sauterelle : gestion onde de choc à la chute
    private final Set<UUID> hopperShockwaveCooldown = new HashSet<>();

    // Nécromancien : UUIDs des serviteurs temporaires invoqués & lien vers le maître
    private final Set<UUID> necroMinions = new HashSet<>();
    private final Map<UUID, UUID> minionToMaster = new HashMap<>();

    // Archer : clé pour identifier les flèches tirées par des squelettes/monstres
    private final NamespacedKey skeletonArrowKey;

    public ClassListener(CustomClasses plugin) {
        this.plugin = plugin;
        this.classManager = plugin.getClassManager();
        this.skeletonArrowKey = new NamespacedKey(plugin, "skeleton_arrow");

        startPeriodicTasks();
    }

    private void startPeriodicTasks() {
        // Tâche 1 : Exécutée chaque seconde (20 ticks) pour les passifs périodiques et maintien des buffs
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerClass pc = classManager.getPlayerClass(player);
                UUID uuid = player.getUniqueId();

                // --- 1. SIRÈNE : Gestion du temps hors de l'eau ---
                if (pc == PlayerClass.SIRENE) {
                    boolean inWater = player.isInWaterOrRainOrBubbleColumn() || player.getLocation().getBlock().getType() == Material.WATER;
                    if (inWater) {
                        long lastWater = sirenLastWaterTime.getOrDefault(uuid, now);
                        if (now - lastWater >= 900_000L) {
                            player.removePotionEffect(PotionEffectType.HUNGER);
                            player.removePotionEffect(PotionEffectType.SLOWNESS);
                            player.sendMessage(Component.text("✦ Vous êtes de nouveau immergé, vous vous réhydratez !", NamedTextColor.AQUA));
                        }
                        sirenLastWaterTime.put(uuid, now);
                    } else {
                        long lastWater = sirenLastWaterTime.getOrDefault(uuid, now);
                        long elapsed = now - lastWater;
                        // 15 minutes = 15 * 60 * 1000 = 900 000 ms
                        if (elapsed >= 900_000L) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 60, 0, false, false, true));
                            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, false, true));
                            player.sendActionBar(Component.text("⚠ Déshydratation ! Plongez dans l'eau ou buvez une fiole !", NamedTextColor.RED, TextDecoration.BOLD));
                        } else if (elapsed >= 840_000L) { // 14 minutes (1 min restante)
                            long remainingSec = (900_000L - elapsed) / 1000L;
                            player.sendActionBar(Component.text("⚠ Déshydratation imminente (" + remainingSec + "s restantes) !", NamedTextColor.GOLD));
                        }
                    }

                    // Maintenir les effets permanents de la sirène
                    ensurePermanentEffect(player, PotionEffectType.WATER_BREATHING, 0);
                    ensurePermanentEffect(player, PotionEffectType.DOLPHINS_GRACE, 0);
                    ensurePermanentEffect(player, PotionEffectType.NIGHT_VISION, 0);
                }

                // --- 2. DIABLE : Dégâts directs sous la pluie ---
                if (pc == PlayerClass.DIABLE) {
                    Location loc = player.getLocation();
                    World world = loc.getWorld();
                    if (world != null && world.getEnvironment() == World.Environment.NORMAL && world.hasStorm()) {
                        int highestY = world.getHighestBlockYAt(loc);
                        if (loc.getBlockY() >= highestY) {
                            String biomeName = loc.getBlock().getBiome().name().toLowerCase();
                            boolean noRainBiome = biomeName.contains("desert") || biomeName.contains("savanna") || biomeName.contains("badlands");
                            if (!noRainBiome) {
                                player.damage(1.5); // Dégâts directs sous la pluie
                                world.spawnParticle(Particle.SMOKE, loc.clone().add(0, 1, 0), 8, 0.25, 0.25, 0.25, 0.05);
                                playExtinguishSound(player);
                                player.sendActionBar(Component.text("🌧 L'eau de pluie vous blesse ! Mettez-vous à l'abri !", NamedTextColor.RED, TextDecoration.BOLD));
                            }
                        }
                    }

                    // Maintenir résistance au feu permanente
                    ensurePermanentEffect(player, PotionEffectType.FIRE_RESISTANCE, 0);
                }

                // --- 3. GUERRIER : Maintenir Résistance I ---
                if (pc == PlayerClass.GUERRIER) {
                    ensurePermanentEffect(player, PotionEffectType.RESISTANCE, 0);
                }

                // --- 4. SAUTERELLE : Maintenir Jump Boost II ---
                if (pc == PlayerClass.SAUTERELLE) {
                    ensurePermanentEffect(player, PotionEffectType.JUMP_BOOST, 1);
                }

                // --- 5. ARCHER : Vitesse I permanente si arme à distance en main ---
                if (pc == PlayerClass.ARCHER) {
                    ItemStack mainHand = player.getInventory().getItemInMainHand();
                    ItemStack offHand = player.getInventory().getItemInOffHand();
                    boolean holdsRanged = isRangedWeapon(mainHand) || isRangedWeapon(offHand);
                    if (holdsRanged) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false, true));
                    }
                }
            }
        }, 20L, 20L);

        // Tâche 2 : Exécutée toutes les 5 ticks (0.25s) pour la détection fine (sneak Sirène, noyade Diable)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerClass pc = classManager.getPlayerClass(player);
                UUID uuid = player.getUniqueId();

                // Sirène : Préparation et déclenchement Cri Sonique via accroupissement de 2 secondes
                if (pc == PlayerClass.SIRENE) {
                    if (player.isSneaking()) {
                        long start = sirenSneakStart.getOrDefault(uuid, now);
                        long duration = now - start;
                        long lastShout = sirenLastShoutTime.getOrDefault(uuid, 0L);
                        long cooldownRemaining = 10_000L - (now - lastShout);

                        if (cooldownRemaining <= 0) {
                            if (duration >= 2000L) {
                                triggerSirenSonicShout(player);
                                sirenLastShoutTime.put(uuid, now);
                                sirenSneakStart.put(uuid, now + 10_000L); // Évite de redéclencher immédiatement si on reste accroupi
                            } else if (duration >= 500L) {
                                int percent = (int) Math.min(100, (duration * 100) / 2000L);
                                player.sendActionBar(Component.text("⚡ Chargement Cri Sonique : " + percent + "%", NamedTextColor.DARK_AQUA));
                                player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation().clone().add(0, 1.2, 0), 2, 0.2, 0.2, 0.2, 0.05);
                            }
                        }
                    }
                }

                // Diable : Se noyer 3 fois plus vite sous l'eau
                if (pc == PlayerClass.DIABLE) {
                    if (player.getEyeLocation().getBlock().getType() == Material.WATER) {
                        // En 5 ticks, vanilla retire 5 ticks d'air. On retire 10 ticks d'air de plus pour faire 15 ticks / 5 ticks = 3x plus vite !
                        int air = player.getRemainingAir();
                        if (air > 0) {
                            player.setRemainingAir(Math.max(0, air - 10));
                        } else {
                            player.damage(1.0); // Dégâts de suffocation accélérés
                        }
                    }
                }

                // 8. ARCHER : Visée sans ralentissement (vitesse normale) + ramassage flèches de squelettes
                if (pc == PlayerClass.ARCHER) {
                    ItemStack mainHand = player.getInventory().getItemInMainHand();
                    ItemStack offHand = player.getInventory().getItemInOffHand();
                    boolean holdsRanged = isRangedWeapon(mainHand) || isRangedWeapon(offHand);

                    if (holdsRanged) {
                        if (player.isHandRaised()) {
                            // Neutralise le ralentissement de 80% causé par la visée de l'arc
                            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10, 4, false, false, false));
                        } else {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false, true));
                        }
                    }

                    // Ramassage automatique des flèches au sol tirées par des squelettes/monstres
                    for (Entity nearby : player.getNearbyEntities(2.0, 2.0, 2.0)) {
                        if (nearby instanceof AbstractArrow arrow && (arrow.isInBlock() || arrow.isOnGround())) {
                            if (arrow.getPickupStatus() != AbstractArrow.PickupStatus.ALLOWED
                                    || arrow.getPersistentDataContainer().has(skeletonArrowKey, PersistentDataType.BYTE)) {
                                player.getInventory().addItem(new ItemStack(Material.ARROW));
                                try {
                                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.3f);
                                } catch (Exception ignored) {}
                                player.sendActionBar(Component.text("✦ Flèche de squelette ramassée !", NamedTextColor.YELLOW));
                                arrow.remove();
                            }
                        }
                    }
                }
            }
        }, 5L, 5L);
    }

    private void ensurePermanentEffect(Player player, PotionEffectType type, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current == null || current.getDuration() < 100) {
            player.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, false, false, true));
        }
    }

    private void playExtinguishSound(Player player) {
        try {
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.3f);
        } catch (Exception ignored) {}
    }

    private boolean isRangedWeapon(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.BOW || type == Material.CROSSBOW;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!classManager.hasClass(player)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !classManager.hasClass(player)) {
                    ClassSelectionGui.open(plugin, player);
                }
            }, 5L);
        } else {
            classManager.refreshPlayer(player);
        }

        sirenLastWaterTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                classManager.refreshPlayer(player);
                sirenLastWaterTime.put(player.getUniqueId(), System.currentTimeMillis());
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        classManager.unloadPlayer(player);
        sirenLastWaterTime.remove(uuid);
        sirenSneakStart.remove(uuid);
        sirenLastShoutTime.remove(uuid);
        hopperShockwaveCooldown.remove(uuid);
        removePlayerMinions(uuid);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        removePlayerMinions(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ClassGuiHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String classId = meta.getPersistentDataContainer().get(ClassSelectionGui.CLASS_ICON_KEY, PersistentDataType.STRING);
        if (classId == null) return;

        PlayerClass pc = PlayerClass.fromId(classId);
        if (pc != PlayerClass.NONE) {
            classManager.setPlayerClass(player, pc);
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
            player.sendMessage(
                    Component.text("✦ Vous avez choisi la classe : ", NamedTextColor.GREEN, TextDecoration.BOLD)
                            .append(Component.text(pc.getDisplayName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                            .append(Component.text(" !", NamedTextColor.GREEN))
            );
            sirenLastWaterTime.put(player.getUniqueId(), System.currentTimeMillis());

            // Proposer ensuite le choix du métier si aucun n'est sélectionné
            if (!plugin.getJobManager().hasJob(player)) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && !plugin.getJobManager().hasJob(player)) {
                        JobSelectionGui.open(plugin, player);
                    }
                }, 20L);
            }
        }
    }

    // ==========================================
    // 1. HUMAIN : +25% XP sur toutes les sources
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        if (classManager.getPlayerClass(player) == PlayerClass.HUMAIN) {
            int originalExp = event.getAmount();
            if (originalExp > 0) {
                int bonusExp = (int) Math.ceil(originalExp * 1.25);
                event.setAmount(bonusExp);
            }
        }
    }

    // ==========================================================
    // COMBAT AU CORPS-À-CORPS ET DÉGÂTS PAR CLASSE
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        // Cas 1 : Le joueur attaque directement au corps-à-corps
        if (damager instanceof Player player) {
            PlayerClass pc = classManager.getPlayerClass(player);
            ItemStack hand = player.getInventory().getItemInMainHand();

            // Empêcher le maître d'attaquer ses propres serviteurs
            if (necroMinions.contains(victim.getUniqueId()) && player.getUniqueId().equals(minionToMaster.get(victim.getUniqueId()))) {
                event.setCancelled(true);
                return;
            }

            // Nécromancien : ordonner aux serviteurs de se focaliser sur la cible attaquée
            if (pc == PlayerClass.NECROMANCIEN && victim instanceof LivingEntity target && !necroMinions.contains(victim.getUniqueId())) {
                focusMinionsOnTarget(player.getUniqueId(), target);
            }

            switch (pc) {
                case ASSASSIN -> {
                    // Sneak attack sur monstre
                    if (player.isSneaking() && victim instanceof Monster) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1, false, true, true));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, false, true));
                        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.6f);
                    }
                }

                case SAUTERELLE -> {
                    // Knockback léger passif qui propulse les cibles en l'air
                    if (victim instanceof LivingEntity livingVictim && !(victim instanceof Player)) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (livingVictim.isValid() && !livingVictim.isDead()) {
                                Vector currentVelocity = livingVictim.getVelocity();
                                livingVictim.setVelocity(new Vector(currentVelocity.getX() * 0.5, 0.48, currentVelocity.getZ() * 0.5));
                                livingVictim.getWorld().spawnParticle(Particle.CLOUD, livingVictim.getLocation().clone().add(0, 0.2, 0), 4, 0.1, 0.1, 0.1, 0.05);
                            }
                        }, 1L);
                    }
                }

                case DIABLE -> {
                    // Tous les coups au corps-à-corps enflamment les cibles
                    victim.setFireTicks(100); // 5 secondes de feu
                    victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation().clone().add(0, 1, 0), 10, 0.25, 0.3, 0.25, 0.05);
                    try {
                        victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 1.2f);
                    } catch (Exception ignored) {}
                }

                case NECROMANCIEN -> {
                    // Malus : Épées, haches inefficaces (-80% de dégâts)
                    if (isSwordOrAxe(hand)) {
                        event.setDamage(event.getDamage() * 0.20);
                    }

                    // Bonus houe : Poison II (6s = 120t), Wither II (3s = 60t), Lenteur (4s = 80t)
                    if (isHoe(hand) && victim instanceof LivingEntity livingVictim) {
                        livingVictim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 120, 1));
                        livingVictim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
                        livingVictim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                        livingVictim.getWorld().spawnParticle(Particle.WITCH, livingVictim.getLocation().clone().add(0, 1, 0), 12, 0.3, 0.3, 0.3, 0.05);
                        try {
                            player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.7f, 1.8f);
                        } catch (Exception ignored) {}
                    }
                }

                case ARCHER -> {
                    // Malus : Dégâts réduits de moitié (-50%) avec épées et haches
                    if (isSwordOrAxe(hand)) {
                        event.setDamage(event.getDamage() * 0.50);
                    }
                }

                default -> {}
            }
        }

        // Cas 2 : Flèche tirée par un joueur (ARCHER ou NÉCROMANCIEN)
        if (damager instanceof Arrow arrow && arrow.getShooter() instanceof Player shooter) {
            PlayerClass pc = classManager.getPlayerClass(shooter);

            if (pc == PlayerClass.ARCHER) {
                // +30% de dégâts avec arc/arbalète
                event.setDamage(event.getDamage() * 1.30);

                // Révèle les cibles touchées à travers les blocs (Surbrillance 5 secondes)
                if (victim instanceof LivingEntity livingVictim) {
                    livingVictim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false, true));
                }

                // Tir à plus de 20 blocs : Critique garanti + Fort recul (Knockback II)
                Location shootLoc = arrow.getOrigin();
                if (shootLoc != null && shootLoc.distance(victim.getLocation()) >= 20.0) {
                    arrow.setCritical(true);
                    event.setDamage(event.getDamage() * 1.25); // Bonus critique garanti

                    Vector dir = arrow.getVelocity().normalize().multiply(1.6).setY(0.4);
                    victim.setVelocity(dir);

                    try {
                        shooter.playSound(shooter.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.5f);
                        shooter.playSound(shooter.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
                    } catch (Exception ignored) {}
                    shooter.sendActionBar(Component.text("✦ Tir d'élite longue distance (+20 blocs) ! Coup Critique + Fort Recul !", NamedTextColor.GOLD, TextDecoration.BOLD));
                    victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().clone().add(0, 1, 0), 12, 0.3, 0.3, 0.3, 0.2);
                }
            } else if (pc == PlayerClass.NECROMANCIEN) {
                // Malus Nécromancien : tir inefficace (-80% dégâts)
                event.setDamage(event.getDamage() * 0.20);
            }
        }

        // Cas 3 : Serviteurs du nécromancien (pas d'attaque envers le maître ni entre serviteurs du même maître)
        if (damager instanceof LivingEntity minion && necroMinions.contains(minion.getUniqueId())) {
            UUID masterId = minionToMaster.get(minion.getUniqueId());
            if (victim.getUniqueId().equals(masterId)) {
                event.setCancelled(true);
                return;
            }
            if (necroMinions.contains(victim.getUniqueId()) && masterId != null && masterId.equals(minionToMaster.get(victim.getUniqueId()))) {
                event.setCancelled(true);
                return;
            }
        }

        // Si le maître est attaqué, riposte coordonnée de ses serviteurs
        if (victim instanceof Player master && minionToMaster.containsValue(master.getUniqueId())) {
            if (damager instanceof LivingEntity attacker && !necroMinions.contains(attacker.getUniqueId())) {
                focusMinionsOnTarget(master.getUniqueId(), attacker);
            }
        }
    }

    private boolean isSwordOrAxe(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE");
    }

    private boolean isHoe(ItemStack item) {
        if (item == null) return false;
        return item.getType().name().endsWith("_HOE");
    }

    // =========================================================================
    // DÉGÂTS GÉNÉRAUX : Diable (immunité feu/lave) & Sauterelle (chute / choc)
    // =========================================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        PlayerClass pc = classManager.getPlayerClass(player);

        // 6. DIABLE : Immunité totale au feu, à la lave et aux blocs brûlants
        if (pc == PlayerClass.DIABLE) {
            switch (event.getCause()) {
                case LAVA, FIRE, FIRE_TICK, HOT_FLOOR, CAMPFIRE -> {
                    event.setCancelled(true);
                    player.setFireTicks(0);
                    return;
                }
                default -> {}
            }
        }

        // 4. SAUTERELLE : Dégâts de chute accrus (+50%) & Onde de choc à l'atterrissage
        if (pc == PlayerClass.SAUTERELLE && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            // Malus : +50% de dégâts de chute
            event.setDamage(event.getDamage() * 1.5);

            // Déclencher une onde de choc si la chute était conséquente
            float fallDistance = player.getFallDistance();
            double effectiveFall = Math.max((double) fallDistance, event.getDamage() + 3.0);
            if (effectiveFall >= 3.5 && !hopperShockwaveCooldown.contains(player.getUniqueId())) {
                triggerHopperShockwave(player, (float) effectiveFall);
            }
        }
    }

    private void triggerHopperShockwave(Player player, float fallDistance) {
        hopperShockwaveCooldown.add(player.getUniqueId());
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 0.2, 0), 2, 0.5, 0.1, 0.5, 0.05);
        world.spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 0.3, 0), 6, 1.0, 0.1, 1.0, 0.1);

        try {
            world.playSound(loc, Sound.valueOf("ITEM_MACE_SMASH_GROUND"), 1.2f, 1.0f);
        } catch (IllegalArgumentException e) {
            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.3f);
        }

        double radius = Math.min(8.0, 3.0 + (fallDistance * 0.35));
        double damage = Math.min(16.0, 4.0 + (fallDistance * 0.8));

        for (Entity entity : world.getNearbyEntities(loc, radius, 3.5, radius)) {
            if (entity instanceof LivingEntity target && !target.equals(player)) {
                if (necroMinions.contains(target.getUniqueId())) continue;

                target.damage(damage, player);
                Vector knockback = target.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(0.85).setY(0.42);
                target.setVelocity(knockback);
            }
        }

        player.sendMessage(Component.text("✦ Onde de choc à l'atterrissage !", NamedTextColor.GREEN, TextDecoration.BOLD));
        Bukkit.getScheduler().runTaskLater(plugin, () -> hopperShockwaveCooldown.remove(player.getUniqueId()), 30L);
    }

    // =========================================================================
    // 6. DIABLE : Nage normale dans la lave
    // =========================================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (classManager.getPlayerClass(player) == PlayerClass.DIABLE) {
            if (player.isInLava()) {
                Location from = event.getFrom();
                Location to = event.getTo();
                if (to == null) return;
                double dx = to.getX() - from.getX();
                double dz = to.getZ() - from.getZ();
                double moveDistSq = dx * dx + dz * dz;

                if (moveDistSq > 0.001) {
                    Vector dir = player.getLocation().getDirection().normalize();
                    Vector vel = player.getVelocity();

                    // Vaincre la friction lourde de la lave pour nager avec la fluidité de l'eau
                    Vector boosted = new Vector(dir.getX() * 0.22, vel.getY(), dir.getZ() * 0.22);
                    if (vel.getY() > 0.01) {
                        boosted.setY(0.18);
                    }
                    player.setVelocity(boosted);
                }
            }
        }
    }

    // ==========================================================
    // 5. SIRÈNE : Cri Sonique (sneak ou corne) & réinitialisation eau
    // ==========================================================
    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (classManager.getPlayerClass(player) == PlayerClass.SIRENE) {
            if (event.isSneaking()) {
                sirenSneakStart.put(player.getUniqueId(), System.currentTimeMillis());
            } else {
                sirenSneakStart.remove(player.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PlayerClass pc = classManager.getPlayerClass(player);

        if (pc == PlayerClass.SIRENE && event.getAction().isRightClick()) {
            ItemStack item = event.getItem();
            long now = System.currentTimeMillis();

            // 1. Souffler dans une corne pour déclencher le Cri Sonique
            if (item != null && item.getType() == Material.GOAT_HORN) {
                long lastShout = sirenLastShoutTime.getOrDefault(player.getUniqueId(), 0L);
                long cooldownRemaining = 10_000L - (now - lastShout);
                if (cooldownRemaining <= 0) {
                    triggerSirenSonicShout(player);
                    sirenLastShoutTime.put(player.getUniqueId(), now);
                } else {
                    long remainingSec = (cooldownRemaining / 1000L) + 1;
                    player.sendActionBar(Component.text("⏳ Cri sonique en recharge (" + remainingSec + "s)...", NamedTextColor.RED));
                }
            }

            // 2. Se réhydrater avec un seau d'eau ou une fiole d'eau
            if (item != null && (item.getType() == Material.WATER_BUCKET || isWaterPotion(item))) {
                resetSirenHydration(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (classManager.getPlayerClass(player) == PlayerClass.SIRENE) {
            ItemStack item = event.getItem();
            if (isWaterPotion(item) || item.getType() == Material.POTION || item.getType() == Material.MILK_BUCKET) {
                resetSirenHydration(player);
            }
        }
    }

    private boolean isWaterPotion(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) return false;
        if (item.getItemMeta() instanceof PotionMeta meta) {
            return meta.getBasePotionType() == PotionType.WATER;
        }
        return false;
    }

    private void resetSirenHydration(Player player) {
        sirenLastWaterTime.put(player.getUniqueId(), System.currentTimeMillis());
        player.removePotionEffect(PotionEffectType.HUNGER);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        try {
            player.playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 0.8f, 1.2f);
        } catch (Exception ignored) {}
        player.sendMessage(Component.text("✦ Vous vous êtes réhydraté !", NamedTextColor.AQUA, TextDecoration.BOLD));
    }

    private void triggerSirenSonicShout(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        try {
            world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.4f);
        } catch (Exception e) {
            world.playSound(loc, Sound.ENTITY_ALLAY_HURT, 1.5f, 0.5f);
        }
        world.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0, 1.2, 0), 1);

        int affected = 0;
        for (Entity entity : world.getNearbyEntities(loc, 8.0, 8.0, 8.0)) {
            if (entity instanceof LivingEntity target && !target.equals(player)) {
                // Lenteur III (amplifier 2) pendant 5s (100 ticks)
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2, false, true, true));
                // Faiblesse II (amplifier 1) pendant 5s (100 ticks)
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1, false, true, true));
                affected++;
            }
        }

        player.sendActionBar(Component.text("⚡ CRI SONIQUE DÉCLENCHÉ ! (" + affected + " créatures touchées) ⚡", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        player.sendMessage(Component.text("✦ Cri sonique libéré : toutes les créatures dans 8 blocs subissent Lenteur III et Faiblesse II pendant 5s !", NamedTextColor.DARK_AQUA));
    }

    private void focusMinionsOnTarget(UUID masterId, LivingEntity target) {
        for (Map.Entry<UUID, UUID> entry : minionToMaster.entrySet()) {
            if (entry.getValue().equals(masterId)) {
                Entity m = Bukkit.getEntity(entry.getKey());
                if (m instanceof Monster minion && minion.isValid()) {
                    minion.setTarget(target);
                }
            }
        }
    }

    private void removePlayerMinions(UUID masterId) {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : minionToMaster.entrySet()) {
            if (entry.getValue().equals(masterId)) {
                Entity m = Bukkit.getEntity(entry.getKey());
                if (m != null && m.isValid()) {
                    m.remove();
                }
                toRemove.add(entry.getKey());
            }
        }
        for (UUID id : toRemove) {
            necroMinions.remove(id);
            minionToMaster.remove(id);
        }
    }

    // =========================================================================
    // 7. NÉCROMANCIEN : 40% de réanimer Zombie/Squelette à la mort d'un monstre
    // =========================================================================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMonsterDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        if (classManager.getPlayerClass(killer) == PlayerClass.NECROMANCIEN && entity instanceof Monster) {
            // 40% de chances de réanimer un serviteur
            if (Math.random() <= 0.40) {
                spawnNecroMinion(killer, entity.getLocation());
            }
        }
    }

    private void spawnNecroMinion(Player master, Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        EntityType minionType = (Math.random() < 0.5) ? EntityType.ZOMBIE : EntityType.SKELETON;
        Entity spawned = world.spawnEntity(loc, minionType);

        if (spawned instanceof Monster minion) {
            UUID minionId = minion.getUniqueId();
            necroMinions.add(minionId);
            minionToMaster.put(minionId, master.getUniqueId());

            // Nom du serviteur
            minion.customName(Component.text("Serviteur de " + master.getName(), NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            minion.setCustomNameVisible(true);

            // Équiper pour immuniser au soleil et armer le serviteur
            org.bukkit.inventory.EntityEquipment equip = minion.getEquipment();
            if (equip != null) {
                equip.setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
                equip.setHelmetDropChance(0.0f);

                if (minionType == EntityType.ZOMBIE) {
                    equip.setItemInMainHand(new ItemStack(Material.IRON_HOE));
                    equip.setItemInMainHandDropChance(0.0f);
                } else {
                    equip.setItemInMainHand(new ItemStack(Material.BOW));
                    equip.setItemInMainHandDropChance(0.0f);
                }
            }

            // Cibler les monstres ennemis autour
            for (Entity nearby : world.getNearbyEntities(loc, 14, 6, 14)) {
                if (nearby instanceof Monster target && !necroMinions.contains(target.getUniqueId()) && !target.equals(minion)) {
                    minion.setTarget(target);
                    break;
                }
            }

            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 0.5, 0), 20, 0.5, 0.5, 0.5, 0.05);
            world.spawnParticle(Particle.WITCH, loc.clone().add(0, 0.5, 0), 12, 0.4, 0.4, 0.4, 0.05);
            try {
                world.playSound(loc, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.8f, 1.8f);
            } catch (Exception ignored) {}

            master.sendActionBar(Component.text("✦ Serviteur " + (minionType == EntityType.ZOMBIE ? "Zombie" : "Squelette") + " réanimé (25s) !", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));

            // Serviteur éphémère (25 secondes max = 500 ticks)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!minion.isDead() && minion.isValid()) {
                    world.spawnParticle(Particle.SMOKE, minion.getLocation().clone().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.05);
                    try {
                        world.playSound(minion.getLocation(), Sound.ENTITY_SKELETON_DEATH, 0.8f, 1.4f);
                    } catch (Exception ignored) {}
                    minion.remove();
                }
                necroMinions.remove(minionId);
                minionToMaster.remove(minionId);
            }, 500L);
        }
    }

    // =========================================================================
    // 8. ARCHER : 35% d'économie de flèche & restriction de tir NÉCROMANCIEN
    // =========================================================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShootBow(EntityShootBowEvent event) {
        LivingEntity shooter = event.getEntity();

        // 7. NÉCROMANCIEN : Tir impossible avec arc ou arbalète
        if (shooter instanceof Player player && classManager.getPlayerClass(player) == PlayerClass.NECROMANCIEN) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("❌ Le Nécromancien est incapable d'utiliser des armes à distance !", NamedTextColor.RED, TextDecoration.BOLD));
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.8f);
            } catch (Exception ignored) {}
            return;
        }

        // 8. ARCHER : 35% de chance de préserver la munition
        if (shooter instanceof Player player && classManager.getPlayerClass(player) == PlayerClass.ARCHER) {
            if (Math.random() <= 0.35) {
                event.setConsumeItem(false);
                try {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 2.0f);
                } catch (Exception ignored) {}
                player.sendActionBar(Component.text("✦ Munition préservée (35%) !", NamedTextColor.AQUA, TextDecoration.BOLD));
            }
        }

        // Identifier les flèches tirées par des monstres pour l'Archer
        if (shooter instanceof Monster && event.getProjectile() instanceof AbstractArrow arrow) {
            arrow.getPersistentDataContainer().set(skeletonArrowKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArrowPickup(PlayerPickupArrowEvent event) {
        Player player = event.getPlayer();
        PlayerClass pc = classManager.getPlayerClass(player);

        // Ramassage automatique des flèches tirées par les monstres (squelettes)
        if (pc == PlayerClass.ARCHER) {
            AbstractArrow arrow = event.getArrow();
            if (arrow.getPickupStatus() != AbstractArrow.PickupStatus.ALLOWED) {
                arrow.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
            }
        }
    }
}

package fr.loual.customclasses.listeners;

import fr.loual.customclasses.CustomClasses;
import fr.loual.customclasses.classes.ClassManager;
import fr.loual.customclasses.classes.PlayerClass;
import fr.loual.customclasses.gui.ClassGuiHolder;
import fr.loual.customclasses.gui.ClassSelectionGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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

    // Nécromancien : UUIDs des serviteurs temporaires invoqués
    private final Set<UUID> necroMinions = new HashSet<>();

    public ClassListener(CustomClasses plugin) {
        this.plugin = plugin;
        this.classManager = plugin.getClassManager();

        startPeriodicTasks();
    }

    private void startPeriodicTasks() {
        // Tâche exécutée chaque seconde (20 ticks) pour les passifs périodiques
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerClass pc = classManager.getPlayerClass(player);

                // --- 1. SIRÈNE : Gestion du temps hors de l'eau ---
                if (pc == PlayerClass.SIRENE) {
                    if (player.isInWaterOrRainOrBubbleColumn() || player.getLocation().getBlock().getType() == Material.WATER) {
                        sirenLastWaterTime.put(player.getUniqueId(), now);
                    } else {
                        long lastWater = sirenLastWaterTime.getOrDefault(player.getUniqueId(), now);
                        // 15 minutes = 15 * 60 * 1000 = 900 000 ms
                        if (now - lastWater >= 900_000L) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 60, 0, false, false, true));
                            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, false, true));
                        }
                    }

                    // Détection du Cri sonique : s'accroupir pendant 2 secondes
                    if (player.isSneaking()) {
                        long start = sirenSneakStart.getOrDefault(player.getUniqueId(), now);
                        if (now - start >= 2000L) {
                            long lastShout = sirenLastShoutTime.getOrDefault(player.getUniqueId(), 0L);
                            if (now - lastShout >= 10000L) { // Cooldown de 10s sur le cri sonique
                                triggerSirenSonicShout(player);
                                sirenLastShoutTime.put(player.getUniqueId(), now);
                                sirenSneakStart.put(player.getUniqueId(), now + 10000L); // Reset
                            }
                        }
                    }
                }

                // --- 2. DIABLE : Dégâts sous la pluie + noyade 3x plus rapide ---
                if (pc == PlayerClass.DIABLE) {
                    Location loc = player.getLocation();
                    World world = loc.getWorld();
                    if (world != null && world.hasStorm()) {
                        // Si le joueur est sous le ciel ouvert et qu'il pleut
                        int highestY = world.getHighestBlockYAt(loc);
                        if (loc.getBlockY() >= highestY) {
                            player.damage(1.0); // 1/2 cœur de dégât par seconde sous la pluie
                            player.getWorld().spawnParticle(Particle.SMOKE, loc.add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.02);
                        }
                    }

                    // Se noyer 3x plus vite sous l'eau
                    if (player.getRemainingAir() > 0 && player.getEyeLocation().getBlock().getType() == Material.WATER) {
                        player.setRemainingAir(Math.max(0, player.getRemainingAir() - 40));
                    }
                }

                // --- 3. ARCHER : Vitesse I permanente si arme à distance en main ---
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
                    // Knockback léger passif qui propulse les monstres en l'air
                    if (victim instanceof Monster monster) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            Vector currentVelocity = monster.getVelocity();
                            monster.setVelocity(currentVelocity.add(new Vector(0, 0.45, 0)));
                        }, 1L);
                    }
                }

                case DIABLE -> {
                    // Tous les coups au corps-à-corps enflamment les cibles
                    victim.setFireTicks(100); // 5 secondes de feu
                }

                case NECROMANCIEN -> {
                    // Malus : Épées, haches inefficaces (-80% de dégâts)
                    if (isSwordOrAxe(hand)) {
                        event.setDamage(event.getDamage() * 0.20);
                    }

                    // Bonus houe : Poison II (6s), Wither II (3s), Lenteur I (4s)
                    if (isHoe(hand) && victim instanceof LivingEntity livingVictim) {
                        livingVictim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 120, 1));
                        livingVictim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
                        livingVictim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0));
                        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.7f, 1.8f);
                    }
                }

                case ARCHER -> {
                    // Malus : -50% de dégâts avec épées et haches
                    if (isSwordOrAxe(hand)) {
                        event.setDamage(event.getDamage() * 0.50);
                    }
                }

                default -> {}
            }
        }

        // Cas 2 : Flèche tirée par un joueur (ARCHER)
        if (damager instanceof Arrow arrow && arrow.getShooter() instanceof Player shooter) {
            PlayerClass pc = classManager.getPlayerClass(shooter);

            if (pc == PlayerClass.ARCHER) {
                // +30% de dégâts avec arc/arbalète
                event.setDamage(event.getDamage() * 1.30);

                // Surbrillance (Glowing) pendant 5 secondes sur la cible
                if (victim instanceof LivingEntity livingVictim) {
                    livingVictim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false, true));
                }

                // Tir à plus de 20 blocs : Critique garanti + Knockback II
                Location shootLoc = arrow.getOrigin();
                if (shootLoc != null && shootLoc.distance(victim.getLocation()) >= 20.0) {
                    arrow.setCritical(true);
                    event.setDamage(event.getDamage() * 1.25); // Bonus critique

                    Vector dir = arrow.getVelocity().normalize().multiply(1.5).setY(0.4);
                    victim.setVelocity(dir);

                    shooter.playSound(shooter.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.5f);
                    shooter.sendMessage(Component.text("✦ Tir d'élite longue distance (+20 blocs) !", NamedTextColor.GOLD, TextDecoration.BOLD));
                }
            } else if (pc == PlayerClass.NECROMANCIEN) {
                // Malus Nécromancien : tir inefficace (-80% dégâts)
                event.setDamage(event.getDamage() * 0.20);
            }
        }

        // Empêcher les serviteurs du nécromancien d'attaquer leur maître
        if (damager instanceof LivingEntity minion && necroMinions.contains(minion.getUniqueId()) && victim instanceof Player) {
            event.setCancelled(true);
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

    // =================================================================
    // 4. SAUTERELLE : Dégâts de chute accrus & Onde de choc à l'atterrissage
    // =================================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            PlayerClass pc = classManager.getPlayerClass(player);
            if (pc == PlayerClass.SAUTERELLE) {
                // Malus : dégâts de chute augmentés de 50%
                event.setDamage(event.getDamage() * 1.5);

                // Déclencher une onde de choc si la chute était importante (chute >= 4 blocs)
                float fallDistance = player.getFallDistance();
                if (fallDistance >= 4.0f && !hopperShockwaveCooldown.contains(player.getUniqueId())) {
                    triggerHopperShockwave(player, fallDistance);
                }
            }
        }
    }

    private void triggerHopperShockwave(Player player, float fallDistance) {
        hopperShockwaveCooldown.add(player.getUniqueId());
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.EXPLOSION, loc, 3, 1.0, 0.2, 1.0, 0.1);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);

        double radius = Math.min(8.0, 3.0 + (fallDistance * 0.3));
        double damage = Math.min(15.0, 3.0 + (fallDistance * 0.8));

        for (Entity entity : world.getNearbyEntities(loc, radius, 3.0, radius)) {
            if (entity instanceof Monster monster) {
                monster.damage(damage, player);
                Vector knockback = monster.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(0.8).setY(0.4);
                monster.setVelocity(knockback);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> hopperShockwaveCooldown.remove(player.getUniqueId()), 40L);
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

        // Sirène : souffler dans une corne déclenche aussi le Cri sonique
        if (pc == PlayerClass.SIRENE && event.getAction().isRightClick()) {
            ItemStack item = event.getItem();
            if (item != null && item.getType() == Material.GOAT_HORN) {
                long now = System.currentTimeMillis();
                long lastShout = sirenLastShoutTime.getOrDefault(player.getUniqueId(), 0L);
                if (now - lastShout >= 10000L) {
                    triggerSirenSonicShout(player);
                    sirenLastShoutTime.put(player.getUniqueId(), now);
                }
            }

            // Réinitialiser le chronomètre d'eau avec une fiole d'eau ou un seau d'eau
            if (item != null && (item.getType() == Material.POTION || item.getType() == Material.WATER_BUCKET)) {
                sirenLastWaterTime.put(player.getUniqueId(), System.currentTimeMillis());
                player.removePotionEffect(PotionEffectType.HUNGER);
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                player.sendMessage(Component.text("✦ Vous vous êtes réhydraté !", NamedTextColor.AQUA));
            }
        }
    }

    private void triggerSirenSonicShout(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.4f);
        world.spawnParticle(Particle.SONIC_BOOM, loc.add(0, 1, 0), 1);

        for (Entity e : world.getNearbyEntities(loc, 8.0, 8.0, 8.0)) {
            if (e instanceof LivingEntity living && !e.equals(player)) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2)); // Lenteur III (5s)
                living.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1)); // Faiblesse II (5s)
            }
        }
        player.sendMessage(Component.text("✦ Cri sonique déclenché !", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
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
            // 40% de chances
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
            necroMinions.add(minion.getUniqueId());

            // Apparence du serviteur
            minion.customName(Component.text("Serviteur de " + master.getName(), NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            minion.setCustomNameVisible(true);

            // Cibler les monstres ennemis autour
            for (Entity nearby : world.getNearbyEntities(loc, 12, 6, 12)) {
                if (nearby instanceof Monster target && !necroMinions.contains(target.getUniqueId()) && !target.equals(minion)) {
                    minion.setTarget(target);
                    break;
                }
            }

            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 15, 0.5, 0.5, 0.5, 0.05);
            world.playSound(loc, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.8f, 1.8f);

            // Serviteur éphémère (25 secondes max = 500 ticks)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!minion.isDead()) {
                    world.spawnParticle(Particle.SMOKE, minion.getLocation(), 10, 0.3, 0.5, 0.3, 0.05);
                    minion.remove();
                    necroMinions.remove(minion.getUniqueId());
                }
            }, 500L);
        }
    }

    // =========================================================================
    // 8. ARCHER : 35% de chance de pas consommer de flèche & ramassage flèches squelettes
    // =========================================================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        PlayerClass pc = classManager.getPlayerClass(player);
        if (pc == PlayerClass.ARCHER) {
            // 35% de chance de préserver la munition
            if (Math.random() <= 0.35) {
                event.setConsumeItem(false);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 2.0f);
            }
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

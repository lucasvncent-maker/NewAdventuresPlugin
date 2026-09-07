package fr.loual.customclasses.listeners;

import fr.loual.newadventure.NewAdventurePlugin;
import fr.loual.customclasses.classes.ClassManager;
import fr.loual.customclasses.classes.PlayerClass;
import fr.loual.customclasses.gui.ClassGuiHolder;
import fr.loual.customclasses.gui.ClassSelectionGui;
import fr.loual.customclasses.jobs.gui.JobSelectionGui;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.FluidCollisionMode;

import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.Locale;

import java.util.*;

public class ClassListener implements Listener {

    private final NewAdventurePlugin plugin;
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

    // Capacités Spéciales (Touche F)
    private final Map<UUID, Long> abilityCooldowns = new HashMap<>();
    private final Map<UUID, Long> assassinBackstabBuff = new HashMap<>();
    private final Set<UUID> sauterelleSuperDrop = new HashSet<>();
    private final Set<UUID> sauterelleFallImmunity = new HashSet<>();
    private final Set<UUID> diablePuddleActive = new HashSet<>();
    private final NamespacedKey arrowRainKey;

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("mon-pack-unique-v1".getBytes());

    public ClassListener(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.classManager = plugin.getClassManager();
        this.skeletonArrowKey = new NamespacedKey(plugin, "skeleton_arrow");
        this.arrowRainKey = new NamespacedKey(plugin, "archer_arrow_rain");

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
                            String biomeName = loc.getBlock().getBiome().getKey().getKey().toLowerCase();
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

        String url = "https://github.com/lucasvncent-maker/NewAdventuresPlugin/releases/download/v1.0.0/newAdventureTexturePack.zip";
        String hash = "04b7af75206492f0dd16dd1445a180bd0d474a82";

        // 2. Créer l'info du pack
        ResourcePackInfo packInfo = ResourcePackInfo.resourcePackInfo()
                .id(PACK_ID)
                .uri(java.net.URI.create(url))
                .hash(hash) // Ici on passe la String hexadécimale directement (ou le byte[])
                .build();

        // 3. Créer la requête complète
        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(packInfo)
                .required(true) // Si tu veux forcer le pack
                .prompt(Component.text("Ce serveur nécessite un pack de textures custom.", NamedTextColor.YELLOW))
                .build();

        // 4. Envoyer le pack
        player.sendResourcePacks(request);

        // Gestion de la classe du joueur
        if (!classManager.hasClass(player)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !classManager.hasClass(player)) {
                    ClassSelectionGui.open(plugin, player);
                }
            }, 5L);
        } else {
            classManager.refreshPlayer(player);
        }

        sirenLastWaterTime.put(
                player.getUniqueId(),
                System.currentTimeMillis()
        );
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

        abilityCooldowns.remove(uuid);
        assassinBackstabBuff.remove(uuid);
        sauterelleSuperDrop.remove(uuid);
        sauterelleFallImmunity.remove(uuid);
        if (diablePuddleActive.remove(uuid)) {
            player.setInvulnerable(false);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        removePlayerMinions(uuid);
        assassinBackstabBuff.remove(uuid);
        sauterelleSuperDrop.remove(uuid);
        sauterelleFallImmunity.remove(uuid);
        if (diablePuddleActive.remove(uuid)) {
            player.setInvulnerable(false);
        }
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

                    // Capacité Pas de l'Ombre : Dégâts doublés dans le dos
                    if (assassinBackstabBuff.containsKey(player.getUniqueId())) {
                        long expiry = assassinBackstabBuff.get(player.getUniqueId());
                        if (System.currentTimeMillis() <= expiry) {
                            Vector victimDir = victim.getLocation().getDirection().setY(0).normalize();
                            Vector attackerDir = player.getLocation().getDirection().setY(0).normalize();
                            Vector toVictim = victim.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0).normalize();

                            boolean isBehind = victimDir.dot(attackerDir) > 0.35 && victimDir.dot(toVictim) > 0.20;
                            if (isBehind) {
                                assassinBackstabBuff.remove(player.getUniqueId());
                                event.setDamage(event.getDamage() * 2.0);
                                Location vLoc = victim.getLocation().add(0, 1.0, 0);
                                World w = victim.getWorld();
                                w.spawnParticle(Particle.CRIT, vLoc, 35, 0.4, 0.4, 0.4, 0.3);
                                w.spawnParticle(Particle.DAMAGE_INDICATOR, vLoc, 15, 0.3, 0.3, 0.3, 0.1);
                                w.spawnParticle(Particle.SOUL, vLoc, 20, 0.3, 0.3, 0.3, 0.05);
                                w.playSound(vLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.6f);
                                w.playSound(vLoc, Sound.BLOCK_ANVIL_LAND, 0.7f, 1.9f);
                                player.sendActionBar(Component.text("☠ COUP CRITIQUE DANS LE DOS (x2 DÉGÂTS) ! ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD));
                            }
                        } else {
                            assassinBackstabBuff.remove(player.getUniqueId());
                        }
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

        // Diable : Invincibilité totale pendant la liquéfaction en flaque de braises
        if (diablePuddleActive.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

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
            // Immunité au saut de la capacité Catapulte Aérienne
            if (sauterelleFallImmunity.contains(player.getUniqueId())) {
                event.setCancelled(true);
                if (sauterelleSuperDrop.contains(player.getUniqueId())) {
                    sauterelleSuperDrop.remove(player.getUniqueId());
                    triggerHopperMegaShockwave(player);
                }
                return;
            }

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
            world.playSound(loc, Sound.ITEM_MACE_SMASH_GROUND, 1.2f, 1.0f);
        } catch (Throwable e) {
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
            world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.3f, 1.4f);
        } catch (Exception e) {
            world.playSound(loc, Sound.ENTITY_ALLAY_HURT, 1.5f, 0.5f);
        }
        world.playSound(loc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.8f);

        world.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0, 1.2, 0), 1);
        world.spawnParticle(Particle.BUBBLE_COLUMN_UP, loc.clone().add(0, 1.0, 0), 40, 1.5, 0.5, 1.5, 0.1);
        world.spawnParticle(Particle.NAUTILUS, loc.clone().add(0, 1.0, 0), 30, 1.2, 0.5, 1.2, 0.1);
        world.spawnParticle(Particle.SPLASH, loc.clone().add(0, 1.0, 0), 50, 1.5, 0.5, 1.5, 0.2);

        int affected = 0;
        for (Entity entity : world.getNearbyEntities(loc, 8.0, 5.0, 8.0)) {
            if (entity instanceof LivingEntity target && !target.equals(player)) {
                if (necroMinions.contains(target.getUniqueId())) continue;

                // Lenteur III (amplifier 2) pendant 5s (100 ticks)
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2, false, true, true));
                // Faiblesse II (amplifier 1) pendant 5s (100 ticks)
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1, false, true, true));

                // Recul aquatique
                Vector push = target.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(0.8).setY(0.35);
                target.setVelocity(push);
                affected++;
            }
        }

        player.sendActionBar(Component.text("⚡ CRI SONIQUE LIBÉRÉ ! (" + affected + " créatures affaiblies) ⚡", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        player.sendMessage(Component.text("✦ Cri sonique : Toutes les créatures dans 8 blocs subissent Lenteur III et Faiblesse II pendant 5s !", NamedTextColor.DARK_AQUA));
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

    // ==========================================================
    // CAPACITÉS SPÉCIALES ACTIVES (TOUCHE F)
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        PlayerClass pc = classManager.getPlayerClass(player);

        // Les humains et sans classe gardent l'échange normal d'offhand
        if (pc == PlayerClass.NONE || pc == PlayerClass.HUMAIN) {
            return;
        }

        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cdUntil = abilityCooldowns.getOrDefault(uuid, 0L);

        if (now < cdUntil) {
            long remainingMs = cdUntil - now;
            double sec = Math.ceil(remainingMs / 100.0) / 10.0;
            player.sendActionBar(Component.text("⏳ Capacité en recharge (" + String.format(Locale.US, "%.1f", sec) + "s)", NamedTextColor.RED, TextDecoration.BOLD));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }

        switch (pc) {
            case ASSASSIN -> {
                abilityCooldowns.put(uuid, now + 14_000L);
                triggerAssassinShadowStep(player);
            }
            case GUERRIER -> {
                abilityCooldowns.put(uuid, now + 16_000L);
                triggerWarriorGroundSlam(player);
            }
            case SAUTERELLE -> {
                abilityCooldowns.put(uuid, now + 14_000L);
                triggerHopperSuperJump(player);
            }
            case SIRENE -> {
                abilityCooldowns.put(uuid, now + 14_000L);
                triggerSirenSonicShout(player);
            }
            case DIABLE -> {
                abilityCooldowns.put(uuid, now + 20_000L);
                triggerDiableLavaPuddle(player);
            }
            case NECROMANCIEN -> {
                triggerNecromancerMinionExplosion(player);
            }
            case ARCHER -> {
                abilityCooldowns.put(uuid, now + 18_000L);
                triggerArcherArrowRain(player);
            }
            default -> {}
        }
    }

    // 1. ASSASSIN : Pas de l'Ombre (Téléportation 8 blocs + dégâts x2 dans le dos)
    private void triggerAssassinShadowStep(Player player) {
        UUID uuid = player.getUniqueId();
        Location startLoc = player.getLocation();
        World world = startLoc.getWorld();
        if (world == null) return;

        // Effets de fumée et d'ombre au point de départ
        world.spawnParticle(Particle.LARGE_SMOKE, startLoc.clone().add(0, 1.0, 0), 25, 0.4, 0.5, 0.4, 0.05);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, startLoc.clone().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.02);
        world.spawnParticle(Particle.SQUID_INK, startLoc.clone().add(0, 1.0, 0), 20, 0.4, 0.4, 0.4, 0.05);
        world.spawnParticle(Particle.PORTAL, startLoc.clone().add(0, 1.0, 0), 25, 0.5, 0.5, 0.5, 0.1);
        world.playSound(startLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 1.4f);
        world.playSound(startLoc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);

        // Téléportation sécurisée 8 blocs en avant
        Vector dir = startLoc.getDirection();
        Location targetLoc = startLoc.clone();

        for (double d = 1.0; d <= 8.0; d += 0.5) {
            Location test = startLoc.clone().add(dir.clone().multiply(d));
            Block feetBlock = test.getBlock();
            Block headBlock = test.clone().add(0, 1, 0).getBlock();
            if (!feetBlock.isPassable() || !headBlock.isPassable()) {
                break;
            }
            targetLoc = test;
        }
        targetLoc.setYaw(startLoc.getYaw());
        targetLoc.setPitch(startLoc.getPitch());

        player.teleport(targetLoc);

        // Effets à l'arrivée
        world.spawnParticle(Particle.LARGE_SMOKE, targetLoc.clone().add(0, 1.0, 0), 20, 0.4, 0.5, 0.4, 0.05);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, targetLoc.clone().add(0, 1.0, 0), 15, 0.3, 0.4, 0.3, 0.05);
        world.spawnParticle(Particle.REVERSE_PORTAL, targetLoc.clone().add(0, 1.0, 0), 20, 0.4, 0.5, 0.4, 0.05);
        world.playSound(targetLoc, Sound.ENTITY_PHANTOM_FLAP, 1.2f, 1.6f);
        world.playSound(targetLoc, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.0f, 1.5f);

        assassinBackstabBuff.put(uuid, System.currentTimeMillis() + 10_000L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 50, 1, false, false, true));

        player.sendActionBar(Component.text("✦ PAS DE L'OMBRE ! Prochain coup dans le dos x2 DÉGÂTS ! ✦", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        player.sendMessage(Component.text("✦ Pas de l'Ombre : Téléporté 8 blocs en avant ! Votre prochaine frappe dans le dos infligera le DOUBLE de dégâts !", NamedTextColor.LIGHT_PURPLE));
    }

    // 2. GUERRIER : Choc Tellurique (Frappe au sol en cône, étourdit 3s et renverse)
    private void triggerWarriorGroundSlam(Player player) {
        Location origin = player.getLocation();
        World world = origin.getWorld();
        if (world == null) return;

        Vector forward = origin.getDirection().setY(0).normalize();
        player.swingMainHand();

        // Onde de choc visuelle au sol le long du cône
        for (double r = 1.5; r <= 7.5; r += 1.5) {
            double spread = Math.toRadians(35);
            for (double a = -spread; a <= spread; a += spread / 3.0) {
                Vector rot = forward.clone().rotateAroundY(a).multiply(r);
                Location pLoc = origin.clone().add(rot);
                Block b = pLoc.getBlock();
                world.spawnParticle(Particle.BLOCK, pLoc.clone().add(0, 0.2, 0), 12, 0.3, 0.1, 0.3, 0.05,
                        b.getType().isAir() ? Material.DIRT.createBlockData() : b.getBlockData());
                world.spawnParticle(Particle.SWEEP_ATTACK, pLoc.clone().add(0, 0.3, 0), 2, 0.1, 0.1, 0.1, 0.02);
            }
        }
        world.spawnParticle(Particle.EXPLOSION, origin.clone().add(forward.clone().multiply(2.0)).add(0, 0.5, 0), 3, 0.5, 0.2, 0.5, 0.05);
        try {
            world.playSound(origin, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.3f, 0.8f);
        } catch (Throwable t) {
            world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        }
        world.playSound(origin, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 0.6f);
        world.playSound(origin, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.7f);

        int stunnedCount = 0;
        for (Entity entity : world.getNearbyEntities(origin, 8.0, 4.0, 8.0)) {
            if (entity instanceof LivingEntity target && !target.equals(player)) {
                if (necroMinions.contains(target.getUniqueId())) continue;

                Vector toTarget = target.getLocation().toVector().subtract(origin.toVector());
                double dist = toTarget.length();
                if (dist > 7.5 || dist < 0.2) continue;

                Vector hToTarget = toTarget.clone().setY(0).normalize();
                double dot = forward.dot(hToTarget);
                // Cône de ~70 degrés (dot >= 0.70)
                if (dot >= 0.70) {
                    stunnedCount++;
                    target.damage(10.0, player);

                    // Renversement & projection
                    Vector kb = forward.clone().multiply(0.6).setY(0.45);
                    target.setVelocity(kb);

                    // Étourdissement 3 secondes (60 ticks)
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 5, false, true, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 4, false, true, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 4, false, true, true));

                    if (target instanceof Mob mob) {
                        mob.setAI(false);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (mob.isValid()) mob.setAI(true);
                        }, 60L);
                    }

                    // Étoiles d'étourdissement au-dessus de la tête
                    new BukkitRunnable() {
                        int ticks = 0;
                        @Override
                        public void run() {
                            if (!target.isValid() || ticks++ >= 12) {
                                cancel();
                                return;
                            }
                            Location head = target.getEyeLocation().add(0, 0.4, 0);
                            double angle = ticks * 0.8;
                            Location p1 = head.clone().add(Math.cos(angle) * 0.4, 0, Math.sin(angle) * 0.4);
                            target.getWorld().spawnParticle(Particle.CRIT, p1, 1, 0, 0, 0, 0);
                            target.getWorld().spawnParticle(Particle.WAX_ON, p1, 1, 0, 0, 0, 0);
                        }
                    }.runTaskTimer(plugin, 0L, 5L);
                }
            }
        }

        player.sendActionBar(Component.text("🔨 CHOC TELLURIQUE ! " + stunnedCount + " monstres étourdis (3s) !", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("✦ Choc Tellurique : Sol martelé violemment ! Les ennemis dans le cône sont projetés et étourdis pendant 3s !", NamedTextColor.GOLD));
    }

    // 3. SAUTERELLE : Catapulte Aérienne (Propulsion 15 blocs + onde de choc à l'impact)
    private void triggerHopperSuperJump(Player player) {
        UUID uuid = player.getUniqueId();
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        Vector dir = loc.getDirection().setY(0).normalize().multiply(0.3);
        dir.setY(1.65); // Catapulte à 15 blocs de haut
        player.setVelocity(dir);

        sauterelleSuperDrop.add(uuid);
        sauterelleFallImmunity.add(uuid);

        world.spawnParticle(Particle.EXPLOSION, loc, 3, 0.4, 0.2, 0.4, 0.05);
        world.spawnParticle(Particle.POOF, loc, 20, 0.4, 0.2, 0.4, 0.08);
        world.spawnParticle(Particle.ITEM_SLIME, loc.clone().add(0, 0.5, 0), 30, 0.6, 0.3, 0.6, 0.1);
        world.spawnParticle(Particle.CLOUD, loc, 20, 0.5, 0.2, 0.5, 0.1);

        try {
            world.playSound(loc, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.4f, 0.8f);
        } catch (Throwable t) {
            world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.2f, 0.7f);
        }
        world.playSound(loc, Sound.ENTITY_SLIME_JUMP, 1.3f, 0.6f);

        player.sendActionBar(Component.text("🚀 CATAPULTE AÉRIENNE ! Onde de choc prête pour l'impact !", NamedTextColor.GREEN, TextDecoration.BOLD));

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!player.isOnline() || !sauterelleSuperDrop.contains(uuid) || ticks++ > 120) {
                    sauterelleSuperDrop.remove(uuid);
                    sauterelleFallImmunity.remove(uuid);
                    cancel();
                    return;
                }

                player.getWorld().spawnParticle(Particle.ITEM_SLIME, player.getLocation().clone().add(0, 0.2, 0), 4, 0.2, 0.2, 0.2, 0.02);
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 2, 0.1, 0.1, 0.1, 0.02);

                if (ticks >= 15 && (player.isOnGround() || player.getLocation().getBlock().getRelative(0, -1, 0).getType().isSolid())) {
                    triggerHopperMegaShockwave(player);
                    sauterelleSuperDrop.remove(uuid);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> sauterelleFallImmunity.remove(uuid), 20L);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private void triggerHopperMegaShockwave(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0, 0.3, 0), 2);
        for (int deg = 0; deg < 360; deg += 15) {
            double rad = Math.toRadians(deg);
            Location pLoc = loc.clone().add(Math.cos(rad) * 4.5, 0.2, Math.sin(rad) * 4.5);
            world.spawnParticle(Particle.ITEM_SLIME, pLoc, 6, 0.2, 0.1, 0.2, 0.05);
            world.spawnParticle(Particle.SWEEP_ATTACK, pLoc, 1, 0.1, 0.1, 0.1, 0.02);
        }

        try {
            world.playSound(loc, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.4f, 0.8f);
        } catch (Throwable t) {
            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
        }
        world.playSound(loc, Sound.ENTITY_SLIME_SQUISH, 1.4f, 0.6f);

        int count = 0;
        for (Entity entity : world.getNearbyEntities(loc, 9.0, 4.0, 9.0)) {
            if (entity instanceof LivingEntity target && !target.equals(player)) {
                if (necroMinions.contains(target.getUniqueId())) continue;

                target.damage(14.0, player);
                Vector kb = target.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(0.9).setY(0.95);
                target.setVelocity(kb);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, false, true, true));
                count++;
            }
        }

        player.sendActionBar(Component.text("💥 IMPACT DÉVASTATEUR ! (" + count + " ennemis pulvérisés) 💥", NamedTextColor.GREEN, TextDecoration.BOLD));
        player.sendMessage(Component.text("✦ Cataclysme de la Sauterelle : Atterrissage surpuissant ! Les monstres dans 9 blocs sont propulsés et lourdement blessés !", NamedTextColor.GREEN));
    }

    // 4. DIABLE : Flaque de Braises (Liquéfaction invincible 3s, brûle les monstres)
    private void triggerDiableLavaPuddle(Player player) {
        UUID uuid = player.getUniqueId();
        diablePuddleActive.add(uuid);

        Location startLoc = player.getLocation();
        World world = startLoc.getWorld();
        if (world == null) return;

        player.setInvulnerable(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 70, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 65, 1, false, false, false));

        world.spawnParticle(Particle.LAVA, startLoc.clone().add(0, 0.3, 0), 25, 0.4, 0.1, 0.4, 0.05);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, startLoc.clone().add(0, 0.3, 0), 20, 0.5, 0.2, 0.5, 0.05);
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, startLoc.clone().add(0, 0.2, 0), 15, 0.3, 0.1, 0.3, 0.05);
        world.playSound(startLoc, Sound.BLOCK_LAVA_EXTINGUISH, 1.2f, 0.6f);
        world.playSound(startLoc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);

        player.sendActionBar(Component.text("🔥 FLAQUE DE BRAISES ACTIVE (Invincible 3s) ! 🔥", NamedTextColor.RED, TextDecoration.BOLD));

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!player.isOnline() || ticks >= 60) {
                    diablePuddleActive.remove(uuid);
                    if (player.isOnline()) {
                        player.setInvulnerable(false);
                        player.removePotionEffect(PotionEffectType.INVISIBILITY);
                        Location exitLoc = player.getLocation();
                        World w = exitLoc.getWorld();
                        if (w != null) {
                            w.spawnParticle(Particle.EXPLOSION, exitLoc.clone().add(0, 1.0, 0), 3, 0.4, 0.5, 0.4, 0.05);
                            w.spawnParticle(Particle.FLAME, exitLoc.clone().add(0, 1.0, 0), 30, 0.5, 0.6, 0.5, 0.08);
                            w.spawnParticle(Particle.LAVA, exitLoc.clone().add(0, 0.5, 0), 15, 0.4, 0.3, 0.4, 0.05);
                            w.playSound(exitLoc, Sound.ENTITY_BLAZE_DEATH, 1.0f, 1.4f);
                            w.playSound(exitLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.6f);
                        }
                        player.sendActionBar(Component.text("✦ Vous reprenez forme physique !", NamedTextColor.GOLD, TextDecoration.BOLD));
                    }
                    cancel();
                    return;
                }

                ticks += 3;
                Location loc = player.getLocation();
                World w = loc.getWorld();
                if (w == null) return;

                for (int deg = 0; deg < 360; deg += 45) {
                    double rad = Math.toRadians(deg);
                    double dist = 0.8 + (ticks % 6 == 0 ? 0.4 : 0.0);
                    Location pLoc = loc.clone().add(Math.cos(rad) * dist, 0.1, Math.sin(rad) * dist);
                    w.spawnParticle(Particle.FLAME, pLoc, 2, 0.1, 0.05, 0.1, 0.02);
                    w.spawnParticle(Particle.LAVA, pLoc, 1, 0.05, 0.05, 0.05, 0);
                }
                w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0, 0.1, 0), 2, 0.3, 0.05, 0.3, 0.02);

                if (ticks % 6 == 0) {
                    w.playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 0.8f, 1.1f);
                    w.playSound(loc, Sound.BLOCK_LAVA_POP, 0.7f, 1.3f);
                }

                for (Entity entity : w.getNearbyEntities(loc, 2.5, 1.5, 2.5)) {
                    if (entity instanceof LivingEntity target && !target.equals(player)) {
                        if (necroMinions.contains(target.getUniqueId())) continue;

                        target.setFireTicks(100);
                        target.damage(4.0, player);
                        w.spawnParticle(Particle.SMALL_FLAME, target.getLocation().add(0, 0.5, 0), 5, 0.2, 0.2, 0.2, 0.05);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    // 5. NÉCROMANCIEN : Détonation Putride (Fait exploser un serviteur pour turbo dégâts)
    private void triggerNecromancerMinionExplosion(Player player) {
        UUID masterId = player.getUniqueId();
        Location pLoc = player.getLocation();

        Monster targetMinion = null;
        double closestDistSq = Double.MAX_VALUE;

        for (Map.Entry<UUID, UUID> entry : minionToMaster.entrySet()) {
            if (entry.getValue().equals(masterId)) {
                Entity ent = Bukkit.getEntity(entry.getKey());
                if (ent instanceof Monster minion && minion.isValid()) {
                    double dSq = minion.getLocation().distanceSquared(pLoc);
                    if (dSq < closestDistSq) {
                        closestDistSq = dSq;
                        targetMinion = minion;
                    }
                }
            }
        }

        if (targetMinion == null) {
            player.sendActionBar(Component.text("✖ Aucun serviteur actif à faire exploser !", NamedTextColor.RED, TextDecoration.BOLD));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        abilityCooldowns.put(masterId, System.currentTimeMillis() + 12_000L);

        Location mLoc = targetMinion.getLocation();
        World world = mLoc.getWorld();
        UUID minionId = targetMinion.getUniqueId();

        targetMinion.remove();
        necroMinions.remove(minionId);
        minionToMaster.remove(minionId);

        if (world == null) return;

        world.spawnParticle(Particle.EXPLOSION_EMITTER, mLoc.clone().add(0, 1.0, 0), 2);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, mLoc.clone().add(0, 1.0, 0), 50, 1.0, 1.0, 1.0, 0.1);
        world.spawnParticle(Particle.SQUID_INK, mLoc.clone().add(0, 1.0, 0), 30, 0.8, 0.8, 0.8, 0.08);
        world.spawnParticle(Particle.WITCH, mLoc.clone().add(0, 1.0, 0), 30, 0.9, 0.9, 0.9, 0.05);

        world.playSound(mLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.6f);
        world.playSound(mLoc, Sound.ENTITY_WITHER_DEATH, 0.9f, 1.6f);
        world.playSound(mLoc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 0.5f);

        int count = 0;
        for (Entity entity : world.getNearbyEntities(mLoc, 7.5, 4.0, 7.5)) {
            if (entity instanceof LivingEntity target && !target.equals(player)) {
                if (necroMinions.contains(target.getUniqueId())) continue;

                target.damage(25.0, player);

                Vector kb = target.getLocation().toVector().subtract(mLoc.toVector()).normalize().multiply(1.3).setY(0.55);
                target.setVelocity(kb);

                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1, false, true, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, false, true, true));
                count++;
            }
        }

        player.sendActionBar(Component.text("☠ TURBO DÉTONATION : Serviteur sacrifié (" + count + " cibles anéanties) ! ☠", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        player.sendMessage(Component.text("✦ Détonation Putride : Votre serviteur a été sacrifié dans une formidable déflagration nécrotique !", NamedTextColor.DARK_PURPLE));
    }

    // 6. ARCHER : Pluie de Flèches (Déluge de flèches sur la zone ciblée)
    private void triggerArcherArrowRain(Player player) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) return;

        RayTraceResult result = world.rayTraceBlocks(eye, eye.getDirection(), 28.0, FluidCollisionMode.NEVER, true);
        Location targetCenter;
        if (result != null && result.getHitPosition() != null) {
            targetCenter = result.getHitPosition().toLocation(world);
        } else {
            targetCenter = eye.clone().add(eye.getDirection().multiply(15.0));
            targetCenter.setY(world.getHighestBlockYAt(targetCenter));
        }

        world.playSound(eye, Sound.ITEM_CROSSBOW_SHOOT, 1.2f, 0.7f);
        world.playSound(eye, Sound.ENTITY_ARROW_SHOOT, 1.4f, 0.5f);
        try {
            world.playSound(targetCenter, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.2f, 1.5f);
        } catch (Throwable ignored) {}

        for (int deg = 0; deg < 360; deg += 20) {
            double rad = Math.toRadians(deg);
            Location pLoc = targetCenter.clone().add(Math.cos(rad) * 4.0, 0.2, Math.sin(rad) * 4.0);
            world.spawnParticle(Particle.ENCHANTED_HIT, pLoc, 4, 0.1, 0.1, 0.1, 0.05);
            world.spawnParticle(Particle.CRIT, pLoc, 2, 0.05, 0.05, 0.05, 0.02);
        }

        player.sendActionBar(Component.text("🏹 PLUIE DE FLÈCHES DÉCLENCHÉE ! 🏹", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("✦ Pluie de Flèches : Un déluge de flèches s'abat sur la zone ciblée !", NamedTextColor.YELLOW));

        new BukkitRunnable() {
            int wave = 0;
            @Override
            public void run() {
                if (wave++ >= 6) {
                    cancel();
                    return;
                }

                for (int i = 0; i < 4; i++) {
                    double offsetX = (Math.random() - 0.5) * 8.0;
                    double offsetZ = (Math.random() - 0.5) * 8.0;
                    Location spawnLoc = targetCenter.clone().add(offsetX, 13.0 + Math.random() * 2.0, offsetZ);

                    Arrow arrow = world.spawnArrow(spawnLoc, new Vector((Math.random() - 0.5) * 0.1, -1.8, (Math.random() - 0.5) * 0.1), 1.8f, 12.0f);
                    arrow.setShooter(player);
                    arrow.setDamage(8.0);
                    arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                    arrow.getPersistentDataContainer().set(arrowRainKey, PersistentDataType.BYTE, (byte) 1);
                    arrow.setCritical(true);

                    world.spawnParticle(Particle.CRIT, spawnLoc, 3, 0.1, 0.1, 0.1, 0.05);
                }
                world.playSound(targetCenter, Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.2f);
            }
        }.runTaskTimer(plugin, 4L, 4L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArrowRainHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow && arrow.getPersistentDataContainer().has(arrowRainKey, PersistentDataType.BYTE)) {
            Location loc = arrow.getLocation();
            World w = loc.getWorld();
            if (w != null) {
                w.spawnParticle(Particle.CRIT, loc, 5, 0.2, 0.2, 0.2, 0.05);
            }
            Bukkit.getScheduler().runTaskLater(plugin, arrow::remove, 1L);
        }
    }
}

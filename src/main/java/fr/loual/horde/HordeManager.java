package fr.loual.horde;

import fr.loual.customminerals.items.Cuprite;
import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

public class HordeManager {

    public enum State {
        INACTIVE,
        STARTING,
        WAVE_IN_PROGRESS,
        WAVE_CLEARED,
        VICTORY,
        DEFEAT
    }

    public static final NamespacedKey MOB_KEY = new NamespacedKey("horde", "mob_type");
    public static final NamespacedKey KAMIKAZE_KEY = new NamespacedKey("horde", "kamikaze");
    public static final NamespacedKey NECRO_KEY = new NamespacedKey("horde", "necromancer");
    public static final NamespacedKey BOSS_KEY = new NamespacedKey("horde", "boss");

    public static final int ARENA_X = 10000;
    public static final int ARENA_Y = 120;
    public static final int ARENA_Z = 10000;
    public static final int ARENA_RADIUS = 18;

    private static boolean arenaAlreadyGenerated = false;

    private final NewAdventurePlugin plugin;
    private State state = State.INACTIVE;
    private Location centerLocation;
    private int currentWave = 0;
    private int defeatCountdown = 0;
    private final Set<UUID> activeMobs = new HashSet<>();
    private final Set<UUID> activeParticipants = new HashSet<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private LivingEntity bossEntity = null;
    private BossBar bossBar = null;
    private BukkitTask hordeTask = null;
    private int totalWaveMobs = 0;
    private int bossSkillCooldown = 0;

    public HordeManager(NewAdventurePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isHordeActive() {
        return state != State.INACTIVE;
    }

    public State getState() {
        return state;
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public boolean isParticipant(UUID uuid) {
        return activeParticipants.contains(uuid);
    }

    public void removeParticipant(UUID uuid) {
        activeParticipants.remove(uuid);
    }

    public Location getReturnLocation(UUID uuid) {
        return returnLocations.get(uuid);
    }

    public void clearReturnLocation(UUID uuid) {
        returnLocations.remove(uuid);
    }

    public boolean isInArena(Location loc) {
        if (loc == null || loc.getWorld() == null || centerLocation == null) return false;
        if (!loc.getWorld().equals(centerLocation.getWorld())) return false;
        return Math.abs(loc.getX() - ARENA_X) <= (ARENA_RADIUS + 8) &&
                Math.abs(loc.getZ() - ARENA_Z) <= (ARENA_RADIUS + 8) &&
                loc.getY() >= (ARENA_Y - 5) && loc.getY() <= (ARENA_Y + 30);
    }

    public void ensureArenaBuilt(World world) {
        if (world == null) return;

        Block centerFloor = world.getBlockAt(ARENA_X, ARENA_Y, ARENA_Z);
        if (arenaAlreadyGenerated && centerFloor.getType() == Material.RESPAWN_ANCHOR) {
            return;
        }

        // Préchargement des chunks de l'arène
        int minChunkX = (ARENA_X - ARENA_RADIUS - 2) >> 4;
        int maxChunkX = (ARENA_X + ARENA_RADIUS + 2) >> 4;
        int minChunkZ = (ARENA_Z - ARENA_RADIUS - 2) >> 4;
        int maxChunkZ = (ARENA_Z + ARENA_RADIUS + 2) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                world.loadChunk(cx, cz, true);
            }
        }

        // Construction du sol double épaisseur et de l'arène
        for (int dx = -ARENA_RADIUS; dx <= ARENA_RADIUS; dx++) {
            for (int dz = -ARENA_RADIUS; dz <= ARENA_RADIUS; dz++) {
                int x = ARENA_X + dx;
                int z = ARENA_Z + dz;

                // Sous-sol sécurisé à ARENA_Y - 1 (Bedrock)
                world.getBlockAt(x, ARENA_Y - 1, z).setType(Material.BEDROCK, false);

                // Sol principal à ARENA_Y
                Block floorBlock = world.getBlockAt(x, ARENA_Y, z);
                if (dx == 0 && dz == 0) {
                    floorBlock.setType(Material.RESPAWN_ANCHOR, false);
                } else if ((Math.abs(dx) % 5 == 0 && Math.abs(dz) % 5 == 0)) {
                    floorBlock.setType(Material.SEA_LANTERN, false);
                } else if ((Math.abs(dx) + Math.abs(dz)) % 3 == 0) {
                    floorBlock.setType(Material.GILDED_BLACKSTONE, false);
                } else {
                    floorBlock.setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
                }

                // Dégagement intérieur généreux et murs d'enceinte (Y = ARENA_Y + 1 à ARENA_Y + 14)
                boolean isPerimeter = Math.abs(dx) == ARENA_RADIUS || Math.abs(dz) == ARENA_RADIUS;
                for (int dy = 1; dy <= 14; dy++) {
                    Block block = world.getBlockAt(x, ARENA_Y + dy, z);
                    if (isPerimeter) {
                        if (dy <= 8) {
                            if ((dy == 3 || dy == 4) && Math.abs(dx) % 3 != 0 && Math.abs(dz) % 3 != 0) {
                                block.setType(Material.IRON_BARS, false);
                            } else {
                                block.setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
                            }
                        } else {
                            block.setType(Material.AIR, false);
                        }
                    } else {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }

        arenaAlreadyGenerated = true;
    }

    public boolean joinArena(Player player) {
        if (!isHordeActive()) {
            player.sendMessage(Component.text("Aucune invasion de la Horde n'est active pour le moment.", NamedTextColor.RED));
            return false;
        }
        if (isInArena(player.getLocation())) {
            player.sendMessage(Component.text("Vous êtes déjà dans l'Arène !", NamedTextColor.YELLOW));
            return false;
        }
        returnLocations.put(player.getUniqueId(), player.getLocation().clone());
        activeParticipants.add(player.getUniqueId());
        player.teleportAsync(centerLocation).thenAccept(success -> {
            if (success && player.isOnline()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 4));
                player.playSound(centerLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                player.sendMessage(Component.text("✦ Vous rejoignez l'Arène des Damnés ! Combattez pour votre survie !", NamedTextColor.GOLD, TextDecoration.BOLD));
            }
        });
        return true;
    }

    public boolean leaveArena(Player player) {
        activeParticipants.remove(player.getUniqueId());
        Location ret = returnLocations.remove(player.getUniqueId());
        if (ret != null) {
            player.teleportAsync(ret).thenAccept(s -> {
                player.sendMessage(Component.text("✦ Vous avez quitté l'Arène et êtes retourné à votre point de départ.", NamedTextColor.YELLOW));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            });
            return true;
        } else if (isInArena(player.getLocation())) {
            Location spawn = player.getWorld().getSpawnLocation();
            player.teleportAsync(spawn).thenAccept(s -> {
                player.sendMessage(Component.text("✦ Vous avez quitté l'Arène.", NamedTextColor.YELLOW));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            });
            return true;
        }
        return false;
    }

    public void teleportParticipantsBack() {
        for (Map.Entry<UUID, Location> entry : returnLocations.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                p.teleportAsync(entry.getValue()).thenAccept(s -> {
                    p.sendMessage(Component.text("✦ Vous avez été retéléporté à votre position d'origine.", NamedTextColor.GREEN));
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                });
            }
        }
        returnLocations.clear();
        activeParticipants.clear();
    }

    public void handleHordeDefeat() {
        this.state = State.DEFEAT;
        this.defeatCountdown = 0;
        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Component.text("☠ =================================================== ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("✦ DÉFAITE : LA HORDE DES DAMNÉS A TRIOMPHÉ ! ✦", NamedTextColor.RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("Tous les combattants ont succombé dans l'Arène...", NamedTextColor.GRAY));
        Bukkit.broadcast(Component.text("☠ =================================================== ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.empty());

        if (centerLocation != null && centerLocation.getWorld() != null) {
            centerLocation.getWorld().setStorm(false);
            centerLocation.getWorld().setThundering(false);
        }
        cleanUp();
    }

    public void startHorde(Player initiator, Location location) {
        if (isHordeActive()) {
            if (initiator != null) {
                initiator.sendMessage(Component.text("Une Horde des Damnés est déjà en cours dans le monde !", NamedTextColor.RED));
            }
            return;
        }

        World world = (initiator != null) ? initiator.getWorld() : ((location != null && location.getWorld() != null) ? location.getWorld() : Bukkit.getWorlds().get(0));
        if (world == null) return;

        ensureArenaBuilt(world);

        this.centerLocation = new Location(world, ARENA_X + 0.5, ARENA_Y + 1.0, ARENA_Z + 0.5, 0f, 0f);
        this.state = State.STARTING;
        this.currentWave = 0;
        this.defeatCountdown = 0;
        this.activeMobs.clear();
        this.bossEntity = null;
        this.returnLocations.clear();
        this.activeParticipants.clear();

        world.setStorm(true);
        world.setThundering(true);
        world.setWeatherDuration(20 * 60 * 15); // 15 min d'orage

        // Téléportation asynchrone sécurisée de l'initiateur et des compagnons proches
        if (initiator != null && initiator.isOnline()) {
            Location orig = initiator.getLocation().clone();
            returnLocations.put(initiator.getUniqueId(), orig);
            activeParticipants.add(initiator.getUniqueId());

            for (Player near : orig.getWorld().getPlayers()) {
                if (!near.equals(initiator) && near.getLocation().distance(orig) <= 8.0) {
                    returnLocations.put(near.getUniqueId(), near.getLocation().clone());
                    activeParticipants.add(near.getUniqueId());
                    near.teleportAsync(centerLocation).thenAccept(success -> {
                        if (success && near.isOnline()) {
                            near.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 4));
                            near.sendMessage(Component.text("✦ Vous avez été entraîné dans l'Arène avec " + initiator.getName() + " !", NamedTextColor.GOLD));
                        }
                    });
                }
            }

            initiator.teleportAsync(centerLocation).thenAccept(success -> {
                if (success && initiator.isOnline()) {
                    initiator.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 4));
                    initiator.playSound(centerLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                }
            });
        }

        // Annonce globale
        String initiatorName = (initiator != null) ? initiator.getName() : "Un mystérieux rituel";
        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Component.text("☠ =================================================== ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("✦ ALERTE DU CASINO : L'INVASION DE LA HORDE A DÉBUTÉ ! ✦", NamedTextColor.RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("L'armée des morts-vivants déferle suite au défi de ", NamedTextColor.GOLD)
                .append(Component.text(initiatorName, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" !", NamedTextColor.GOLD)));
        Bukkit.broadcast(Component.text("Les combattants sont téléportés dans l'Arène des Damnés ! Tapez ", NamedTextColor.YELLOW)
                .append(Component.text("/horde join", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" pour prêter main-forte !", NamedTextColor.YELLOW)));
        Bukkit.broadcast(Component.text("☠ =================================================== ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.empty());

        // Sons et effets
        for (Player p : world.getPlayers()) {
            if (isInArena(p.getLocation())) {
                p.playSound(centerLocation, Sound.EVENT_RAID_HORN, SoundCategory.HOSTILE, 2.0f, 0.8f);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
                p.showTitle(Title.title(
                        Component.text("☠ L'ARÈNE DES DAMNÉS ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                        Component.text("Préparez-vous au combat...", NamedTextColor.GOLD),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))
                ));
            }
        }

        // Foudre d'ambiance (sans dégât)
        world.strikeLightningEffect(centerLocation.clone().add(2, 0, 2));
        world.strikeLightningEffect(centerLocation.clone().add(-2, 0, -2));

        // Création de la BossBar
        bossBar = BossBar.bossBar(
                Component.text("☠ Horde des Damnés : Préparation de l'assaut... ☠", NamedTextColor.RED, TextDecoration.BOLD),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.NOTCHED_10
        );

        // Lancement de la boucle périodique (1 seconde)
        startHordeTask();

        // Début de la Vague 1 après 5 secondes
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state == State.STARTING) {
                nextWave();
            }
        }, 100L);
    }

    private void startHordeTask() {
        if (hordeTask != null) hordeTask.cancel();

        hordeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isHordeActive()) {
                    cancel();
                    return;
                }

                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void tick() {
        if (centerLocation == null || centerLocation.getWorld() == null) return;
        World world = centerLocation.getWorld();

        // 1. Mise à jour de la visibilité de la BossBar
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isInArena(p.getLocation())) {
                p.showBossBar(bossBar);
            } else {
                p.hideBossBar(bossBar);
            }
        }

        // 2. Vérifier si des combattants vivants sont encore présents dans l'Arène
        if (state == State.WAVE_IN_PROGRESS || state == State.WAVE_CLEARED) {
            boolean anyPlayerAlive = false;
            for (UUID uuid : new ArrayList<>(activeParticipants)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline() && !p.isDead()) {
                    if (isInArena(p.getLocation())) {
                        anyPlayerAlive = true;
                        break;
                    }
                }
            }

            // Ne déclarer défaite que si tous les participants enregistrés sont absents/morts pendant au moins 4 secondes
            if (!anyPlayerAlive && !activeParticipants.isEmpty()) {
                defeatCountdown++;
                if (defeatCountdown >= 4) {
                    handleHordeDefeat();
                    return;
                }
            } else {
                defeatCountdown = 0;
            }
        }

        // 3. Nettoyage des entités mortes ou invalides
        activeMobs.removeIf(uuid -> {
            Entity e = Bukkit.getEntity(uuid);
            return e == null || e.isDead() || !e.isValid();
        });

        // 4. Gestion de la vague en cours
        if (state == State.WAVE_IN_PROGRESS) {
            if (currentWave < 4) {
                int remaining = activeMobs.size();
                float progress = totalWaveMobs > 0 ? (float) remaining / (float) totalWaveMobs : 0f;
                progress = Math.max(0f, Math.min(1f, progress));
                bossBar.progress(progress);
                bossBar.name(Component.text("☠ Vague " + currentWave + "/4 : " + remaining + " monstres restants ☠", NamedTextColor.RED, TextDecoration.BOLD));

                // Si plus aucun monstre dans la vague -> Vague suivante
                if (remaining == 0) {
                    state = State.WAVE_CLEARED;
                    onWaveCleared();
                } else {
                    // Particules kamikaze et sons
                    tickSpecialMobs();
                }
            } else if (currentWave == 4) {
                // Vague de Boss
                if (bossEntity == null || bossEntity.isDead() || !bossEntity.isValid()) {
                    completeHordeVictory();
                } else {
                    double hp = bossEntity.getHealth();
                    AttributeInstance maxAttr = bossEntity.getAttribute(Attribute.MAX_HEALTH);
                    double maxHp = (maxAttr != null) ? maxAttr.getValue() : 300.0;
                    float progress = (float) Math.max(0f, Math.min(1f, hp / maxHp));
                    bossBar.progress(progress);
                    bossBar.name(Component.text("☠ LE TITAN PUTRÉFIÉ : " + (int) hp + " / " + (int) maxHp + " HP ☠", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));

                    tickBossSkills();
                }
            }
        }
    }

    private void tickSpecialMobs() {
        for (UUID uuid : new ArrayList<>(activeMobs)) {
            Entity entity = Bukkit.getEntity(uuid);
            if (!(entity instanceof Zombie zombie) || !zombie.isValid()) continue;

            // Kamikaze
            if (zombie.getPersistentDataContainer().has(KAMIKAZE_KEY, PersistentDataType.BYTE)) {
                zombie.getWorld().spawnParticle(Particle.SMOKE, zombie.getLocation().add(0, 1.2, 0), 4, 0.1, 0.1, 0.1, 0.02);
                zombie.getWorld().spawnParticle(Particle.FLAME, zombie.getLocation().add(0, 1.2, 0), 2, 0.1, 0.1, 0.1, 0.01);

                Player nearest = findNearestPlayer(zombie.getLocation(), 2.8);
                if (nearest != null) {
                    // Explose !
                    Location loc = zombie.getLocation();
                    loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
                    loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.0f);
                    // Dégâts sans casser les blocs
                    loc.getWorld().createExplosion(loc, 3.2f, false, false);
                    zombie.remove();
                    activeMobs.remove(uuid);
                }
            }

            // Nécromancien
            if (zombie.getPersistentDataContainer().has(NECRO_KEY, PersistentDataType.BYTE)) {
                zombie.getWorld().spawnParticle(Particle.WITCH, zombie.getLocation().add(0, 1.5, 0), 3, 0.2, 0.3, 0.2, 0.02);
            }
        }
    }

    private void tickBossSkills() {
        if (bossEntity == null || !bossEntity.isValid()) return;
        bossSkillCooldown++;

        // Particules permanentes autour du Boss
        Location bLoc = bossEntity.getLocation();
        bLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, bLoc.clone().add(0, 1.0, 0), 6, 0.5, 0.8, 0.5, 0.02);

        if (bossSkillCooldown >= 8) { // Toutes les 8 secondes
            bossSkillCooldown = 0;
            int skill = (int) (Math.random() * 3);

            if (skill == 0) {
                // Compétence 1 : Onde Sismique
                bLoc.getWorld().playSound(bLoc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.5f, 0.6f);
                bLoc.getWorld().playSound(bLoc, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.5f, 0.8f);
                bLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, bLoc.clone().add(0, 0.5, 0), 10, 2.0, 0.2, 2.0, 0.1);
                bLoc.getWorld().spawnParticle(Particle.BLOCK, bLoc, 40, 2.5, 0.2, 2.5, Material.DIRT.createBlockData());

                for (Entity e : bossEntity.getNearbyEntities(8.0, 4.0, 8.0)) {
                    if (e instanceof Player p) {
                        Vector vec = p.getLocation().toVector().subtract(bLoc.toVector()).normalize().multiply(1.3).setY(0.7);
                        p.setVelocity(vec);
                        p.damage(6.0, bossEntity);
                        p.sendMessage(Component.text("✦ Le Titan Putréfié frappe le sol avec fracas !", NamedTextColor.RED));
                    }
                }
            } else if (skill == 1) {
                // Compétence 2 : Éveil des Ombres (Invoque 3 zombies)
                bLoc.getWorld().playSound(bLoc, Sound.ENTITY_WITHER_AMBIENT, 1.2f, 1.4f);
                bLoc.getWorld().spawnParticle(Particle.SOUL, bLoc.clone().add(0, 1.0, 0), 25, 1.0, 0.5, 1.0, 0.05);

                for (int i = 0; i < 3; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double dist = 2.5 + Math.random() * 3.0;
                    Location spawnLoc = bLoc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
                    spawnMinion(spawnLoc);
                }
            } else {
                // Compétence 3 : Brume Putride (Poison & Wither)
                bLoc.getWorld().playSound(bLoc, Sound.BLOCK_BREWING_STAND_BREW, 1.5f, 0.5f);
                bLoc.getWorld().spawnParticle(Particle.ENTITY_EFFECT, bLoc.clone().add(0, 1.0, 0), 50, 4.0, 1.0, 4.0, Color.fromRGB(75, 0, 130));

                for (Entity e : bossEntity.getNearbyEntities(6.0, 3.0, 6.0)) {
                    if (e instanceof Player p) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 0));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0));
                        p.sendMessage(Component.text("✦ Une brume mortelle émane du Titan Putréfié !", NamedTextColor.DARK_PURPLE));
                    }
                }
            }
        }
    }

    private void onWaveCleared() {
        World world = centerLocation.getWorld();
        if (world == null) return;

        world.playSound(centerLocation, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.2f);
        bossBar.name(Component.text("✔ Vague " + currentWave + " terrassée ! Préparation de la suite...", NamedTextColor.GREEN, TextDecoration.BOLD));

        for (Player p : world.getPlayers()) {
            if (isInArena(p.getLocation())) {
                p.sendMessage(Component.text("✦ [Horde] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("La vague " + currentWave + " a été repoussée avec succès !", NamedTextColor.GREEN)));
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state == State.WAVE_CLEARED) {
                nextWave();
            }
        }, 80L); // 4 secondes de répit
    }

    public void nextWave() {
        currentWave++;
        if (currentWave > 4) {
            completeHordeVictory();
            return;
        }

        state = State.WAVE_IN_PROGRESS;
        activeMobs.clear();

        World world = centerLocation.getWorld();
        if (world == null) return;

        if (currentWave < 4) {
            bossBar.color(BossBar.Color.RED);
            bossBar.name(Component.text("☠ Vague " + currentWave + "/4 en cours... ☠", NamedTextColor.RED, TextDecoration.BOLD));
            spawnWave(currentWave);
        } else {
            // Vague 4 : Boss !
            bossBar.color(BossBar.Color.PURPLE);
            bossBar.name(Component.text("☠ LE TITAN PUTRÉFIÉ ARRIVE ! ☠", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            spawnBoss();
        }
    }

    private void spawnWave(int wave) {
        World world = centerLocation.getWorld();
        if (world == null) return;

        switch (wave) {
            case 1 -> {
                // Vague 1 : 14 Éclaireurs
                totalWaveMobs = 14;
                for (int i = 0; i < totalWaveMobs; i++) {
                    Location loc = getRandomSpawnLocation(8.0, 15.0);
                    Zombie z = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
                    setupScout(z);
                    activeMobs.add(z.getUniqueId());
                }
            }
            case 2 -> {
                // Vague 2 : 8 Briseurs de Siège + 4 Kamikazes
                totalWaveMobs = 12;
                for (int i = 0; i < 8; i++) {
                    Location loc = getRandomSpawnLocation(8.0, 15.0);
                    Zombie z = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
                    setupBreaker(z);
                    activeMobs.add(z.getUniqueId());
                }
                for (int i = 0; i < 4; i++) {
                    Location loc = getRandomSpawnLocation(9.0, 15.0);
                    Zombie z = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
                    setupKamikaze(z);
                    activeMobs.add(z.getUniqueId());
                }
            }
            case 3 -> {
                // Vague 3 : 7 Gardes d'Élite + 3 Nécromanciens
                totalWaveMobs = 10;
                for (int i = 0; i < 7; i++) {
                    Location loc = getRandomSpawnLocation(8.0, 15.0);
                    Zombie z = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
                    setupElite(z);
                    activeMobs.add(z.getUniqueId());
                }
                for (int i = 0; i < 3; i++) {
                    Location loc = getRandomSpawnLocation(9.0, 15.0);
                    Zombie z = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
                    setupNecromancer(z);
                    activeMobs.add(z.getUniqueId());
                }
            }
        }
    }

    private void spawnBoss() {
        World world = centerLocation.getWorld();
        if (world == null) return;

        Location bossLoc = centerLocation.clone().add(0, 1, 0);
        world.strikeLightningEffect(bossLoc);
        world.playSound(bossLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.7f);

        Zombie boss = (Zombie) world.spawnEntity(bossLoc, EntityType.ZOMBIE);
        boss.customName(Component.text("☠ LE TITAN PUTRÉFIÉ ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);

        // Attributs colossaux
        AttributeInstance hpAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(300.0);
            boss.setHealth(300.0);
        }

        AttributeInstance scaleAttr = boss.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(1.65); // Géant imposant !
        }

        AttributeInstance knockAttr = boss.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockAttr != null) {
            knockAttr.setBaseValue(0.9);
        }

        AttributeInstance speedAttr = boss.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(0.28);
        }

        AttributeInstance dmgAttr = boss.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmgAttr != null) {
            dmgAttr.setBaseValue(12.0);
        }

        // Équipement du Boss
        var inv = boss.getEquipment();
        if (inv != null) {
            inv.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            inv.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            inv.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            inv.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            inv.setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
            inv.setHelmetDropChance(0f);
            inv.setChestplateDropChance(0f);
            inv.setLeggingsDropChance(0f);
            inv.setBootsDropChance(0f);
            inv.setItemInMainHandDropChance(0f);
        }

        boss.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "boss");
        boss.getPersistentDataContainer().set(BOSS_KEY, PersistentDataType.BYTE, (byte) 1);

        this.bossEntity = boss;
        this.activeMobs.add(boss.getUniqueId());
    }

    private void spawnMinion(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        double xCoord = Math.max(ARENA_X - ARENA_RADIUS + 2, Math.min(ARENA_X + ARENA_RADIUS - 2, loc.getX()));
        double zCoord = Math.max(ARENA_Z - ARENA_RADIUS + 2, Math.min(ARENA_Z + ARENA_RADIUS - 2, loc.getZ()));
        Location spawnLoc = new Location(world, xCoord, ARENA_Y + 1.0, zCoord);
        Zombie z = (Zombie) world.spawnEntity(spawnLoc, EntityType.ZOMBIE);
        z.customName(Component.text("Âme Damnée", NamedTextColor.GRAY));
        z.setCustomNameVisible(false);
        z.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "minion");
        world.spawnParticle(Particle.BLOCK, spawnLoc, 15, 0.4, 0.2, 0.4, Material.SOUL_SAND.createBlockData());
    }

    private void setupScout(Zombie z) {
        z.customName(Component.text("Éclaireur des Damnés", NamedTextColor.RED));
        z.setCustomNameVisible(true);
        z.setRemoveWhenFarAway(false);
        z.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1));
        var eq = z.getEquipment();
        if (eq != null) {
            ItemStack helm = new ItemStack(Material.LEATHER_HELMET);
            LeatherArmorMeta meta = (LeatherArmorMeta) helm.getItemMeta();
            if (meta != null) {
                meta.setColor(Color.RED);
                helm.setItemMeta(meta);
            }
            eq.setHelmet(helm);
            eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            eq.setHelmetDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }
        z.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "scout");
    }

    private void setupBreaker(Zombie z) {
        z.customName(Component.text("Briseur de Siège", NamedTextColor.GOLD));
        z.setCustomNameVisible(true);
        z.setRemoveWhenFarAway(false);
        var eq = z.getEquipment();
        if (eq != null) {
            eq.setHelmet(new ItemStack(Material.IRON_HELMET));
            eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            eq.setItemInMainHand(new ItemStack(Material.IRON_AXE));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }
        z.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "breaker");
    }

    private void setupKamikaze(Zombie z) {
        z.customName(Component.text("☠ Zombie Explosif ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        z.setCustomNameVisible(true);
        z.setRemoveWhenFarAway(false);
        z.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1));
        var eq = z.getEquipment();
        if (eq != null) {
            eq.setHelmet(new ItemStack(Material.TNT));
            eq.setHelmetDropChance(0f);
        }
        z.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "kamikaze");
        z.getPersistentDataContainer().set(KAMIKAZE_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    private void setupElite(Zombie z) {
        z.customName(Component.text("Garde d'Élite Corrompu", NamedTextColor.AQUA, TextDecoration.BOLD));
        z.setCustomNameVisible(true);
        z.setRemoveWhenFarAway(false);
        var eq = z.getEquipment();
        if (eq != null) {
            eq.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
            eq.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            eq.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }
        z.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "elite");
    }

    private void setupNecromancer(Zombie z) {
        z.customName(Component.text("✦ Nécromancien des Abysses ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        z.setCustomNameVisible(true);
        z.setRemoveWhenFarAway(false);
        var eq = z.getEquipment();
        if (eq != null) {
            eq.setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
            eq.setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
            eq.setItemInMainHand(new ItemStack(Material.SPLASH_POTION));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }
        z.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "necromancer");
        z.getPersistentDataContainer().set(NECRO_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    private Location getRandomSpawnLocation(double minRadius, double maxRadius) {
        World world = centerLocation.getWorld();
        double angle = Math.random() * Math.PI * 2;
        double dist = minRadius + Math.random() * (maxRadius - minRadius);
        dist = Math.min(dist, ARENA_RADIUS - 2.0);
        double x = centerLocation.getX() + Math.cos(angle) * dist;
        double z = centerLocation.getZ() + Math.sin(angle) * dist;
        return new Location(world, x, ARENA_Y + 1.0, z);
    }

    private Player findNearestPlayer(Location loc, double radius) {
        Player nearest = null;
        double minD = radius * radius;
        for (Player p : loc.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(loc);
            if (d <= minD) {
                minD = d;
                nearest = p;
            }
        }
        return nearest;
    }

    public void completeHordeVictory() {
        this.state = State.VICTORY;
        World world = centerLocation.getWorld();
        if (world != null) {
            world.setStorm(false);
            world.setThundering(false);

            // Annonce victorieuse
            Bukkit.broadcast(Component.empty());
            Bukkit.broadcast(Component.text("★ =================================================== ★", NamedTextColor.GOLD, TextDecoration.BOLD));
            Bukkit.broadcast(Component.text("✦ VICTOIRE ÉPIQUE : LA HORDE DES DAMNÉS A ÉTÉ ÉLIMINÉE ! ✦", NamedTextColor.GREEN, TextDecoration.BOLD));
            Bukkit.broadcast(Component.text("Le Titan Putréfié est tombé. Le Coffre Ancestral du Triomphe est apparu !", NamedTextColor.YELLOW));
            Bukkit.broadcast(Component.text("Vous disposez de 45 secondes pour piller le coffre avant d'être retéléporté (ou tapez /horde leave) !", NamedTextColor.GOLD));
            Bukkit.broadcast(Component.text("★ =================================================== ★", NamedTextColor.GOLD, TextDecoration.BOLD));
            Bukkit.broadcast(Component.empty());

            // Feux d'artifice
            for (int i = 0; i < 5; i++) {
                final int delay = i * 8;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Location fLoc = centerLocation.clone().add((Math.random() - 0.5) * 8, 2, (Math.random() - 0.5) * 8);
                    Firework fw = world.spawn(fLoc, Firework.class);
                    var fm = fw.getFireworkMeta();
                    fm.addEffect(FireworkEffect.builder()
                            .withColor(Color.fromRGB(255, 215, 0), Color.RED, Color.PURPLE)
                            .withFade(Color.YELLOW, Color.WHITE)
                            .with(FireworkEffect.Type.BALL_LARGE)
                            .trail(true)
                            .build());
                    fm.setPower(1);
                    fw.setFireworkMeta(fm);
                }, delay);
            }

            // Génération du Coffre de récompense
            spawnRewardChest(centerLocation);

            // Orbes d'XP
            for (int i = 0; i < 15; i++) {
                ExperienceOrb orb = (ExperienceOrb) world.spawnEntity(centerLocation.clone().add((Math.random() - 0.5) * 3, 1, (Math.random() - 0.5) * 3), EntityType.EXPERIENCE_ORB);
                orb.setExperience(35);
            }
        }

        // Retéléportation et nettoyage après 45s (900 ticks)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state == State.VICTORY) {
                if (centerLocation != null) {
                    centerLocation.getBlock().setType(Material.AIR);
                }
                cleanUp();
            }
        }, 900L);
    }

    private void spawnRewardChest(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        Block block = world.getBlockAt(loc);
        block.setType(Material.CHEST);
        if (block.getState() instanceof Chest chest) {
            var inv = chest.getInventory();
            inv.clear();

            // 1. Arme Légendaire du Boss
            inv.setItem(13, HordeItems.getTitanSoulSlicer());

            // 2. Cuprites (6 Lingots)
            inv.setItem(11, Cuprite.create(plugin, 6));

            // 3. Débris Antiques
            inv.setItem(15, new ItemStack(Material.ANCIENT_DEBRIS, 2));

            // 4. Pomme d'or enchantée
            inv.setItem(12, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));

            // 5. Diamants
            inv.setItem(14, new ItemStack(Material.DIAMOND, 8));

            // Particules au-dessus du coffre
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0.5, 1.2, 0.5), 40, 0.4, 0.4, 0.4, 0.1);
            world.playSound(loc, Sound.BLOCK_CHEST_OPEN, 1.0f, 0.8f);
        }
    }

    public void stopHorde(boolean force) {
        if (!isHordeActive()) return;
        this.state = State.INACTIVE;
        cleanUp();
        if (force) {
            Bukkit.broadcast(Component.text("[Horde] L'événement a été interrompu par un administrateur.", NamedTextColor.RED));
        }
    }

    public void cleanup() {
        cleanUp();
    }

    public void cleanUp() {
        this.state = State.INACTIVE;
        if (hordeTask != null) {
            hordeTask.cancel();
            hordeTask = null;
        }

        for (UUID uuid : activeMobs) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        activeMobs.clear();

        if (bossEntity != null && bossEntity.isValid()) {
            bossEntity.remove();
            bossEntity = null;
        }

        if (bossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bossBar);
            }
            bossBar = null;
        }

        teleportParticipantsBack();
    }
}

package fr.loual.horde;

import fr.loual.customminerals.items.Cuprite;
import fr.loual.customminerals.items.CupriteBlock;
import fr.loual.customminerals.items.ReinforcedCupriteBlock;
import fr.loual.customminerals.items.CupriteHammer;
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
    public static final NamespacedKey VORTEX_KEY = new NamespacedKey("horde", "vortex");
    public static final NamespacedKey FROST_KEY = new NamespacedKey("horde", "frost");
    public static final NamespacedKey SUPER_KAMIKAZE_KEY = new NamespacedKey("horde", "super_kamikaze");
    public static final NamespacedKey INFERNAL_BLAZE_KEY = new NamespacedKey("horde", "infernal_blaze");

    public static final String HORDE_WORLD_NAME = "horde_arena";
    public static final int ARENA_X = 0;
    public static final int ARENA_Y = 100;
    public static final int ARENA_Z = 0;
    public static final int ARENA_RADIUS = 24;

    private static boolean arenaAlreadyGenerated = false;

    private final NewAdventurePlugin plugin;
    private State state = State.INACTIVE;
    private HordeTier currentTier = HordeTier.INGOT;
    private Location centerLocation;
    private int currentWave = 0;
    private int defeatCountdown = 0;
    private final Set<UUID> activeMobs = new HashSet<>();
    private final Set<UUID> activeParticipants = new HashSet<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private LivingEntity bossEntity = null;
    private BossBar bossBar = null;
    private BukkitTask hordeTask = null;
    private final List<BukkitTask> waveTasks = new ArrayList<>();
    private boolean allPacksSpawned = false;
    private int totalWaveMobs = 0;
    private int bossSkillCooldown = 0;

    public HordeManager(NewAdventurePlugin plugin) {
        this.plugin = plugin;
    }

    public World getOrCreateHordeWorld() {
        World world = Bukkit.getWorld(HORDE_WORLD_NAME);
        if (world == null) {
            WorldCreator creator = new WorldCreator(HORDE_WORLD_NAME);
            creator.generator(new VoidChunkGenerator());
            creator.environment(World.Environment.NORMAL);
            creator.generateStructures(false);
            world = creator.createWorld();
        }
        if (world != null) {
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.MOB_GRIEFING, false);
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
            world.setTime(18000);
            world.setDifficulty(Difficulty.HARD);
        }
        return world;
    }

    public World getHordeWorld() {
        return Bukkit.getWorld(HORDE_WORLD_NAME);
    }

    public boolean isHordeWorld(World world) {
        return world != null && world.getName().equalsIgnoreCase(HORDE_WORLD_NAME);
    }

    public boolean isHordeActive() {
        return state != State.INACTIVE;
    }

    public State getState() {
        return state;
    }

    public HordeTier getCurrentTier() {
        return currentTier;
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

    public Location getAndClearReturnLocation(UUID uuid) {
        return returnLocations.remove(uuid);
    }

    public boolean isInArena(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!isHordeWorld(loc.getWorld())) return false;
        return Math.abs(loc.getX() - ARENA_X) <= (ARENA_RADIUS + 8) &&
                Math.abs(loc.getZ() - ARENA_Z) <= (ARENA_RADIUS + 8) &&
                loc.getY() >= (ARENA_Y - 5) && loc.getY() <= (ARENA_Y + 30);
    }

    public void ensureArenaBuilt(World world) {
        if (world == null) return;

        Block centerFloor = world.getBlockAt(ARENA_X, ARENA_Y, ARENA_Z);
        Block outerCheck = world.getBlockAt(ARENA_X + ARENA_RADIUS, ARENA_Y + 1, ARENA_Z);
        if (arenaAlreadyGenerated && centerFloor.getType() == Material.RESPAWN_ANCHOR && outerCheck.getType() == Material.POLISHED_BLACKSTONE_BRICKS) {
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
        } else if (isHordeWorld(player.getWorld()) || isInArena(player.getLocation())) {
            Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
            player.teleportAsync(spawn).thenAccept(s -> {
                player.sendMessage(Component.text("✦ Vous avez quitté l'Arène.", NamedTextColor.YELLOW));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            });
            return true;
        }
        return false;
    }

    public void teleportParticipantsBack() {
        for (Map.Entry<UUID, Location> entry : new HashMap<>(returnLocations).entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline() && !p.isDead()) {
                p.teleportAsync(entry.getValue()).thenAccept(s -> {
                    p.sendMessage(Component.text("✦ Vous avez été retéléporté à votre position d'origine.", NamedTextColor.GREEN));
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                });
                returnLocations.remove(entry.getKey());
            }
            // Les joueurs actuellement morts conservent leur returnLocation pour onPlayerRespawn
        }
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
        startHorde(initiator, location, HordeTier.INGOT);
    }

    public void startHorde(Player initiator, Location location, HordeTier tier) {
        if (isHordeActive()) {
            if (initiator != null) {
                initiator.sendMessage(Component.text("Une Horde des Damnés est déjà en cours dans le monde !", NamedTextColor.RED));
            }
            return;
        }

        this.currentTier = (tier != null) ? tier : HordeTier.INGOT;

        // Récupération ou génération du monde vide dédié
        World hordeWorld = getOrCreateHordeWorld();
        if (hordeWorld == null) {
            if (initiator != null) {
                initiator.sendMessage(Component.text("Erreur : Impossible de charger le monde du Néant de l'Arène !", NamedTextColor.RED));
            }
            return;
        }

        ensureArenaBuilt(hordeWorld);

        this.centerLocation = new Location(hordeWorld, ARENA_X + 0.5, ARENA_Y + 1.0, ARENA_Z + 0.5, 0f, 0f);
        this.state = State.STARTING;
        this.currentWave = 0;
        this.defeatCountdown = 0;
        this.activeMobs.clear();
        for (BukkitTask t : waveTasks) {
            if (t != null) t.cancel();
        }
        this.waveTasks.clear();
        this.allPacksSpawned = false;
        this.bossEntity = null;
        this.returnLocations.clear();
        this.activeParticipants.clear();

        hordeWorld.setStorm(true);
        hordeWorld.setThundering(true);
        hordeWorld.setWeatherDuration(20 * 60 * 15); // 15 min d'orage dans le monde horde

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
                            near.sendMessage(Component.text("✦ Vous avez été entraîné dans l'Arène du Néant avec " + initiator.getName() + " !", NamedTextColor.GOLD));
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
        Bukkit.broadcast(Component.text("✦ ALERTE DU CASINO : " + currentTier.getDisplayName().toUpperCase() + " DE LA HORDE A DÉBUTÉ ! ✦", currentTier.getColor(), TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("L'armée des démons déferle suite au défi de ", NamedTextColor.GOLD)
                .append(Component.text(initiatorName, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" (Mise: " + currentTier.getRequiredItemName() + ") !", NamedTextColor.GOLD)));
        Bukkit.broadcast(Component.text("Les combattants sont téléportés dans l'Arène des Damnés ! Tapez ", NamedTextColor.YELLOW)
                .append(Component.text("/horde join", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" pour prêter main-forte !", NamedTextColor.YELLOW)));
        Bukkit.broadcast(Component.text("☠ =================================================== ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.empty());

        // Sons et effets
        for (Player p : hordeWorld.getPlayers()) {
            if (isInArena(p.getLocation())) {
                p.playSound(centerLocation, Sound.EVENT_RAID_HORN, SoundCategory.HOSTILE, 2.0f, 0.8f);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
                p.showTitle(Title.title(
                        Component.text("☠ " + currentTier.getDisplayName().toUpperCase() + " : L'ARÈNE ☠", currentTier.getColor(), TextDecoration.BOLD),
                        Component.text("Préparez-vous à l'assaut...", NamedTextColor.GOLD),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))
                ));
            }
        }

        // Foudre d'ambiance (sans dégât)
        hordeWorld.strikeLightningEffect(centerLocation.clone().add(2, 0, 2));
        hordeWorld.strikeLightningEffect(centerLocation.clone().add(-2, 0, -2));

        // Création de la BossBar
        bossBar = BossBar.bossBar(
                Component.text("☠ Horde (" + currentTier.getDisplayName() + ") : Préparation de l'assaut... ☠", currentTier.getColor(), TextDecoration.BOLD),
                1.0f,
                currentTier == HordeTier.REINFORCED_BLOCK ? BossBar.Color.PURPLE : (currentTier == HordeTier.BLOCK ? BossBar.Color.RED : BossBar.Color.YELLOW),
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

        // 0. Protection anti-chute dans le vide (Void rescue)
        for (Player p : world.getPlayers()) {
            if (p.isOnline() && !p.isDead() && p.getLocation().getY() < (ARENA_Y - 5)) {
                p.setVelocity(new Vector(0, 0, 0));
                p.setFallDistance(0);
                p.teleportAsync(centerLocation).thenAccept(s -> {
                    p.sendMessage(Component.text("⚠ Une barrière mystique vous propulse hors du vide abyssal !", NamedTextColor.RED, TextDecoration.BOLD));
                    p.playSound(centerLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                });
            }
        }

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
            for (Player p : world.getPlayers()) {
                if (p.isOnline() && !p.isDead() && isInArena(p.getLocation())) {
                    anyPlayerAlive = true;
                    break;
                }
            }

            // Ne déclarer défaite que si tous les participants sont absents/morts pendant au moins 4 secondes
            if (!anyPlayerAlive) {
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

                String tierLabel = currentTier == HordeTier.INGOT ? "" : "[" + currentTier.getDisplayName() + "] ";
                if (!allPacksSpawned) {
                    bossBar.name(Component.text("☠ " + tierLabel + "Vague " + currentWave + "/4 : " + remaining + " monstres (Renforts imminents...) ☠", currentTier.getColor(), TextDecoration.BOLD));
                } else {
                    bossBar.name(Component.text("☠ " + tierLabel + "Vague " + currentWave + "/4 : " + remaining + " monstres restants ☠", currentTier.getColor(), TextDecoration.BOLD));
                }

                // Si plus aucun monstre dans la vague et que tous les packs sont sortis -> Vague suivante
                if (remaining == 0 && allPacksSpawned) {
                    state = State.WAVE_CLEARED;
                    onWaveCleared();
                } else {
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

                    Component bName = bossEntity.customName() != null ? bossEntity.customName() : Component.text("LE BOSS");
                    bossBar.name(Component.text("☠ ", currentTier.getColor(), TextDecoration.BOLD)
                            .append(bName)
                            .append(Component.text(" : " + (int) hp + " / " + (int) maxHp + " HP ☠", currentTier.getColor(), TextDecoration.BOLD)));

                    tickBossSkills();
                }
            }
        }
    }

    private void tickSpecialMobs() {
        for (UUID uuid : new ArrayList<>(activeMobs)) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity == null || !entity.isValid()) continue;

            if (entity instanceof Zombie zombie) {
                // Kamikaze classique
                if (zombie.getPersistentDataContainer().has(KAMIKAZE_KEY, PersistentDataType.BYTE)) {
                    zombie.getWorld().spawnParticle(Particle.SMOKE, zombie.getLocation().add(0, 1.2, 0), 4, 0.1, 0.1, 0.1, 0.02);
                    zombie.getWorld().spawnParticle(Particle.FLAME, zombie.getLocation().add(0, 1.2, 0), 2, 0.1, 0.1, 0.1, 0.01);

                    Player nearest = findNearestPlayer(zombie.getLocation(), 2.8);
                    if (nearest != null) {
                        Location loc = zombie.getLocation();
                        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
                        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.0f);
                        loc.getWorld().createExplosion(loc, 3.2f, false, false);
                        zombie.remove();
                        activeMobs.remove(uuid);
                    }
                }

                // Super-Kamikaze (Explosion massive)
                if (zombie.getPersistentDataContainer().has(SUPER_KAMIKAZE_KEY, PersistentDataType.BYTE)) {
                    zombie.getWorld().spawnParticle(Particle.SMOKE, zombie.getLocation().add(0, 1.2, 0), 8, 0.2, 0.2, 0.2, 0.04);
                    zombie.getWorld().spawnParticle(Particle.FLAME, zombie.getLocation().add(0, 1.2, 0), 6, 0.2, 0.2, 0.2, 0.03);

                    Player nearest = findNearestPlayer(zombie.getLocation(), 3.0);
                    if (nearest != null) {
                        Location loc = zombie.getLocation();
                        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2);
                        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
                        loc.getWorld().createExplosion(loc, 4.5f, false, false);
                        zombie.remove();
                        activeMobs.remove(uuid);
                    }
                }

                // Zombie Vortex / Gravitationnel
                if (zombie.getPersistentDataContainer().has(VORTEX_KEY, PersistentDataType.BYTE)) {
                    zombie.getWorld().spawnParticle(Particle.PORTAL, zombie.getLocation().add(0, 1.2, 0), 5, 0.3, 0.3, 0.3, 0.1);
                    if (Math.random() < 0.25) { // Toutes les ~4 secondes
                        Location zLoc = zombie.getLocation();
                        zLoc.getWorld().playSound(zLoc, Sound.BLOCK_BEACON_POWER_SELECT, 1.3f, 1.8f);
                        zLoc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, zLoc.clone().add(0, 1, 0), 35, 1.5, 0.8, 1.5, 0.08);
                        for (Entity e : zombie.getNearbyEntities(9.0, 4.0, 9.0)) {
                            if (e instanceof Player p) {
                                Vector pull = zLoc.toVector().subtract(p.getLocation().toVector()).normalize().multiply(0.7).setY(0.25);
                                p.setVelocity(pull);
                                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                                p.sendActionBar(Component.text("⚠ Le Vortex magnétique vous attire vers le danger !", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
                            }
                        }
                    }
                }

                // Zombie Frimaire / Cryomancien
                if (zombie.getPersistentDataContainer().has(FROST_KEY, PersistentDataType.BYTE)) {
                    zombie.getWorld().spawnParticle(Particle.SNOWFLAKE, zombie.getLocation().add(0, 1.2, 0), 3, 0.2, 0.3, 0.2, 0.02);
                    for (Entity e : zombie.getNearbyEntities(3.5, 2.0, 3.5)) {
                        if (e instanceof Player p) {
                            p.setFreezeTicks(Math.max(p.getFreezeTicks(), 140));
                            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0));
                        }
                    }
                }

                // Nécromancien
                if (zombie.getPersistentDataContainer().has(NECRO_KEY, PersistentDataType.BYTE)) {
                    zombie.getWorld().spawnParticle(Particle.WITCH, zombie.getLocation().add(0, 1.5, 0), 3, 0.2, 0.3, 0.2, 0.02);
                }
            } else if (entity instanceof Blaze blaze) {
                // Blaze Infernal
                if (blaze.getPersistentDataContainer().has(INFERNAL_BLAZE_KEY, PersistentDataType.BYTE)) {
                    blaze.getWorld().spawnParticle(Particle.LAVA, blaze.getLocation().add(0, 0.8, 0), 2, 0.2, 0.2, 0.2, 0.01);
                    if (Math.random() < 0.3) {
                        Player nearest = findNearestPlayer(blaze.getLocation(), 16.0);
                        if (nearest != null) {
                            Vector dir = nearest.getLocation().add(0, 1.0, 0).toVector().subtract(blaze.getLocation().add(0, 1.0, 0).toVector()).normalize();
                            SmallFireball fireball = blaze.launchProjectile(SmallFireball.class, dir.multiply(0.85));
                            fireball.setShooter(blaze);
                            blaze.getWorld().playSound(blaze.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.2f);
                        }
                    }
                }
            } else if (entity instanceof WitherSkeleton ws) {
                // Faucheur d'âmes
                if (Math.random() < 0.25) {
                    ws.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, ws.getLocation().add(0, 1.2, 0), 2, 0.15, 0.2, 0.15, 0.01);
                }
            } else if (entity instanceof PiglinBrute pb) {
                // Bourreau enragé / démoniaque
                if (Math.random() < 0.2) {
                    pb.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, pb.getLocation().add(0, 2.0, 0), 1, 0.1, 0.1, 0.1, 0);
                }
            } else if (entity instanceof AbstractSkeleton skeleton) {
                // Squelette Pesteur
                String type = skeleton.getPersistentDataContainer().get(MOB_KEY, PersistentDataType.STRING);
                if ("plague_archer".equals(type)) {
                    skeleton.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, skeleton.getLocation().add(0, 1.2, 0), 4, 0.3, 0.4, 0.3, 0.02);
                }
            } else if (entity instanceof Spider spider) {
                // Veuve des tempêtes
                String type = spider.getPersistentDataContainer().get(MOB_KEY, PersistentDataType.STRING);
                if ("storm_spider".equals(type)) {
                    spider.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, spider.getLocation().add(0, 0.5, 0), 3, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }
    }

    private void tickBossSkills() {
        if (bossEntity == null || !bossEntity.isValid()) return;
        bossSkillCooldown++;

        Location bLoc = bossEntity.getLocation();
        bLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, bLoc.clone().add(0, 1.0, 0), 6, 0.5, 0.8, 0.5, 0.02);

        if (bossSkillCooldown >= 8) { // Toutes les 8 secondes
            bossSkillCooldown = 0;

            if (currentTier == HordeTier.INGOT) {
                // Compétences Tier 1 : Titan Putréfié
                int skill = (int) (Math.random() * 3);
                if (skill == 0) {
                    // Onde Sismique
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
                    // Éveil des Ombres
                    bLoc.getWorld().playSound(bLoc, Sound.ENTITY_WITHER_AMBIENT, 1.2f, 1.4f);
                    bLoc.getWorld().spawnParticle(Particle.SOUL, bLoc.clone().add(0, 1.0, 0), 25, 1.0, 0.5, 1.0, 0.05);
                    for (int i = 0; i < 3; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double dist = 2.5 + Math.random() * 3.0;
                        Location spawnLoc = bLoc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
                        spawnMinion(spawnLoc);
                    }
                } else {
                    // Brume Putride
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
            } else if (currentTier == HordeTier.BLOCK) {
                // Compétences Tier 2 : Général Inquisiteur
                int skill = (int) (Math.random() * 3);
                if (skill == 0) {
                    // Vortex & Écrasement Inquisiteur
                    bLoc.getWorld().playSound(bLoc, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.2f);
                    bLoc.getWorld().playSound(bLoc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.5f, 0.5f);
                    bLoc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, bLoc.clone().add(0, 1, 0), 50, 3.0, 1.5, 3.0, 0.1);
                    for (Entity e : bossEntity.getNearbyEntities(10.0, 5.0, 10.0)) {
                        if (e instanceof Player p) {
                            Vector pull = bLoc.toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.1).setY(0.4);
                            p.setVelocity(pull);
                            p.damage(7.0, bossEntity);
                            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                            p.sendMessage(Component.text("✦ Le Général Inquisiteur déploie une onde gravitationnelle !", NamedTextColor.RED));
                        }
                    }
                } else if (skill == 1) {
                    // Mâchoires des Abysses
                    bLoc.getWorld().playSound(bLoc, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.4f, 0.8f);
                    World w = bLoc.getWorld();
                    for (int i = 0; i < 8; i++) {
                        double angle = i * (Math.PI / 4.0);
                        Location fangLoc = bLoc.clone().add(Math.cos(angle) * 3.5, 0, Math.sin(angle) * 3.5);
                        w.spawn(fangLoc, EvokerFangs.class);
                    }
                    for (Player p : w.getPlayers()) {
                        if (isInArena(p.getLocation())) {
                            w.spawn(p.getLocation(), EvokerFangs.class);
                            p.sendMessage(Component.text("✦ Les mâchoires de l'Inquisition jaillissent du sol !", NamedTextColor.GOLD));
                        }
                    }
                } else {
                    // Invocations du Conseil (1 Frimaire + 1 Vortex)
                    bLoc.getWorld().playSound(bLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.5f);
                    spawnFrostZombies(bLoc.getWorld(), 1);
                    spawnVortexZombies(bLoc.getWorld(), 1);
                }
            } else {
                // Compétences Tier 3 : L'Archidémon de Cuprite
                int skill = (int) (Math.random() * 4);
                if (skill == 0) {
                    // Cataclysme Solaire (Pluie de Météores)
                    bLoc.getWorld().playSound(bLoc, Sound.ENTITY_GHAST_SHOOT, 1.5f, 0.6f);
                    for (Player p : bLoc.getWorld().getPlayers()) {
                        if (isInArena(p.getLocation())) {
                            for (int i = 0; i < 2; i++) {
                                Location dropLoc = p.getLocation().clone().add((Math.random() - 0.5) * 4, 9, (Math.random() - 0.5) * 4);
                                SmallFireball fb = bLoc.getWorld().spawn(dropLoc, SmallFireball.class);
                                fb.setVelocity(new Vector(0, -0.9, 0));
                                fb.setShooter(bossEntity);
                            }
                            p.sendMessage(Component.text("✦ Des météores de feu solaire s'abattent du ciel !", NamedTextColor.GOLD, TextDecoration.BOLD));
                        }
                    }
                } else if (skill == 1) {
                    // Nova Tellurique Infernale
                    bLoc.getWorld().playSound(bLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
                    bLoc.getWorld().playSound(bLoc, Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.7f);
                    bLoc.getWorld().spawnParticle(Particle.FLAME, bLoc.clone().add(0, 1, 0), 80, 4.0, 1.5, 4.0, 0.1);
                    bLoc.getWorld().spawnParticle(Particle.LAVA, bLoc.clone().add(0, 1, 0), 30, 2.5, 1.0, 2.5, 0.05);
                    for (Entity e : bossEntity.getNearbyEntities(10.0, 5.0, 10.0)) {
                        if (e instanceof Player p) {
                            Vector launch = p.getLocation().toVector().subtract(bLoc.toVector()).normalize().multiply(1.2).setY(0.9);
                            p.setVelocity(launch);
                            p.damage(10.0, bossEntity);
                            p.setFireTicks(100);
                            p.sendMessage(Component.text("✦ L'Archidémon libère une Nova Tellurique destructrice !", NamedTextColor.RED, TextDecoration.BOLD));
                        }
                    }
                } else if (skill == 2) {
                    // Faille Démoniaque (2 Blazes + 1 Super Kamikaze)
                    bLoc.getWorld().playSound(bLoc, Sound.ENTITY_BLAZE_DEATH, 1.2f, 0.6f);
                    spawnInfernalBlazes(bLoc.getWorld(), 2);
                    spawnSuperKamikazes(bLoc.getWorld(), 1);
                } else {
                    // Déchirement Spectral (Téléportation furtive)
                    Player target = findNearestPlayer(bLoc, 18.0);
                    if (target != null) {
                        Vector backDir = target.getLocation().getDirection().multiply(-1.5);
                        Location tpLoc = target.getLocation().clone().add(backDir);
                        tpLoc.setY(ARENA_Y + 1.0);
                        bossEntity.teleport(tpLoc);
                        bLoc.getWorld().playSound(tpLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.6f);
                        bLoc.getWorld().strikeLightningEffect(tpLoc);
                        target.damage(8.0, bossEntity);
                        target.sendMessage(Component.text("✦ L'Archidémon surgit dans votre dos avec violence !", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
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
        for (BukkitTask task : waveTasks) {
            if (task != null) task.cancel();
        }
        waveTasks.clear();
        allPacksSpawned = false;

        World world = centerLocation.getWorld();
        if (world == null) return;

        if (currentWave < 4) {
            bossBar.color(currentTier == HordeTier.REINFORCED_BLOCK ? BossBar.Color.PURPLE : (currentTier == HordeTier.BLOCK ? BossBar.Color.RED : BossBar.Color.YELLOW));
            bossBar.name(Component.text("☠ [" + currentTier.getDisplayName() + "] Vague " + currentWave + "/4 en cours... ☠", currentTier.getColor(), TextDecoration.BOLD));
            spawnWave(currentWave);
        } else {
            // Vague 4 : Boss !
            bossBar.color(currentTier == HordeTier.REINFORCED_BLOCK ? BossBar.Color.PURPLE : (currentTier == HordeTier.BLOCK ? BossBar.Color.RED : BossBar.Color.PURPLE));
            bossBar.name(Component.text("☠ L'ASSASSIN DES DAMNÉS ARRIVE ! ☠", currentTier.getColor(), TextDecoration.BOLD));
            spawnBoss();
        }
    }

    private void spawnWave(int wave) {
        World world = centerLocation.getWorld();
        if (world == null) return;

        if (currentTier == HordeTier.BLOCK) {
            spawnWaveBlock(world, wave);
        } else if (currentTier == HordeTier.REINFORCED_BLOCK) {
            spawnWaveReinforced(world, wave);
        } else {
            spawnWaveIngot(world, wave);
        }
    }

    private void spawnWaveIngot(World world, int wave) {
        switch (wave) {
            case 1 -> {
                // Vague 1 (Total : 24 mobs = 1.5x de 16)
                totalWaveMobs = 24;
                spawnScouts(world, 6);
                spawnArchers(world, 3);

                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 1) return;
                    announceReinforcements(world);
                    spawnScouts(world, 4);
                    spawnArchers(world, 3);
                    spawnSpiders(world, 3);
                }, 140L));

                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 1) return;
                    announceReinforcements(world);
                    spawnSpiders(world, 3);
                    spawnKamikazes(world, 2);
                    allPacksSpawned = true;
                }, 300L));
            }
            case 2 -> {
                // Vague 2 (Total : 24 mobs = 1.5x de 16)
                totalWaveMobs = 24;
                spawnBreakers(world, 5);
                spawnArchers(world, 4);

                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 2) return;
                    announceReinforcements(world);
                    spawnReapers(world, 3);
                    spawnKamikazes(world, 3);
                    spawnBreakers(world, 2);
                }, 160L));

                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 2) return;
                    announceReinforcements(world);
                    spawnReapers(world, 3);
                    spawnKamikazes(world, 2);
                    spawnBreakers(world, 2);
                    allPacksSpawned = true;
                }, 320L));
            }
            case 3 -> {
                // Vague 3 (Total : 24 mobs = 1.5x de 16)
                totalWaveMobs = 24;
                spawnElites(world, 5);
                spawnReapers(world, 4);

                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 3) return;
                    announceReinforcements(world);
                    spawnBrutes(world, 3);
                    spawnNecromancers(world, 3);
                    spawnElites(world, 2);
                }, 160L));

                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 3) return;
                    announceReinforcements(world);
                    spawnBrutes(world, 3);
                    spawnArchers(world, 2);
                    spawnKamikazes(world, 2);
                    allPacksSpawned = true;
                }, 320L));
            }
        }
    }

    private void spawnWaveBlock(World world, int wave) {
        switch (wave) {
            case 1 -> {
                // Total : 27 mobs (1.5x de 18)
                totalWaveMobs = 27;
                // Pack 1 (t=0s) : 6 Briseurs + 3 Squelettes Pesteurs
                spawnBreakers(world, 6);
                spawnPlagueArchers(world, 3);

                // Pack 2 (t=7s / 140 ticks) : 4 Éclaireurs + 3 Squelettes Pesteurs + 3 Zombies Vortex
                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 1) return;
                    announceReinforcements(world);
                    spawnScouts(world, 4);
                    spawnPlagueArchers(world, 3);
                    spawnVortexZombies(world, 3);
                }, 140L));

                // Pack 3 (t=15s / 300 ticks) : 5 Araignées + 3 Zombies Frimaires
                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 1) return;
                    announceReinforcements(world);
                    spawnSpiders(world, 5);
                    spawnFrostZombies(world, 3);
                    allPacksSpawned = true;
                }, 300L));
            }
            case 2 -> {
                // Total : 27 mobs (1.5x de 18)
                totalWaveMobs = 27;
                // Pack 1 (t=0s) : 5 Faucheurs + 4 Squelettes Pesteurs
                spawnReapers(world, 5);
                spawnPlagueArchers(world, 4);

                // Pack 2 (t=8s / 160 ticks) : 3 Bourreaux + 3 Kamikazes + 3 Zombies Vortex
                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 2) return;
                    announceReinforcements(world);
                    spawnBrutes(world, 3);
                    spawnKamikazes(world, 3);
                    spawnVortexZombies(world, 3);
                }, 160L));

                // Pack 3 (t=16s / 320 ticks) : 3 Faucheurs + 3 Zombies Frimaires + 3 Kamikazes
                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 2) return;
                    announceReinforcements(world);
                    spawnReapers(world, 3);
                    spawnFrostZombies(world, 3);
                    spawnKamikazes(world, 3);
                    allPacksSpawned = true;
                }, 320L));
            }
            case 3 -> {
                // Total : 27 mobs (1.5x de 18)
                totalWaveMobs = 27;
                // Pack 1 (t=0s) : 6 Gardes d'Élite + 3 Zombies Vortex
                spawnElites(world, 6);
                spawnVortexZombies(world, 3);

                // Pack 2 (t=8s / 160 ticks) : 4 Bourreaux + 3 Nécromanciens + 3 Zombies Frimaires
                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 3) return;
                    announceReinforcements(world);
                    spawnBrutes(world, 4);
                    spawnNecromancers(world, 3);
                    spawnFrostZombies(world, 3);
                }, 160L));

                // Pack 3 (t=16s / 320 ticks) : 3 Gardes d'Élite + 3 Squelettes Pesteurs + 2 Super-Kamikazes
                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 3) return;
                    announceReinforcements(world);
                    spawnElites(world, 3);
                    spawnPlagueArchers(world, 3);
                    spawnSuperKamikazes(world, 2);
                    allPacksSpawned = true;
                }, 320L));
            }
        }
    }

    private void spawnWaveReinforced(World world, int wave) {
        switch (wave) {
            case 1 -> {
                // Total : 30 mobs (1.5x de 20)
                totalWaveMobs = 30;
                // Pack 1 (t=0s) : 6 Faucheurs + 4 Squelettes Pesteurs
                spawnReapers(world, 6);
                spawnPlagueArchers(world, 4);

                // Pack 2 (t=7s / 140 ticks) : 4 Veuves des Tempêtes + 3 Blazes Infernaux + 3 Zombies Vortex
                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 1) return;
                    announceReinforcements(world);
                    spawnStormSpiders(world, 4);
                    spawnInfernalBlazes(world, 3);
                    spawnVortexZombies(world, 3);
                }, 140L));

                // Pack 3 (t=15s / 300 ticks) : 5 Bourreaux Démoniaques + 5 Zombies Frimaires
                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 1) return;
                    announceReinforcements(world);
                    spawnInfernalBrutes(world, 5);
                    spawnFrostZombies(world, 5);
                    allPacksSpawned = true;
                }, 300L));
            }
            case 2 -> {
                // Total : 30 mobs (1.5x de 20)
                totalWaveMobs = 30;
                // Pack 1 (t=0s) : 6 Gardes d'Élite + 3 Blazes Infernaux
                spawnElites(world, 6);
                spawnInfernalBlazes(world, 3);

                // Pack 2 (t=8s / 160 ticks) : 4 Bourreaux Démoniaques + 4 Super-Kamikazes + 3 Zombies Vortex
                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 2) return;
                    announceReinforcements(world);
                    spawnInfernalBrutes(world, 4);
                    spawnSuperKamikazes(world, 4);
                    spawnVortexZombies(world, 3);
                }, 160L));

                // Pack 3 (t=16s / 320 ticks) : 4 Veuves des Tempêtes + 3 Blazes Infernaux + 3 Squelettes Pesteurs
                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 2) return;
                    announceReinforcements(world);
                    spawnStormSpiders(world, 4);
                    spawnInfernalBlazes(world, 3);
                    spawnPlagueArchers(world, 3);
                    allPacksSpawned = true;
                }, 320L));
            }
            case 3 -> {
                // Total : 30 mobs (1.5x de 20)
                totalWaveMobs = 30;
                // Pack 1 (t=0s) : 6 Bourreaux Démoniaques + 4 Blazes Infernaux
                spawnInfernalBrutes(world, 6);
                spawnInfernalBlazes(world, 4);

                // Pack 2 (t=8s / 160 ticks) : 4 Nécromanciens + 3 Super-Kamikazes + 3 Zombies Vortex
                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 3) return;
                    announceReinforcements(world);
                    spawnNecromancers(world, 4);
                    spawnSuperKamikazes(world, 3);
                    spawnVortexZombies(world, 3);
                }, 160L));

                // Pack 3 (t=16s / 320 ticks) : 4 Bourreaux Démoniaques + 3 Veuves des Tempêtes + 3 Zombies Frimaires
                waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state != State.WAVE_IN_PROGRESS || currentWave != 3) return;
                    announceReinforcements(world);
                    spawnInfernalBrutes(world, 4);
                    spawnStormSpiders(world, 3);
                    spawnFrostZombies(world, 3);
                    allPacksSpawned = true;
                }, 320L));
            }
        }
    }

    private void spawnBoss() {
        World world = centerLocation.getWorld();
        if (world == null) return;

        if (currentTier == HordeTier.BLOCK) {
            spawnBossBlock(world);
        } else if (currentTier == HordeTier.REINFORCED_BLOCK) {
            spawnBossReinforced(world);
        } else {
            spawnBossIngot(world);
        }
    }

    private void spawnBossIngot(World world) {
        allPacksSpawned = true;

        Location bossLoc = centerLocation.clone().add(0, 1, 0);
        world.strikeLightningEffect(bossLoc);
        world.playSound(bossLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.7f);

        Zombie boss = (Zombie) world.spawnEntity(bossLoc, EntityType.ZOMBIE);
        boss.customName(Component.text("☠ LE TITAN PUTRÉFIÉ ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);

        AttributeInstance hpAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(300.0);
            boss.setHealth(300.0);
        }
        AttributeInstance scaleAttr = boss.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) scaleAttr.setBaseValue(1.65);
        AttributeInstance knockAttr = boss.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockAttr != null) knockAttr.setBaseValue(0.9);
        AttributeInstance speedAttr = boss.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.28);
        AttributeInstance dmgAttr = boss.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmgAttr != null) dmgAttr.setBaseValue(12.0);

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

        waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.WAVE_IN_PROGRESS || currentWave != 4) return;
            announceReinforcements(world);
            spawnElites(world, 3);
            spawnArchers(world, 3);
        }, 240L));

        waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.WAVE_IN_PROGRESS || currentWave != 4) return;
            announceReinforcements(world);
            spawnBrutes(world, 3);
            spawnKamikazes(world, 3);
        }, 520L));
    }

    private void spawnBossBlock(World world) {
        allPacksSpawned = true;

        Location bossLoc = centerLocation.clone().add(0, 1, 0);
        world.strikeLightningEffect(bossLoc);
        world.playSound(bossLoc, Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.6f);

        Zombie boss = (Zombie) world.spawnEntity(bossLoc, EntityType.ZOMBIE);
        boss.customName(Component.text("☠ LE GÉNÉRAL INQUISITEUR ☠", NamedTextColor.RED, TextDecoration.BOLD));
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);

        AttributeInstance hpAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(450.0);
            boss.setHealth(450.0);
        }
        AttributeInstance scaleAttr = boss.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) scaleAttr.setBaseValue(1.75);
        AttributeInstance knockAttr = boss.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockAttr != null) knockAttr.setBaseValue(0.95);
        AttributeInstance speedAttr = boss.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.30);
        AttributeInstance dmgAttr = boss.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmgAttr != null) dmgAttr.setBaseValue(16.0);

        var inv = boss.getEquipment();
        if (inv != null) {
            inv.setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
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

        waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.WAVE_IN_PROGRESS || currentWave != 4) return;
            announceReinforcements(world);
            spawnBrutes(world, 3);
            spawnPlagueArchers(world, 3);
            spawnVortexZombies(world, 2);
        }, 240L));

        waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.WAVE_IN_PROGRESS || currentWave != 4) return;
            announceReinforcements(world);
            spawnReapers(world, 3);
            spawnKamikazes(world, 3);
            spawnFrostZombies(world, 2);
        }, 520L));
    }

    private void spawnBossReinforced(World world) {
        allPacksSpawned = true;

        Location bossLoc = centerLocation.clone().add(0, 1, 0);
        world.strikeLightningEffect(bossLoc);
        world.strikeLightningEffect(bossLoc.clone().add(2, 0, 2));
        world.strikeLightningEffect(bossLoc.clone().add(-2, 0, -2));
        world.playSound(bossLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
        world.playSound(bossLoc, Sound.ENTITY_WITHER_DEATH, 1.5f, 0.8f);

        Zombie boss = (Zombie) world.spawnEntity(bossLoc, EntityType.ZOMBIE);
        boss.customName(Component.text("☠ L'ARCHIDÉMON DE CUPRITE ☠", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);

        AttributeInstance hpAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(650.0);
            boss.setHealth(650.0);
        }
        AttributeInstance scaleAttr = boss.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) scaleAttr.setBaseValue(1.85);
        AttributeInstance knockAttr = boss.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockAttr != null) knockAttr.setBaseValue(0.98);
        AttributeInstance speedAttr = boss.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.32);
        AttributeInstance dmgAttr = boss.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmgAttr != null) dmgAttr.setBaseValue(20.0);

        var inv = boss.getEquipment();
        if (inv != null) {
            inv.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            inv.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            inv.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            inv.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            inv.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
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

        waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.WAVE_IN_PROGRESS || currentWave != 4) return;
            announceReinforcements(world);
            spawnInfernalBlazes(world, 3);
            spawnInfernalBrutes(world, 3);
            spawnVortexZombies(world, 2);
        }, 240L));

        waveTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.WAVE_IN_PROGRESS || currentWave != 4) return;
            announceReinforcements(world);
            spawnSuperKamikazes(world, 3);
            spawnStormSpiders(world, 3);
            spawnInfernalBlazes(world, 2);
        }, 520L));
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

    private void setupShadowArcher(AbstractSkeleton s) {
        s.customName(Component.text("Rôdeur des Ombres", NamedTextColor.DARK_GRAY, TextDecoration.BOLD));
        s.setCustomNameVisible(true);
        s.setRemoveWhenFarAway(false);
        s.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0));
        var eq = s.getEquipment();
        if (eq != null) {
            ItemStack helm = new ItemStack(Material.LEATHER_HELMET);
            LeatherArmorMeta meta = (LeatherArmorMeta) helm.getItemMeta();
            if (meta != null) {
                meta.setColor(Color.BLACK);
                helm.setItemMeta(meta);
            }
            eq.setHelmet(helm);
            eq.setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
            eq.setItemInMainHand(new ItemStack(Material.BOW));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }
        s.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "archer");
    }

    private void setupSoulReaper(WitherSkeleton ws) {
        ws.customName(Component.text("Faucheur d'Âmes", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        ws.setCustomNameVisible(true);
        ws.setRemoveWhenFarAway(false);
        ws.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0));
        var eq = ws.getEquipment();
        if (eq != null) {
            eq.setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
            eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            eq.setChestplateDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }
        ws.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "reaper");
    }

    private void setupEnragedBrute(PiglinBrute pb) {
        pb.customName(Component.text("Bourreau Enragé", NamedTextColor.GOLD, TextDecoration.BOLD));
        pb.setCustomNameVisible(true);
        pb.setRemoveWhenFarAway(false);
        pb.setImmuneToZombification(true);

        AttributeInstance hpAttr = pb.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(40.0);
            pb.setHealth(40.0);
        }
        AttributeInstance speedAttr = pb.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(0.32);
        }
        AttributeInstance knockAttr = pb.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockAttr != null) {
            knockAttr.setBaseValue(0.5);
        }

        var eq = pb.getEquipment();
        if (eq != null) {
            eq.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
            eq.setItemInMainHand(new ItemStack(Material.GOLDEN_AXE));
            eq.setHelmetDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }
        pb.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "brute");
    }

    private void setupShadowSpider(Spider spider) {
        spider.customName(Component.text("Araignée des Ténèbres", NamedTextColor.DARK_RED));
        spider.setCustomNameVisible(true);
        spider.setRemoveWhenFarAway(false);

        AttributeInstance hpAttr = spider.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(24.0);
            spider.setHealth(24.0);
        }
        spider.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1));
        spider.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "spider");
    }

    private void setupVortexZombie(Zombie z) {
        z.customName(Component.text("⚡ Zombie du Vortex ⚡", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        z.setCustomNameVisible(true);
        z.setRemoveWhenFarAway(false);
        z.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1));
        var eq = z.getEquipment();
        if (eq != null) {
            ItemStack helm = new ItemStack(Material.LEATHER_HELMET);
            LeatherArmorMeta meta = (LeatherArmorMeta) helm.getItemMeta();
            if (meta != null) {
                meta.setColor(Color.PURPLE);
                helm.setItemMeta(meta);
            }
            eq.setHelmet(helm);
            ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
            LeatherArmorMeta cMeta = (LeatherArmorMeta) chest.getItemMeta();
            if (cMeta != null) {
                cMeta.setColor(Color.PURPLE);
                chest.setItemMeta(cMeta);
            }
            eq.setChestplate(chest);
            eq.setItemInMainHand(new ItemStack(Material.IRON_AXE));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }
        z.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "vortex_zombie");
        z.getPersistentDataContainer().set(VORTEX_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    private void setupFrostZombie(Zombie z) {
        z.customName(Component.text("❄ Zombie Frimaire ❄", NamedTextColor.AQUA, TextDecoration.BOLD));
        z.setCustomNameVisible(true);
        z.setRemoveWhenFarAway(false);
        var eq = z.getEquipment();
        if (eq != null) {
            eq.setHelmet(new ItemStack(Material.PACKED_ICE));
            ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
            LeatherArmorMeta cMeta = (LeatherArmorMeta) chest.getItemMeta();
            if (cMeta != null) {
                cMeta.setColor(Color.AQUA);
                chest.setItemMeta(cMeta);
            }
            eq.setChestplate(chest);
            eq.setItemInMainHand(new ItemStack(Material.DIAMOND_SHOVEL));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }
        z.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "frost");
        z.getPersistentDataContainer().set(FROST_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    private void setupSuperKamikaze(Zombie z) {
        z.customName(Component.text("💥 GIGA-KAMIKAZE 💥", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        z.setCustomNameVisible(true);
        z.setRemoveWhenFarAway(false);
        z.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 2));
        var eq = z.getEquipment();
        if (eq != null) {
            eq.setHelmet(new ItemStack(Material.TNT));
            eq.setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
        }
        z.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "super_kamikaze");
        z.getPersistentDataContainer().set(SUPER_KAMIKAZE_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    private void setupInfernalBlaze(Blaze b) {
        b.customName(Component.text("🔥 Blaze Infernal des Abysses 🔥", NamedTextColor.GOLD, TextDecoration.BOLD));
        b.setCustomNameVisible(true);
        b.setRemoveWhenFarAway(false);
        AttributeInstance hp = b.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(35.0);
            b.setHealth(35.0);
        }
        b.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "infernal_blaze");
        b.getPersistentDataContainer().set(INFERNAL_BLAZE_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    private void setupPlagueArcher(AbstractSkeleton s) {
        s.customName(Component.text("☠ Squelette Pestilentiel ☠", NamedTextColor.DARK_GREEN, TextDecoration.BOLD));
        s.setCustomNameVisible(true);
        s.setRemoveWhenFarAway(false);
        s.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1));
        var eq = s.getEquipment();
        if (eq != null) {
            ItemStack helm = new ItemStack(Material.LEATHER_HELMET);
            LeatherArmorMeta meta = (LeatherArmorMeta) helm.getItemMeta();
            if (meta != null) {
                meta.setColor(Color.GREEN);
                helm.setItemMeta(meta);
            }
            eq.setHelmet(helm);
            ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
            LeatherArmorMeta cMeta = (LeatherArmorMeta) chest.getItemMeta();
            if (cMeta != null) {
                cMeta.setColor(Color.GREEN);
                chest.setItemMeta(cMeta);
            }
            eq.setChestplate(chest);
            eq.setItemInMainHand(new ItemStack(Material.BOW));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }
        s.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "plague_archer");
    }

    private void setupInfernalBrute(PiglinBrute pb) {
        pb.customName(Component.text("👹 Bourreau Démoniaque 👹", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        pb.setCustomNameVisible(true);
        pb.setRemoveWhenFarAway(false);
        pb.setImmuneToZombification(true);

        AttributeInstance hpAttr = pb.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(60.0);
            pb.setHealth(60.0);
        }
        AttributeInstance speedAttr = pb.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(0.34);
        }
        AttributeInstance knockAttr = pb.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockAttr != null) {
            knockAttr.setBaseValue(0.7);
        }
        AttributeInstance dmgAttr = pb.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmgAttr != null) {
            dmgAttr.setBaseValue(14.0);
        }

        var eq = pb.getEquipment();
        if (eq != null) {
            eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            eq.setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
            eq.setHelmetDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }
        pb.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "infernal_brute");
    }

    private void setupStormSpider(Spider spider) {
        spider.customName(Component.text("⚡ Veuve des Tempêtes ⚡", NamedTextColor.AQUA, TextDecoration.BOLD));
        spider.setCustomNameVisible(true);
        spider.setRemoveWhenFarAway(false);

        AttributeInstance hpAttr = spider.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(35.0);
            spider.setHealth(35.0);
        }
        AttributeInstance speedAttr = spider.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(0.36);
        }
        spider.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1));
        spider.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, "storm_spider");
    }

    private void announceReinforcements(World world) {
        if (world == null || state != State.WAVE_IN_PROGRESS) return;
        for (Player p : world.getPlayers()) {
            if (isInArena(p.getLocation())) {
                p.playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.2f, 1.3f);
                p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 0.9f);
                p.sendActionBar(Component.text("⚠ Des renforts ennemis surgissent dans l'arène ! ⚠", NamedTextColor.RED, TextDecoration.BOLD));
            }
        }
    }

    private void playSpawnEffect(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.SMOKE, loc.clone().add(0, 0.5, 0), 10, 0.3, 0.5, 0.3, 0.05);
        world.spawnParticle(Particle.SOUL, loc.clone().add(0, 0.5, 0), 6, 0.3, 0.5, 0.3, 0.02);
    }

    private void spawnScouts(World world, int count) {
        for (int i = 0; i < count; i++) {
            Location loc = getDistributedSpawnLocation(i, count, 12.0, ARENA_RADIUS - 2.5);
            playSpawnEffect(loc);
            Zombie z = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
            setupScout(z);
            activeMobs.add(z.getUniqueId());
        }
    }

    private void spawnArchers(World world, int count) {
        for (int i = 0; i < count; i++) {
            Location loc = getDistributedSpawnLocation(i, count, 12.0, ARENA_RADIUS - 2.5);
            playSpawnEffect(loc);
            AbstractSkeleton s = (AbstractSkeleton) world.spawnEntity(loc, EntityType.SKELETON);
            setupShadowArcher(s);
            activeMobs.add(s.getUniqueId());
        }
    }

    private void spawnBreakers(World world, int count) {
        for (int i = 0; i < count; i++) {
            Location loc = getDistributedSpawnLocation(i, count, 12.0, ARENA_RADIUS - 2.5);
            playSpawnEffect(loc);
            Zombie z = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
            setupBreaker(z);
            activeMobs.add(z.getUniqueId());
        }
    }

    private void spawnKamikazes(World world, int count) {
        for (int i = 0; i < count; i++) {
            Location loc = getDistributedSpawnLocation(i, count, 12.0, ARENA_RADIUS - 2.5);
            playSpawnEffect(loc);
            Zombie z = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
            setupKamikaze(z);
            activeMobs.add(z.getUniqueId());
        }
    }

    private void spawnElites(World world, int count) {
        for (int i = 0; i < count; i++) {
            Location loc = getDistributedSpawnLocation(i, count, 12.0, ARENA_RADIUS - 2.5);
            playSpawnEffect(loc);
            Zombie z = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
            setupElite(z);
            activeMobs.add(z.getUniqueId());
        }
    }

    private void spawnNecromancers(World world, int count) {
        for (int i = 0; i < count; i++) {
            Location loc = getDistributedSpawnLocation(i, count, 12.0, ARENA_RADIUS - 2.5);
            playSpawnEffect(loc);
            Zombie z = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
            setupNecromancer(z);
            activeMobs.add(z.getUniqueId());
        }
    }

    private void spawnReapers(World world, int count) {
        for (int i = 0; i < count; i++) {
            Location loc = getDistributedSpawnLocation(i, count, 12.0, ARENA_RADIUS - 2.5);
            playSpawnEffect(loc);
            WitherSkeleton ws = (WitherSkeleton) world.spawnEntity(loc, EntityType.WITHER_SKELETON);
            setupSoulReaper(ws);
            activeMobs.add(ws.getUniqueId());
        }
    }

    private void spawnBrutes(World world, int count) {
        for (int i = 0; i < count; i++) {
            Location loc = getDistributedSpawnLocation(i, count, 12.0, ARENA_RADIUS - 2.5);
            playSpawnEffect(loc);
            PiglinBrute pb = (PiglinBrute) world.spawnEntity(loc, EntityType.PIGLIN_BRUTE);
            setupEnragedBrute(pb);
            activeMobs.add(pb.getUniqueId());
        }
    }

    private void spawnSpiders(World world, int count) {
        for (int i = 0; i < count; i++) {
            Location loc = getDistributedSpawnLocation(i, count, 12.0, ARENA_RADIUS - 2.5);
            playSpawnEffect(loc);
            Spider spider = (Spider) world.spawnEntity(loc, EntityType.SPIDER);
            setupShadowSpider(spider);
            activeMobs.add(spider.getUniqueId());
        }
    }

    private void spawnVortexZombies(World world, int count) {
        for (int i = 0; i < count; i++) {
            Location loc = getDistributedSpawnLocation(i, count, 12.0, ARENA_RADIUS - 2.5);
            playSpawnEffect(loc);
            Zombie z = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
            setupVortexZombie(z);
            activeMobs.add(z.getUniqueId());
        }
    }

    private void spawnFrostZombies(World world, int count) {
        for (int i = 0; i < count; i++) {
            Location loc = getDistributedSpawnLocation(i, count, 12.0, ARENA_RADIUS - 2.5);
            playSpawnEffect(loc);
            Zombie z = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
            setupFrostZombie(z);
            activeMobs.add(z.getUniqueId());
        }
    }

    private void spawnSuperKamikazes(World world, int count) {
        for (int i = 0; i < count; i++) {
            Location loc = getDistributedSpawnLocation(i, count, 12.0, ARENA_RADIUS - 2.5);
            playSpawnEffect(loc);
            Zombie z = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
            setupSuperKamikaze(z);
            activeMobs.add(z.getUniqueId());
        }
    }

    private void spawnInfernalBlazes(World world, int count) {
        for (int i = 0; i < count; i++) {
            Location loc = getDistributedSpawnLocation(i, count, 12.0, ARENA_RADIUS - 2.5);
            playSpawnEffect(loc);
            Blaze b = (Blaze) world.spawnEntity(loc, EntityType.BLAZE);
            setupInfernalBlaze(b);
            activeMobs.add(b.getUniqueId());
        }
    }

    private void spawnPlagueArchers(World world, int count) {
        for (int i = 0; i < count; i++) {
            Location loc = getDistributedSpawnLocation(i, count, 12.0, ARENA_RADIUS - 2.5);
            playSpawnEffect(loc);
            AbstractSkeleton s = (AbstractSkeleton) world.spawnEntity(loc, EntityType.SKELETON);
            setupPlagueArcher(s);
            activeMobs.add(s.getUniqueId());
        }
    }

    private void spawnInfernalBrutes(World world, int count) {
        for (int i = 0; i < count; i++) {
            Location loc = getDistributedSpawnLocation(i, count, 12.0, ARENA_RADIUS - 2.5);
            playSpawnEffect(loc);
            PiglinBrute pb = (PiglinBrute) world.spawnEntity(loc, EntityType.PIGLIN_BRUTE);
            setupInfernalBrute(pb);
            activeMobs.add(pb.getUniqueId());
        }
    }

    private void spawnStormSpiders(World world, int count) {
        for (int i = 0; i < count; i++) {
            Location loc = getDistributedSpawnLocation(i, count, 12.0, ARENA_RADIUS - 2.5);
            playSpawnEffect(loc);
            Spider spider = (Spider) world.spawnEntity(loc, EntityType.SPIDER);
            setupStormSpider(spider);
            activeMobs.add(spider.getUniqueId());
        }
    }

    private Location getRandomSpawnLocation(double minRadius, double maxRadius) {
        return getDistributedSpawnLocation(0, 1, minRadius, maxRadius);
    }

    private Location getDistributedSpawnLocation(int index, int totalCount, double minRadius, double maxRadius) {
        World world = centerLocation.getWorld();
        double baseAngle = (index * (2.0 * Math.PI / Math.max(1, totalCount))) + (Math.random() * 0.4);
        double jitter = (Math.random() - 0.5) * 0.35;
        double angle = baseAngle + jitter;

        double dist = minRadius + Math.random() * (maxRadius - minRadius);
        dist = Math.max(minRadius, Math.min(dist, ARENA_RADIUS - 2.5));

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

            // Annonce victorieuse selon le tier
            String bossDefeated;
            String chestDesc;
            int orbCount;
            int orbXp;

            if (currentTier == HordeTier.BLOCK) {
                bossDefeated = "Le Général Inquisiteur est tombé au combat.";
                chestDesc = "Le Trésor Héroïque de Cuprite est apparu !";
                orbCount = 25;
                orbXp = 50;
            } else if (currentTier == HordeTier.REINFORCED_BLOCK) {
                bossDefeated = "L'ARCHIDÉMON DE CUPRITE A ÉTÉ PULVÉRISÉ !";
                chestDesc = "Le Trésor Suprême de l'Apocalypse est apparu !";
                orbCount = 40;
                orbXp = 75;
            } else {
                bossDefeated = "Le Titan Putréfié est tombé.";
                chestDesc = "Le Coffre Ancestral du Triomphe est apparu !";
                orbCount = 15;
                orbXp = 35;
            }

            Bukkit.broadcast(Component.empty());
            Bukkit.broadcast(Component.text("★ =================================================== ★", currentTier.getColor(), TextDecoration.BOLD));
            Bukkit.broadcast(Component.text("✦ VICTOIRE ÉPIQUE : LA HORDE [" + currentTier.getDisplayName().toUpperCase() + "] A ÉTÉ VAINCUE ! ✦", NamedTextColor.GREEN, TextDecoration.BOLD));
            Bukkit.broadcast(Component.text(bossDefeated + " " + chestDesc, NamedTextColor.YELLOW));
            Bukkit.broadcast(Component.text("Vous disposez de 45 secondes pour piller le coffre avant d'être retéléporté (ou tapez /horde leave) !", NamedTextColor.GOLD));
            Bukkit.broadcast(Component.text("★ =================================================== ★", currentTier.getColor(), TextDecoration.BOLD));
            Bukkit.broadcast(Component.empty());

            // Feux d'artifice
            for (int i = 0; i < (currentTier == HordeTier.REINFORCED_BLOCK ? 9 : 5); i++) {
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
            for (int i = 0; i < orbCount; i++) {
                ExperienceOrb orb = (ExperienceOrb) world.spawnEntity(centerLocation.clone().add((Math.random() - 0.5) * 3, 1, (Math.random() - 0.5) * 3), EntityType.EXPERIENCE_ORB);
                orb.setExperience(orbXp);
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

            if (currentTier == HordeTier.BLOCK) {
                // Tier 2 (Bloc de Cuprite)
                // 1. Arme Légendaire Tier 2
                inv.setItem(13, HordeItems.getInquisitorWrathBlade());
                // 2. Marteau en Cuprite Tier 2 (4x4x4)
                inv.setItem(4, CupriteHammer.create(plugin, 2));
                // 3. 5 Blocs de Cuprite
                inv.setItem(11, CupriteBlock.create(plugin, 5));
                // 4. Lingots de Netherite
                inv.setItem(15, new ItemStack(Material.NETHERITE_INGOT, 3));
                // 5. Pommes d'or enchantées
                inv.setItem(12, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 2));
                // 6. Blocs de Diamant
                inv.setItem(14, new ItemStack(Material.DIAMOND_BLOCK, 2));
                // 7. Totem d'immortalité
                inv.setItem(22, new ItemStack(Material.TOTEM_OF_UNDYING, 1));
            } else if (currentTier == HordeTier.REINFORCED_BLOCK) {
                // Tier 3 (Bloc de Cuprite Renforcé) - Récompenses Divines / Extrêmes
                // 1. Arme Ultime Tier 3
                inv.setItem(13, HordeItems.getApocalypseClaymore());
                // 2. Marteau en Cuprite Tier 3 (5x5x5)
                inv.setItem(4, CupriteHammer.create(plugin, 3));
                // 3. 3 Blocs de Cuprite Renforcés
                inv.setItem(11, ReinforcedCupriteBlock.create(plugin, 3));
                // 4. Bloc de Netherite
                inv.setItem(15, new ItemStack(Material.NETHERITE_BLOCK, 1));
                // 5. Pommes de Notch
                inv.setItem(12, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4));
                // 6. Blocs de diamant
                inv.setItem(14, new ItemStack(Material.DIAMOND_BLOCK, 5));
                // 7. Totems d'immortalité
                inv.setItem(22, new ItemStack(Material.TOTEM_OF_UNDYING, 2));
                // 8. Cuprites bonus
                inv.setItem(10, Cuprite.create(plugin, 16));
            } else {
                // Tier 1 (Lingot de Cuprite)
                // 1. Arme Légendaire Tier 1
                inv.setItem(13, HordeItems.getTitanSoulSlicer());
                // 2. Cuprites (6 Lingots)
                inv.setItem(11, Cuprite.create(plugin, 6));
                // 3. Débris Antiques
                inv.setItem(15, new ItemStack(Material.ANCIENT_DEBRIS, 2));
                // 4. Pomme d'or enchantée
                inv.setItem(12, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
                // 5. Diamants
                inv.setItem(14, new ItemStack(Material.DIAMOND, 8));
            }

            // Particules au-dessus du coffre
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0.5, 1.2, 0.5), 60, 0.4, 0.4, 0.4, 0.1);
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

        for (BukkitTask task : waveTasks) {
            if (task != null) task.cancel();
        }
        waveTasks.clear();
        allPacksSpawned = false;

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

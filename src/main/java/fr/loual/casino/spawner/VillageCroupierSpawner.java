package fr.loual.casino.spawner;

import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class VillageCroupierSpawner implements Listener {

    private final NewAdventurePlugin plugin;
    private final NamespacedKey croupierKey;
    private final NamespacedKey structureCheckedKey;

    // Cache mémoire pour éviter les vérifications répétées sur le même village
    private final Set<String> processedVillages = new HashSet<>();

    public VillageCroupierSpawner(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.croupierKey = new NamespacedKey(plugin, "is_casino_croupier");
        this.structureCheckedKey = new NamespacedKey(plugin, "village_croupier_spawned");

        // Tâche périodique (toutes les 45 secondes) pour vérifier les villages autour des joueurs connectés
        Bukkit.getScheduler().runTaskTimer(plugin, this::scanVillagesAroundPlayers, 100L, 20L * 45L);
    }

    public static Villager spawnCroupier(Location loc, NewAdventurePlugin plugin) {
        World world = loc.getWorld();
        if (world == null) return null;

        return world.spawn(loc, Villager.class, villager -> {
            villager.customName(Component.text("♠ Croupier de Blackjack ♠", NamedTextColor.GOLD, TextDecoration.BOLD));
            villager.setCustomNameVisible(true);
            villager.setProfession(Villager.Profession.CLERIC);
            villager.setVillagerType(Villager.Type.PLAINS);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setCollidable(false);
            villager.setSilent(true);
            villager.setRemoveWhenFarAway(false);
            villager.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_casino_croupier"), PersistentDataType.BYTE, (byte) 1);
        });
    }

    public boolean isCroupier(Entity entity) {
        if (!(entity instanceof Villager villager)) return false;
        Byte tag = villager.getPersistentDataContainer().get(croupierKey, PersistentDataType.BYTE);
        return tag != null && tag == 1;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        checkAndSpawnInChunk(event.getChunk());
    }

    private void scanVillagesAroundPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Chunk chunk = player.getLocation().getChunk();
            checkAndSpawnInChunk(chunk);
        }
    }

    public void checkAndSpawnInChunk(Chunk chunk) {
        try {
            Collection<GeneratedStructure> structures = chunk.getStructures();
            if (structures == null || structures.isEmpty()) return;

            for (GeneratedStructure genStructure : structures) {
                if (genStructure == null || genStructure.getStructure() == null) continue;

                org.bukkit.NamespacedKey key = org.bukkit.Registry.STRUCTURE.getKey(genStructure.getStructure());
                if (key == null) continue;

                String structKey = key.asString().toLowerCase();
                if (!structKey.contains("village")) continue;

                // Identifiant unique du village basé sur le centre de sa BoundingBox
                BoundingBox box = genStructure.getBoundingBox();
                int centerX = (int) box.getCenterX();
                int centerZ = (int) box.getCenterZ();
                String villageId = chunk.getWorld().getName() + "_" + (centerX / 64) + "_" + (centerZ / 64);

                if (processedVillages.contains(villageId)) {
                    continue;
                }

                // Vérifier si le village a déjà été marqué
                if (genStructure.getPersistentDataContainer().has(structureCheckedKey)) {
                    processedVillages.add(villageId);
                    continue;
                }

                Location spawnLoc = findVillageSpawnLocation(chunk.getWorld(), box);
                if (spawnLoc != null) {
                    // S'assurer qu'un croupier n'est pas déjà présent dans les parages
                    if (!hasCroupierNearby(spawnLoc, 60)) {
                        spawnCroupier(spawnLoc, plugin);
                        plugin.getLogger().info("Croupier de Blackjack généré automatiquement dans le village à " +
                                spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ());
                    }
                    genStructure.getPersistentDataContainer().set(structureCheckedKey, PersistentDataType.BYTE, (byte) 1);
                    processedVillages.add(villageId);
                }
            }
        } catch (Throwable ignored) {
            // Sécurité si les structures ne sont pas encore calculées
        }
    }

    private Location findVillageSpawnLocation(World world, BoundingBox box) {
        int centerX = (int) box.getCenterX();
        int centerZ = (int) box.getCenterZ();

        // 1. Chercher si une Cloche (Bell - centre du village) se trouve aux alentours
        int searchRadius = 25;
        for (int x = centerX - searchRadius; x <= centerX + searchRadius; x += 2) {
            for (int z = centerZ - searchRadius; z <= centerZ + searchRadius; z += 2) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                int highestY = world.getHighestBlockYAt(x, z);
                for (int y = Math.max(world.getMinHeight(), highestY - 8); y <= Math.min(world.getMaxHeight() - 1, highestY + 4); y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.BELL) {
                        // Placer le croupier juste à côté de la cloche sur un bloc stable
                        Block ground = block.getRelative(1, -1, 0);
                        if (ground.getType().isSolid()) {
                            return block.getLocation().add(1.5, 0, 0.5);
                        }
                        return block.getLocation().add(0.5, 0, 1.5);
                    }
                }
            }
        }

        // 2. Si pas de cloche trouvée, trouver le bloc le plus haut au centre
        if (world.isChunkLoaded(centerX >> 4, centerZ >> 4)) {
            Block highest = world.getHighestBlockAt(centerX, centerZ);
            Block above = highest.getRelative(0, 1, 0);
            return highest.getLocation().add(0.5, 1.0, 0.5);
        }

        return null;
    }

    private boolean hasCroupierNearby(Location loc, double radius) {
        World world = loc.getWorld();
        if (world == null) return false;

        Collection<Entity> nearby = world.getNearbyEntities(loc, radius, radius, radius, ent -> ent instanceof Villager);
        for (Entity ent : nearby) {
            if (isCroupier(ent)) {
                return true;
            }
        }
        return false;
    }
}

package fr.loual.customminerals.listeners;

import fr.loual.newadventure.NewAdventurePlugin;
import fr.loual.customminerals.items.CupriteAxe;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class TreeMiningListener implements Listener {

    private final NewAdventurePlugin plugin;
    private final Set<Block> currentlyBreaking = new HashSet<>();

    private static final int MAX_LOGS = 250;
    private static final int MAX_LEAVES = 400;
    private static final int MAX_LEAF_DISTANCE = 4;

    public TreeMiningListener(NewAdventurePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block startBlock = event.getBlock();

        // Éviter la récursion si on est déjà en train de casser des blocs
        if (currentlyBreaking.contains(startBlock)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!CupriteAxe.isCupriteAxe(plugin, tool)) {
            return;
        }

        // Si le joueur est accroupi (sneak), il peut miner un seul bloc normalement
        if (player.isSneaking()) {
            return;
        }

        Material startType = startBlock.getType();
        if (!Tag.LOGS.isTagged(startType)) {
            return;
        }

        String woodFamily = getWoodFamily(startType);
        int minY = startBlock.getY() - 1;

        // 1. Détection des troncs connectés : Bois vers Bois uniquement
        Set<Block> logsToBreak = new HashSet<>();
        Queue<Block> logQueue = new ArrayDeque<>();

        logQueue.add(startBlock);
        logsToBreak.add(startBlock);

        while (!logQueue.isEmpty() && logsToBreak.size() < MAX_LOGS) {
            Block current = logQueue.poll();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        Block neighbor = current.getRelative(dx, dy, dz);
                        if (logsToBreak.contains(neighbor)) continue;

                        // Ne pas descendre sous la coupe initiale (évite de propager dans le sol vers d'autres arbres)
                        if (neighbor.getY() < minY) continue;

                        // Doit être un tronc et appartenir à la même famille de bois
                        if (!Tag.LOGS.isTagged(neighbor.getType()) || !isSameWoodType(startType, neighbor.getType())) {
                            continue;
                        }

                        // Si c'est un déplacement diagonal horizontal sur le même niveau Y (dy == 0)
                        if (dy == 0 && Math.abs(dx) == 1 && Math.abs(dz) == 1) {
                            Block corner1 = current.getRelative(dx, 0, 0);
                            Block corner2 = current.getRelative(0, 0, dz);
                            // S'il n'y a aucun tronc adjacent sur les faces, ce sont deux troncs distincts qui ne se touchent que par le coin
                            if (!isSameWoodType(startType, corner1.getType()) && !isSameWoodType(startType, corner2.getType())) {
                                continue;
                            }
                        }

                        logsToBreak.add(neighbor);
                        logQueue.add(neighbor);
                    }
                }
            }
        }

        // 2. Détection des feuilles : Bois vers Feuilles (puis Feuilles vers Feuilles)
        // RÈGLE CRUCIALE : Pas de feuilles vers le bois ! Aucune bûche ne sera ajoutée à partir des feuilles.
        Set<Block> leavesToBreak = new HashSet<>();
        Queue<Block> leafQueue = new ArrayDeque<>();
        Map<Block, Integer> leafDistances = new HashMap<>();

        // Initialisation : les feuilles directement adjacentes aux bûches trouvées
        for (Block log : logsToBreak) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        Block neighbor = log.getRelative(dx, dy, dz);
                        if (logsToBreak.contains(neighbor) || leavesToBreak.contains(neighbor)) {
                            continue;
                        }

                        if (isMatchingLeaf(woodFamily, neighbor.getType())) {
                            if (isPersistent(neighbor)) continue;
                            // Si la feuille touche un autre tronc d'arbre vivant (non inclus dans logsToBreak), ne pas la casser
                            if (isAdjacentToLivingOtherLog(neighbor, logsToBreak)) continue;

                            leavesToBreak.add(neighbor);
                            leafQueue.add(neighbor);
                            leafDistances.put(neighbor, 1);
                        }
                    }
                }
            }
        }

        // Propagation contrôlée feuille -> feuille (jusqu'à MAX_LEAF_DISTANCE)
        while (!leafQueue.isEmpty() && leavesToBreak.size() < MAX_LEAVES) {
            Block currentLeaf = leafQueue.poll();
            int dist = leafDistances.getOrDefault(currentLeaf, 1);

            if (dist >= MAX_LEAF_DISTANCE) {
                continue;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        Block neighbor = currentLeaf.getRelative(dx, dy, dz);
                        if (logsToBreak.contains(neighbor) || leavesToBreak.contains(neighbor)) {
                            continue;
                        }

                        // IMPORTANT : "Pas des feuilles vers le bois" -> on ignore scrupuleusement les bûches !
                        if (isMatchingLeaf(woodFamily, neighbor.getType())) {
                            if (isPersistent(neighbor)) continue;
                            if (isAdjacentToLivingOtherLog(neighbor, logsToBreak)) continue;

                            leavesToBreak.add(neighbor);
                            leafQueue.add(neighbor);
                            leafDistances.put(neighbor, dist + 1);
                        }
                    }
                }
            }
        }

        // 3. Destruction des blocs (hors du bloc initial qui est déjà cassé par l'événement)
        logsToBreak.remove(startBlock);
        currentlyBreaking.addAll(logsToBreak);
        currentlyBreaking.addAll(leavesToBreak);

        try {
            for (Block log : logsToBreak) {
                log.breakNaturally(tool);
            }

            for (Block leaf : leavesToBreak) {
                leaf.breakNaturally(tool);
            }

            if (!logsToBreak.isEmpty() || !leavesToBreak.isEmpty()) {
                player.playSound(player.getLocation(), Sound.BLOCK_WOOD_BREAK, 1.0f, 0.8f);
            }
        } finally {
            currentlyBreaking.removeAll(logsToBreak);
            currentlyBreaking.removeAll(leavesToBreak);
        }
    }

    private boolean isPersistent(Block block) {
        if (block.getBlockData() instanceof Leaves leaves) {
            return leaves.isPersistent();
        }
        return false;
    }

    private boolean isAdjacentToLivingOtherLog(Block leaf, Set<Block> treeLogs) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block neighbor = leaf.getRelative(dx, dy, dz);
                    if (treeLogs.contains(neighbor)) continue;
                    if (Tag.LOGS.isTagged(neighbor.getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isSameWoodType(Material base, Material other) {
        if (base == other) return true;
        String baseFamily = getWoodFamily(base);
        String otherFamily = getWoodFamily(other);
        return baseFamily != null && baseFamily.equals(otherFamily);
    }

    private String getWoodFamily(Material material) {
        if (material == null) return null;
        String name = material.name();
        if (name.contains("DARK_OAK")) return "DARK_OAK";
        if (name.contains("PALE_OAK")) return "PALE_OAK";
        if (name.contains("OAK")) return "OAK";
        if (name.contains("BIRCH")) return "BIRCH";
        if (name.contains("SPRUCE")) return "SPRUCE";
        if (name.contains("JUNGLE")) return "JUNGLE";
        if (name.contains("ACACIA")) return "ACACIA";
        if (name.contains("MANGROVE")) return "MANGROVE";
        if (name.contains("CHERRY")) return "CHERRY";
        if (name.contains("CRIMSON")) return "CRIMSON";
        if (name.contains("WARPED")) return "WARPED";
        return null;
    }

    private boolean isMatchingLeaf(String woodFamily, Material leafMaterial) {
        if (leafMaterial == null) return false;
        if (woodFamily == null) {
            return Tag.LEAVES.isTagged(leafMaterial);
        }
        return switch (woodFamily) {
            case "OAK" -> leafMaterial == Material.OAK_LEAVES || leafMaterial == Material.AZALEA_LEAVES || leafMaterial == Material.FLOWERING_AZALEA_LEAVES;
            case "DARK_OAK" -> leafMaterial == Material.DARK_OAK_LEAVES;
            case "PALE_OAK" -> leafMaterial.name().equals("PALE_OAK_LEAVES");
            case "BIRCH" -> leafMaterial == Material.BIRCH_LEAVES;
            case "SPRUCE" -> leafMaterial == Material.SPRUCE_LEAVES;
            case "JUNGLE" -> leafMaterial == Material.JUNGLE_LEAVES;
            case "ACACIA" -> leafMaterial == Material.ACACIA_LEAVES;
            case "MANGROVE" -> leafMaterial == Material.MANGROVE_LEAVES;
            case "CHERRY" -> leafMaterial == Material.CHERRY_LEAVES;
            case "CRIMSON" -> leafMaterial == Material.NETHER_WART_BLOCK || leafMaterial == Material.SHROOMLIGHT;
            case "WARPED" -> leafMaterial == Material.WARPED_WART_BLOCK || leafMaterial == Material.SHROOMLIGHT;
            default -> Tag.LEAVES.isTagged(leafMaterial);
        };
    }
}

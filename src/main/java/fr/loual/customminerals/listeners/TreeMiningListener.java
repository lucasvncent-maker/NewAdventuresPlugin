package fr.loual.customminerals.listeners;

import fr.loual.newadventure.NewAdventurePlugin;
import fr.loual.customminerals.items.CupriteAxe;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public class TreeMiningListener implements Listener {

    private final NewAdventurePlugin plugin;
    private final Set<Block> currentlyBreaking = new HashSet<>();

    private static final int MAX_LOGS = 300;
    private static final int MAX_LEAVES = 500;

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

        // Recherche en largeur (BFS) de tous les troncs et feuilles connectés
        Set<Block> logsToBreak = new HashSet<>();
        Set<Block> leavesToBreak = new HashSet<>();

        Queue<Block> logQueue = new ArrayDeque<>();
        logQueue.add(startBlock);
        logsToBreak.add(startBlock);

        // 1. Détection des troncs connectés
        while (!logQueue.isEmpty() && logsToBreak.size() < MAX_LOGS) {
            Block current = logQueue.poll();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        Block neighbor = current.getRelative(dx, dy, dz);
                        if (logsToBreak.contains(neighbor)) continue;

                        if (Tag.LOGS.isTagged(neighbor.getType())) {
                            logsToBreak.add(neighbor);
                            logQueue.add(neighbor);
                        }
                    }
                }
            }
        }

        // 2. Détection des feuilles autour des troncs trouvés
        for (Block log : logsToBreak) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (leavesToBreak.size() >= MAX_LEAVES) break;

                        Block neighbor = log.getRelative(dx, dy, dz);
                        if (leavesToBreak.contains(neighbor) || logsToBreak.contains(neighbor)) {
                            continue;
                        }

                        if (Tag.LEAVES.isTagged(neighbor.getType())) {
                            leavesToBreak.add(neighbor);
                        }
                    }
                }
            }
        }

        // 3. Destruction des troncs (hors du bloc initial qui est déjà cassé par l'événement)
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
}

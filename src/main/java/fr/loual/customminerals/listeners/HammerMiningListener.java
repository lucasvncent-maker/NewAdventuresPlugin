package fr.loual.customminerals.listeners;

import fr.loual.newadventure.NewAdventurePlugin;
import fr.loual.customminerals.items.CupriteHammer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class HammerMiningListener implements Listener {

    private final NewAdventurePlugin plugin;
    private final Set<UUID> activeMiners = new HashSet<>();

    private static final Set<Material> EXCLUDED_MATERIALS = Set.of(
            Material.BEDROCK,
            Material.BARRIER,
            Material.STRUCTURE_BLOCK,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.SPAWNER,
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.BARREL,
            Material.SHULKER_BOX,
            Material.FURNACE,
            Material.BLAST_FURNACE,
            Material.SMOKER,
            Material.DISPENSER,
            Material.DROPPER,
            Material.HOPPER
    );

    public HammerMiningListener(NewAdventurePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (activeMiners.contains(playerId)) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!CupriteHammer.isCupriteHammer(plugin, tool)) {
            return;
        }

        Block origin = event.getBlock();
        if (!isAllowedMaterial(origin.getType())) {
            return;
        }

        int radius = CupriteHammer.getRadius(plugin, tool);
        int depth = CupriteHammer.getDepth(plugin, tool);
        boolean autoSmelt = CupriteHammer.hasAutoSmelt(plugin, tool);

        List<Block> blocksToMine = getMiningBlocks(player, origin, radius, depth);

        activeMiners.add(playerId);
        try {
            // Désactive le drop vanilla par défaut du bloc cliqué pour gérer tous les drops nous-mêmes
            event.setDropItems(false);
            event.setExpToDrop(0);

            List<ItemStack> totalDrops = new ArrayList<>();
            int totalExp = 0;
            boolean anySmelted = false;

            // Assurer que l'outil possède Fortune pour les drops
            ItemStack fortuneTool = tool.clone();
            if (!fortuneTool.containsEnchantment(Enchantment.FORTUNE)) {
                fortuneTool.addUnsafeEnchantment(Enchantment.FORTUNE, 3);
            }

            for (Block b : blocksToMine) {
                if (b.getType().isAir() || EXCLUDED_MATERIALS.contains(b.getType()) || !isAllowedMaterial(b.getType())) {
                    continue;
                }

                // Vérification des claims / protections pour les blocs secondaires
                if (!b.equals(origin)) {
                    BlockBreakEvent subEvent = new BlockBreakEvent(b, player);
                    Bukkit.getPluginManager().callEvent(subEvent);
                    if (subEvent.isCancelled()) {
                        continue;
                    }
                }

                // Récolte des drops avec l'effet Fortune
                Collection<ItemStack> drops = b.getDrops(fortuneTool, player);

                for (ItemStack drop : drops) {
                    if (autoSmelt) {
                        ItemStack smelted = smeltItem(drop);
                        if (!smelted.isSimilar(drop)) {
                            anySmelted = true;
                        }
                        totalDrops.add(smelted);
                    } else {
                        totalDrops.add(drop);
                    }
                }

                // Calcul d'expérience pour les minerais
                totalExp += calculateExp(b.getType());

                // Destruction du bloc
                b.setType(Material.AIR, false);
            }

            // Regroupement et drop des items au niveau du bloc d'origine
            Location dropLoc = origin.getLocation().add(0.5, 0.5, 0.5);
            for (ItemStack drop : totalDrops) {
                if (drop != null && !drop.getType().isAir() && drop.getAmount() > 0) {
                    origin.getWorld().dropItemNaturally(dropLoc, drop);
                }
            }

            // Drop d'orbes d'expérience
            if (totalExp > 0) {
                ExperienceOrb orb = origin.getWorld().spawn(dropLoc, ExperienceOrb.class);
                orb.setExperience(totalExp);
            }

            // Effets de fonte automatique
            if (anySmelted) {
                origin.getWorld().spawnParticle(Particle.FLAME, dropLoc, 20, 0.5, 0.5, 0.5, 0.05);
                origin.getWorld().playSound(dropLoc, Sound.BLOCK_FURNACE_FIRE_CRACKLE, 1.0f, 1.0f);
            }

            // Son d'impact du marteau lourd
            origin.getWorld().playSound(dropLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.6f, 0.8f);

        } finally {
            activeMiners.remove(playerId);
        }
    }

    private List<Block> getMiningBlocks(Player player, Block origin, int radius, int depth) {
        List<Block> blocks = new ArrayList<>();
        float pitch = player.getLocation().getPitch();

        if (pitch > 55.0f) {
            // Minage vers le bas (sol)
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = 0; dy > -depth; dy--) {
                        blocks.add(origin.getRelative(dx, dy, dz));
                    }
                }
            }
        } else if (pitch < -55.0f) {
            // Minage vers le haut (plafond)
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = 0; dy < depth; dy++) {
                        blocks.add(origin.getRelative(dx, dy, dz));
                    }
                }
            }
        } else {
            // Minage horizontal selon la direction
            BlockFace facing = player.getFacing();
            int dirX = facing.getModX();
            int dirZ = facing.getModZ();

            for (int dy = -radius; dy <= radius; dy++) {
                if (dirX != 0) { // Regarde EST ou OUEST
                    for (int dz = -radius; dz <= radius; dz++) {
                        for (int d = 0; d < depth; d++) {
                            blocks.add(origin.getRelative(dirX * d, dy, dz));
                        }
                    }
                } else { // Regarde NORD ou SUD
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int d = 0; d < depth; d++) {
                            blocks.add(origin.getRelative(dx, dy, dirZ * d));
                        }
                    }
                }
            }
        }

        return blocks;
    }

    private boolean isAllowedMaterial(Material mat) {
        if (mat.isAir() || EXCLUDED_MATERIALS.contains(mat)) {
            return false;
        }

        String name = mat.name();

        // Minerais (stone, deepslate, nether)
        if (name.contains("ORE") || name.equals("ANCIENT_DEBRIS")) return true;

        // Pierres et variantes (stone, cobble, granite, diorite, andesite, tuff, calcite, etc.)
        if (name.contains("STONE") || name.contains("COBBLE") || name.contains("ANDESITE")
                || name.contains("DIORITE") || name.contains("GRANITE") || name.contains("TUFF")
                || name.contains("CALCITE") || name.contains("BASALT") || name.contains("BLACKSTONE")
                || name.contains("NETHERRACK") || name.contains("END_STONE")) {
            return true;
        }

        // Deepslate et variantes
        if (name.contains("DEEPSLATE")) return true;

        // Gravier
        if (name.contains("GRAVEL")) return true;

        // Terres, sables et boues
        if (name.contains("DIRT") || name.contains("GRASS_BLOCK") || name.contains("PODZOL")
                || name.contains("MYCELIUM") || name.contains("MUD") || name.contains("CLAY")
                || name.contains("SAND")) {
            return true;
        }

        return false;
    }

    private ItemStack smeltItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return item;

        Material type = item.getType();
        Material resultMat = switch (type) {
            case RAW_IRON, IRON_ORE, DEEPSLATE_IRON_ORE -> Material.IRON_INGOT;
            case RAW_COPPER, COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.COPPER_INGOT;
            case RAW_GOLD, GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> Material.GOLD_INGOT;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            case COBBLESTONE -> Material.STONE;
            case COBBLED_DEEPSLATE -> Material.DEEPSLATE;
            case SAND, RED_SAND -> Material.GLASS;
            case CLAY_BALL -> Material.BRICK;
            default -> null;
        };

        if (resultMat != null) {
            ItemStack res = item.clone();
            res.setType(resultMat);
            return res;
        }

        return item;
    }

    private int calculateExp(Material mat) {
        return switch (mat) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> ThreadLocalRandom.current().nextInt(0, 3);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> ThreadLocalRandom.current().nextInt(3, 8);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE, NETHER_QUARTZ_ORE -> ThreadLocalRandom.current().nextInt(2, 6);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> ThreadLocalRandom.current().nextInt(1, 6);
            case NETHER_GOLD_ORE -> ThreadLocalRandom.current().nextInt(0, 2);
            default -> 0;
        };
    }
}

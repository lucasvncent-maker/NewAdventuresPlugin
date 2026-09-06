package fr.loual.customminerals.listeners;

import fr.loual.newadventure.NewAdventurePlugin;
import fr.loual.customminerals.items.CupriteBlock;
import fr.loual.customminerals.items.CupritePickaxe;
import fr.loual.customminerals.items.ReinforcedCupriteBlock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class SpawnerMiningListener implements Listener {

    private final NewAdventurePlugin plugin;

    public SpawnerMiningListener(NewAdventurePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!CupritePickaxe.isCupritePickaxe(plugin, tool)) {
            return;
        }

        if (event.getBlock().getType() == Material.SPAWNER) {
            // Empêche la destruction classique en XP
            event.setDropItems(false);
            event.setExpToDrop(0);

            CreatureSpawner blockSpawner = (CreatureSpawner) event.getBlock().getState();
            EntityType mobType = blockSpawner.getSpawnedType();

            // Création de l'item Spawner avec conservation du type de monstre
            ItemStack spawnerItem = new ItemStack(Material.SPAWNER);
            BlockStateMeta meta = (BlockStateMeta) spawnerItem.getItemMeta();

            if (meta != null) {
                CreatureSpawner state = (CreatureSpawner) meta.getBlockState();
                if (mobType != null) {
                    state.setSpawnedType(mobType);
                }
                meta.setBlockState(state);

                String mobName = formatMobName(mobType);
                meta.displayName(
                        Component.text("Générateur de " + mobName, NamedTextColor.GOLD)
                                .decoration(TextDecoration.BOLD, true)
                                .decoration(TextDecoration.ITALIC, false)
                );

                meta.lore(List.of(
                        Component.text("Générateur extrait grâce à une Pioche en Cuprite.", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("Monstre : ", NamedTextColor.YELLOW)
                                .append(Component.text(mobName, NamedTextColor.AQUA))
                                .decoration(TextDecoration.ITALIC, false)
                ));

                if (mobType != null) {
                    NamespacedKey spawnerKey = new NamespacedKey(plugin, "custom_spawner_mob");
                    meta.getPersistentDataContainer().set(spawnerKey, PersistentDataType.STRING, mobType.name());
                }

                spawnerItem.setItemMeta(meta);
            }

            // Drop du Spawner
            event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                    spawnerItem
            );

            // Effets visuels & sonores
            event.getBlock().getWorld().playSound(event.getBlock().getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
            event.getBlock().getWorld().spawnParticle(
                    Particle.TOTEM_OF_UNDYING,
                    event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                    30, 0.3, 0.3, 0.3, 0.1
            );

            // La pioche se brise (1 utilisation unique)
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);

            player.sendActionBar(
                    Component.text("✦ Spawner récupéré ! Votre Pioche en Cuprite s'est brisée.", NamedTextColor.GOLD)
            );

        } else {
            // Casse d'un bloc quelconque avec la pioche : consommation de son unique utilisation
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
            player.sendActionBar(
                    Component.text("✦ Votre Pioche en Cuprite s'est brisée !", NamedTextColor.RED)
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();

        // Empêcher la pose accidentelle des Blocs de Cuprite (matériaux de forge)
        if (CupriteBlock.isCupriteBlock(plugin, item) || ReinforcedCupriteBlock.isReinforcedCupriteBlock(plugin, item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                    Component.text("Ce bloc de Cuprite est un matériau précieux d'artisanat et ne peut pas être posé.", NamedTextColor.RED)
            );
            return;
        }

        // Restauration du monstre lors de la pose d'un Spawner custom
        if (item.getType() == Material.SPAWNER && item.hasItemMeta()) {
            NamespacedKey spawnerKey = new NamespacedKey(plugin, "custom_spawner_mob");
            String mobName = item.getItemMeta().getPersistentDataContainer().get(spawnerKey, PersistentDataType.STRING);

            if (mobName != null) {
                try {
                    EntityType type = EntityType.valueOf(mobName);
                    CreatureSpawner placed = (CreatureSpawner) event.getBlockPlaced().getState();
                    placed.setSpawnedType(type);
                    placed.update(true, true);

                    event.getPlayer().sendActionBar(
                            Component.text("✦ Générateur de " + formatMobName(type) + " placé !", NamedTextColor.GREEN)
                    );
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String formatMobName(EntityType type) {
        if (type == null) return "Créature Inconnue";
        return switch (type) {
            case ZOMBIE -> "Zombie";
            case SKELETON -> "Squelette";
            case SPIDER -> "Araignée";
            case CAVE_SPIDER -> "Araignée venimeuse";
            case BLAZE -> "Blaze";
            case SILVERFISH -> "Poisson d'argent";
            case MAGMA_CUBE -> "Cube de magma";
            case PIG -> "Cochon";
            case COW -> "Vache";
            default -> {
                String raw = type.name().toLowerCase().replace('_', ' ');
                yield Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
            }
        };
    }
}

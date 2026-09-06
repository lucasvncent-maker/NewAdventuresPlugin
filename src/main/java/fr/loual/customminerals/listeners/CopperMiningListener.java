package fr.loual.customminerals.listeners;

import fr.loual.newadventure.NewAdventurePlugin;
import fr.loual.customminerals.items.Cuprite;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class CopperMiningListener implements Listener {

    private final NewAdventurePlugin plugin;
    private final Set<Material> copperOres = Set.of(
            Material.COPPER_ORE,
            Material.DEEPSLATE_COPPER_ORE
    );

    public CopperMiningListener(NewAdventurePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Ne rien drop en mode Créatif ou Spectateur
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        Block block = event.getBlock();
        if (!copperOres.contains(block.getType())) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();

        // Vérification de l'outil approprié (en vanilla le cuivre nécessite une pioche en pierre minimum)
        if (!isValidMiningTool(tool)) {
            return;
        }

        // Anti-duplication via Toucher de soie (Silk Touch)
        boolean preventSilkTouch = plugin.getConfig().getBoolean("cuprite.prevent-silk-touch", true);
        if (preventSilkTouch && tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            return;
        }

        // Tirage aléatoire (1% de base ou selon config)
        double dropChance = plugin.getConfig().getDouble("cuprite.drop-chance", 0.01);
        if (ThreadLocalRandom.current().nextDouble() >= dropChance) {
            return;
        }

        // Drop de la Cuprite
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), Cuprite.create(plugin, 1));

        // Effets visuels et sonores
        if (plugin.getConfig().getBoolean("cuprite.effects.sound", true)) {
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.3f);
        }

        if (plugin.getConfig().getBoolean("cuprite.effects.particles", true)) {
            block.getWorld().spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    block.getLocation().add(0.5, 0.5, 0.5),
                    12,
                    0.3,
                    0.3,
                    0.3,
                    0.05
            );
        }

        // Notification discrète dans l'action bar
        if (plugin.getConfig().getBoolean("cuprite.notify-player", true)) {
            player.sendActionBar(
                    Component.text("✦ Vous avez extrait de la ", NamedTextColor.GOLD)
                            .append(Component.text("Cuprite", NamedTextColor.YELLOW))
                            .append(Component.text(" !", NamedTextColor.GOLD))
            );
        }
    }

    /**
     * Vérifie si l'outil utilisé est capable de récolter le minerai de cuivre
     * (pioche en pierre ou supérieure en vanilla, excluant le bois ou la main nue).
     */
    private boolean isValidMiningTool(ItemStack tool) {
        if (tool == null || tool.getType().isAir()) {
            return false;
        }

        String typeName = tool.getType().name();
        return typeName.endsWith("_PICKAXE") && tool.getType() != Material.WOODEN_PICKAXE;
    }
}

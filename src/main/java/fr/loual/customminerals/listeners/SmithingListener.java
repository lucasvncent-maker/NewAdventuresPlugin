package fr.loual.customminerals.listeners;

import fr.loual.newadventure.NewAdventurePlugin;
import fr.loual.customminerals.items.CupriteBlock;
import fr.loual.customminerals.items.CupriteHammer;
import fr.loual.customminerals.items.ReinforcedCupriteBlock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

public class SmithingListener implements Listener {

    private final NewAdventurePlugin plugin;

    public SmithingListener(NewAdventurePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        SmithingInventory inv = event.getInventory();
        ItemStack template = inv.getInputTemplate();
        ItemStack base = inv.getInputEquipment();
        ItemStack addition = inv.getInputMineral();

        if (base == null || addition == null) {
            return;
        }

        // Vérification si la tentative correspond aux matériaux de nos marteaux (Pioche Netherite + Bloc Cuivre)
        if (base.getType() == CupriteHammer.BASE_MATERIAL && addition.getType() == CupriteBlock.BASE_MATERIAL) {
            // Le slot de template (modèle de forge) doit rester vide pour les marteaux
            if (template != null && !template.isEmpty()) {
                event.setResult(null);
                return;
            }

            if (CupriteHammer.isCupriteHammer(plugin, base)) {
                int currentTier = CupriteHammer.getTier(plugin, base);

                if (currentTier == 1 && ReinforcedCupriteBlock.isReinforcedCupriteBlock(plugin, addition)) {
                    event.setResult(CupriteHammer.upgrade(plugin, base, 2));
                    return;
                } else if (currentTier == 2 && ReinforcedCupriteBlock.isReinforcedCupriteBlock(plugin, addition)) {
                    event.setResult(CupriteHammer.upgrade(plugin, base, 3));
                    return;
                }
            }

            // Si ce n'est pas un marteau en cuprite valide ou pas le bon bloc, annuler le résultat
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSmithItem(SmithItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack result = event.getCurrentItem();
        if (result == null || !CupriteHammer.isCupriteHammer(plugin, result)) {
            return;
        }

        int newTier = CupriteHammer.getTier(plugin, result);
        if (newTier == 2) {
            player.sendMessage(Component.text("✦ Votre Marteau en Cuprite a été amélioré au Palier II (Efficacité VII & Fonte) !", NamedTextColor.YELLOW));
            player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1.0f, 1.0f);
        } else if (newTier == 3) {
            player.sendMessage(Component.text("✦ Votre Marteau en Cuprite a été renforcé au Palier III (5x5x5 Titanesque) !", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        }
    }
}

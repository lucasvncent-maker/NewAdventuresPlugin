package fr.loual.horde;

import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class HordeListener implements Listener {

    private final NewAdventurePlugin plugin;
    private final HordeManager hordeManager;

    public HordeListener(NewAdventurePlugin plugin, HordeManager hordeManager) {
        this.plugin = plugin;
        this.hordeManager = hordeManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (HordeItems.isHordeItem(mainHand, HordeItems.ID_TITAN_SOUL_SLICER)) {
                // Drain vital : soigne le joueur de 2 HP (1 cœur)
                AttributeInstance maxHpAttr = player.getAttribute(Attribute.MAX_HEALTH);
                double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;
                double newHp = Math.min(maxHp, player.getHealth() + 2.0);
                player.setHealth(newHp);

                Location loc = player.getLocation().add(0, 1, 0);
                loc.getWorld().spawnParticle(Particle.HEART, loc, 3, 0.3, 0.3, 0.3, 0.05);
                player.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.6f);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getPersistentDataContainer().has(HordeManager.MOB_KEY, PersistentDataType.STRING)) {
            // Effet visuel de disparition
            entity.getWorld().spawnParticle(Particle.SOUL, entity.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.05);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.8f, 1.6f);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        Entity entity = event.getEntity();
        if (entity.getPersistentDataContainer().has(HordeManager.MOB_KEY, PersistentDataType.STRING)) {
            // Empêche les monstres de la horde d'attaquer les villageois (ex: le Croupier)
            if (event.getTarget() instanceof Villager) {
                event.setCancelled(true);
            }
        }
    }
}

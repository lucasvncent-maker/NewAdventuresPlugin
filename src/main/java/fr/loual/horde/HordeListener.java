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
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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
            } else if (HordeItems.isHordeItem(mainHand, HordeItems.ID_INQUISITOR_WRATH_BLADE)) {
                // Onde Tellurique de l'Inquisiteur
                Location loc = event.getEntity().getLocation();
                loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.2f, 0.8f);
                loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 0.5, 0), 6, 0.8, 0.2, 0.8, 0.05);
                loc.getWorld().spawnParticle(Particle.BLOCK, loc, 20, 1.2, 0.2, 1.2, Material.COPPER_BLOCK.createBlockData());
                for (Entity near : event.getEntity().getNearbyEntities(3.5, 2.0, 3.5)) {
                    if (near instanceof LivingEntity target && !(near instanceof Player)) {
                        target.damage(5.0, player);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                    }
                }
            } else if (HordeItems.isHordeItem(mainHand, HordeItems.ID_APOCALYPSE_CLAYMORE)) {
                // Fléau Stellaire de l'Apocalypse
                Location loc = event.getEntity().getLocation();
                loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
                loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 0.8, 0), 30, 1.2, 0.6, 1.2, 0.08);
                loc.getWorld().spawnParticle(Particle.LAVA, loc.clone().add(0, 0.8, 0), 10, 0.5, 0.5, 0.5, 0.02);
                for (Entity near : event.getEntity().getNearbyEntities(4.5, 2.0, 4.5)) {
                    if (near instanceof LivingEntity target && !(near instanceof Player)) {
                        target.damage(8.0, player);
                        target.setFireTicks(100);
                    }
                }
            }
        }

        // Effets quand un mob spécial de la horde blesse un joueur
        if (event.getEntity() instanceof Player victim) {
            Entity attacker = event.getDamager();
            if (attacker instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Entity shooter) {
                attacker = shooter;
            }
            if (attacker instanceof LivingEntity mob) {
                String mobType = mob.getPersistentDataContainer().get(HordeManager.MOB_KEY, PersistentDataType.STRING);
                if (mobType != null) {
                    switch (mobType) {
                        case "frost" -> {
                            victim.setFreezeTicks(Math.max(victim.getFreezeTicks(), 140));
                            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                        }
                        case "storm_spider" -> {
                            victim.getWorld().strikeLightningEffect(victim.getLocation());
                            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 4));
                            victim.playSound(victim.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
                        }
                        case "plague_archer" -> {
                            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1));
                            victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
                        }
                        case "infernal_brute" -> {
                            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1));
                            victim.setFireTicks(80);
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        String mobType = entity.getPersistentDataContainer().get(HordeManager.MOB_KEY, PersistentDataType.STRING);
        if (mobType != null) {
            // Empêche les débris de mob (os, flèches, yeux, chair) de polluer l'arène
            event.getDrops().clear();

            if ("infernal_blaze".equals(mobType)) {
                // Déflagration pyrotechnique à la mort du Blaze
                entity.getWorld().spawnParticle(Particle.EXPLOSION, entity.getLocation().add(0, 0.5, 0), 2);
                entity.getWorld().spawnParticle(Particle.FLAME, entity.getLocation().add(0, 0.5, 0), 25, 0.4, 0.4, 0.4, 0.05);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.3f);
            } else if ("frost".equals(mobType)) {
                // Éclats de givre à la mort
                entity.getWorld().spawnParticle(Particle.SNOWFLAKE, entity.getLocation().add(0, 0.5, 0), 25, 0.5, 0.5, 0.5, 0.05);
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
            } else {
                // Effet visuel standard de disparition
                entity.getWorld().spawnParticle(Particle.SOUL, entity.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.05);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.8f, 1.6f);
            }
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (hordeManager.isParticipant(player.getUniqueId())) {
            hordeManager.removeParticipant(player.getUniqueId());
            player.sendMessage(Component.text("☠ Vous avez succombé dans l'Arène des Damnés...", NamedTextColor.DARK_RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location returnLoc = hordeManager.getReturnLocation(player.getUniqueId());
        if (returnLoc != null) {
            event.setRespawnLocation(returnLoc);
            hordeManager.clearReturnLocation(player.getUniqueId());
            player.sendMessage(Component.text("✦ Vous avez péri dans l'Arène et êtes réapparu à votre point d'origine.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        hordeManager.removeParticipant(player.getUniqueId());
        Location returnLoc = hordeManager.getReturnLocation(player.getUniqueId());
        if (returnLoc != null) {
            player.teleport(returnLoc);
            hordeManager.clearReturnLocation(player.getUniqueId());
        }
    }
}

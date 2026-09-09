package fr.loual.horde;

import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class HordeListener implements Listener {

    private final NewAdventurePlugin plugin;
    private final HordeManager hordeManager;

    public HordeListener(NewAdventurePlugin plugin, HordeManager hordeManager) {
        this.plugin = plugin;
        this.hordeManager = hordeManager;
    }

    private final Set<java.util.UUID> aoeDamageInProgress = new java.util.HashSet<>();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Protection anti-récursion : ne pas réenclencher l'onde tellurique sur les dégâts de zone
        if (aoeDamageInProgress.contains(event.getEntity().getUniqueId())) {
            return;
        }

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
                    if (near instanceof LivingEntity target && !(near instanceof Player) && !target.equals(event.getEntity())) {
                        aoeDamageInProgress.add(target.getUniqueId());
                        try {
                            target.damage(5.0, player);
                            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                        } finally {
                            aoeDamageInProgress.remove(target.getUniqueId());
                        }
                    }
                }
            } else if (HordeItems.isHordeItem(mainHand, HordeItems.ID_APOCALYPSE_CLAYMORE)) {
                // Fléau Stellaire de l'Apocalypse
                Location loc = event.getEntity().getLocation();
                loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
                loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 0.8, 0), 30, 1.2, 0.6, 1.2, 0.08);
                loc.getWorld().spawnParticle(Particle.LAVA, loc.clone().add(0, 0.8, 0), 10, 0.5, 0.5, 0.5, 0.02);
                for (Entity near : event.getEntity().getNearbyEntities(4.5, 2.0, 4.5)) {
                    if (near instanceof LivingEntity target && !(near instanceof Player) && !target.equals(event.getEntity())) {
                        aoeDamageInProgress.add(target.getUniqueId());
                        try {
                            target.damage(8.0, player);
                            target.setFireTicks(100);
                        } finally {
                            aoeDamageInProgress.remove(target.getUniqueId());
                        }
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getPlayer();
        boolean inHorde = hordeManager.isParticipant(player.getUniqueId())
                || hordeManager.isInArena(player.getLocation())
                || hordeManager.isHordeWorld(player.getWorld());

        if (inHorde) {
            // Conservation ABSOLUE de tout le stuff et des niveaux d'expérience
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            event.getDrops().clear();

            hordeManager.removeParticipant(player.getUniqueId());

            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("☠ =================================================== ☠", NamedTextColor.DARK_RED, net.kyori.adventure.text.format.TextDecoration.BOLD));
            player.sendMessage(Component.text("✦ MORT DANS L'ARÈNE DE LA HORDE ✦", NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.BOLD));
            player.sendMessage(Component.text("Tout votre équipement et votre expérience ont été intégralement conservés !", NamedTextColor.GREEN, net.kyori.adventure.text.format.TextDecoration.BOLD));
            player.sendMessage(Component.text("☠ =================================================== ☠", NamedTextColor.DARK_RED, net.kyori.adventure.text.format.TextDecoration.BOLD));
            player.sendMessage(Component.empty());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location returnLoc = hordeManager.getAndClearReturnLocation(player.getUniqueId());
        if (returnLoc != null) {
            event.setRespawnLocation(returnLoc);
            player.sendMessage(Component.text("✦ Vous avez réapparu à votre point d'origine avec l'intégralité de votre équipement !", NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD));
            player.playSound(returnLoc, Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1.0f, 1.0f);
        } else if (hordeManager.isHordeWorld(event.getRespawnLocation().getWorld())) {
            // Sécurité absolue : ne jamais laisser un joueur respawn dans le monde vide de la horde
            World mainWorld = org.bukkit.Bukkit.getWorlds().get(0);
            event.setRespawnLocation(mainWorld.getSpawnLocation());
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        hordeManager.removeParticipant(player.getUniqueId());
        Location returnLoc = hordeManager.getAndClearReturnLocation(player.getUniqueId());
        if (returnLoc != null) {
            player.teleport(returnLoc);
        } else if (hordeManager.isHordeWorld(player.getWorld())) {
            World mainWorld = org.bukkit.Bukkit.getWorlds().get(0);
            player.teleport(mainWorld.getSpawnLocation());
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (hordeManager.isHordeWorld(player.getWorld()) && !hordeManager.isHordeActive()) {
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.hasPermission("horde.admin")) {
                player.sendMessage(Component.text("✦ Vous êtes dans l'Arène de la Horde en mode Créatif/Admin.", NamedTextColor.AQUA));
                return;
            }
            World mainWorld = org.bukkit.Bukkit.getWorlds().get(0);
            player.teleport(mainWorld.getSpawnLocation());
            player.sendMessage(Component.text("✦ L'invasion de la Horde étant terminée, vous avez été téléporté au spawn.", NamedTextColor.YELLOW));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK &&
            event.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            return;
        }
        org.bukkit.block.Block block = event.getClickedBlock();
        if (block == null) return;

        if (hordeManager.isHordeWorld(block.getWorld())) {
            // Bouton de départ au centre
            if (block.getX() == HordeManager.ARENA_X && block.getZ() == HordeManager.ARENA_Z) {
                if (block.getY() == HordeManager.ARENA_Y + 1 || block.getY() == HordeManager.ARENA_Y + 2) {
                    if (hordeManager.getState() == HordeManager.State.STARTING) {
                        event.setCancelled(true);
                        hordeManager.triggerWaveStart(event.getPlayer());
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (hordeManager.isHordeWorld(event.getBlock().getWorld())) {
            if (hordeManager.isHordeActive()) {
                org.bukkit.block.Block b = event.getBlock();
                if (b.getX() == HordeManager.ARENA_X && b.getZ() == HordeManager.ARENA_Z) {
                    if (b.getY() == HordeManager.ARENA_Y + 1 || b.getY() == HordeManager.ARENA_Y + 2) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (hordeManager.isHordeWorld(player.getWorld()) && event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID) {
                event.setCancelled(true);
                player.setFallDistance(0);
                player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                Location center = new Location(player.getWorld(), HordeManager.ARENA_X + 0.5, HordeManager.ARENA_Y + 1.0, HordeManager.ARENA_Z + 0.5);
                player.teleport(center);
                player.sendMessage(Component.text("⚠ Une force mystique vous protège du vide et vous ramène sur l'Arène !", NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.BOLD));
                player.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            }
        }
    }
}

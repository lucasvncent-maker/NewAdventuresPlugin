package fr.loual.customclasses.classes;

import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClassManager {

    private final NewAdventurePlugin plugin;
    private final NamespacedKey classKey;
    private final NamespacedKey warriorSpeedKey;
    private final Map<UUID, PlayerClass> cache = new HashMap<>();

    public ClassManager(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.classKey = new NamespacedKey(plugin, "player_class");
        this.warriorSpeedKey = new NamespacedKey(plugin, "warrior_attack_speed");
    }

    public PlayerClass getPlayerClass(Player player) {
        if (cache.containsKey(player.getUniqueId())) {
            return cache.get(player.getUniqueId());
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String id = pdc.get(classKey, PersistentDataType.STRING);
        PlayerClass pc = PlayerClass.fromId(id);
        cache.put(player.getUniqueId(), pc);
        return pc;
    }

    public boolean hasClass(Player player) {
        return getPlayerClass(player) != PlayerClass.NONE;
    }

    public void setPlayerClass(Player player, PlayerClass pc) {
        cache.put(player.getUniqueId(), pc);
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pc == PlayerClass.NONE) {
            pdc.remove(classKey);
        } else {
            pdc.set(classKey, PersistentDataType.STRING, pc.getId());
        }

        applyClassAttributesAndEffects(player, pc);
    }

    public void resetPlayerClass(Player player) {
        setPlayerClass(player, PlayerClass.NONE);
        player.sendMessage(
                Component.text("[Classes] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("Votre classe a été réinitialisée !", NamedTextColor.YELLOW))
        );
    }

    public void applyClassAttributesAndEffects(Player player, PlayerClass pc) {
        // 1. Réinitialiser les attributs de base
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(20.0);
            if (player.getHealth() > 20.0) {
                player.setHealth(20.0);
            }
        }

        AttributeInstance attackSpeedAttr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeedAttr != null) {
            attackSpeedAttr.setBaseValue(4.0); // Valeur vanilla par défaut
            removeWarriorSpeedModifier(attackSpeedAttr);
        }

        // 2. Nettoyer les effets permanents connus des classes
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.WATER_BREATHING);
        player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.HUNGER);
        player.removePotionEffect(PotionEffectType.SLOWNESS);

        // 3. Appliquer selon la classe
        switch (pc) {
            case ASSASSIN -> {
                // Santé max réduite à 7 cœurs (14 HP)
                if (maxHealthAttr != null) {
                    maxHealthAttr.setBaseValue(14.0);
                    if (player.getHealth() > 14.0) {
                        player.setHealth(14.0);
                    }
                }
                // Vitesse I permanente
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false, true));
            }
            case GUERRIER -> {
                // 12 cœurs max (24 HP)
                if (maxHealthAttr != null) {
                    maxHealthAttr.setBaseValue(24.0);
                }
                // Résistance I permanente
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false, true));
                // Vitesse d'attaque au corps-à-corps réduite de 15% (-0.15 via modificateur scalaire)
                if (attackSpeedAttr != null) {
                    removeWarriorSpeedModifier(attackSpeedAttr);
                    AttributeModifier modifier = new AttributeModifier(
                            warriorSpeedKey,
                            -0.15,
                            getAddScalarOperation()
                    );
                    attackSpeedAttr.addModifier(modifier);
                }
            }
            case SAUTERELLE -> {
                // Jump Boost II permanent
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, 1, false, false, true));
            }
            case SIRENE -> {
                // Respiration aquatique, Grâce du dauphin et Vision nocturne permanentes
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, PotionEffect.INFINITE_DURATION, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, PotionEffect.INFINITE_DURATION, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, true));
            }
            case DIABLE -> {
                // Immunité au feu et à la lave
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false, true));
            }
            case HUMAIN, NECROMANCIEN, ARCHER, NONE -> {
                // Pas d'attributs de base modifiés
            }
        }
    }

    private void removeWarriorSpeedModifier(AttributeInstance attr) {
        for (AttributeModifier mod : attr.getModifiers()) {
            if (warriorSpeedKey.equals(mod.getKey())) {
                attr.removeModifier(mod);
            }
        }
    }

    private AttributeModifier.Operation getAddScalarOperation() {
        try {
            return AttributeModifier.Operation.valueOf("ADD_SCALAR");
        } catch (IllegalArgumentException e) {
            return AttributeModifier.Operation.valueOf("MULTIPLY_SCALAR_1");
        }
    }

    public void refreshPlayer(Player player) {
        PlayerClass pc = getPlayerClass(player);
        if (pc != PlayerClass.NONE) {
            applyClassAttributesAndEffects(player, pc);
        }
    }

    public void unloadPlayer(Player player) {
        cache.remove(player.getUniqueId());
    }
}

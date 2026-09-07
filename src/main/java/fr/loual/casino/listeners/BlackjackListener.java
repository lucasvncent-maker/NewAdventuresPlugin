package fr.loual.casino.listeners;

import fr.loual.casino.BlackjackGame;
import fr.loual.casino.gui.BlackjackGui;
import fr.loual.casino.gui.BlackjackGuiHolder;
import fr.loual.newadventure.NewAdventurePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BlackjackListener implements Listener {

    private final NewAdventurePlugin plugin;
    private final NamespacedKey croupierKey;
    private final Map<UUID, BlackjackGame> activeSessions = new HashMap<>();

    public BlackjackListener(NewAdventurePlugin plugin) {
        this.plugin = plugin;
        this.croupierKey = new NamespacedKey(plugin, "is_casino_croupier");
    }

    public boolean isCroupier(Entity entity) {
        if (!(entity instanceof Villager villager)) return false;
        Byte tag = villager.getPersistentDataContainer().get(croupierKey, PersistentDataType.BYTE);
        return tag != null && tag == 1;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (isCroupier(entity)) {
            event.setCancelled(true);
            Player player = event.getPlayer();

            BlackjackGame game = activeSessions.computeIfAbsent(player.getUniqueId(), id -> new BlackjackGame(player));
            if (game.getState() == BlackjackGame.State.GAME_OVER) {
                game.resetToBetting();
            }

            BlackjackGui.open(player, game);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BlackjackGuiHolder holder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        BlackjackGame game = holder.getGame();
        int rawSlot = event.getRawSlot();
        Inventory topInv = event.getView().getTopInventory();

        // 1. Clics dans l'inventaire du haut (La table de Blackjack)
        if (rawSlot < topInv.getSize()) {
            if (game.getState() == BlackjackGame.State.BETTING) {
                if (rawSlot == BlackjackGui.BET_SLOT) {
                    ItemStack clicked = event.getCurrentItem();
                    if (clicked != null && (clicked.getType() == Material.GREEN_STAINED_GLASS_PANE 
                            || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE 
                            || clicked.getType() == Material.YELLOW_STAINED_GLASS_PANE)) {
                        event.setCancelled(true);
                        topInv.setItem(BlackjackGui.BET_SLOT, null);
                        return;
                    }
                    // Autoriser le joueur à placer / retirer son item de mise
                    Bukkit.getScheduler().runTask(plugin, () -> BlackjackGui.render(topInv, game));
                    return;
                }

                event.setCancelled(true);

                if (rawSlot == BlackjackGui.BUTTON_START_BET) {
                    ItemStack bet = topInv.getItem(BlackjackGui.BET_SLOT);
                    if (bet != null && !bet.getType().isAir() && bet.getAmount() > 0) {
                        // Sécurité : Ne jamais accepter une vitre du GUI comme mise
                        if (bet.getType() == Material.GREEN_STAINED_GLASS_PANE 
                                || bet.getType() == Material.BLACK_STAINED_GLASS_PANE 
                                || bet.getType() == Material.YELLOW_STAINED_GLASS_PANE) {
                            topInv.setItem(BlackjackGui.BET_SLOT, null);
                            return;
                        }

                        topInv.setItem(BlackjackGui.BET_SLOT, null);
                        game.startAnimated(plugin, bet, () -> {
                            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BlackjackGuiHolder) {
                                BlackjackGui.render(topInv, game);
                            }
                        });
                        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 1.2f);
                    }
                } else if (rawSlot == BlackjackGui.BUTTON_QUIT) {
                    player.closeInventory();
                }

            } else if (game.getState() == BlackjackGame.State.PLAYING) {
                event.setCancelled(true);

                if (rawSlot == BlackjackGui.BUTTON_HIT) {
                    game.hitAnimated(plugin, () -> {
                        if (player.getOpenInventory().getTopInventory().getHolder() instanceof BlackjackGuiHolder) {
                            BlackjackGui.render(topInv, game);
                        }
                    });
                } else if (rawSlot == BlackjackGui.BUTTON_STAND) {
                    game.standAnimated(plugin, () -> {
                        if (player.getOpenInventory().getTopInventory().getHolder() instanceof BlackjackGuiHolder) {
                            BlackjackGui.render(topInv, game);
                        }
                    });
                }

            } else if (game.getState() == BlackjackGame.State.GAME_OVER) {
                event.setCancelled(true);

                if (rawSlot == BlackjackGui.BUTTON_REPLAY) {
                    game.resetToBetting();
                    topInv.setItem(BlackjackGui.BET_SLOT, null);
                    BlackjackGui.render(topInv, game);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                } else if (rawSlot == BlackjackGui.BUTTON_QUIT) {
                    player.closeInventory();
                }
            }
        } else {
            // 2. Clics dans l'inventaire du joueur
            if (game.getState() == BlackjackGame.State.BETTING) {
                // Si shift-click depuis son inventaire, mettre à jour le bouton de mise
                Bukkit.getScheduler().runTask(plugin, () -> BlackjackGui.render(topInv, game));
            } else {
                // Interdire le shift-click pour ne pas injecter d'items dans la table en cours de partie
                if (event.isShiftClick()) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof BlackjackGuiHolder holder)) {
            return;
        }

        BlackjackGame game = holder.getGame();
        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize()) {
                if (game.getState() != BlackjackGame.State.BETTING || slot != BlackjackGui.BET_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (game.getState() == BlackjackGame.State.BETTING) {
            Bukkit.getScheduler().runTask(plugin, () -> BlackjackGui.render(event.getView().getTopInventory(), game));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BlackjackGuiHolder holder)) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        BlackjackGame game = holder.getGame();

        // Si le joueur ferme en phase de mise : lui restituer l'item posé dans le slot central (sauf si c'est une vitre)
        if (game.getState() == BlackjackGame.State.BETTING) {
            ItemStack betInSlot = event.getInventory().getItem(BlackjackGui.BET_SLOT);
            if (betInSlot != null && !betInSlot.getType().isAir() && betInSlot.getAmount() > 0) {
                event.getInventory().setItem(BlackjackGui.BET_SLOT, null);
                if (betInSlot.getType() != Material.GREEN_STAINED_GLASS_PANE 
                        && betInSlot.getType() != Material.BLACK_STAINED_GLASS_PANE 
                        && betInSlot.getType() != Material.YELLOW_STAINED_GLASS_PANE) {
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(betInSlot);
                    for (ItemStack rem : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), rem);
                    }
                }
            }
        } else if (game.getState() == BlackjackGame.State.PLAYING) {
            // Anti-triche : si le joueur ferme pendant qu'il joue pour fuir un mauvais tirage,
            // la main est automatiquement résolue avec 'stand' pour ne pas abuser.
            game.standAnimated(plugin, () -> {});
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCroupierDamage(EntityDamageEvent event) {
        if (isCroupier(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}

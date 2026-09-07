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

        // Sécurité universelle : Interdire le double-clic (COLLECT_TO_CURSOR) qui pourrait aspirer des blocs du GUI
        if (event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        // 1. Clics dans l'inventaire du haut (La table de Blackjack)
        if (rawSlot < topInv.getSize()) {
            // Toujours interdire les touches numériques (1-9) pour échanger des items avec la barre d'action
            if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
                event.setCancelled(true);
                return;
            }

            // SEUL le slot de mise en phase BETTING est modifiable
            if (game.getState() == BlackjackGame.State.BETTING && rawSlot == BlackjackGui.BET_SLOT) {
                ItemStack cursor = event.getCursor();
                if (cursor != null && isGuiMaterial(cursor.getType())) {
                    event.setCancelled(true);
                    return;
                }

                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && isGuiMaterial(clicked.getType())) {
                    event.setCancelled(true);
                    topInv.setItem(BlackjackGui.BET_SLOT, null);
                    return;
                }
                // Autoriser le joueur à placer / retirer son item de mise
                Bukkit.getScheduler().runTask(plugin, () -> BlackjackGui.render(topInv, game));
                return;
            }

            // Tout autre clic dans l'inventaire du haut est STRICTEMENT ANNULÉ (distribution, croupier, boutons, cartes, etc.)
            event.setCancelled(true);

            if (game.getState() == BlackjackGame.State.BETTING) {
                if (rawSlot == BlackjackGui.BUTTON_START_BET) {
                    ItemStack bet = topInv.getItem(BlackjackGui.BET_SLOT);
                    if (bet != null && !bet.getType().isAir() && bet.getAmount() > 0) {
                        // Sécurité : Ne jamais accepter une vitre ou un bloc du GUI comme mise
                        if (isGuiMaterial(bet.getType())) {
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
            if (event.isShiftClick()) {
                event.setCancelled(true);
                // Si en phase de mise et le slot de mise est vide, transférer l'item vers BET_SLOT
                if (game.getState() == BlackjackGame.State.BETTING) {
                    ItemStack current = event.getCurrentItem();
                    if (current != null && !current.getType().isAir() && !isGuiMaterial(current.getType())) {
                        ItemStack betSlotItem = topInv.getItem(BlackjackGui.BET_SLOT);
                        if (betSlotItem == null || betSlotItem.getType().isAir() || isGuiMaterial(betSlotItem.getType())) {
                            topInv.setItem(BlackjackGui.BET_SLOT, current.clone());
                            event.setCurrentItem(null);
                            Bukkit.getScheduler().runTask(plugin, () -> BlackjackGui.render(topInv, game));
                        }
                    }
                }
            }
        }
    }

    private boolean isGuiMaterial(Material mat) {
        if (mat == null) return false;
        return mat == Material.GREEN_STAINED_GLASS_PANE
                || mat == Material.BLACK_STAINED_GLASS_PANE
                || mat == Material.GRAY_STAINED_GLASS_PANE
                || mat == Material.YELLOW_STAINED_GLASS_PANE
                || mat == Material.GRAY_CONCRETE
                || mat == Material.LIME_CONCRETE
                || mat == Material.RED_CONCRETE
                || mat == Material.BARRIER
                || mat == Material.GOLD_BLOCK
                || mat == Material.REDSTONE_BLOCK
                || mat == Material.EMERALD_BLOCK
                || mat == Material.TOTEM_OF_UNDYING
                || mat == Material.PLAYER_HEAD;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof BlackjackGuiHolder holder)) {
            return;
        }

        BlackjackGame game = holder.getGame();
        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize()) {
                if (game.getState() != BlackjackGame.State.BETTING || slot != BlackjackGui.BET_SLOT || isGuiMaterial(event.getOldCursor().getType())) {
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

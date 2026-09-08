package fr.loual.casino.listeners;

import fr.loual.casino.BlackjackGame;
import fr.loual.casino.gui.BlackjackGui;
import fr.loual.casino.gui.BlackjackGuiHolder;
import fr.loual.newadventure.NewAdventurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
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

            BlackjackGame game = activeSessions.computeIfAbsent(player.getUniqueId(), id -> new BlackjackGame(player, plugin));
            game.loadChallengeFromPdc(plugin);
            if (game.getChallengeChips() > 0) {
                game.setMode(BlackjackGame.Mode.CHALLENGE);
            }
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
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        // 1. Clics dans l'inventaire du haut (La table de Blackjack)
        if (rawSlot < topInv.getSize()) {
            // Toujours interdire les touches numériques (1-9) pour échanger des items avec la barre d'action
            if (event.getClick() == ClickType.NUMBER_KEY) {
                event.setCancelled(true);
                return;
            }

            // Bouton de changement de Mode (Slot 0)
            if (rawSlot == BlackjackGui.BUTTON_MODE_SWITCH) {
                event.setCancelled(true);
                if (game.getState() != BlackjackGame.State.BETTING) {
                    player.sendMessage(Component.text("§cVous ne pouvez pas changer de mode pendant une manche en cours !"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                if (game.getMode() == BlackjackGame.Mode.CLASSIC) {
                    // Restituer l'item de mise si présent dans BET_SLOT
                    ItemStack betSlotItem = topInv.getItem(BlackjackGui.BET_SLOT);
                    if (betSlotItem != null && !betSlotItem.getType().isAir() && !isDecorativePane(betSlotItem.getType())) {
                        topInv.setItem(BlackjackGui.BET_SLOT, null);
                        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(betSlotItem);
                        for (ItemStack rem : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), rem);
                        }
                    }
                    game.setMode(BlackjackGame.Mode.CHALLENGE);
                    game.loadChallengeFromPdc(plugin);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.3f);
                    player.sendMessage(Component.text("♠ Passage en Mode Défi Cuprite !", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
                } else {
                    game.setMode(BlackjackGame.Mode.CLASSIC);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    player.sendMessage(Component.text("♠ Passage en Mode Standard (Objets) !", NamedTextColor.GOLD, TextDecoration.BOLD));
                }
                BlackjackGui.render(topInv, game);
                return;
            }

            // Bouton ENCAISSER Palier x4 (Slot 46)
            if (rawSlot == BlackjackGui.BUTTON_CASHOUT) {
                event.setCancelled(true);
                if (game.getMode() == BlackjackGame.Mode.CHALLENGE 
                        && game.getChallengeChips() >= BlackjackGame.CHALLENGE_PALIER_1 
                        && (game.getState() == BlackjackGame.State.BETTING || game.getState() == BlackjackGame.State.GAME_OVER)) {
                    game.cashoutPalier1(plugin);
                    BlackjackGui.render(topInv, game);
                    return;
                }
            }

            // Gestion selon le Mode
            if (game.getMode() == BlackjackGame.Mode.CLASSIC) {
                // En mode classique : SEUL le slot de mise en phase BETTING est modifiable par le joueur
                if (game.getState() == BlackjackGame.State.BETTING && rawSlot == BlackjackGui.BET_SLOT) {
                    ItemStack cursor = event.getCursor();
                    if (cursor != null && isDecorativePane(cursor.getType())) {
                        event.setCancelled(true);
                        return;
                    }

                    ItemStack clicked = event.getCurrentItem();
                    if (clicked != null && isDecorativePane(clicked.getType())) {
                        event.setCancelled(true);
                        topInv.setItem(BlackjackGui.BET_SLOT, null);
                        return;
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> BlackjackGui.render(topInv, game));
                    return;
                }

                // Tout autre clic dans le haut est annulé
                event.setCancelled(true);

                if (game.getState() == BlackjackGame.State.BETTING) {
                    if (rawSlot == BlackjackGui.BUTTON_START_BET) {
                        ItemStack bet = topInv.getItem(BlackjackGui.BET_SLOT);
                        if (bet != null && !bet.getType().isAir() && bet.getAmount() > 0) {
                            if (isDecorativePane(bet.getType())) {
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
                // Mode CHALLENGE : Tout clic dans la table est strictement annulé
                event.setCancelled(true);

                if (game.getState() == BlackjackGame.State.BETTING) {
                    if (game.getChallengeChips() <= 0) {
                        // Pas de session active : paiement de l'entrée au centre (Slot 22)
                        if (rawSlot == BlackjackGui.BET_SLOT) {
                            if (BlackjackGame.consumeEntryItems(player)) {
                                game.setChallengeChips(BlackjackGame.CHALLENGE_START_CHIPS);
                                game.setChallengeBet(10);
                                game.saveChallengeToPdc(plugin);
                                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.1f);
                                player.sendMessage(Component.text("✦ MISSION ACTIVÉE ! ✦ Vous recevez 100 jetons. Visez le x4 (400) ou le x8 (800) !", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
                                BlackjackGui.render(topInv, game);
                            } else {
                                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                                player.sendMessage(Component.text("§cVous n'avez pas les objets requis : " + BlackjackGame.getEntryCostDescription(player)));
                            }
                        } else if (rawSlot == BlackjackGui.BUTTON_QUIT) {
                            player.closeInventory();
                        }
                    } else {
                        // Session active : boutons de sélection de mise
                        if (rawSlot == BlackjackGui.BUTTON_CHALLENGE_BET_10) {
                            game.addChallengeBet(10);
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                            BlackjackGui.render(topInv, game);
                        } else if (rawSlot == BlackjackGui.BUTTON_CHALLENGE_BET_25) {
                            game.addChallengeBet(25);
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
                            BlackjackGui.render(topInv, game);
                        } else if (rawSlot == BlackjackGui.BUTTON_CHALLENGE_BET_50) {
                            game.addChallengeBet(50);
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.6f);
                            BlackjackGui.render(topInv, game);
                        } else if (rawSlot == BlackjackGui.BUTTON_BET_CURRENT || rawSlot == BlackjackGui.BUTTON_CHALLENGE_BET_RESET) {
                            game.setChallengeBet(Math.min(10, game.getChallengeChips()));
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.8f);
                            BlackjackGui.render(topInv, game);
                        } else if (rawSlot == BlackjackGui.BUTTON_CHALLENGE_BET_ALL_IN) {
                            game.setChallengeBet(game.getChallengeChips());
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                            BlackjackGui.render(topInv, game);
                        } else if (rawSlot == BlackjackGui.BUTTON_START_BET) {
                            int bet = game.getChallengeBet();
                            if (bet > 0 && bet <= game.getChallengeChips()) {
                                game.startChallengeHand(plugin, bet, () -> {
                                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof BlackjackGuiHolder) {
                                        BlackjackGui.render(topInv, game);
                                    }
                                });
                                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 1.2f);
                            }
                        } else if (rawSlot == BlackjackGui.BUTTON_QUIT) {
                            player.closeInventory();
                        }
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
                        BlackjackGui.render(topInv, game);
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                    } else if (rawSlot == BlackjackGui.BUTTON_QUIT) {
                        player.closeInventory();
                    }
                }
            }

        } else {
            // 2. Clics dans l'inventaire du joueur
            if (event.isShiftClick()) {
                event.setCancelled(true);
                // Si en Mode Classique et en phase BETTING et slot central libre : insérer l'item
                if (game.getMode() == BlackjackGame.Mode.CLASSIC && game.getState() == BlackjackGame.State.BETTING) {
                    ItemStack current = event.getCurrentItem();
                    if (current != null && !current.getType().isAir() && !isDecorativePane(current.getType())) {
                        ItemStack betSlotItem = topInv.getItem(BlackjackGui.BET_SLOT);
                        if (betSlotItem == null || betSlotItem.getType().isAir() || isDecorativePane(betSlotItem.getType())) {
                            topInv.setItem(BlackjackGui.BET_SLOT, current.clone());
                            event.setCurrentItem(null);
                            Bukkit.getScheduler().runTask(plugin, () -> BlackjackGui.render(topInv, game));
                        }
                    }
                }
            }
        }
    }

    private boolean isDecorativePane(Material mat) {
        if (mat == null) return false;
        return mat == Material.GREEN_STAINED_GLASS_PANE
                || mat == Material.BLACK_STAINED_GLASS_PANE
                || mat == Material.GRAY_STAINED_GLASS_PANE
                || mat == Material.YELLOW_STAINED_GLASS_PANE;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof BlackjackGuiHolder holder)) {
            return;
        }

        BlackjackGame game = holder.getGame();
        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize()) {
                if (game.getMode() != BlackjackGame.Mode.CLASSIC 
                        || game.getState() != BlackjackGame.State.BETTING 
                        || slot != BlackjackGui.BET_SLOT 
                        || isDecorativePane(event.getOldCursor().getType())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (game.getMode() == BlackjackGame.Mode.CLASSIC && game.getState() == BlackjackGame.State.BETTING) {
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

        // Si le joueur ferme en phase de mise en Mode Classique : lui restituer l'item posé dans le slot central
        if (game.getMode() == BlackjackGame.Mode.CLASSIC && game.getState() == BlackjackGame.State.BETTING) {
            ItemStack betInSlot = event.getInventory().getItem(BlackjackGui.BET_SLOT);
            if (betInSlot != null && !betInSlot.getType().isAir() && betInSlot.getAmount() > 0) {
                event.getInventory().setItem(BlackjackGui.BET_SLOT, null);
                if (!isDecorativePane(betInSlot.getType())) {
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(betInSlot);
                    for (ItemStack rem : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), rem);
                    }
                }
            }
        } else if (game.getState() == BlackjackGame.State.PLAYING) {
            // Anti-triche : si le joueur ferme pendant qu'il joue pour fuir un mauvais tirage,
            // la main est automatiquement résolue avec 'stand'.
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

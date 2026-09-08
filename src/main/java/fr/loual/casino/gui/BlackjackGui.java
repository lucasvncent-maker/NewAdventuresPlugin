package fr.loual.casino.gui;

import fr.loual.casino.BlackjackGame;
import fr.loual.casino.Card;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class BlackjackGui {

    public static final int BUTTON_MODE_SWITCH = 0;
    public static final int BET_SLOT = 22;
    public static final int BUTTON_START_BET = 49;
    public static final int BUTTON_HIT = 47;
    public static final int BUTTON_DOUBLE = 48;
    public static final int BUTTON_STAND = 51;
    public static final int BUTTON_REPLAY = 48;
    public static final int BUTTON_QUIT = 50;
    public static final int BUTTON_CASHOUT = 46;

    public static final int SLOT_CHALLENGE_STATUS = 18;
    public static final int BUTTON_CHALLENGE_BET_DECREASE = 21;
    public static final int BUTTON_BET_CURRENT = 22;
    public static final int BUTTON_CHALLENGE_BET_INCREASE = 23;

    private static final int[] DEALER_CARD_SLOTS = { 10, 11, 12, 13, 14, 15, 16 };
    private static final int[] PLAYER_CARD_SLOTS = { 28, 29, 30, 31, 32, 33, 34 };

    public static void open(Player player, BlackjackGame game) {
        BlackjackGuiHolder holder = new BlackjackGuiHolder(game);
        Inventory inv = Bukkit.createInventory(
                holder,
                54,
                Component.text("♠ Casino - Blackjack ♠", NamedTextColor.DARK_GREEN, TextDecoration.BOLD)
        );
        holder.setInventory(inv);
        render(inv, game);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
    }

    public static void render(Inventory inv, BlackjackGame game) {
        // Fond tapis vert feutré
        ItemStack greenFelt = createItem(Material.GREEN_STAINED_GLASS_PANE, Component.text(" "));
        ItemStack darkFelt = createItem(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "));

        boolean isDepositSlot = game.getState() == BlackjackGame.State.BETTING 
                && (game.getMode() == BlackjackGame.Mode.CLASSIC 
                    || (game.getMode() == BlackjackGame.Mode.CHALLENGE && game.getChallengeChips() <= 0)
                    || (game.getMode() == BlackjackGame.Mode.HORDE && game.getHordeChips() <= 0));

        for (int i = 0; i < 54; i++) {
            if (i == BET_SLOT && isDepositSlot) {
                ItemStack current = inv.getItem(BET_SLOT);
                if (current != null && (current.getType() == Material.GREEN_STAINED_GLASS_PANE 
                        || current.getType() == Material.BLACK_STAINED_GLASS_PANE 
                        || current.getType() == Material.YELLOW_STAINED_GLASS_PANE)) {
                    inv.setItem(BET_SLOT, null);
                }
                continue;
            }
            inv.setItem(i, greenFelt);
        }

        // Bords sombres (coins sauf slot 0 réservé au sélecteur de mode)
        for (int i : new int[]{ 8, 45, 53 }) {
            inv.setItem(i, darkFelt);
        }

        // Bouton Sélecteur de Mode (Slot 0)
        renderModeSwitchButton(inv, game);

        // Section CROUPIER (Lignes 0 et 1)
        renderDealerSection(inv, game);

        // Section JOUEUR (Lignes 3 et 4)
        renderPlayerSection(inv, game);

        // Section COMMANDES & ÉTATS (Ligne 5)
        renderControls(inv, game);

        // Affichage dynamique et permanent de l'ActionBar
        renderActionBar(game);
    }

    private static void renderModeSwitchButton(Inventory inv, BlackjackGame game) {
        if (game.getMode() == BlackjackGame.Mode.CLASSIC) {
            inv.setItem(BUTTON_MODE_SWITCH, createItem(
                    Material.GOLD_INGOT,
                    Component.text("Mode : Standard", NamedTextColor.GOLD, TextDecoration.BOLD),
                    "§7Misez vos items (x2, BJ x3)"
            ));
        } else if (game.getMode() == BlackjackGame.Mode.CHALLENGE) {
            ItemStack copper = createItem(
                    Material.RAW_COPPER,
                    Component.text("Mode : Défi Cuprite", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                    "§7Objectif : 400 ou 800 jetons pour la Cuprite"
            );
            ItemMeta meta = copper.getItemMeta();
            if (meta != null) {
                meta.setEnchantmentGlintOverride(true);
                copper.setItemMeta(meta);
            }
            inv.setItem(BUTTON_MODE_SWITCH, copper);
        } else {
            ItemStack redstone = createItem(
                    Material.REDSTONE,
                    Component.text("Mode : Mission Horde", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                    "§7Objectif : 300 jetons (x3) pour l'Invasion"
            );
            ItemMeta meta = redstone.getItemMeta();
            if (meta != null) {
                meta.setEnchantmentGlintOverride(true);
                redstone.setItemMeta(meta);
            }
            inv.setItem(BUTTON_MODE_SWITCH, redstone);
        }
    }

    private static void renderDealerSection(Inventory inv, BlackjackGame game) {
        ItemStack dealerHeader;
        if (game.getState() == BlackjackGame.State.BETTING) {
            if (game.getMode() == BlackjackGame.Mode.CHALLENGE) {
                dealerHeader = createItem(Material.PLAYER_HEAD,
                        Component.text("♠ Croupier - Défi Cuprite ♠", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                        "§7Objectifs : 400 (x4) ou 800 (x8) jetons"
                );
            } else if (game.getMode() == BlackjackGame.Mode.HORDE) {
                dealerHeader = createItem(Material.PLAYER_HEAD,
                        Component.text("♠ Croupier - Mission Horde ♠", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                        "§7Objectif : 300 jetons (x3) pour l'Invasion"
                );
            } else {
                dealerHeader = createItem(Material.PLAYER_HEAD,
                        Component.text("♠ Croupier ♠", NamedTextColor.GOLD, TextDecoration.BOLD),
                        "§7Le croupier tire jusqu'à 17"
                );
            }
        } else if (game.getState() == BlackjackGame.State.DEALING) {
            dealerHeader = createItem(Material.PLAYER_HEAD,
                    Component.text("♠ Distribution... ♠", NamedTextColor.YELLOW, TextDecoration.BOLD)
            );
            dealerHeader.setAmount(1);
        } else if (game.getState() == BlackjackGame.State.PLAYING) {
            int visibleScore = game.getDealerHand().isEmpty() ? 0 : game.getDealerHand().get(0).getValue();
            dealerHeader = createItem(Material.GOLD_INGOT,
                    Component.text("♠ Croupier : §e" + visibleScore + " + ? ♠", NamedTextColor.GOLD, TextDecoration.BOLD)
            );
            dealerHeader.setAmount(Math.max(1, Math.min(64, visibleScore)));
        } else if (game.getState() == BlackjackGame.State.DEALER_TURN) {
            int currentScore = BlackjackGame.calculateScore(game.getDealerHand());
            dealerHeader = createItem(Material.GOLD_BLOCK,
                    Component.text("♠ Croupier : §e" + currentScore + " ♠", NamedTextColor.GOLD, TextDecoration.BOLD)
            );
            dealerHeader.setAmount(Math.max(1, Math.min(64, currentScore)));
        } else {
            int totalScore = BlackjackGame.calculateScore(game.getDealerHand());
            String scoreText = totalScore > 21 ? "§c" + totalScore + " (Bust)" : "§a" + totalScore;
            Material mat = totalScore > 21 ? Material.REDSTONE_BLOCK : Material.GOLD_BLOCK;
            dealerHeader = createItem(mat,
                    Component.text("♠ Croupier : ", NamedTextColor.GOLD, TextDecoration.BOLD).append(Component.text(scoreText))
            );
            dealerHeader.setAmount(Math.max(1, Math.min(64, totalScore)));
        }
        inv.setItem(4, dealerHeader);

        // Affichage des cartes du croupier
        if (game.getState() == BlackjackGame.State.BETTING) {
            for (int slot : DEALER_CARD_SLOTS) {
                inv.setItem(slot, createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ")));
            }
        } else {
            List<Card> dealerHand = game.getDealerHand();
            for (int i = 0; i < DEALER_CARD_SLOTS.length; i++) {
                int slot = DEALER_CARD_SLOTS[i];
                if (i < dealerHand.size()) {
                    if (i == 1 && (game.getState() == BlackjackGame.State.PLAYING || game.getState() == BlackjackGame.State.DEALING)) {
                        inv.setItem(slot, Card.getHiddenCardItem());
                    } else {
                        inv.setItem(slot, dealerHand.get(i).toItemStack());
                    }
                } else {
                    inv.setItem(slot, createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ")));
                }
            }
        }
    }

    private static void renderPlayerSection(Inventory inv, BlackjackGame game) {
        ItemStack playerHeader;
        if (game.getState() == BlackjackGame.State.BETTING) {
            if (game.getMode() == BlackjackGame.Mode.CHALLENGE) {
                if (game.getChallengeChips() <= 0) {
                    playerHeader = createItem(Material.RAW_COPPER,
                            Component.text("✦ Défi Cuprite ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                            "§7Déposez le droit d'entrée au centre"
                    );
                } else {
                    playerHeader = createItem(Material.RAW_COPPER,
                            Component.text("✦ Solde : §a" + game.getChallengeChips() + " Jetons ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                            "§7Paliers : §6400 §8| §d800"
                    );
                }
            } else if (game.getMode() == BlackjackGame.Mode.HORDE) {
                if (game.getHordeChips() <= 0) {
                    playerHeader = createItem(Material.REDSTONE,
                            Component.text("✦ Mission Horde ✦", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                            "§7Déposez 1 Cuprite au centre"
                    );
                } else {
                    playerHeader = createItem(Material.REDSTONE,
                            Component.text("✦ Solde : §c" + game.getHordeChips() + " Jetons de Sang ✦", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                            "§7Objectif Horde : §c300 (x3)"
                    );
                }
            } else {
                playerHeader = createItem(Material.NETHER_STAR,
                        Component.text("✦ Votre Main ✦", NamedTextColor.AQUA, TextDecoration.BOLD),
                        "§7Déposez un item au centre"
                );
            }
        } else {
            int playerScore = BlackjackGame.calculateScore(game.getPlayerHand());
            String scoreColor = playerScore > 21 ? "§c" : (playerScore == 21 ? "§6" : "§a");
            Material mat = playerScore == 21 ? Material.NETHER_STAR : (playerScore > 21 ? Material.REDSTONE_BLOCK : Material.EMERALD);
            playerHeader = createItem(mat,
                    Component.text("✦ Score : " + scoreColor + playerScore + " / 21 ✦", NamedTextColor.AQUA, TextDecoration.BOLD)
            );
            playerHeader.setAmount(Math.max(1, Math.min(64, playerScore)));
        }
        inv.setItem(40, playerHeader);

        // Affichage des cartes et du centre
        if (game.getState() == BlackjackGame.State.BETTING) {
            for (int slot : PLAYER_CARD_SLOTS) {
                inv.setItem(slot, createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ")));
            }

            boolean isDeposit = (game.getMode() == BlackjackGame.Mode.CLASSIC)
                    || (game.getMode() == BlackjackGame.Mode.CHALLENGE && game.getChallengeChips() <= 0)
                    || (game.getMode() == BlackjackGame.Mode.HORDE && game.getHordeChips() <= 0);

            if (isDeposit) {
                // Cadre doré autour du slot de dépôt (Slot 22)
                for (int slot : new int[]{ 13, 21, 23, 31 }) {
                    inv.setItem(slot, createItem(Material.YELLOW_STAINED_GLASS_PANE, Component.text("§e↓ Dépôt ↓", NamedTextColor.YELLOW, TextDecoration.BOLD)));
                }

                if (game.getMode() == BlackjackGame.Mode.CHALLENGE) {
                    inv.setItem(SLOT_CHALLENGE_STATUS, createItem(Material.BOOK,
                            Component.text("§6§lRègles Défi Cuprite"),
                            "§e• Entrée : §f" + BlackjackGame.getEntryCostDescription(game.getPlayer()),
                            "§e• Départ : §a100 Jetons",
                            "§e• Palier 1 (x4) : §6400 Jetons §7(1 Cuprite)",
                            "§e• Palier 2 (x8) : §d800 Jetons §7(3 Cuprites)"
                    ));
                    for (int s : new int[]{ 19, 20, 24 }) {
                        inv.setItem(s, createItem(Material.GREEN_STAINED_GLASS_PANE, Component.text(" ")));
                    }
                } else if (game.getMode() == BlackjackGame.Mode.HORDE) {
                    inv.setItem(SLOT_CHALLENGE_STATUS, createItem(Material.BOOK,
                            Component.text("§4§lRègles Mission Horde"),
                            "§c• Entrée : §f1 Lingot de Cuprite",
                            "§c• Départ : §4100 Jetons de Sang",
                            "§c• Objectif (x3) : §4300 Jetons §7(Invasion)",
                            "§c• 4 Vagues & Boss Titan Putréfié"
                    ));
                    for (int s : new int[]{ 19, 20, 24 }) {
                        inv.setItem(s, createItem(Material.GREEN_STAINED_GLASS_PANE, Component.text(" ")));
                    }
                }

                ItemStack currentBetInSlot = inv.getItem(BET_SLOT);
                if (currentBetInSlot != null && (currentBetInSlot.getType() == Material.GREEN_STAINED_GLASS_PANE 
                        || currentBetInSlot.getType() == Material.BLACK_STAINED_GLASS_PANE 
                        || currentBetInSlot.getType() == Material.YELLOW_STAINED_GLASS_PANE
                        || currentBetInSlot.getType() == Material.SUNFLOWER
                        || currentBetInSlot.getType() == Material.REDSTONE)) {
                    inv.setItem(BET_SLOT, null);
                }
            } else if (game.getMode() == BlackjackGame.Mode.CHALLENGE) {
                inv.setItem(SLOT_CHALLENGE_STATUS, createItem(Material.EXPERIENCE_BOTTLE,
                        Component.text("✦ Progression ✦", NamedTextColor.YELLOW, TextDecoration.BOLD),
                        "§7Solde : §a" + game.getChallengeChips() + " Jetons",
                        "§7Paliers : §6400 §8| §d800"
                ));

                // Bouton Diminuer de 10 (Slot 21)
                inv.setItem(BUTTON_CHALLENGE_BET_DECREASE, createItem(Material.RED_DYE,
                        Component.text("−10 Jetons", NamedTextColor.RED, TextDecoration.BOLD)
                ));

                // Mise sélectionnée au centre (Slot 22)
                ItemStack curBet = createItem(Material.SUNFLOWER,
                        Component.text("Mise : §6" + game.getChallengeBet() + " Jetons", NamedTextColor.YELLOW, TextDecoration.BOLD),
                        "§aVictoire : +" + (game.getChallengeBet() * 2) + " §8| §6BJ : +" + (game.getChallengeBet() * 3)
                );
                curBet.setAmount(Math.max(1, Math.min(64, game.getChallengeBet())));
                inv.setItem(BET_SLOT, curBet);

                // Bouton Augmenter de 10 (Slot 23)
                inv.setItem(BUTTON_CHALLENGE_BET_INCREASE, createItem(Material.LIME_DYE,
                        Component.text("+10 Jetons", NamedTextColor.GREEN, TextDecoration.BOLD)
                ));

                for (int s : new int[]{ 13, 19, 20, 24, 25, 31 }) {
                    inv.setItem(s, createItem(Material.GREEN_STAINED_GLASS_PANE, Component.text(" ")));
                }
            } else {
                inv.setItem(SLOT_CHALLENGE_STATUS, createItem(Material.EXPERIENCE_BOTTLE,
                        Component.text("✦ Progression ✦", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                        "§7Solde : §c" + game.getHordeChips() + " Jetons de Sang",
                        "§7Objectif : §4300 Jetons (x3)"
                ));

                // Bouton Diminuer de 10 (Slot 21)
                inv.setItem(BUTTON_CHALLENGE_BET_DECREASE, createItem(Material.RED_DYE,
                        Component.text("−10 Jetons", NamedTextColor.RED, TextDecoration.BOLD)
                ));

                // Mise sélectionnée au centre (Slot 22)
                ItemStack curBet = createItem(Material.REDSTONE,
                        Component.text("Mise : §c" + game.getHordeBet() + " Jetons", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                        "§aVictoire : +" + (game.getHordeBet() * 2) + " §8| §cBJ : +" + (game.getHordeBet() * 3)
                );
                ItemMeta curMeta = curBet.getItemMeta();
                if (curMeta != null) {
                    curMeta.setEnchantmentGlintOverride(true);
                    curBet.setItemMeta(curMeta);
                }
                curBet.setAmount(Math.max(1, Math.min(64, game.getHordeBet())));
                inv.setItem(BET_SLOT, curBet);

                // Bouton Augmenter de 10 (Slot 23)
                inv.setItem(BUTTON_CHALLENGE_BET_INCREASE, createItem(Material.LIME_DYE,
                        Component.text("+10 Jetons", NamedTextColor.GREEN, TextDecoration.BOLD)
                ));

                for (int s : new int[]{ 13, 19, 20, 24, 25, 31 }) {
                    inv.setItem(s, createItem(Material.GREEN_STAINED_GLASS_PANE, Component.text(" ")));
                }
            }
        } else {
            List<Card> pHand = game.getPlayerHand();
            for (int i = 0; i < PLAYER_CARD_SLOTS.length; i++) {
                int slot = PLAYER_CARD_SLOTS[i];
                if (i < pHand.size()) {
                    inv.setItem(slot, pHand.get(i).toItemStack());
                } else {
                    inv.setItem(slot, createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ")));
                }
            }
        }
    }

    private static void renderControls(Inventory inv, BlackjackGame game) {
        // Bouton Encaissement Palier x4 (Slot 46)
        if (game.getMode() == BlackjackGame.Mode.CHALLENGE 
                && game.getChallengeChips() >= BlackjackGame.CHALLENGE_PALIER_1 
                && (game.getState() == BlackjackGame.State.BETTING || game.getState() == BlackjackGame.State.GAME_OVER)) {
            ItemStack cashout = createItem(Material.RAW_COPPER,
                    Component.text("✦ Encaisser 1 Cuprite ✦", NamedTextColor.GOLD, TextDecoration.BOLD),
                    "§7Palier x4 atteint (§e" + game.getChallengeChips() + " jetons§7)",
                    "§6Récompense : §f1 Lingot de Cuprite"
            );
            ItemMeta meta = cashout.getItemMeta();
            if (meta != null) {
                meta.setEnchantmentGlintOverride(true);
                cashout.setItemMeta(meta);
            }
            inv.setItem(BUTTON_CASHOUT, cashout);
        } else {
            inv.setItem(BUTTON_CASHOUT, createItem(Material.BLACK_STAINED_GLASS_PANE, Component.text(" ")));
        }

        if (game.getState() == BlackjackGame.State.BETTING) {
            if (game.getMode() == BlackjackGame.Mode.CLASSIC) {
                ItemStack betInSlot = inv.getItem(BET_SLOT);
                boolean hasBet = betInSlot != null && !betInSlot.getType().isAir() && betInSlot.getAmount() > 0;

                if (hasBet) {
                    Component itemNameComp = (betInSlot.getItemMeta() != null && betInSlot.getItemMeta().hasDisplayName())
                            ? betInSlot.getItemMeta().displayName()
                            : Component.translatable(betInSlot.translationKey());

                    ItemStack startBtn = new ItemStack(Material.LIME_CONCRETE);
                    ItemMeta startMeta = startBtn.getItemMeta();
                    if (startMeta != null) {
                        startMeta.displayName(Component.text("✔ Distribuer", NamedTextColor.GREEN, TextDecoration.BOLD));
                        List<Component> lore = new ArrayList<>();
                        lore.add(Component.text("Mise : ", NamedTextColor.YELLOW)
                                .append(Component.text(betInSlot.getAmount() + "x ", NamedTextColor.WHITE))
                                .append(itemNameComp));
                        lore.add(Component.text("§7Victoire : §ax2 §8| §6Blackjack : §ex3"));
                        startMeta.lore(lore);
                        startBtn.setItemMeta(startMeta);
                    }
                    inv.setItem(BUTTON_START_BET, startBtn);
                } else {
                    inv.setItem(BUTTON_START_BET, createItem(Material.GRAY_CONCRETE,
                            Component.text("En attente d'une mise...", NamedTextColor.GRAY, TextDecoration.BOLD),
                            "§7Déposez votre mise au centre."
                    ));
                }
            } else if (game.getMode() == BlackjackGame.Mode.CHALLENGE) {
                // Mode CHALLENGE
                if (game.getChallengeChips() <= 0) {
                    ItemStack deposit = inv.getItem(BET_SLOT);
                    World.Environment env = game.getPlayer().getWorld().getEnvironment();
                    boolean valid = BlackjackGame.isValidEntryItem(deposit, env);

                    if (valid) {
                        String costName = (deposit.getType() == Material.DRAGON_HEAD) ? "1x Tête de Dragon" : (BlackjackGame.GILDED_BLACKSTONE_COST + "x Pierres Dorées");
                        inv.setItem(BUTTON_START_BET, createItem(Material.LIME_CONCRETE,
                                Component.text("✔ Démarrer le Défi", NamedTextColor.GREEN, TextDecoration.BOLD),
                                "§eEntrée : §f" + costName,
                                "§7Capital : §a100 Jetons §8(Palier: 400 | Jackpot: 800)"
                        ));
                    } else {
                        inv.setItem(BUTTON_START_BET, createItem(Material.GRAY_CONCRETE,
                                Component.text("Droit d'entrée requis", NamedTextColor.GRAY, TextDecoration.BOLD),
                                "§7Déposez " + BlackjackGame.getEntryCostDescription(game.getPlayer())
                        ));
                    }
                } else {
                    inv.setItem(BUTTON_START_BET, createItem(Material.LIME_CONCRETE,
                            Component.text("✔ Distribuer", NamedTextColor.GREEN, TextDecoration.BOLD),
                            "§eMise : §f" + game.getChallengeBet() + " Jetons",
                            "§7Victoire : §ax2 §8| §6Blackjack : §ex3"
                    ));
                }
            } else {
                // Mode HORDE
                if (game.getHordeChips() <= 0) {
                    ItemStack deposit = inv.getItem(BET_SLOT);
                    org.bukkit.plugin.Plugin currentPlugin = Bukkit.getPluginManager().getPlugin("NewAdventurePlugin");
                    boolean valid = BlackjackGame.isValidHordeEntryItem(currentPlugin, deposit);

                    if (valid) {
                        inv.setItem(BUTTON_START_BET, createItem(Material.LIME_CONCRETE,
                                Component.text("✔ Démarrer la Mission Horde", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                                "§eEntrée : §f1x Lingot de Cuprite",
                                "§7Capital : §c100 Jetons §8(Objectif Invasion : 300)"
                        ));
                    } else {
                        inv.setItem(BUTTON_START_BET, createItem(Material.GRAY_CONCRETE,
                                Component.text("Lingot de Cuprite requis", NamedTextColor.GRAY, TextDecoration.BOLD),
                                "§7Déposez §61 Lingot de Cuprite §7au centre."
                        ));
                    }
                } else {
                    inv.setItem(BUTTON_START_BET, createItem(Material.LIME_CONCRETE,
                            Component.text("✔ Distribuer", NamedTextColor.GREEN, TextDecoration.BOLD),
                            "§eMise : §c" + game.getHordeBet() + " Jetons de Sang",
                            "§7Victoire : §ax2 §8| §6Blackjack : §ex3"
                    ));
                }
            }

            inv.setItem(BUTTON_QUIT, createItem(Material.BARRIER,
                    Component.text("Quitter", NamedTextColor.RED, TextDecoration.BOLD),
                    "§7Fermer et sauvegarder vos jetons."
            ));

        } else if (game.getState() == BlackjackGame.State.DEALING) {
            inv.setItem(BUTTON_HIT, createItem(Material.GRAY_CONCRETE,
                    Component.text("Distribution...", NamedTextColor.YELLOW, TextDecoration.BOLD),
                    "§7Veuillez patienter."
            ));
            inv.setItem(BUTTON_STAND, createItem(Material.GRAY_CONCRETE,
                    Component.text("Distribution...", NamedTextColor.YELLOW, TextDecoration.BOLD),
                    "§7Veuillez patienter."
            ));

        } else if (game.getState() == BlackjackGame.State.DEALER_TURN) {
            inv.setItem(BUTTON_HIT, createItem(Material.GRAY_CONCRETE,
                    Component.text("Tour du Croupier...", NamedTextColor.GOLD, TextDecoration.BOLD),
                    "§7Le croupier tire ses cartes."
            ));
            inv.setItem(BUTTON_STAND, createItem(Material.GRAY_CONCRETE,
                    Component.text("Tour du Croupier...", NamedTextColor.GOLD, TextDecoration.BOLD),
                    "§7Arrêt à 17 ou plus."
            ));

        } else if (game.getState() == BlackjackGame.State.PLAYING) {
            int pScore = BlackjackGame.calculateScore(game.getPlayerHand());

            // Bouton Tirer (Hit - Slot 47)
            ItemStack hitBtn = createItem(Material.LIME_CONCRETE,
                    Component.text("➤ Tirer  §e[" + pScore + "]", NamedTextColor.GREEN, TextDecoration.BOLD),
                    "§7Prendre une carte supplémentaire."
            );
            hitBtn.setAmount(Math.max(1, Math.min(64, pScore)));
            inv.setItem(BUTTON_HIT, hitBtn);

            // Bouton Doubler (Double Down - Slot 48) : Disponible sur les 2 premières cartes
            if (game.getPlayerHand().size() == 2) {
                boolean canDouble = game.canDoubleDown();
                if (canDouble) {
                    ItemStack doubleBtn = createItem(Material.GOLD_BLOCK,
                            Component.text("✦ Doubler", NamedTextColor.GOLD, TextDecoration.BOLD),
                            "§7Double la mise pour 1 seule carte."
                    );
                    inv.setItem(BUTTON_DOUBLE, doubleBtn);
                } else {
                    ItemStack doubleBtn = createItem(Material.GRAY_CONCRETE,
                            Component.text("✦ Doubler", NamedTextColor.GRAY, TextDecoration.BOLD),
                            "§cSolde insuffisant."
                    );
                    inv.setItem(BUTTON_DOUBLE, doubleBtn);
                }
            } else {
                inv.setItem(BUTTON_DOUBLE, createItem(Material.GREEN_STAINED_GLASS_PANE, Component.text(" ")));
            }

            // Rappel de la mise (Slot 49)
            if (game.getMode() == BlackjackGame.Mode.CLASSIC) {
                ItemStack bet = game.getBetItem();
                if (bet != null) {
                    ItemStack betDisplay = bet.clone();
                    ItemMeta meta = betDisplay.getItemMeta();
                    if (meta != null) {
                        List<Component> lore = new ArrayList<>();
                        lore.add(Component.text("§6Mise en jeu : §e" + bet.getAmount() + "x"));
                        lore.add(Component.text("§7Victoire : §ax2 §8| §6Blackjack : §ex3"));
                        meta.lore(lore);
                        betDisplay.setItemMeta(meta);
                    }
                    inv.setItem(49, betDisplay);
                }
            } else if (game.getMode() == BlackjackGame.Mode.CHALLENGE) {
                ItemStack betDisplay = createItem(Material.SUNFLOWER,
                        Component.text("Mise en jeu : §6" + game.getActiveChallengeBet() + " Jetons", NamedTextColor.YELLOW, TextDecoration.BOLD),
                        "§7Victoire : §ax2 §8| §6Blackjack : §ex3"
                );
                betDisplay.setAmount(Math.max(1, Math.min(64, game.getActiveChallengeBet())));
                inv.setItem(49, betDisplay);
            } else {
                ItemStack betDisplay = createItem(Material.REDSTONE,
                        Component.text("Mise en jeu : §c" + game.getActiveHordeBet() + " Jetons de Sang", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                        "§7Victoire : §ax2 §8| §6Blackjack : §ex3"
                );
                ItemMeta meta = betDisplay.getItemMeta();
                if (meta != null) {
                    meta.setEnchantmentGlintOverride(true);
                    betDisplay.setItemMeta(meta);
                }
                betDisplay.setAmount(Math.max(1, Math.min(64, game.getActiveHordeBet())));
                inv.setItem(49, betDisplay);
            }

            // Bouton Rester (Stand - Slot 51)
            ItemStack standBtn = createItem(Material.RED_CONCRETE,
                    Component.text("■ Rester  §e[" + pScore + "]", NamedTextColor.RED, TextDecoration.BOLD),
                    "§7Garder votre main."
            );
            standBtn.setAmount(Math.max(1, Math.min(64, pScore)));
            inv.setItem(BUTTON_STAND, standBtn);

        } else if (game.getState() == BlackjackGame.State.GAME_OVER) {
            // Résultat au centre (Slot 49)
            if (game.getMode() == BlackjackGame.Mode.CHALLENGE && game.isJackpotWon()) {
                inv.setItem(49, createItem(Material.TOTEM_OF_UNDYING,
                        Component.text("✦ Jackpot x8 Atteint ! ✦", NamedTextColor.GOLD, TextDecoration.BOLD),
                        "§aObjectif 800 jetons atteint !",
                        "§63 Lingots de Cuprite §areçus !"
                ));
            } else if (game.getMode() == BlackjackGame.Mode.CHALLENGE && game.getChallengeChips() <= 0) {
                inv.setItem(49, createItem(Material.REDSTONE_BLOCK,
                        Component.text("✘ Faillite ✘", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                        "§c0 jeton restant."
                ));
            } else if (game.getMode() == BlackjackGame.Mode.HORDE && game.getHordeChips() <= 0) {
                inv.setItem(49, createItem(Material.REDSTONE_BLOCK,
                        Component.text("✘ Faillite ✘", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                        "§c0 jeton de sang restant."
                ));
            } else {
                switch (game.getResult()) {
                    case PLAYER_BLACKJACK -> inv.setItem(49, createItem(Material.TOTEM_OF_UNDYING,
                            Component.text("✦ Blackjack Naturel ! ✦", NamedTextColor.GOLD, TextDecoration.BOLD),
                            "§aMise triplée (x3) !"
                    ));
                    case FIVE_CARD_CHARLIE -> inv.setItem(49, createItem(Material.TOTEM_OF_UNDYING,
                            Component.text("✦ 5-Card Charlie ! ✦", NamedTextColor.GOLD, TextDecoration.BOLD),
                            "§a5 cartes sans dépasser 21 (x2) !"
                    ));
                    case PLAYER_WIN, DEALER_BUST -> inv.setItem(49, createItem(Material.EMERALD_BLOCK,
                            Component.text("✔ Victoire ! ✔", NamedTextColor.GREEN, TextDecoration.BOLD),
                            "§aMise doublée (x2) !"
                    ));
                    case PUSH -> inv.setItem(49, createItem(Material.GOLD_BLOCK,
                            Component.text("═ Égalité ═", NamedTextColor.YELLOW, TextDecoration.BOLD),
                            "§eMise restituée."
                    ));
                    case PLAYER_BUST, DEALER_WIN -> inv.setItem(49, createItem(Material.REDSTONE_BLOCK,
                            Component.text("✘ Défaite ✘", NamedTextColor.RED, TextDecoration.BOLD),
                            "§cMise perdue."
                    ));
                    default -> {}
                }
            }

            // Bouton Rejouer / Menu Principal (Slot 48)
            boolean isDefeat = game.getResult() == BlackjackGame.Result.PLAYER_BUST || game.getResult() == BlackjackGame.Result.DEALER_WIN;

            if (game.getMode() == BlackjackGame.Mode.CLASSIC) {
                if (isDefeat) {
                    inv.setItem(BUTTON_REPLAY, createItem(Material.BARRIER,
                            Component.text("Accueil (Standard)", NamedTextColor.YELLOW, TextDecoration.BOLD),
                            "§7Défaite. Revenir à l'accueil."
                    ));
                } else {
                    inv.setItem(BUTTON_REPLAY, createItem(Material.GOLD_INGOT,
                            Component.text("♠ Nouvelle mise", NamedTextColor.YELLOW, TextDecoration.BOLD),
                            "§7Remiser un item."
                    ));
                }
            } else if (game.getMode() == BlackjackGame.Mode.CHALLENGE) {
                if (!isDefeat && game.getChallengeChips() > 0 && !game.isJackpotWon()) {
                    inv.setItem(BUTTON_REPLAY, createItem(Material.GOLD_INGOT,
                            Component.text("♠ Manche suivante", NamedTextColor.YELLOW, TextDecoration.BOLD),
                            "§7Solde : §a" + game.getChallengeChips() + " Jetons"
                    ));
                } else {
                    inv.setItem(BUTTON_REPLAY, createItem(Material.BARRIER,
                            Component.text("Menu Principal", NamedTextColor.RED, TextDecoration.BOLD),
                            "§7Session terminée.",
                            "§7Revenir au mode Standard."
                    ));
                }
            } else {
                if (!isDefeat && game.getHordeChips() > 0) {
                    inv.setItem(BUTTON_REPLAY, createItem(Material.REDSTONE,
                            Component.text("♠ Manche suivante", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                            "§7Solde : §c" + game.getHordeChips() + " Jetons de Sang"
                    ));
                } else {
                    inv.setItem(BUTTON_REPLAY, createItem(Material.BARRIER,
                            Component.text("Menu Principal", NamedTextColor.RED, TextDecoration.BOLD),
                            "§7Session terminée.",
                            "§7Revenir au mode Standard."
                    ));
                }
            }

            // Bouton Quitter
            inv.setItem(BUTTON_QUIT, createItem(Material.BARRIER,
                    Component.text("Quitter", NamedTextColor.RED, TextDecoration.BOLD),
                    "§7Fermer la table."
            ));
        }
    }

    private static void renderActionBar(BlackjackGame game) {
        if (game.getState() == BlackjackGame.State.BETTING) {
            if (game.getMode() == BlackjackGame.Mode.CHALLENGE) {
                game.getPlayer().sendActionBar(Component.text("§d[Défi Cuprite] §fSolde : §a§l" + game.getChallengeChips() + " Jetons §8| §fObjectifs : §6400 (x4) §8- §d800 (x8)"));
                return;
            } else if (game.getMode() == BlackjackGame.Mode.HORDE) {
                game.getPlayer().sendActionBar(Component.text("§4[Mission Horde] §fSolde : §c§l" + game.getHordeChips() + " Jetons de Sang §8| §fObjectif : §c§l300 (x3 pour l'Invasion)"));
                return;
            }
        }

        if (game.getState() == BlackjackGame.State.DEALING) {
            int pScore = BlackjackGame.calculateScore(game.getPlayerHand());
            game.getPlayer().sendActionBar(Component.text("§e♠ Distribution des cartes... §7[Votre score: §a§l" + pScore + "§7] ♠"));
        } else if (game.getState() == BlackjackGame.State.DEALER_TURN) {
            int pScore = BlackjackGame.calculateScore(game.getPlayerHand());
            int dScore = BlackjackGame.calculateScore(game.getDealerHand());
            game.getPlayer().sendActionBar(Component.text("§6♠ Le Croupier joue sa main... §7[Votre score: §a§l" + pScore + " §7| Croupier: §e§l" + dScore + "§7] ♠"));
        } else if (game.getState() == BlackjackGame.State.PLAYING || game.getState() == BlackjackGame.State.GAME_OVER) {
            int pScore = BlackjackGame.calculateScore(game.getPlayerHand());
            String pColor = pScore > 21 ? "§c" : (pScore == 21 ? "§6" : "§a");
            String dText;
            if (game.getState() == BlackjackGame.State.PLAYING) {
                int visible = game.getDealerHand().isEmpty() ? 0 : game.getDealerHand().get(0).getValue();
                dText = "§e" + visible + " + ?";
            } else {
                int dScore = BlackjackGame.calculateScore(game.getDealerHand());
                dText = (dScore > 21 ? "§c" : "§e") + dScore + (dScore > 21 ? " §c(Bust)" : "");
            }
            String suffix = "";
            if (game.getMode() == BlackjackGame.Mode.CHALLENGE) {
                suffix = " §8| §dJetons: §a§l" + game.getChallengeChips();
            } else if (game.getMode() == BlackjackGame.Mode.HORDE) {
                suffix = " §8| §4Jetons Sang: §c§l" + game.getHordeChips();
            }
            game.getPlayer().sendActionBar(Component.text("§f✦ Votre Score : " + pColor + "§l" + pScore + " §8| §fCroupier : " + dText + " ✦" + suffix));
        }
    }

    private static ItemStack createItem(Material mat, Component name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (loreLines.length > 0) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(Component.text(line));
                }
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}

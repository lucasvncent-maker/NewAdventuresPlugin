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
    public static final int BUTTON_CHALLENGE_BET_10 = 19;
    public static final int BUTTON_CHALLENGE_BET_25 = 20;
    public static final int BUTTON_CHALLENGE_BET_50 = 21;
    public static final int BUTTON_BET_CURRENT = 22;
    public static final int BUTTON_CHALLENGE_BET_ALL_IN = 23;
    public static final int BUTTON_CHALLENGE_BET_RESET = 24;

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
                && (game.getMode() == BlackjackGame.Mode.CLASSIC || game.getChallengeChips() <= 0);

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
                    Component.text("Mode : Standard (Objets)", NamedTextColor.GOLD, TextDecoration.BOLD),
                    "§7Pariez n'importe quel objet de votre inventaire.",
                    "§aVictoire standard : §fLe double (x2) !",
                    "§6Blackjack naturel : §ePayé 3 pour 1 (x3) !",
                    "",
                    "§d➤ Cliquer pour passer en Mode Défi Cuprite"
            ));
        } else {
            ItemStack copper = createItem(
                    Material.RAW_COPPER,
                    Component.text("Mode : Défi Cuprite", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                    "§7Mission spéciale du Croupier !",
                    "§7Multipliez vos jetons pour remporter de la Cuprite.",
                    "",
                    "§a➤ Cliquer pour repasser en Mode Standard"
            );
            ItemMeta meta = copper.getItemMeta();
            if (meta != null) {
                meta.setEnchantmentGlintOverride(true);
                copper.setItemMeta(meta);
            }
            inv.setItem(BUTTON_MODE_SWITCH, copper);
        }
    }

    private static void renderDealerSection(Inventory inv, BlackjackGame game) {
        ItemStack dealerHeader;
        if (game.getState() == BlackjackGame.State.BETTING) {
            if (game.getMode() == BlackjackGame.Mode.CHALLENGE) {
                if (game.getChallengeChips() <= 0) {
                    dealerHeader = createItem(Material.PLAYER_HEAD,
                            Component.text("♠ Croupier - Mission Cuprite ♠", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                            "§7\"Relevez le défi du casino !\"",
                            "§7Déposez le droit d'entrée au centre pour débuter."
                    );
                } else {
                    dealerHeader = createItem(Material.PLAYER_HEAD,
                            Component.text("♠ Croupier - Mission Cuprite ♠", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                            "§7\"Objectif : 400 (x4) ou 800 (x8) jetons !\"",
                            "§7Sélectionnez votre mise et lancez la main."
                    );
                }
            } else {
                dealerHeader = createItem(Material.PLAYER_HEAD,
                        Component.text("♠ Croupier du Casino ♠", NamedTextColor.GOLD, TextDecoration.BOLD),
                        "§7Le croupier tire jusqu'à §e17§7.",
                        "§7Déposez votre mise pour commencer !"
                );
            }
        } else if (game.getState() == BlackjackGame.State.DEALING) {
            dealerHeader = createItem(Material.PLAYER_HEAD,
                    Component.text("♠ Distribution des Cartes... ♠", NamedTextColor.YELLOW, TextDecoration.BOLD),
                    "§7Le croupier distribue une à une les cartes.",
                    "§7Veuillez patienter..."
            );
            dealerHeader.setAmount(1);
        } else if (game.getState() == BlackjackGame.State.PLAYING) {
            int visibleScore = game.getDealerHand().isEmpty() ? 0 : game.getDealerHand().get(0).getValue();
            dealerHeader = createItem(Material.GOLD_INGOT,
                    Component.text("♠ Croupier - Score visible : " + visibleScore + " + ? ♠", NamedTextColor.GOLD, TextDecoration.BOLD),
                    "§7Une carte est encore masquée.",
                    "§7Elle sera révélée lorsque vous ferez §cRester (Stand)§7."
            );
            dealerHeader.setAmount(Math.max(1, Math.min(64, visibleScore)));
        } else if (game.getState() == BlackjackGame.State.DEALER_TURN) {
            int currentScore = BlackjackGame.calculateScore(game.getDealerHand());
            dealerHeader = createItem(Material.GOLD_BLOCK,
                    Component.text("♠ Croupier en jeu - Score : §e" + currentScore + " ♠", NamedTextColor.GOLD, TextDecoration.BOLD),
                    "§7Le croupier tire ses cartes une à une...",
                    "§7Il s'arrête dès qu'il atteint 17 ou plus."
            );
            dealerHeader.setAmount(Math.max(1, Math.min(64, currentScore)));
        } else {
            int totalScore = BlackjackGame.calculateScore(game.getDealerHand());
            String scoreText = totalScore > 21 ? "§c" + totalScore + " (BUST)" : "§a" + totalScore;
            Material mat = totalScore > 21 ? Material.REDSTONE_BLOCK : Material.GOLD_BLOCK;
            dealerHeader = createItem(mat,
                    Component.text("♠ Croupier - Score final : ", NamedTextColor.GOLD, TextDecoration.BOLD).append(Component.text(scoreText)),
                    "§7Fin de la manche."
            );
            dealerHeader.setAmount(Math.max(1, Math.min(64, totalScore)));
        }
        inv.setItem(4, dealerHeader);

        // Affichage des cartes du croupier
        if (game.getState() == BlackjackGame.State.BETTING) {
            for (int slot : DEALER_CARD_SLOTS) {
                inv.setItem(slot, createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text("§8[ Emplacement Croupier ]")));
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
                            Component.text("✦ Inscription au Défi Cuprite ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                            "§7Aucune session active.",
                            "§7Déposez votre droit d'entrée au centre !"
                    );
                } else {
                    playerHeader = createItem(Material.RAW_COPPER,
                            Component.text("✦ Solde : §a§l" + game.getChallengeChips() + " Jetons ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                            "§7Palier x4 : §6400 Jetons §7(1 Cuprite)",
                            "§7Palier x8 : §d800 Jetons §7(3 Cuprites)"
                    );
                }
            } else {
                playerHeader = createItem(Material.NETHER_STAR,
                        Component.text("✦ Votre Main ✦", NamedTextColor.AQUA, TextDecoration.BOLD),
                        "§7Placez l'item que vous souhaitez parier",
                        "§7dans le slot doré au milieu de la table !"
                );
            }
        } else {
            int playerScore = BlackjackGame.calculateScore(game.getPlayerHand());
            String scoreColor = playerScore > 21 ? "§c" : (playerScore == 21 ? "§6" : "§a");
            Material mat = playerScore == 21 ? Material.NETHER_STAR : (playerScore > 21 ? Material.REDSTONE_BLOCK : Material.EMERALD);
            playerHeader = createItem(mat,
                    Component.text("✦ Score du Joueur : " + scoreColor + playerScore + " / 21 ✦", NamedTextColor.AQUA, TextDecoration.BOLD),
                    playerScore == 21 ? "§6§l✦ BLACKJACK ! ✦" : (playerScore > 21 ? "§c§lVous avez sauté (Bust) !" : "§7Objectif : Se rapprocher de 21 sans dépasser.")
            );
            playerHeader.setAmount(Math.max(1, Math.min(64, playerScore)));
        }
        inv.setItem(40, playerHeader);

        // Affichage des cartes et du centre
        if (game.getState() == BlackjackGame.State.BETTING) {
            for (int slot : PLAYER_CARD_SLOTS) {
                inv.setItem(slot, createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text("§8[ Emplacement Joueur ]")));
            }

            if (game.getMode() == BlackjackGame.Mode.CLASSIC || game.getChallengeChips() <= 0) {
                // Cadre doré autour du slot de dépôt (Slot 22)
                for (int slot : new int[]{ 13, 21, 23, 31 }) {
                    String borderText = (game.getMode() == BlackjackGame.Mode.CLASSIC)
                            ? "§e↓ DÉPOSEZ VOTRE MISE ICI ↓"
                            : "§e↓ DÉPOSEZ LE DROIT D'ENTRÉE ICI ↓";
                    inv.setItem(slot, createItem(Material.YELLOW_STAINED_GLASS_PANE, Component.text(borderText, NamedTextColor.YELLOW, TextDecoration.BOLD)));
                }

                if (game.getMode() == BlackjackGame.Mode.CHALLENGE) {
                    // Bannière des règles sur slot 18
                    inv.setItem(SLOT_CHALLENGE_STATUS, createItem(Material.BOOK,
                            Component.text("§6§lRègles du Défi Cuprite"),
                            "§e• Droit d'entrée : §f" + BlackjackGame.getEntryCostDescription(game.getPlayer()),
                            "§e• Départ : §a100 Jetons de défi",
                            "§e• Palier x4 (400 jetons) : §61 Lingot de Cuprite",
                            "§e• Palier x8 (800 jetons) : §d3 Lingots de Cuprite !",
                            "§c• Faillite (0 jeton) : Mise d'entrée perdue !"
                    ));
                    for (int s : new int[]{ 19, 20, 24 }) {
                        inv.setItem(s, createItem(Material.GREEN_STAINED_GLASS_PANE, Component.text(" ")));
                    }
                }

                ItemStack currentBetInSlot = inv.getItem(BET_SLOT);
                if (currentBetInSlot != null && (currentBetInSlot.getType() == Material.GREEN_STAINED_GLASS_PANE 
                        || currentBetInSlot.getType() == Material.BLACK_STAINED_GLASS_PANE 
                        || currentBetInSlot.getType() == Material.YELLOW_STAINED_GLASS_PANE)) {
                    inv.setItem(BET_SLOT, null);
                }
            } else {
                // Mode CHALLENGE avec session active : boutons de mise en jetons
                inv.setItem(SLOT_CHALLENGE_STATUS, createItem(Material.EXPERIENCE_BOTTLE,
                        Component.text("✦ Progression Mission ✦", NamedTextColor.YELLOW, TextDecoration.BOLD),
                        "§7Solde actuel : §a§l" + game.getChallengeChips() + " Jetons",
                        "§7Palier x4 : §6400 Jetons §7(§61 Cuprite§7)",
                        "§7Palier x8 : §d800 Jetons §7(§d3 Cuprites§7)"
                ));

                inv.setItem(BUTTON_CHALLENGE_BET_10, createItem(Material.IRON_NUGGET,
                        Component.text("+10 Jetons", NamedTextColor.WHITE, TextDecoration.BOLD),
                        "§7Ajouter 10 jetons à la mise."
                ));

                inv.setItem(BUTTON_CHALLENGE_BET_25, createItem(Material.GOLD_NUGGET,
                        Component.text("+25 Jetons", NamedTextColor.GOLD, TextDecoration.BOLD),
                        "§7Ajouter 25 jetons à la mise."
                ));

                inv.setItem(BUTTON_CHALLENGE_BET_50, createItem(Material.DIAMOND,
                        Component.text("+50 Jetons", NamedTextColor.AQUA, TextDecoration.BOLD),
                        "§7Ajouter 50 jetons à la mise."
                ));

                ItemStack curBet = createItem(Material.SUNFLOWER,
                        Component.text("Mise sélectionnée : §6§l" + game.getChallengeBet() + " Jetons", NamedTextColor.YELLOW, TextDecoration.BOLD),
                        "§7Solde restant en cas de défaite : §f" + (game.getChallengeChips() - game.getChallengeBet()) + " Jetons",
                        "§7Victoire normale : §a+" + (game.getChallengeBet() * 2) + " Jetons (x2)",
                        "§6Blackjack naturel (x3) : §e+" + (game.getChallengeBet() * 3) + " Jetons",
                        "",
                        "§e➤ Cliquer pour réinitialiser au minimum (10)"
                );
                curBet.setAmount(Math.max(1, Math.min(64, game.getChallengeBet())));
                inv.setItem(BET_SLOT, curBet);

                inv.setItem(BUTTON_CHALLENGE_BET_ALL_IN, createItem(Material.NETHERITE_SCRAP,
                        Component.text("§c§lALL-IN (" + game.getChallengeChips() + " Jetons)", NamedTextColor.RED, TextDecoration.BOLD),
                        "§cMiser la totalité de vos jetons restants !",
                        "§4Quitte ou double !"
                ));

                inv.setItem(BUTTON_CHALLENGE_BET_RESET, createItem(Material.REDSTONE,
                        Component.text("Mise minimale (10)", NamedTextColor.RED, TextDecoration.BOLD),
                        "§7Réduire la mise à 10 jetons."
                ));

                inv.setItem(13, createItem(Material.GREEN_STAINED_GLASS_PANE, Component.text(" ")));
                inv.setItem(31, createItem(Material.GREEN_STAINED_GLASS_PANE, Component.text(" ")));
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
                    Component.text("✦ ENCAISSER (Palier x4) ✦", NamedTextColor.GOLD, TextDecoration.BOLD),
                    "§aVous avez atteint au moins 400 jetons !",
                    "§7Solde actuel : §e" + game.getChallengeChips() + " Jetons",
                    "§7Récompense garantie : §61 Lingot de Cuprite",
                    "",
                    "§a➤ Cliquez pour sécuriser 1 Cuprite et terminer la mission",
                    "§7(Ou continuez à jouer pour viser les 800 jetons et 3 Cuprites !)"
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
                        startMeta.displayName(Component.text("✔ Valider la mise & Distribuer", NamedTextColor.GREEN, TextDecoration.BOLD));
                        List<Component> lore = new ArrayList<>();
                        lore.add(Component.text("Mise : ", NamedTextColor.YELLOW)
                                .append(Component.text(betInSlot.getAmount() + "x ", NamedTextColor.WHITE))
                                .append(itemNameComp));
                        lore.add(Component.text("Victoire standard : Le double (x2) !", NamedTextColor.GREEN));
                        lore.add(Component.text("Blackjack (21 naturel) : Payé 3 pour 1 (x3) !", NamedTextColor.GOLD));
                        lore.add(Component.text("Défaite : Votre mise est perdue.", NamedTextColor.RED));
                        lore.add(Component.empty());
                        lore.add(Component.text("➤ Cliquez pour lancer la partie !", NamedTextColor.GREEN));
                        startMeta.lore(lore);
                        startBtn.setItemMeta(startMeta);
                    }
                    inv.setItem(BUTTON_START_BET, startBtn);
                } else {
                    inv.setItem(BUTTON_START_BET, createItem(Material.GRAY_CONCRETE,
                            Component.text("En attente de mise...", NamedTextColor.GRAY, TextDecoration.BOLD),
                            "§7Déposez un item de votre inventaire",
                            "§7dans le slot central pour activer ce bouton."
                    ));
                }
            } else {
                // Mode CHALLENGE
                if (game.getChallengeChips() <= 0) {
                    ItemStack deposit = inv.getItem(BET_SLOT);
                    World.Environment env = game.getPlayer().getWorld().getEnvironment();
                    boolean valid = BlackjackGame.isValidEntryItem(deposit, env);

                    if (valid) {
                        String costName = (deposit.getType() == Material.DRAGON_HEAD) ? "1x Tête de Dragon" : (BlackjackGame.GILDED_BLACKSTONE_COST + "x Pierres Noires Dorées");
                        inv.setItem(BUTTON_START_BET, createItem(Material.LIME_CONCRETE,
                                Component.text("✔ Valider & Démarrer la Mission", NamedTextColor.GREEN, TextDecoration.BOLD),
                                "§eEntrée : §f" + costName,
                                "§7Capital initial : §a100 Jetons de défi",
                                "§7Palier x4 (400 jetons) : §61 Lingot de Cuprite",
                                "§7Palier x8 (800 jetons) : §d3 Lingots de Cuprite !",
                                "",
                                "§a➤ Cliquez pour payer et démarrer la session !"
                        ));
                    } else {
                        inv.setItem(BUTTON_START_BET, createItem(Material.GRAY_CONCRETE,
                                Component.text("En attente du droit d'entrée...", NamedTextColor.GRAY, TextDecoration.BOLD),
                                "§7Déposez " + BlackjackGame.getEntryCostDescription(game.getPlayer()),
                                "§7dans le slot central doré pour activer l'entrée."
                        ));
                    }
                } else {
                    inv.setItem(BUTTON_START_BET, createItem(Material.LIME_CONCRETE,
                            Component.text("✔ Valider la mise & Distribuer", NamedTextColor.GREEN, TextDecoration.BOLD),
                            "§eMise : §f" + game.getChallengeBet() + " Jetons",
                            "§7Victoire : §a+" + (game.getChallengeBet() * 2) + " Jetons (x2)",
                            "§6Blackjack (21 naturel) : §e+" + (game.getChallengeBet() * 3) + " Jetons (x3)",
                            "§cDéfaite : §7Perte de vos " + game.getChallengeBet() + " Jetons.",
                            "",
                            "§a➤ Cliquez pour lancer la manche !"
                    ));
                }
            }

            inv.setItem(BUTTON_QUIT, createItem(Material.BARRIER,
                    Component.text("Quitter la table", NamedTextColor.RED, TextDecoration.BOLD),
                    "§7Ferme le casino et sauvegarde vos jetons."
            ));

        } else if (game.getState() == BlackjackGame.State.DEALING) {
            inv.setItem(BUTTON_HIT, createItem(Material.GRAY_CONCRETE,
                    Component.text("Distribution en cours...", NamedTextColor.YELLOW, TextDecoration.BOLD),
                    "§7Le croupier distribue les cartes une à une...",
                    "§7Veuillez patienter."
            ));
            inv.setItem(BUTTON_STAND, createItem(Material.GRAY_CONCRETE,
                    Component.text("Distribution en cours...", NamedTextColor.YELLOW, TextDecoration.BOLD),
                    "§7Le croupier distribue les cartes une à une...",
                    "§7Veuillez patienter."
            ));

        } else if (game.getState() == BlackjackGame.State.DEALER_TURN) {
            inv.setItem(BUTTON_HIT, createItem(Material.GRAY_CONCRETE,
                    Component.text("Tour du Croupier...", NamedTextColor.GOLD, TextDecoration.BOLD),
                    "§7Le croupier tire ses cartes une à une..."
            ));
            inv.setItem(BUTTON_STAND, createItem(Material.GRAY_CONCRETE,
                    Component.text("Tour du Croupier...", NamedTextColor.GOLD, TextDecoration.BOLD),
                    "§7Le croupier s'arrête à 17 ou plus."
            ));

        } else if (game.getState() == BlackjackGame.State.PLAYING) {
            int pScore = BlackjackGame.calculateScore(game.getPlayerHand());

            // Bouton Tirer (Hit - Slot 47)
            ItemStack hitBtn = createItem(Material.LIME_CONCRETE,
                    Component.text("➤ TIRER (Hit)  §e[" + pScore + "/21]", NamedTextColor.GREEN, TextDecoration.BOLD),
                    "§7Prendre une carte supplémentaire.",
                    "§7Votre score actuel : §e" + pScore + "§7/21",
                    "§cAttention si votre score dépasse 21 !"
            );
            hitBtn.setAmount(Math.max(1, Math.min(64, pScore)));
            inv.setItem(BUTTON_HIT, hitBtn);

            // Bouton Doubler (Double Down - Slot 48) : Disponible sur les 2 premières cartes
            if (game.getPlayerHand().size() == 2) {
                boolean canDouble = game.canDoubleDown();
                if (canDouble) {
                    ItemStack doubleBtn = createItem(Material.GOLD_BLOCK,
                            Component.text("✦ DOUBLER (Double Down) ✦", NamedTextColor.GOLD, TextDecoration.BOLD),
                            "§7Double votre mise pour cette manche.",
                            "§eVous ne recevrez qu'1 seule carte supplémentaire !",
                            "§7Puis le croupier jouera immédiatement.",
                            "",
                            "§a➤ Cliquez pour doubler votre mise !"
                    );
                    inv.setItem(BUTTON_DOUBLE, doubleBtn);
                } else {
                    ItemStack doubleBtn = createItem(Material.GRAY_CONCRETE,
                            Component.text("✦ DOUBLER (Double Down) ✦", NamedTextColor.GRAY, TextDecoration.BOLD),
                            "§7Double la mise actuelle pour 1 seule carte.",
                            "",
                            "§cRessources insuffisantes pour doubler."
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
                        lore.add(Component.text("§6Mise actuelle en jeu : §e" + bet.getAmount() + "x"));
                        lore.add(Component.text("§aVictoire standard = Double (x2)"));
                        lore.add(Component.text("§6Blackjack (21 naturel) = Triple (3:1 / x3)"));
                        lore.add(Component.text("§cDéfaite = Mise perdue"));
                        meta.lore(lore);
                        betDisplay.setItemMeta(meta);
                    }
                    inv.setItem(49, betDisplay);
                }
            } else {
                ItemStack betDisplay = createItem(Material.SUNFLOWER,
                        Component.text("Mise en jeu : §6§l" + game.getActiveChallengeBet() + " Jetons", NamedTextColor.YELLOW, TextDecoration.BOLD),
                        "§aVictoire standard : +" + (game.getActiveChallengeBet() * 2) + " Jetons",
                        "§6Blackjack naturel : +" + (game.getActiveChallengeBet() * 3) + " Jetons",
                        "§cDéfaite : Perte de la mise"
                );
                betDisplay.setAmount(Math.max(1, Math.min(64, game.getActiveChallengeBet())));
                inv.setItem(49, betDisplay);
            }

            // Bouton Rester (Stand - Slot 51)
            ItemStack standBtn = createItem(Material.RED_CONCRETE,
                    Component.text("■ RESTER (Stand)  §e[Garder " + pScore + "]", NamedTextColor.RED, TextDecoration.BOLD),
                    "§7Garder votre score actuel de §e" + pScore + "§7.",
                    "§7Le croupier jouera ensuite sa main !"
            );
            standBtn.setAmount(Math.max(1, Math.min(64, pScore)));
            inv.setItem(BUTTON_STAND, standBtn);

        } else if (game.getState() == BlackjackGame.State.GAME_OVER) {
            // Résultat au centre (Slot 49)
            if (game.getMode() == BlackjackGame.Mode.CHALLENGE && game.isJackpotWon()) {
                inv.setItem(49, createItem(Material.TOTEM_OF_UNDYING,
                        Component.text("✦ JACKPOT X8 ATTEINT ! ✦", NamedTextColor.GOLD, TextDecoration.BOLD),
                        "§aFélicitations ! Vous avez atteint 800+ jetons !",
                        "§63 Lingots de Cuprite vous ont été remis !",
                        "§7La mission est un triomphe !"
                ));
            } else if (game.getMode() == BlackjackGame.Mode.CHALLENGE && game.getChallengeChips() <= 0) {
                inv.setItem(49, createItem(Material.REDSTONE_BLOCK,
                        Component.text("✘ FAILLITE TOTALE ✘", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                        "§cVos jetons sont tombés à zéro.",
                        "§7Votre mise d'entrée est définitivement perdue.",
                        "§7Repayez l'entrée pour recommencer."
                ));
            } else {
                switch (game.getResult()) {
                    case PLAYER_BLACKJACK -> inv.setItem(49, createItem(Material.TOTEM_OF_UNDYING,
                            Component.text("✦ BLACKJACK NATUREL ! ✦", NamedTextColor.GOLD, TextDecoration.BOLD),
                            "§aFélicitations ! Vous avez fait 21 dès la distribution.",
                            "§6Payé 3 pour 1 : Votre mise a été triplée (x3) !"
                    ));
                    case FIVE_CARD_CHARLIE -> inv.setItem(49, createItem(Material.TOTEM_OF_UNDYING,
                            Component.text("✦ FIVE-CARD CHARLIE ! ✦", NamedTextColor.GOLD, TextDecoration.BOLD),
                            "§aExploit ! 5 cartes tirées sans dépasser 21 !",
                            "§6Victoire immédiate (x2) remportée !",
                            "§7La main du croupier a été battue d'office."
                    ));
                    case PLAYER_WIN, DEALER_BUST -> inv.setItem(49, createItem(Material.EMERALD_BLOCK,
                            Component.text("✔ VICTOIRE ! ✔", NamedTextColor.GREEN, TextDecoration.BOLD),
                            "§aVous remportez la manche !",
                            "§6Votre mise a été doublée !"
                    ));
                    case PUSH -> inv.setItem(49, createItem(Material.GOLD_BLOCK,
                            Component.text("═ ÉGALITÉ (PUSH) ═", NamedTextColor.YELLOW, TextDecoration.BOLD),
                            "§eMême score que le croupier.",
                            "§fVotre mise initiale vous a été restituée."
                    ));
                    case PLAYER_BUST, DEALER_WIN -> inv.setItem(49, createItem(Material.REDSTONE_BLOCK,
                            Component.text("✘ DÉFAITE ! ✘", NamedTextColor.RED, TextDecoration.BOLD),
                            "§cLe casino a remporté la manche.",
                            "§7Votre mise a été conservée par la maison."
                    ));
                    default -> {}
                }
            }

            // Bouton Rejouer (Slot 48)
            if (game.getMode() == BlackjackGame.Mode.CLASSIC) {
                inv.setItem(BUTTON_REPLAY, createItem(Material.GOLD_INGOT,
                        Component.text("♠ Rejouer une partie ♠", NamedTextColor.YELLOW, TextDecoration.BOLD),
                        "§7Remiser un item pour une nouvelle manche !"
                ));
            } else {
                if (game.getChallengeChips() > 0) {
                    inv.setItem(BUTTON_REPLAY, createItem(Material.GOLD_INGOT,
                            Component.text("♠ Manche suivante ♠", NamedTextColor.YELLOW, TextDecoration.BOLD),
                            "§7Solde restant : §a" + game.getChallengeChips() + " Jetons",
                            "",
                            "§e➤ Cliquez pour relancer une manche !"
                    ));
                } else {
                    inv.setItem(BUTTON_REPLAY, createItem(Material.BARRIER,
                            Component.text("Session terminée", NamedTextColor.RED, TextDecoration.BOLD),
                            "§7Votre session s'est terminée.",
                            "§e➤ Cliquez pour réinitialiser la table."
                    ));
                }
            }

            // Bouton Quitter
            inv.setItem(BUTTON_QUIT, createItem(Material.BARRIER,
                    Component.text("Fermer la table", NamedTextColor.RED, TextDecoration.BOLD),
                    "§7Quitter le casino."
            ));
        }
    }

    private static void renderActionBar(BlackjackGame game) {
        if (game.getMode() == BlackjackGame.Mode.CHALLENGE && game.getState() == BlackjackGame.State.BETTING) {
            game.getPlayer().sendActionBar(Component.text("§d[Défi Cuprite] §fSolde : §a§l" + game.getChallengeChips() + " Jetons §8| §fObjectifs : §6400 (x4) §8- §d800 (x8)"));
            return;
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
            String suffix = (game.getMode() == BlackjackGame.Mode.CHALLENGE) ? " §8| §dJetons: §a§l" + game.getChallengeChips() : "";
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

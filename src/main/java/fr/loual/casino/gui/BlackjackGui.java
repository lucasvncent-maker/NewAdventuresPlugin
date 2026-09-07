package fr.loual.casino.gui;

import fr.loual.casino.BlackjackGame;
import fr.loual.casino.Card;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class BlackjackGui {

    public static final int BET_SLOT = 22;
    public static final int BUTTON_START_BET = 49;
    public static final int BUTTON_HIT = 47;
    public static final int BUTTON_STAND = 51;
    public static final int BUTTON_REPLAY = 48;
    public static final int BUTTON_QUIT = 50;

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

        for (int i = 0; i < 54; i++) {
            if (i == BET_SLOT) {
                if (game.getState() == BlackjackGame.State.BETTING) {
                    // S'assurer que le slot de mise n'a JAMAIS de vitre de fond
                    ItemStack current = inv.getItem(BET_SLOT);
                    if (current != null && (current.getType() == Material.GREEN_STAINED_GLASS_PANE 
                            || current.getType() == Material.BLACK_STAINED_GLASS_PANE 
                            || current.getType() == Material.YELLOW_STAINED_GLASS_PANE)) {
                        inv.setItem(BET_SLOT, null);
                    }
                    continue;
                }
            }
            inv.setItem(i, greenFelt);
        }

        // Bords sombres
        for (int i : new int[]{ 0, 8, 45, 53 }) {
            inv.setItem(i, darkFelt);
        }

        // Section CROUPIER (Lignes 0 et 1)
        renderDealerSection(inv, game);

        // Section JOUEUR (Lignes 3 et 4)
        renderPlayerSection(inv, game);

        // Section COMMANDES & ÉTATS (Ligne 5)
        renderControls(inv, game);

        // Affichage dynamique et permanent de l'ActionBar pour voir le score en direct sans survol de souris
        if (game.getState() == BlackjackGame.State.DEALING) {
            int pScore = BlackjackGame.calculateScore(game.getPlayerHand());
            game.getPlayer().sendActionBar(Component.text("§e♠ Distribution des cartes en cours... §7[Votre score: §a§l" + pScore + "§7] ♠"));
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
            game.getPlayer().sendActionBar(Component.text("§f✦ Votre Score : " + pColor + "§l" + pScore + " §8| §fCroupier : " + dText + " ✦"));
        }
    }

    private static void renderDealerSection(Inventory inv, BlackjackGame game) {
        ItemStack dealerHeader;
        if (game.getState() == BlackjackGame.State.BETTING) {
            dealerHeader = createItem(Material.PLAYER_HEAD,
                    Component.text("♠ Croupier du Casino ♠", NamedTextColor.GOLD, TextDecoration.BOLD),
                    "§7Le croupier tire jusqu'à §e17§7.",
                    "§7Déposez votre mise pour commencer !"
            );
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
            // Emplacements vides
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
            playerHeader = createItem(Material.NETHER_STAR,
                    Component.text("✦ Votre Main ✦", NamedTextColor.AQUA, TextDecoration.BOLD),
                    "§7Placez l'item que vous souhaitez parier",
                    "§7dans le slot doré au milieu de la table !"
            );
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

        // Affichage des cartes du joueur
        if (game.getState() == BlackjackGame.State.BETTING) {
            for (int slot : PLAYER_CARD_SLOTS) {
                inv.setItem(slot, createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text("§8[ Emplacement Joueur ]")));
            }

            // Cadre doré autour du slot de mise (Slot 22)
            for (int slot : new int[]{ 13, 21, 23, 31 }) {
                inv.setItem(slot, createItem(Material.YELLOW_STAINED_GLASS_PANE, Component.text("§e↓ DÉPOSEZ VOTRE MISE ICI ↓", NamedTextColor.YELLOW, TextDecoration.BOLD)));
            }

            ItemStack currentBetInSlot = inv.getItem(BET_SLOT);
            if (currentBetInSlot != null && (currentBetInSlot.getType() == Material.GREEN_STAINED_GLASS_PANE 
                    || currentBetInSlot.getType() == Material.BLACK_STAINED_GLASS_PANE 
                    || currentBetInSlot.getType() == Material.YELLOW_STAINED_GLASS_PANE)) {
                inv.setItem(BET_SLOT, null);
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
        if (game.getState() == BlackjackGame.State.BETTING) {
            ItemStack betInSlot = inv.getItem(BET_SLOT);
            boolean hasBet = betInSlot != null && !betInSlot.getType().isAir() && betInSlot.getAmount() > 0;

            if (hasBet) {
                String itemName = betInSlot.getItemMeta() != null && betInSlot.getItemMeta().hasDisplayName()
                        ? betInSlot.getItemMeta().getDisplayName()
                        : betInSlot.getType().name().toLowerCase().replace('_', ' ');

                inv.setItem(BUTTON_START_BET, createItem(Material.LIME_CONCRETE,
                        Component.text("✔ Valider la mise & Distribuer", NamedTextColor.GREEN, TextDecoration.BOLD),
                        "§eMise : §f" + betInSlot.getAmount() + "x " + itemName,
                        "§7Victoire standard : §aLe double (x2) !",
                        "§6Blackjack (21 naturel) : §ePayé 3 pour 1 (x3) !",
                        "§7Défaite : §cVotre mise est perdue.",
                        "",
                        "§a➤ Cliquez pour lancer la partie !"
                ));
            } else {
                inv.setItem(BUTTON_START_BET, createItem(Material.GRAY_CONCRETE,
                        Component.text("En attente de mise...", NamedTextColor.GRAY, TextDecoration.BOLD),
                        "§7Déposez un item de votre inventaire",
                        "§7dans le slot central pour activer ce bouton."
                ));
            }

            inv.setItem(BUTTON_QUIT, createItem(Material.BARRIER,
                    Component.text("Quitter la table", NamedTextColor.RED, TextDecoration.BOLD),
                    "§7Ferme le casino et récupère votre mise."
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

            // Bouton Tirer (Hit) avec score visible en gros
            ItemStack hitBtn = createItem(Material.LIME_CONCRETE,
                    Component.text("➤ TIRER (Hit)  §e[" + pScore + "/21]", NamedTextColor.GREEN, TextDecoration.BOLD),
                    "§7Prendre une carte supplémentaire.",
                    "§7Votre score actuel : §e" + pScore + "§7/21",
                    "§cAttention si votre score dépasse 21 !"
            );
            hitBtn.setAmount(Math.max(1, Math.min(64, pScore)));
            inv.setItem(BUTTON_HIT, hitBtn);

            // Rappel de la mise
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

            // Bouton Rester (Stand) avec score visible en gros
            ItemStack standBtn = createItem(Material.RED_CONCRETE,
                    Component.text("■ RESTER (Stand)  §e[Garder " + pScore + "]", NamedTextColor.RED, TextDecoration.BOLD),
                    "§7Garder votre score actuel de §e" + pScore + "§7.",
                    "§7Le croupier jouera ensuite sa main !"
            );
            standBtn.setAmount(Math.max(1, Math.min(64, pScore)));
            inv.setItem(BUTTON_STAND, standBtn);

        } else if (game.getState() == BlackjackGame.State.GAME_OVER) {
            // Résultat au centre (Slot 49)
            switch (game.getResult()) {
                case PLAYER_BLACKJACK -> inv.setItem(49, createItem(Material.TOTEM_OF_UNDYING,
                        Component.text("✦ BLACKJACK NATUREL ! ✦", NamedTextColor.GOLD, TextDecoration.BOLD),
                        "§aFélicitations ! Vous avez fait 21 dès la distribution.",
                        "§6Payé 3 pour 1 : Votre mise a été triplée (x3) !"
                ));
                case PLAYER_WIN, DEALER_BUST -> inv.setItem(49, createItem(Material.EMERALD_BLOCK,
                        Component.text("✔ VICTOIRE ! ✔", NamedTextColor.GREEN, TextDecoration.BOLD),
                        "§aVous remportez la manche !",
                        "§6Votre mise a été doublée et versée dans votre inventaire !"
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

            // Bouton Rejouer
            inv.setItem(BUTTON_REPLAY, createItem(Material.GOLD_INGOT,
                    Component.text("♠ Rejouer une partie ♠", NamedTextColor.YELLOW, TextDecoration.BOLD),
                    "§7Remiser un item pour une nouvelle manche !"
            ));

            // Bouton Quitter
            inv.setItem(BUTTON_QUIT, createItem(Material.BARRIER,
                    Component.text("Fermer la table", NamedTextColor.RED, TextDecoration.BOLD),
                    "§7Quitter le casino."
            ));
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

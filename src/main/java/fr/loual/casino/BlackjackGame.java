package fr.loual.casino;

import fr.loual.customminerals.items.Cuprite;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import fr.loual.newadventure.NewAdventurePlugin;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BlackjackGame {

    public enum Mode {
        CLASSIC,
        CHALLENGE,
        HORDE
    }

    public enum State {
        BETTING,
        DEALING,
        PLAYING,
        DEALER_TURN,
        GAME_OVER
    }

    public enum Result {
        NONE,
        PLAYER_BLACKJACK,
        PLAYER_WIN,
        FIVE_CARD_CHARLIE,
        DEALER_BUST,
        DEALER_WIN,
        PLAYER_BUST,
        PUSH
    }

    public static final int CHALLENGE_START_CHIPS = 100;
    public static final int CHALLENGE_PALIER_1 = 400; // x4 -> 1 Cuprite
    public static final int CHALLENGE_PALIER_2 = 800; // x8 -> 3 Cuprites
    public static final int HORDE_START_CHIPS = 100;
    public static final int HORDE_TARGET_CHIPS = 300; // x3 -> Déclenchement de la Horde !
    public static final int GILDED_BLACKSTONE_COST = 8;
    public static final NamespacedKey HARMLESS_FIREWORK_KEY = new NamespacedKey("casino", "harmless_firework");

    private final Player player;
    private final Plugin plugin;
    private final Deck deck = new Deck();
    private final List<Card> playerHand = new ArrayList<>();
    private final List<Card> dealerHand = new ArrayList<>();

    private Mode mode = Mode.CLASSIC;
    private State state = State.BETTING;
    private Result result = Result.NONE;
    private ItemStack betItem = null;
    private boolean paidOut = false;
    private BukkitTask currentTask = null;

    // Données du Mode Défi (Mission Cuprite)
    private int challengeChips = 0;
    private int challengeBet = 10;
    private int activeChallengeBet = 0;
    private boolean jackpotWon = false;

    // Données du Mode Mission Horde (Jetons de Sang)
    private int hordeChips = 0;
    private int hordeBet = 10;
    private int activeHordeBet = 0;

    public BlackjackGame(Player player) {
        this(player, null);
    }

    public BlackjackGame(Player player, Plugin plugin) {
        this.player = player;
        this.plugin = plugin;
    }

    public static int calculateScore(List<Card> hand) {
        int total = 0;
        int aces = 0;

        for (Card card : hand) {
            total += card.getValue();
            if (card.isAce()) {
                aces++;
            }
        }

        while (total > 21 && aces > 0) {
            total -= 10;
            aces--;
        }

        return total;
    }

    public static boolean isNaturalBlackjack(List<Card> hand) {
        return hand.size() == 2 && calculateScore(hand) == 21;
    }

    public void cancelCurrentTask() {
        if (currentTask != null && !currentTask.isCancelled()) {
            currentTask.cancel();
            currentTask = null;
        }
    }

    public void startAnimated(Plugin plugin, ItemStack bet, Runnable onUpdate) {
        if (bet == null || bet.getType().isAir() || bet.getAmount() <= 0) return;

        cancelCurrentTask();
        this.betItem = bet.clone();
        this.activeChallengeBet = 0;
        this.jackpotWon = false;
        this.playerHand.clear();
        this.dealerHand.clear();
        this.deck.resetAndShuffle();
        this.state = State.DEALING;
        this.result = Result.NONE;
        this.paidOut = false;
        onUpdate.run();

        runDealingAnimation(plugin, onUpdate);
    }

    public void startChallengeHand(Plugin plugin, int betChips, Runnable onUpdate) {
        if (betChips <= 0 || betChips > challengeChips) return;

        cancelCurrentTask();
        this.activeChallengeBet = betChips;
        this.challengeChips -= betChips;
        this.jackpotWon = false;
        saveChallengeToPdc(plugin);

        this.betItem = null;
        this.playerHand.clear();
        this.dealerHand.clear();
        this.deck.resetAndShuffle();
        this.state = State.DEALING;
        this.result = Result.NONE;
        this.paidOut = false;
        onUpdate.run();

        runDealingAnimation(plugin, onUpdate);
    }

    public void startHordeHand(Plugin plugin, int betChips, Runnable onUpdate) {
        if (betChips <= 0 || betChips > hordeChips) return;

        cancelCurrentTask();
        this.activeHordeBet = betChips;
        this.hordeChips -= betChips;
        this.jackpotWon = false;
        saveHordeToPdc(plugin);

        this.betItem = null;
        this.playerHand.clear();
        this.dealerHand.clear();
        this.deck.resetAndShuffle();
        this.state = State.DEALING;
        this.result = Result.NONE;
        this.paidOut = false;
        onUpdate.run();

        runDealingAnimation(plugin, onUpdate);
    }

    private void runDealingAnimation(Plugin plugin, Runnable onUpdate) {
        this.currentTask = new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (state != State.DEALING) {
                    cancel();
                    return;
                }

                switch (step) {
                    case 0 -> {
                        // Carte 1 Joueur
                        playerHand.add(deck.draw());
                        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                        onUpdate.run();
                    }
                    case 1 -> {
                        // Carte 1 Croupier (visible)
                        dealerHand.add(deck.draw());
                        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.1f);
                        onUpdate.run();
                    }
                    case 2 -> {
                        // Carte 2 Joueur
                        playerHand.add(deck.draw());
                        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
                        onUpdate.run();
                    }
                    case 3 -> {
                        // Carte 2 Croupier (cachée)
                        dealerHand.add(deck.draw());
                        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.3f);
                        onUpdate.run();
                    }
                    case 4 -> {
                        // Fin de la distribution : Vérification du Blackjack naturel
                        boolean pBj = isNaturalBlackjack(playerHand);
                        boolean dBj = isNaturalBlackjack(dealerHand);

                        if (pBj && dBj) {
                            state = State.GAME_OVER;
                            result = Result.PUSH;
                            applyPayout(player);
                        } else if (pBj) {
                            state = State.GAME_OVER;
                            result = Result.PLAYER_BLACKJACK;
                            applyPayout(player);
                        } else {
                            int initialScore = calculateScore(playerHand);
                            if (initialScore == 21) {
                                cancel();
                                standAnimated(plugin, onUpdate);
                                return;
                            }
                            state = State.PLAYING;
                        }
                        onUpdate.run();
                        cancel();
                    }
                }
                step++;
            }
        }.runTaskTimer(plugin, 6L, 10L);
    }

    public void hitAnimated(Plugin plugin, Runnable onUpdate) {
        if (state != State.PLAYING) return;

        playerHand.add(deck.draw());
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
        int pScore = calculateScore(playerHand);

        if (pScore > 21) {
            state = State.GAME_OVER;
            result = Result.PLAYER_BUST;
            applyPayout(player);
            onUpdate.run();
        } else if (pScore == 21) {
            // 21 automatique : aucune décision à prendre, on reste (Stand) automatiquement !
            onUpdate.run();
            standAnimated(plugin, onUpdate);
        } else if (playerHand.size() >= 5) {
            // Règle du Five-Card Charlie : 5 cartes sans sauter = Victoire instantanée !
            state = State.GAME_OVER;
            result = Result.FIVE_CARD_CHARLIE;
            applyPayout(player);
            onUpdate.run();
        } else {
            onUpdate.run();
        }
    }

    public boolean canDoubleDown() {
        if (state != State.PLAYING || playerHand.size() != 2) return false;
        if (mode == Mode.CLASSIC) {
            if (betItem == null || betItem.getType().isAir()) return false;
            ItemStack check = betItem.clone();
            check.setAmount(1);
            return player.getInventory().containsAtLeast(check, betItem.getAmount());
        } else if (mode == Mode.CHALLENGE) {
            return challengeChips >= activeChallengeBet;
        } else {
            return hordeChips >= activeHordeBet;
        }
    }

    public boolean doubleDownAnimated(Plugin plugin, Runnable onUpdate) {
        if (!canDoubleDown()) return false;

        // 1. Déduire la mise supplémentaire
        if (mode == Mode.CLASSIC) {
            ItemStack toRemove = betItem.clone();
            toRemove.setAmount(betItem.getAmount());
            HashMap<Integer, ItemStack> notRemoved = player.getInventory().removeItem(toRemove);
            if (!notRemoved.isEmpty()) {
                return false;
            }
            betItem.setAmount(betItem.getAmount() * 2);
        } else if (mode == Mode.CHALLENGE) {
            challengeChips -= activeChallengeBet;
            activeChallengeBet *= 2;
            saveChallengeToPdc(plugin);
        } else {
            hordeChips -= activeHordeBet;
            activeHordeBet *= 2;
            saveHordeToPdc(plugin);
        }

        // 2. Sons d'activation
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GOLD, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

        // 3. Tirer EXACTEMENT une seule carte supplémentaire
        playerHand.add(deck.draw());
        int pScore = calculateScore(playerHand);
        onUpdate.run();

        // 4. Résolution automatique
        if (pScore > 21) {
            state = State.GAME_OVER;
            result = Result.PLAYER_BUST;
            applyPayout(player);
            onUpdate.run();
        } else {
            // Tour du croupier automatique
            standAnimated(plugin, onUpdate);
        }

        return true;
    }

    public void standAnimated(Plugin plugin, Runnable onUpdate) {
        if (state != State.PLAYING) return;

        cancelCurrentTask();
        this.state = State.DEALER_TURN;
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
        onUpdate.run();

        // Le croupier révèle sa carte cachée et tire une par une tant qu'il a moins de 17
        this.currentTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != State.DEALER_TURN) {
                    cancel();
                    return;
                }

                int currentScore = calculateScore(dealerHand);
                if (currentScore < 17) {
                    dealerHand.add(deck.draw());
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.1f);
                    onUpdate.run();
                } else {
                    cancel();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (state == State.DEALER_TURN) {
                            int pScore = calculateScore(playerHand);
                            int dScore = calculateScore(dealerHand);

                            if (dScore > 21) {
                                result = Result.DEALER_BUST;
                            } else if (pScore > dScore) {
                                result = Result.PLAYER_WIN;
                            } else if (pScore == dScore) {
                                result = Result.PUSH;
                            } else {
                                result = Result.DEALER_WIN;
                            }

                            state = State.GAME_OVER;
                            applyPayout(player);
                            onUpdate.run();
                        }
                    }, 10L);
                }
            }
        }.runTaskTimer(plugin, 12L, 12L);
    }

    public void applyPayout(Player targetPlayer) {
        if (paidOut) return;
        paidOut = true;

        Location loc = targetPlayer.getLocation();
        Plugin currentPlugin = (this.plugin != null) ? this.plugin : Bukkit.getPluginManager().getPlugin("NewAdventurePlugin");

        if (mode == Mode.CLASSIC) {
            if (betItem == null) return;
            switch (result) {
                case PLAYER_BLACKJACK -> {
                    giveReward(targetPlayer, 3);
                    targetPlayer.sendMessage(Component.text("✦ BLACKJACK NATUREL ! ✦ Payé 3 pour 1 ! Vous recevez le triple de votre mise !", NamedTextColor.GOLD, TextDecoration.BOLD));
                    targetPlayer.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.1f);
                    targetPlayer.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
                    targetPlayer.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 35, 0.5, 0.5, 0.5, 0.2);
                }
                case FIVE_CARD_CHARLIE -> {
                    giveReward(targetPlayer, 2);
                    targetPlayer.sendMessage(Component.text("✦ FIVE-CARD CHARLIE ! ✦ 5 cartes sans sauter ! Victoire immédiate (x2) !", NamedTextColor.GOLD, TextDecoration.BOLD));
                    targetPlayer.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                    targetPlayer.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    targetPlayer.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.2);
                }
                case PLAYER_WIN, DEALER_BUST -> {
                    giveReward(targetPlayer, 2);
                    String reason = (result == Result.DEALER_BUST) ? "Le croupier a dépassé 21 (Bust) !" : "Votre score est supérieur à celui du croupier !";
                    targetPlayer.sendMessage(Component.text("✔ VICTOIRE ! " + reason + " Vous doublez votre mise !", NamedTextColor.GREEN, TextDecoration.BOLD));
                    targetPlayer.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    targetPlayer.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1, 0), 25, 0.4, 0.4, 0.4, 0.05);
                }
                case PUSH -> {
                    giveReward(targetPlayer, 1);
                    targetPlayer.sendMessage(Component.text("═ ÉGALITÉ (PUSH) ! Même score que le croupier, votre mise vous est rendue.", NamedTextColor.YELLOW, TextDecoration.BOLD));
                    targetPlayer.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                }
                case PLAYER_BUST, DEALER_WIN -> {
                    String reason = (result == Result.PLAYER_BUST) ? "Vous avez dépassé 21 (Bust) !" : "Le score du croupier est supérieur au vôtre.";
                    targetPlayer.sendMessage(Component.text("✘ DÉFAITE ! " + reason + " Votre mise est perdue.", NamedTextColor.RED, TextDecoration.BOLD));
                    targetPlayer.playSound(loc, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.9f);
                    targetPlayer.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.6f);
                }
                default -> {}
            }
        } else if (mode == Mode.CHALLENGE) {
            if (activeChallengeBet <= 0) return;
            // Mode CHALLENGE
            switch (result) {
                case PLAYER_BLACKJACK -> {
                    int winnings = activeChallengeBet * 3;
                    challengeChips += winnings;
                    targetPlayer.sendMessage(Component.text("✦ BLACKJACK NATUREL ! ✦ Payé 3 pour 1 ! +" + winnings + " Jetons (Solde: " + challengeChips + ")", NamedTextColor.GOLD, TextDecoration.BOLD));
                    targetPlayer.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.1f);
                    targetPlayer.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
                    targetPlayer.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 35, 0.5, 0.5, 0.5, 0.2);
                }
                case FIVE_CARD_CHARLIE -> {
                    int winnings = activeChallengeBet * 2;
                    challengeChips += winnings;
                    targetPlayer.sendMessage(Component.text("✦ FIVE-CARD CHARLIE ! ✦ 5 cartes sans sauter ! +" + winnings + " Jetons (Solde: " + challengeChips + ")", NamedTextColor.GOLD, TextDecoration.BOLD));
                    targetPlayer.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                    targetPlayer.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    targetPlayer.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.2);
                }
                case PLAYER_WIN, DEALER_BUST -> {
                    int winnings = activeChallengeBet * 2;
                    challengeChips += winnings;
                    String reason = (result == Result.DEALER_BUST) ? "Le croupier a sauté (Bust) !" : "Votre score l'emporte !";
                    targetPlayer.sendMessage(Component.text("✔ VICTOIRE ! " + reason + " +" + winnings + " Jetons (Solde: " + challengeChips + ")", NamedTextColor.GREEN, TextDecoration.BOLD));
                    targetPlayer.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    targetPlayer.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1, 0), 25, 0.4, 0.4, 0.4, 0.05);
                }
                case PUSH -> {
                    challengeChips += activeChallengeBet;
                    targetPlayer.sendMessage(Component.text("═ ÉGALITÉ (PUSH) ! Mise de " + activeChallengeBet + " Jetons restituée. (Solde: " + challengeChips + ")", NamedTextColor.YELLOW, TextDecoration.BOLD));
                    targetPlayer.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                }
                case PLAYER_BUST, DEALER_WIN -> {
                    String reason = (result == Result.PLAYER_BUST) ? "Vous avez dépassé 21 (Bust) !" : "Le croupier l'emporte.";
                    targetPlayer.sendMessage(Component.text("✘ DÉFAITE ! " + reason + " Fin de la session de défi.", NamedTextColor.RED, TextDecoration.BOLD));
                    targetPlayer.playSound(loc, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.9f);
                    targetPlayer.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.6f);
                    challengeChips = 0;
                    saveChallengeToPdc(currentPlugin);
                }
                default -> {}
            }

            // Enregistrement des statistiques
            if (currentPlugin instanceof NewAdventurePlugin nap && nap.getCasinoStatsManager() != null) {
                boolean won = (result == Result.PLAYER_WIN || result == Result.PLAYER_BLACKJACK);
                boolean natural = (result == Result.PLAYER_BLACKJACK);
                nap.getCasinoStatsManager().recordHand(targetPlayer, won, natural, challengeChips);
            }

            // Vérification des conditions de victoire x8 et faillite
            if (challengeChips >= CHALLENGE_PALIER_2) {
                this.jackpotWon = true;
                giveCuprite(targetPlayer, 3, currentPlugin);
                if (currentPlugin instanceof NewAdventurePlugin nap && nap.getCasinoStatsManager() != null) {
                    nap.getCasinoStatsManager().recordCuprite(targetPlayer, 3);
                }
                spawnVictoryFireworks(loc, true, currentPlugin);

                targetPlayer.showTitle(Title.title(
                        Component.text("✦ VICTOIRE x8 ✦", NamedTextColor.GOLD, TextDecoration.BOLD),
                        Component.text("3 Lingots de Cuprite remportés !", NamedTextColor.YELLOW)
                ));
                targetPlayer.sendMessage(Component.text("✦ JACKPOT X8 ATTEINT ! ✦ Solde: " + challengeChips + " Jetons. Vous recevez 3 Lingots de Cuprite ! Félicitations !", NamedTextColor.GOLD, TextDecoration.BOLD));
                targetPlayer.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.9f);
                targetPlayer.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.2f);
                targetPlayer.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 60, 0.8, 0.8, 0.8, 0.3);

                challengeChips = 0;
                saveChallengeToPdc(currentPlugin);
            } else if (challengeChips <= 0) {
                challengeChips = 0;
                saveChallengeToPdc(currentPlugin);
                targetPlayer.showTitle(Title.title(
                        Component.text("✘ FAILLITE ✘", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                        Component.text("Mise d'entrée définitivement perdue.", NamedTextColor.RED)
                ));
                targetPlayer.sendMessage(Component.text("✘ FAILLITE TOTALE ! ✘ Vos jetons sont tombés à zéro. Votre droit d'entrée est perdu.", NamedTextColor.DARK_RED, TextDecoration.BOLD));
                targetPlayer.playSound(loc, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                targetPlayer.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.7f, 0.5f);
            } else {
                saveChallengeToPdc(currentPlugin);
                if (challengeChips >= CHALLENGE_PALIER_1) {
                    targetPlayer.sendMessage(Component.text("★ PALIER x4 ATTEINT (" + challengeChips + "/400 Jetons) ! ★ Vous pouvez encaisser 1 Cuprite dès maintenant ou continuer vers le x8 (800 Jetons pour 3 Cuprites) !", NamedTextColor.YELLOW, TextDecoration.BOLD));
                    targetPlayer.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.2f);
                }
                if (challengeBet > challengeChips) {
                    challengeBet = challengeChips;
                }
            }
        } else {
            if (activeHordeBet <= 0) return;
            // Mode HORDE
            switch (result) {
                case PLAYER_BLACKJACK -> {
                    int winnings = activeHordeBet * 3;
                    hordeChips += winnings;
                    targetPlayer.sendMessage(Component.text("✦ BLACKJACK NATUREL ! ✦ Payé 3 pour 1 ! +" + winnings + " Jetons de Sang (Solde: " + hordeChips + ")", NamedTextColor.RED, TextDecoration.BOLD));
                    targetPlayer.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.1f);
                    targetPlayer.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
                    targetPlayer.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 35, 0.5, 0.5, 0.5, 0.2);
                }
                case FIVE_CARD_CHARLIE -> {
                    int winnings = activeHordeBet * 2;
                    hordeChips += winnings;
                    targetPlayer.sendMessage(Component.text("✦ FIVE-CARD CHARLIE ! ✦ 5 cartes sans sauter ! +" + winnings + " Jetons de Sang (Solde: " + hordeChips + ")", NamedTextColor.RED, TextDecoration.BOLD));
                    targetPlayer.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                    targetPlayer.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    targetPlayer.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.2);
                }
                case PLAYER_WIN, DEALER_BUST -> {
                    int winnings = activeHordeBet * 2;
                    hordeChips += winnings;
                    String reason = (result == Result.DEALER_BUST) ? "Le croupier a sauté (Bust) !" : "Votre score l'emporte !";
                    targetPlayer.sendMessage(Component.text("✔ VICTOIRE ! " + reason + " +" + winnings + " Jetons de Sang (Solde: " + hordeChips + ")", NamedTextColor.GREEN, TextDecoration.BOLD));
                    targetPlayer.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    targetPlayer.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1, 0), 25, 0.4, 0.4, 0.4, 0.05);
                }
                case PUSH -> {
                    hordeChips += activeHordeBet;
                    targetPlayer.sendMessage(Component.text("═ ÉGALITÉ (PUSH) ! Mise de " + activeHordeBet + " Jetons de Sang restituée. (Solde: " + hordeChips + ")", NamedTextColor.YELLOW, TextDecoration.BOLD));
                    targetPlayer.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                }
                case PLAYER_BUST, DEALER_WIN -> {
                    String reason = (result == Result.PLAYER_BUST) ? "Vous avez dépassé 21 (Bust) !" : "Le croupier l'emporte.";
                    targetPlayer.sendMessage(Component.text("✘ DÉFAITE ! " + reason + " Votre Lingot de Cuprite est perdu.", NamedTextColor.DARK_RED, TextDecoration.BOLD));
                    targetPlayer.playSound(loc, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.9f);
                    targetPlayer.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.6f);
                    hordeChips = 0;
                    saveHordeToPdc(currentPlugin);
                }
                default -> {}
            }

            if (hordeChips >= HORDE_TARGET_CHIPS) {
                targetPlayer.sendMessage(Component.text("☠ OBJECTIF ATTEINT (300 JETONS DE SANG) ! LA HORDE SE RÉVEILLE ! ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD));
                targetPlayer.showTitle(Title.title(
                        Component.text("☠ INVASION DE LA HORDE ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                        Component.text("Le Titan Putréfié et son armée attaquent !", NamedTextColor.RED)
                ));
                targetPlayer.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
                targetPlayer.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.9f);
                hordeChips = 0;
                saveHordeToPdc(currentPlugin);

                if (currentPlugin instanceof NewAdventurePlugin nap && nap.getHordeManager() != null) {
                    Bukkit.getScheduler().runTaskLater(currentPlugin, () -> {
                        targetPlayer.closeInventory();
                        nap.getHordeManager().startHorde(targetPlayer, targetPlayer.getLocation());
                    }, 30L);
                }
            } else if (hordeChips <= 0) {
                hordeChips = 0;
                saveHordeToPdc(currentPlugin);
                targetPlayer.showTitle(Title.title(
                        Component.text("✘ FAILLITE ✘", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                        Component.text("Lingot de Cuprite perdu.", NamedTextColor.RED)
                ));
                targetPlayer.sendMessage(Component.text("✘ FAILLITE TOTALE ! ✘ Vos jetons de sang sont tombés à zéro. Votre Cuprite est perdue.", NamedTextColor.DARK_RED, TextDecoration.BOLD));
                targetPlayer.playSound(loc, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                targetPlayer.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.7f, 0.5f);
            } else {
                saveHordeToPdc(currentPlugin);
                if (hordeBet > hordeChips) {
                    hordeBet = hordeChips;
                }
            }
        }
    }

    public boolean cashoutPalier1(Plugin plugin) {
        if (challengeChips < CHALLENGE_PALIER_1) return false;
        if (state != State.BETTING && state != State.GAME_OVER) return false;

        Location loc = player.getLocation();
        Plugin currentPlugin = (plugin != null) ? plugin : (this.plugin != null ? this.plugin : Bukkit.getPluginManager().getPlugin("NewAdventurePlugin"));
        giveCuprite(player, 1, currentPlugin);
        if (currentPlugin instanceof NewAdventurePlugin nap && nap.getCasinoStatsManager() != null) {
            nap.getCasinoStatsManager().recordCuprite(player, 1);
        }
        spawnVictoryFireworks(loc, false, currentPlugin);

        player.showTitle(Title.title(
                Component.text("✔ ENCAISSEMENT x4 ✔", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("1 Lingot de Cuprite sécurisé !", NamedTextColor.YELLOW)
        ));
        player.sendMessage(Component.text("✔ MISSION VALIDÉE ! Vous encaissez 1 Lingot de Cuprite avec un solde de " + challengeChips + " Jetons !", NamedTextColor.GREEN, TextDecoration.BOLD));
        player.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.1f);
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        player.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.2);

        challengeChips = 0;
        saveChallengeToPdc(currentPlugin);
        this.mode = Mode.CLASSIC;
        resetToBetting();
        return true;
    }

    private void spawnVictoryFireworks(Location loc, boolean isJackpot, Plugin currentPlugin) {
        World world = loc.getWorld();
        if (world == null) return;
        Location fwLoc = loc.clone().add(0, 2.0, 0);

        try {
            Firework fw = world.spawn(fwLoc, Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .withColor(Color.fromRGB(160, 32, 240), Color.fromRGB(255, 215, 0)) // Violet et Or
                    .withFade(Color.fromRGB(255, 105, 180), Color.fromRGB(255, 255, 255))
                    .with(isJackpot ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.BURST)
                    .trail(true)
                    .flicker(true)
                    .build());
            meta.setPower(isJackpot ? 1 : 0);
            meta.getPersistentDataContainer().set(HARMLESS_FIREWORK_KEY, PersistentDataType.BYTE, (byte) 1);
            fw.setFireworkMeta(meta);

            if (isJackpot && currentPlugin != null) {
                Bukkit.getScheduler().runTaskLater(currentPlugin, () -> {
                    if (loc.getWorld() != null) {
                        Firework fw2 = loc.getWorld().spawn(fwLoc.clone().add(0.5, 0, 0.5), Firework.class);
                        FireworkMeta meta2 = fw2.getFireworkMeta();
                        meta2.addEffect(FireworkEffect.builder()
                                .withColor(Color.fromRGB(255, 215, 0), Color.fromRGB(186, 85, 211))
                                .with(FireworkEffect.Type.STAR)
                                .trail(true)
                                .flicker(true)
                                .build());
                        meta2.setPower(1);
                        meta2.getPersistentDataContainer().set(HARMLESS_FIREWORK_KEY, PersistentDataType.BYTE, (byte) 1);
                        fw2.setFireworkMeta(meta2);
                    }
                }, 8L);
            }
        } catch (Exception ignored) {}
    }

    private void giveReward(Player targetPlayer, int multiplier) {
        if (betItem == null || betItem.getType().isAir()) return;

        int totalAmount = betItem.getAmount() * multiplier;
        int maxStack = betItem.getMaxStackSize();

        while (totalAmount > 0) {
            int toGive = Math.min(totalAmount, maxStack);
            ItemStack reward = betItem.clone();
            reward.setAmount(toGive);

            HashMap<Integer, ItemStack> leftover = targetPlayer.getInventory().addItem(reward);
            for (ItemStack rem : leftover.values()) {
                targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), rem);
            }

            totalAmount -= toGive;
        }
    }

    private void giveCuprite(Player targetPlayer, int amount, Plugin currentPlugin) {
        if (currentPlugin == null) return;
        ItemStack cuprite = Cuprite.create(currentPlugin, amount);
        HashMap<Integer, ItemStack> leftover = targetPlayer.getInventory().addItem(cuprite);
        for (ItemStack rem : leftover.values()) {
            targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), rem);
        }
    }

    public static boolean isValidEntryItem(ItemStack item, World.Environment env) {
        if (item == null || item.getType().isAir()) return false;
        if (env == World.Environment.THE_END) {
            return item.getType() == Material.DRAGON_HEAD && item.getAmount() >= 1;
        } else if (env == World.Environment.NETHER) {
            return item.getType() == Material.GILDED_BLACKSTONE && item.getAmount() >= GILDED_BLACKSTONE_COST;
        } else {
            return (item.getType() == Material.DRAGON_HEAD && item.getAmount() >= 1)
                    || (item.getType() == Material.GILDED_BLACKSTONE && item.getAmount() >= GILDED_BLACKSTONE_COST);
        }
    }

    public static boolean hasEntryItems(Player player) {
        World.Environment env = player.getWorld().getEnvironment();
        if (env == World.Environment.THE_END) {
            return player.getInventory().containsAtLeast(new ItemStack(Material.DRAGON_HEAD), 1);
        } else if (env == World.Environment.NETHER) {
            return player.getInventory().containsAtLeast(new ItemStack(Material.GILDED_BLACKSTONE), GILDED_BLACKSTONE_COST);
        } else {
            return player.getInventory().containsAtLeast(new ItemStack(Material.DRAGON_HEAD), 1)
                    || player.getInventory().containsAtLeast(new ItemStack(Material.GILDED_BLACKSTONE), GILDED_BLACKSTONE_COST);
        }
    }

    public static boolean consumeEntryItems(Player player) {
        World.Environment env = player.getWorld().getEnvironment();
        if (env == World.Environment.THE_END) {
            if (!player.getInventory().containsAtLeast(new ItemStack(Material.DRAGON_HEAD), 1)) return false;
            player.getInventory().removeItem(new ItemStack(Material.DRAGON_HEAD, 1));
            return true;
        } else if (env == World.Environment.NETHER) {
            if (!player.getInventory().containsAtLeast(new ItemStack(Material.GILDED_BLACKSTONE), GILDED_BLACKSTONE_COST)) return false;
            player.getInventory().removeItem(new ItemStack(Material.GILDED_BLACKSTONE, GILDED_BLACKSTONE_COST));
            return true;
        } else {
            if (player.getInventory().containsAtLeast(new ItemStack(Material.DRAGON_HEAD), 1)) {
                player.getInventory().removeItem(new ItemStack(Material.DRAGON_HEAD, 1));
                return true;
            } else if (player.getInventory().containsAtLeast(new ItemStack(Material.GILDED_BLACKSTONE), GILDED_BLACKSTONE_COST)) {
                player.getInventory().removeItem(new ItemStack(Material.GILDED_BLACKSTONE, GILDED_BLACKSTONE_COST));
                return true;
            }
            return false;
        }
    }

    public static String getEntryCostDescription(Player player) {
        World.Environment env = player.getWorld().getEnvironment();
        if (env == World.Environment.THE_END) {
            return "1 Tête de Dragon";
        } else if (env == World.Environment.NETHER) {
            return GILDED_BLACKSTONE_COST + " Pierres Noires Dorées";
        } else {
            return "1 Tête de Dragon ou " + GILDED_BLACKSTONE_COST + " Pierres Noires Dorées";
        }
    }

    public static boolean isValidHordeEntryItem(Plugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return Cuprite.isCuprite(plugin, item) && item.getAmount() >= 1;
    }

    public void loadChallengeFromPdc(Plugin plugin) {
        if (plugin == null) return;
        NamespacedKey key = new NamespacedKey(plugin, "casino_challenge_chips");
        Integer chips = player.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        this.challengeChips = (chips != null && chips > 0) ? chips : 0;
        if (this.challengeChips > 0 && (this.challengeBet > this.challengeChips || this.challengeBet <= 0)) {
            this.challengeBet = Math.min(10, this.challengeChips);
        }
    }

    public void saveChallengeToPdc(Plugin plugin) {
        if (plugin == null) return;
        NamespacedKey key = new NamespacedKey(plugin, "casino_challenge_chips");
        if (challengeChips > 0) {
            player.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, challengeChips);
        } else {
            player.getPersistentDataContainer().remove(key);
        }
    }

    public void loadHordeFromPdc(Plugin plugin) {
        if (plugin == null) return;
        NamespacedKey key = new NamespacedKey(plugin, "casino_horde_chips");
        Integer chips = player.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        this.hordeChips = (chips != null && chips > 0) ? chips : 0;
        if (this.hordeChips > 0 && (this.hordeBet > this.hordeChips || this.hordeBet <= 0)) {
            this.hordeBet = Math.min(10, this.hordeChips);
        }
    }

    public void saveHordeToPdc(Plugin plugin) {
        if (plugin == null) return;
        NamespacedKey key = new NamespacedKey(plugin, "casino_horde_chips");
        if (hordeChips > 0) {
            player.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, hordeChips);
        } else {
            player.getPersistentDataContainer().remove(key);
        }
    }

    public void cancelBetAndReturn(Player targetPlayer) {
        if (mode == Mode.CLASSIC && state == State.BETTING && betItem != null && !paidOut) {
            giveReward(targetPlayer, 1);
            paidOut = true;
        }
    }

    public void resetToBetting() {
        cancelCurrentTask();
        this.playerHand.clear();
        this.dealerHand.clear();
        this.state = State.BETTING;
        this.result = Result.NONE;
        this.betItem = null;
        this.paidOut = false;
        this.activeChallengeBet = 0;
        this.activeHordeBet = 0;
        this.jackpotWon = false;
        if (this.challengeChips > 0 && this.challengeBet > this.challengeChips) {
            this.challengeBet = this.challengeChips;
        } else if (this.challengeChips > 0 && this.challengeBet <= 0) {
            this.challengeBet = Math.min(10, this.challengeChips);
        } else if (this.challengeChips <= 0) {
            this.challengeBet = 10;
        }
        if (this.hordeChips > 0 && this.hordeBet > this.hordeChips) {
            this.hordeBet = this.hordeChips;
        } else if (this.hordeChips > 0 && this.hordeBet <= 0) {
            this.hordeBet = Math.min(10, this.hordeChips);
        } else if (this.hordeChips <= 0) {
            this.hordeBet = 10;
        }
    }

    public void addChallengeBet(int amount) {
        if (state != State.BETTING) return;
        this.challengeBet = Math.min(challengeChips, this.challengeBet + amount);
    }

    public void adjustChallengeBet(int delta) {
        if (state != State.BETTING) return;
        int minBet = Math.min(10, challengeChips);
        int newBet = this.challengeBet + delta;
        if (newBet < minBet) newBet = minBet;
        if (newBet > challengeChips) newBet = challengeChips;
        this.challengeBet = newBet;
    }

    public void setChallengeBet(int amount) {
        if (state != State.BETTING) return;
        this.challengeBet = Math.max(1, Math.min(challengeChips, amount));
    }

    public void adjustHordeBet(int delta) {
        if (state != State.BETTING) return;
        int minBet = Math.min(10, hordeChips);
        int newBet = this.hordeBet + delta;
        if (newBet < minBet) newBet = minBet;
        if (newBet > hordeChips) newBet = hordeChips;
        this.hordeBet = newBet;
    }

    public void setHordeBet(int amount) {
        if (state != State.BETTING) return;
        this.hordeBet = Math.max(1, Math.min(hordeChips, amount));
    }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public int getChallengeChips() { return challengeChips; }
    public void setChallengeChips(int challengeChips) { this.challengeChips = challengeChips; }
    public int getChallengeBet() { return challengeBet; }
    public int getActiveChallengeBet() { return activeChallengeBet; }
    public boolean isJackpotWon() { return jackpotWon; }

    public int getHordeChips() { return hordeChips; }
    public void setHordeChips(int hordeChips) { this.hordeChips = hordeChips; }
    public int getHordeBet() { return hordeBet; }
    public int getActiveHordeBet() { return activeHordeBet; }

    public Player getPlayer() { return player; }
    public List<Card> getPlayerHand() { return playerHand; }
    public List<Card> getDealerHand() { return dealerHand; }
    public State getState() { return state; }
    public Result getResult() { return result; }
    public ItemStack getBetItem() { return betItem; }
    public boolean isPaidOut() { return paidOut; }
}

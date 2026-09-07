package fr.loual.casino;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BlackjackGame {

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
        DEALER_BUST,
        DEALER_WIN,
        PLAYER_BUST,
        PUSH
    }

    private final Player player;
    private final Deck deck = new Deck();
    private final List<Card> playerHand = new ArrayList<>();
    private final List<Card> dealerHand = new ArrayList<>();

    private State state = State.BETTING;
    private Result result = Result.NONE;
    private ItemStack betItem = null;
    private boolean paidOut = false;
    private BukkitTask currentTask = null;

    public BlackjackGame(Player player) {
        this.player = player;
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
        this.playerHand.clear();
        this.dealerHand.clear();
        this.deck.resetAndShuffle();
        this.state = State.DEALING;
        this.result = Result.NONE;
        this.paidOut = false;
        onUpdate.run();

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
        }
        onUpdate.run();
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
        if (paidOut || betItem == null) return;
        paidOut = true;

        Location loc = targetPlayer.getLocation();

        switch (result) {
            case PLAYER_BLACKJACK -> {
                // Victoire Blackjack naturel : Payé 3 pour 1 (Triple la mise !)
                giveReward(targetPlayer, 3);
                targetPlayer.sendMessage(Component.text("✦ BLACKJACK NATUREL ! ✦ Payé 3 pour 1 ! Vous recevez le triple de votre mise !", NamedTextColor.GOLD, TextDecoration.BOLD));
                targetPlayer.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.1f);
                targetPlayer.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
                targetPlayer.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 35, 0.5, 0.5, 0.5, 0.2);
            }
            case PLAYER_WIN, DEALER_BUST -> {
                // Victoire standard : Doubler la mise !
                giveReward(targetPlayer, 2);
                String reason = (result == Result.DEALER_BUST) ? "Le croupier a dépassé 21 (Bust) !" : "Votre score est supérieur à celui du croupier !";
                targetPlayer.sendMessage(Component.text("✔ VICTOIRE ! " + reason + " Vous doublez votre mise !", NamedTextColor.GREEN, TextDecoration.BOLD));
                targetPlayer.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                targetPlayer.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1, 0), 25, 0.4, 0.4, 0.4, 0.05);
            }
            case PUSH -> {
                // Égalité : Restitution de la mise
                giveReward(targetPlayer, 1);
                targetPlayer.sendMessage(Component.text("═ ÉGALITÉ (PUSH) ! Même score que le croupier, votre mise vous est rendue.", NamedTextColor.YELLOW, TextDecoration.BOLD));
                targetPlayer.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            }
            case PLAYER_BUST, DEALER_WIN -> {
                // Défaite : Mise perdue
                String reason = (result == Result.PLAYER_BUST) ? "Vous avez dépassé 21 (Bust) !" : "Le score du croupier est supérieur au vôtre.";
                targetPlayer.sendMessage(Component.text("✘ DÉFAITE ! " + reason + " Votre mise est perdue.", NamedTextColor.RED, TextDecoration.BOLD));
                targetPlayer.playSound(loc, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.9f);
                targetPlayer.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.6f);
            }
            default -> {}
        }
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

    public void cancelBetAndReturn(Player targetPlayer) {
        if (state == State.BETTING && betItem != null && !paidOut) {
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
    }

    public Player getPlayer() { return player; }
    public List<Card> getPlayerHand() { return playerHand; }
    public List<Card> getDealerHand() { return dealerHand; }
    public State getState() { return state; }
    public Result getResult() { return result; }
    public ItemStack getBetItem() { return betItem; }
    public boolean isPaidOut() { return paidOut; }
}

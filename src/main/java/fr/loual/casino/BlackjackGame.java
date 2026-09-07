package fr.loual.casino;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BlackjackGame {

    public enum State {
        BETTING,
        PLAYING,
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

    public void start(ItemStack bet) {
        if (bet == null || bet.getType().isAir() || bet.getAmount() <= 0) return;

        this.betItem = bet.clone();
        this.playerHand.clear();
        this.dealerHand.clear();
        this.deck.resetAndShuffle();
        this.state = State.PLAYING;
        this.result = Result.NONE;
        this.paidOut = false;

        // Distribution initiale : 2 cartes joueur, 2 cartes croupier (1 visible, 1 cachée)
        playerHand.add(deck.draw());
        dealerHand.add(deck.draw());
        playerHand.add(deck.draw());
        dealerHand.add(deck.draw());

        // Vérification immédiate du Blackjack naturel
        boolean pBj = isNaturalBlackjack(playerHand);
        boolean dBj = isNaturalBlackjack(dealerHand);

        if (pBj && dBj) {
            this.state = State.GAME_OVER;
            this.result = Result.PUSH;
            applyPayout(player);
        } else if (pBj) {
            this.state = State.GAME_OVER;
            this.result = Result.PLAYER_BLACKJACK;
            applyPayout(player);
        }
    }

    public void hit() {
        if (state != State.PLAYING) return;

        playerHand.add(deck.draw());
        int pScore = calculateScore(playerHand);

        if (pScore > 21) {
            state = State.GAME_OVER;
            result = Result.PLAYER_BUST;
            applyPayout(player);
        }
    }

    public void stand() {
        if (state != State.PLAYING) return;

        // Le croupier joue selon la règle casino standard : tire tant qu'il a moins de 17
        while (calculateScore(dealerHand) < 17) {
            dealerHand.add(deck.draw());
        }

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
    }

    public void applyPayout(Player targetPlayer) {
        if (paidOut || betItem == null) return;
        paidOut = true;

        Location loc = targetPlayer.getLocation();

        switch (result) {
            case PLAYER_BLACKJACK -> {
                // Victoire Blackjack naturel : Doubler la mise !
                giveReward(targetPlayer, 2);
                targetPlayer.sendMessage(Component.text("✦ BLACKJACK ! ✦ Vous réalisez un 21 naturel et doublez votre mise !", NamedTextColor.GOLD, TextDecoration.BOLD));
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

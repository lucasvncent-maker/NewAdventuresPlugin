package fr.loual.casino;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class Card {

    public enum Suit {
        HEARTS("♥", NamedTextColor.RED, "Cœur"),
        DIAMONDS("♦", NamedTextColor.RED, "Carreau"),
        CLUBS("♣", NamedTextColor.DARK_GRAY, "Trèfle"),
        SPADES("♠", NamedTextColor.BLACK, "Pique");

        private final String symbol;
        private final NamedTextColor color;
        private final String frenchName;

        Suit(String symbol, NamedTextColor color, String frenchName) {
            this.symbol = symbol;
            this.color = color;
            this.frenchName = frenchName;
        }

        public String getSymbol() { return symbol; }
        public NamedTextColor getColor() { return color; }
        public String getFrenchName() { return frenchName; }
    }

    public enum Rank {
        TWO("2", 2),
        THREE("3", 3),
        FOUR("4", 4),
        FIVE("5", 5),
        SIX("6", 6),
        SEVEN("7", 7),
        EIGHT("8", 8),
        NINE("9", 9),
        TEN("10", 10),
        JACK("Valet", 10),
        QUEEN("Dame", 10),
        KING("Roi", 10),
        ACE("As", 11);

        private final String label;
        private final int value;

        Rank(String label, int value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() { return label; }
        public int getValue() { return value; }
    }

    private final Suit suit;
    private final Rank rank;

    public Card(Suit suit, Rank rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public Suit getSuit() { return suit; }
    public Rank getRank() { return rank; }
    public int getValue() { return rank.getValue(); }
    public boolean isAce() { return rank == Rank.ACE; }

    public ItemStack toItemStack() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String shortSymbol = rank.getLabel().equals("10") ? "10" : rank.getLabel().substring(0, 1);
            if (rank == Rank.ACE) shortSymbol = "A";
            else if (rank == Rank.KING) shortSymbol = "R";
            else if (rank == Rank.QUEEN) shortSymbol = "D";
            else if (rank == Rank.JACK) shortSymbol = "V";

            Component name = Component.text(suit.getSymbol() + " " + rank.getLabel() + " de " + suit.getFrenchName(), suit.getColor(), TextDecoration.BOLD);
            meta.displayName(name);

            String valDesc = isAce() ? "1 ou 11" : String.valueOf(rank.getValue());
            meta.lore(List.of(
                    Component.text("§7Type : §fCarte de Blackjack"),
                    Component.text("§7Valeur : §e" + valDesc),
                    Component.text("§8[" + shortSymbol + " " + suit.getSymbol() + "]")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack getHiddenCardItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("🂠 Carte cachée du Croupier", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("§7Cette carte sera révélée"),
                    Component.text("§7lorsque vous ferez §cRester (Stand)§7.")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }
}

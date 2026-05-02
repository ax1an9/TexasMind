package com.texasholdem.core.model;

import java.util.Objects;

public final class Card {
    private final Rank rank;
    private final Suit suit;

    public Card(Rank rank, Suit suit) {
        this.rank = Objects.requireNonNull(rank, "rank");
        this.suit = Objects.requireNonNull(suit, "suit");
    }

    public Rank getRank() {
        return rank;
    }

    public Suit getSuit() {
        return suit;
    }

    public static Card of(String code) {
        if (code == null || code.length() < 2 || code.length() > 3) {
            throw new IllegalArgumentException("Invalid card code: " + code);
        }
        String rankCode = code.substring(0, code.length() - 1).toUpperCase();
        char suitCode = Character.toLowerCase(code.charAt(code.length() - 1));
        return new Card(parseRank(rankCode), parseSuit(suitCode));
    }

    private static Rank parseRank(String code) {
        if ("A".equals(code)) {
            return Rank.ACE;
        }
        if ("K".equals(code)) {
            return Rank.KING;
        }
        if ("Q".equals(code)) {
            return Rank.QUEEN;
        }
        if ("J".equals(code)) {
            return Rank.JACK;
        }
        if ("T".equals(code) || "10".equals(code)) {
            return Rank.TEN;
        }
        return Rank.fromValue(Integer.parseInt(code));
    }

    private static Suit parseSuit(char code) {
        switch (code) {
            case 'h':
                return Suit.HEARTS;
            case 'd':
                return Suit.DIAMONDS;
            case 'c':
                return Suit.CLUBS;
            case 's':
                return Suit.SPADES;
            default:
                throw new IllegalArgumentException("Invalid suit code: " + code);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass())
            ;
        Card card = (Card) other;
        return rank == card.rank && suit == card.suit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rank, suit);
    }

    @Override
    public String toString() {
        return rank + " of " + suit;
    }
}
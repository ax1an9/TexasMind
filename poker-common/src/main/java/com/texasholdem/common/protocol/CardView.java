package com.texasholdem.common.protocol;

public class CardView {
    private String rank;
    private String suit;

    public CardView() {}

    public CardView(String rank, String suit) {
        this.rank = rank;
        this.suit = suit;
    }

    public static CardView fromCard(String code) {
        if (code == null || code.length() < 2) return null;
        String rank = code.substring(0, code.length() - 1);
        String suit = code.substring(code.length() - 1);
        return new CardView(rank, suit);
    }

    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }
    public String getSuit() { return suit; }
    public void setSuit(String suit) { this.suit = suit; }
}

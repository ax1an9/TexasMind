package com.texasholdem.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class Deck {
    private final List<Card> cards;

    private Deck(List<Card> cards) {
        this.cards = Collections.unmodifiableList(new ArrayList<Card>(cards));
    }

    public static Deck standardShuffled(long seed) {
        List<Card> cards = new ArrayList<Card>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(cards, new Random(seed));
        return new Deck(cards);
    }

    public static Deck fixed(List<Card> cards) {
        return new Deck(cards);
    }

    public List<Card> getCards() {
        return cards;
    }
}
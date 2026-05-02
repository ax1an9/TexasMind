package com.texasholdem.core.eval;

import com.texasholdem.core.model.Card;
import com.texasholdem.core.model.HandRank;
import com.texasholdem.core.model.Rank;
import com.texasholdem.core.model.Suit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HandEvaluator {

    public HandValue evaluateBest(List<Card> cards) {
        if (cards == null || cards.size() < 5) {
            throw new IllegalArgumentException("At least 5 cards are required to evaluate a hand");
        }
        HandValue best = null;
        for (List<Card> combo : combinations(cards, 5)) {
            HandValue current = evaluateFive(combo);
            if (best == null || current.compareTo(best) > 0) {
                best = current;
            }
        }
        return best;
    }

    public HandValue evaluateFive(List<Card> cards) {
        if (cards == null || cards.size() != 5) {
            throw new IllegalArgumentException("Exactly 5 cards are required");
        }

        List<Integer> ranks = new ArrayList<Integer>();
        Map<Integer, Integer> counts = new HashMap<Integer, Integer>();
        Map<Suit, Integer> suitCounts = new HashMap<Suit, Integer>();
        for (Card card : cards) {
            int value = card.getRank().getValue();
            ranks.add(value);
            counts.put(value, counts.containsKey(value) ? counts.get(value) + 1 : 1);
            suitCounts.put(card.getSuit(),
                    suitCounts.containsKey(card.getSuit()) ? suitCounts.get(card.getSuit()) + 1 : 1);
        }

        Collections.sort(ranks, Collections.reverseOrder());
        boolean flush = false;
        for (Integer count : suitCounts.values()) {
            if (count == 5) {
                flush = true;
                break;
            }
        }

        Integer straightHigh = straightHigh(ranks);
        if (flush && straightHigh != null) {
            if (straightHigh == Rank.ACE.getValue()) {
                return new HandValue(HandRank.ROYAL_FLUSH, Arrays.asList(straightHigh));
            }
            return new HandValue(HandRank.STRAIGHT_FLUSH, Arrays.asList(straightHigh));
        }

        List<Map.Entry<Integer, Integer>> grouped = new ArrayList<Map.Entry<Integer, Integer>>(counts.entrySet());
        Collections.sort(grouped, new Comparator<Map.Entry<Integer, Integer>>() {
            @Override
            public int compare(Map.Entry<Integer, Integer> left, Map.Entry<Integer, Integer> right) {
                int byCount = Integer.compare(right.getValue(), left.getValue());
                if (byCount != 0) {
                    return byCount;
                }
                return Integer.compare(right.getKey(), left.getKey());
            }
        });

        if (grouped.get(0).getValue() == 4) {
            int four = grouped.get(0).getKey();
            int kicker = grouped.get(1).getKey();
            return new HandValue(HandRank.FOUR_OF_A_KIND, Arrays.asList(four, kicker));
        }

        if (grouped.get(0).getValue() == 3 && grouped.size() > 1 && grouped.get(1).getValue() >= 2) {
            return new HandValue(HandRank.FULL_HOUSE, Arrays.asList(grouped.get(0).getKey(), grouped.get(1).getKey()));
        }

        if (flush) {
            return new HandValue(HandRank.FLUSH, ranks);
        }

        if (straightHigh != null) {
            return new HandValue(HandRank.STRAIGHT, Arrays.asList(straightHigh));
        }

        if (grouped.get(0).getValue() == 3) {
            int trips = grouped.get(0).getKey();
            List<Integer> kickers = new ArrayList<Integer>();
            for (int i = 1; i < grouped.size(); i++) {
                kickers.add(grouped.get(i).getKey());
            }
            Collections.sort(kickers, Collections.reverseOrder());
            List<Integer> tieBreakers = new ArrayList<Integer>();
            tieBreakers.add(trips);
            tieBreakers.addAll(kickers);
            return new HandValue(HandRank.THREE_OF_A_KIND, tieBreakers);
        }

        if (grouped.get(0).getValue() == 2 && grouped.size() > 1 && grouped.get(1).getValue() == 2) {
            int highPair = Math.max(grouped.get(0).getKey(), grouped.get(1).getKey());
            int lowPair = Math.min(grouped.get(0).getKey(), grouped.get(1).getKey());
            int kicker = grouped.size() > 2 ? grouped.get(2).getKey() : 0;
            return new HandValue(HandRank.TWO_PAIR, Arrays.asList(highPair, lowPair, kicker));
        }

        if (grouped.get(0).getValue() == 2) {
            int pair = grouped.get(0).getKey();
            List<Integer> kickers = new ArrayList<Integer>();
            for (int i = 1; i < grouped.size(); i++) {
                kickers.add(grouped.get(i).getKey());
            }
            Collections.sort(kickers, Collections.reverseOrder());
            List<Integer> tieBreakers = new ArrayList<Integer>();
            tieBreakers.add(pair);
            tieBreakers.addAll(kickers);
            return new HandValue(HandRank.ONE_PAIR, tieBreakers);
        }

        return new HandValue(HandRank.HIGH_CARD, ranks);
    }

    private Integer straightHigh(List<Integer> ranksDesc) {
        List<Integer> distinct = new ArrayList<Integer>();
        Set<Integer> seen = new HashSet<Integer>();
        for (Integer rank : ranksDesc) {
            if (seen.add(rank)) {
                distinct.add(rank);
            }
        }
        Collections.sort(distinct, Collections.reverseOrder());
        if (isSequential(distinct)) {
            return distinct.get(0);
        }

        List<Integer> wheel = Arrays.asList(Rank.FIVE.getValue(), Rank.FOUR.getValue(), Rank.THREE.getValue(),
                Rank.TWO.getValue(), Rank.ACE.getValue());
        if (distinct.size() == 5 && distinct.containsAll(wheel)) {
            return Rank.FIVE.getValue();
        }
        return null;
    }

    private boolean isSequential(List<Integer> ranksDesc) {
        if (ranksDesc.size() != 5) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (ranksDesc.get(i) - 1 != ranksDesc.get(i + 1)) {
                return false;
            }
        }
        return true;
    }

    private List<List<Card>> combinations(List<Card> cards, int choose) {
        List<List<Card>> result = new ArrayList<List<Card>>();
        buildCombinations(cards, choose, 0, new ArrayList<Card>(), result);
        return result;
    }

    private void buildCombinations(List<Card> cards, int choose, int index, List<Card> current,
            List<List<Card>> result) {
        if (current.size() == choose) {
            result.add(new ArrayList<Card>(current));
            return;
        }
        for (int i = index; i < cards.size(); i++) {
            current.add(cards.get(i));
            buildCombinations(cards, choose, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}
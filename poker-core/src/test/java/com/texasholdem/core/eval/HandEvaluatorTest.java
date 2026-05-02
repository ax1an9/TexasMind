package com.texasholdem.core.eval;

import com.texasholdem.core.model.Card;
import com.texasholdem.core.model.HandRank;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HandEvaluatorTest {
    private final HandEvaluator evaluator = new HandEvaluator();

    @Test
    void detectsRoyalFlush() {
        HandValue value = evaluator.evaluateFive(Arrays.asList(
                Card.of("Ah"), Card.of("Kh"), Card.of("Qh"), Card.of("Jh"), Card.of("Th")));

        assertEquals(HandRank.ROYAL_FLUSH, value.getHandRank());
    }

    @Test
    void comparesFullHouseAboveFlush() {
        HandValue fullHouse = evaluator.evaluateFive(Arrays.asList(
                Card.of("As"), Card.of("Ad"), Card.of("Ac"), Card.of("Kc"), Card.of("Kd")));
        HandValue flush = evaluator.evaluateFive(Arrays.asList(
                Card.of("2h"), Card.of("5h"), Card.of("7h"), Card.of("9h"), Card.of("Kh")));

        assertEquals(true, fullHouse.compareTo(flush) > 0);
    }

    @Test
    void picksBestOfSevenCards() {
        HandValue value = evaluator.evaluateBest(Arrays.asList(
                Card.of("Ah"), Card.of("Kh"), Card.of("Qh"), Card.of("Jh"), Card.of("Th"),
                Card.of("2c"), Card.of("3d")));

        assertEquals(HandRank.ROYAL_FLUSH, value.getHandRank());
    }
}
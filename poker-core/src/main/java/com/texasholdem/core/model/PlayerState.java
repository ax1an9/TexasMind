package com.texasholdem.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PlayerState {
    private final String seatId;
    private final int chips;
    private final List<Card> holeCards;
    private final boolean folded;
    private final boolean allIn;
    private final int roundContribution;
    private final int totalContribution;
    private final boolean actedThisRound;

    public PlayerState(String seatId, int chips) {
        this(seatId, chips, Collections.<Card>emptyList(), false, false, 0, 0, false);
    }

    private PlayerState(String seatId, int chips, List<Card> holeCards, boolean folded, boolean allIn,
            int roundContribution, int totalContribution, boolean actedThisRound) {
        this.seatId = Objects.requireNonNull(seatId, "seatId");
        this.chips = chips;
        this.holeCards = Collections.unmodifiableList(new ArrayList<Card>(holeCards));
        this.folded = folded;
        this.allIn = allIn;
        this.roundContribution = roundContribution;
        this.totalContribution = totalContribution;
        this.actedThisRound = actedThisRound;
    }

    public String getSeatId() {
        return seatId;
    }

    public int getChips() {
        return chips;
    }

    public List<Card> getHoleCards() {
        return holeCards;
    }

    public boolean isFolded() {
        return folded;
    }

    public boolean isAllIn() {
        return allIn;
    }

    public int getRoundContribution() {
        return roundContribution;
    }

    public int getTotalContribution() {
        return totalContribution;
    }

    public boolean isActedThisRound() {
        return actedThisRound;
    }

    public boolean canAct() {
        return !folded && !allIn && chips > 0;
    }

    public PlayerState withHoleCards(List<Card> cards) {
        return new PlayerState(seatId, chips, cards, folded, allIn, roundContribution, totalContribution,
                actedThisRound);
    }

    public PlayerState withChips(int chips) {
        return new PlayerState(seatId, chips, holeCards, folded, allIn, roundContribution, totalContribution,
                actedThisRound);
    }

    public PlayerState withActedThisRound(boolean actedThisRound) {
        return new PlayerState(seatId, chips, holeCards, folded, allIn, roundContribution, totalContribution,
                actedThisRound);
    }

    public PlayerState resetForNewHand(List<Card> cards) {
        return new PlayerState(seatId, chips, cards, false, false, 0, 0, false);
    }

    public PlayerState rebuild(int chips, List<Card> cards, boolean folded, boolean allIn, int roundContribution,
            int totalContribution, boolean actedThisRound) {
        return new PlayerState(seatId, chips, cards, folded, allIn, roundContribution, totalContribution,
                actedThisRound);
    }

    public PlayerState fold() {
        return new PlayerState(seatId, chips, holeCards, true, allIn, roundContribution, totalContribution, true);
    }

    public PlayerState commit(int amount, int roundContributionTarget) {
        int contribution = Math.min(amount, chips);
        int remainingChips = chips - contribution;
        int newRoundContribution = roundContribution + contribution;
        int newTotalContribution = totalContribution + contribution;
        boolean newAllIn = allIn || remainingChips == 0;
        return new PlayerState(seatId, remainingChips, holeCards, folded, newAllIn, newRoundContribution,
                newTotalContribution, true);
    }
}
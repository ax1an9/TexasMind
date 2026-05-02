package com.texasholdem.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PotSlice {
    private final int amount;
    private final int threshold;
    private final List<String> eligibleSeatIds;

    public PotSlice(int amount, int threshold, List<String> eligibleSeatIds) {
        this.amount = amount;
        this.threshold = threshold;
        this.eligibleSeatIds = Collections.unmodifiableList(new ArrayList<String>(eligibleSeatIds));
    }

    public int getAmount() {
        return amount;
    }

    public int getThreshold() {
        return threshold;
    }

    public List<String> getEligibleSeatIds() {
        return eligibleSeatIds;
    }
}
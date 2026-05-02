package com.texasholdem.core.eval;

import com.texasholdem.core.model.HandRank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HandValue implements Comparable<HandValue> {
    private final HandRank handRank;
    private final List<Integer> tieBreakers;

    public HandValue(HandRank handRank, List<Integer> tieBreakers) {
        this.handRank = handRank;
        this.tieBreakers = Collections.unmodifiableList(new ArrayList<Integer>(tieBreakers));
    }

    public HandRank getHandRank() {
        return handRank;
    }

    public List<Integer> getTieBreakers() {
        return tieBreakers;
    }

    @Override
    public int compareTo(HandValue other) {
        if (handRank != other.handRank) {
            return Integer.compare(handRank.ordinal(), other.handRank.ordinal());
        }
        int size = Math.max(tieBreakers.size(), other.tieBreakers.size());
        for (int i = 0; i < size; i++) {
            int left = i < tieBreakers.size() ? tieBreakers.get(i) : 0;
            int right = i < other.tieBreakers.size() ? other.tieBreakers.get(i) : 0;
            if (left != right) {
                return Integer.compare(left, right);
            }
        }
        return 0;
    }
}
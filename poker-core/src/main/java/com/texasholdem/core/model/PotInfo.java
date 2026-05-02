package com.texasholdem.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PotInfo {
    private final PotSlice mainPotSlice;
    private final List<PotSlice> sidePots;

    public PotInfo(PotSlice mainPotSlice, List<PotSlice> sidePots) {
        this.mainPotSlice = mainPotSlice;
        this.sidePots = Collections.unmodifiableList(new ArrayList<PotSlice>(sidePots));
    }

    public int getMainPot() {
        return mainPotSlice != null ? mainPotSlice.getAmount() : 0;
    }

    public PotSlice getMainPotSlice() {
        return mainPotSlice;
    }

    public List<PotSlice> getSidePots() {
        return sidePots;
    }

    public int getTotalPot() {
        int total = getMainPot();
        for (PotSlice sidePot : sidePots) {
            total += sidePot.getAmount();
        }
        return total;
    }

    public static PotInfo empty() {
        return new PotInfo(null, Collections.<PotSlice>emptyList());
    }

    public static PotInfo fromPlayers(List<PlayerState> players) {
        List<Integer> thresholds = new ArrayList<Integer>();
        for (PlayerState player : players) {
            if (player.getTotalContribution() > 0) {
                thresholds.add(player.getTotalContribution());
            }
        }
        if (thresholds.isEmpty()) {
            return empty();
        }
        Collections.sort(thresholds);
        List<Integer> uniqueThresholds = new ArrayList<Integer>();
        Integer previous = null;
        for (Integer threshold : thresholds) {
            if (!threshold.equals(previous)) {
                uniqueThresholds.add(threshold);
            }
            previous = threshold;
        }

        List<PotSlice> slices = new ArrayList<PotSlice>();
        int previousThreshold = 0;
        for (Integer threshold : uniqueThresholds) {
            List<String> eligible = new ArrayList<String>();
            int contributors = 0;
            for (PlayerState player : players) {
                if (player.getTotalContribution() >= threshold) {
                    contributors++;
                    eligible.add(player.getSeatId());
                }
            }
            int amount = (threshold - previousThreshold) * contributors;
            slices.add(new PotSlice(amount, threshold, eligible));
            previousThreshold = threshold;
        }

        if (slices.isEmpty()) {
            return empty();
        }
        PotSlice mainPot = slices.get(0);
        List<PotSlice> sidePots = slices.size() > 1 ? slices.subList(1, slices.size())
                : Collections.<PotSlice>emptyList();
        return new PotInfo(mainPot, sidePots);
    }
}

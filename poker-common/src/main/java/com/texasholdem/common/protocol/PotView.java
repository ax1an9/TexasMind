package com.texasholdem.common.protocol;

import java.util.List;

public class PotView {
    private int totalPot;
    private int mainPot;
    private List<SidePotView> sidePots;

    public PotView() {}

    public int getTotalPot() { return totalPot; }
    public void setTotalPot(int totalPot) { this.totalPot = totalPot; }
    public int getMainPot() { return mainPot; }
    public void setMainPot(int mainPot) { this.mainPot = mainPot; }
    public List<SidePotView> getSidePots() { return sidePots; }
    public void setSidePots(List<SidePotView> sidePots) { this.sidePots = sidePots; }

    public static class SidePotView {
        private int amount;
        private List<String> eligibleSeatIds;

        public SidePotView() {}

        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }
        public List<String> getEligibleSeatIds() { return eligibleSeatIds; }
        public void setEligibleSeatIds(List<String> eligibleSeatIds) { this.eligibleSeatIds = eligibleSeatIds; }
    }
}

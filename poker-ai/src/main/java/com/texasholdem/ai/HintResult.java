package com.texasholdem.ai;

import java.util.List;

public final class HintResult {
    private final String suggestedAction;
    private final double handStrength;
    private final double potOdds;
    private final String reasoning;
    private final String handRankName;
    private final List<StrengthFactor> strengthFactors;
    private final String simpleReasoning;
    private final int toCall;
    private final int totalPot;

    public HintResult(String suggestedAction, double handStrength, double potOdds, String reasoning,
                      String handRankName, List<StrengthFactor> strengthFactors,
                      String simpleReasoning, int toCall, int totalPot) {
        this.suggestedAction = suggestedAction;
        this.handStrength = handStrength;
        this.potOdds = potOdds;
        this.reasoning = reasoning;
        this.handRankName = handRankName;
        this.strengthFactors = strengthFactors;
        this.simpleReasoning = simpleReasoning;
        this.toCall = toCall;
        this.totalPot = totalPot;
    }

    public String getSuggestedAction() { return suggestedAction; }
    public double getHandStrength() { return handStrength; }
    public double getPotOdds() { return potOdds; }
    public String getReasoning() { return reasoning; }
    public String getHandRankName() { return handRankName; }
    public List<StrengthFactor> getStrengthFactors() { return strengthFactors; }
    public String getSimpleReasoning() { return simpleReasoning; }
    public int getToCall() { return toCall; }
    public int getTotalPot() { return totalPot; }

    public static final class StrengthFactor {
        private final String label;
        private final double value;
        private final String description;

        public StrengthFactor(String label, double value, String description) {
            this.label = label;
            this.value = value;
            this.description = description;
        }

        public String getLabel() { return label; }
        public double getValue() { return value; }
        public String getDescription() { return description; }
    }
}

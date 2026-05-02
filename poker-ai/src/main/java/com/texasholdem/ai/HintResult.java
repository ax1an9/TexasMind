package com.texasholdem.ai;

public final class HintResult {
    private final String suggestedAction;
    private final double handStrength;
    private final double potOdds;
    private final String reasoning;

    public HintResult(String suggestedAction, double handStrength, double potOdds, String reasoning) {
        this.suggestedAction = suggestedAction;
        this.handStrength = handStrength;
        this.potOdds = potOdds;
        this.reasoning = reasoning;
    }

    public String getSuggestedAction() { return suggestedAction; }
    public double getHandStrength() { return handStrength; }
    public double getPotOdds() { return potOdds; }
    public String getReasoning() { return reasoning; }
}

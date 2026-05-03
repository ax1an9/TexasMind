package com.texasholdem.common.protocol;

import java.util.List;

public class HintResultMessage extends ServerMessage {
    private String gameId;
    private String suggestedAction;
    private double handStrength;
    private double potOdds;
    private String reasoning;
    private String handRankName;
    private List<StrengthFactorDto> strengthFactors;
    private String simpleReasoning;
    private int toCall;
    private int totalPot;

    public HintResultMessage() {
        super("HINT_RESULT");
    }

    public HintResultMessage(String gameId, String suggestedAction, double handStrength, double potOdds, String reasoning) {
        super("HINT_RESULT");
        this.gameId = gameId;
        this.suggestedAction = suggestedAction;
        this.handStrength = handStrength;
        this.potOdds = potOdds;
        this.reasoning = reasoning;
    }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public String getSuggestedAction() { return suggestedAction; }
    public void setSuggestedAction(String suggestedAction) { this.suggestedAction = suggestedAction; }
    public double getHandStrength() { return handStrength; }
    public void setHandStrength(double handStrength) { this.handStrength = handStrength; }
    public double getPotOdds() { return potOdds; }
    public void setPotOdds(double potOdds) { this.potOdds = potOdds; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    public String getHandRankName() { return handRankName; }
    public void setHandRankName(String handRankName) { this.handRankName = handRankName; }
    public List<StrengthFactorDto> getStrengthFactors() { return strengthFactors; }
    public void setStrengthFactors(List<StrengthFactorDto> strengthFactors) { this.strengthFactors = strengthFactors; }
    public String getSimpleReasoning() { return simpleReasoning; }
    public void setSimpleReasoning(String simpleReasoning) { this.simpleReasoning = simpleReasoning; }
    public int getToCall() { return toCall; }
    public void setToCall(int toCall) { this.toCall = toCall; }
    public int getTotalPot() { return totalPot; }
    public void setTotalPot(int totalPot) { this.totalPot = totalPot; }

    public static final class StrengthFactorDto {
        private String label;
        private double value;
        private String description;

        public StrengthFactorDto() {}

        public StrengthFactorDto(String label, double value, String description) {
            this.label = label;
            this.value = value;
            this.description = description;
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}

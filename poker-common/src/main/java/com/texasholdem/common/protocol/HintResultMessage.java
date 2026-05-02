package com.texasholdem.common.protocol;

public class HintResultMessage extends ServerMessage {
    private String gameId;
    private String suggestedAction;
    private double handStrength;
    private double potOdds;
    private String reasoning;

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
}

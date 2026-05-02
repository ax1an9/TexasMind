package com.texasholdem.core.model;

public final class BetAction extends Action {
    public BetAction(String playerId, int amount) {
        super(playerId, ActionType.BET, amount);
    }
}
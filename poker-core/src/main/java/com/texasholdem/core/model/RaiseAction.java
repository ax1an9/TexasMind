package com.texasholdem.core.model;

public final class RaiseAction extends Action {
    public RaiseAction(String playerId, int amount) {
        super(playerId, ActionType.RAISE, amount);
    }
}
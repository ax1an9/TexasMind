package com.texasholdem.core.model;

public final class CheckAction extends Action {
    public CheckAction(String playerId) {
        super(playerId, ActionType.CHECK, 0);
    }
}
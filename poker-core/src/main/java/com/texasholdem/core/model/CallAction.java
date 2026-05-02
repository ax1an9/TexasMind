package com.texasholdem.core.model;

public final class CallAction extends Action {
    public CallAction(String playerId) {
        super(playerId, ActionType.CALL, 0);
    }
}
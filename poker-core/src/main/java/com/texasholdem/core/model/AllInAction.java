package com.texasholdem.core.model;

public final class AllInAction extends Action {
    public AllInAction(String playerId) {
        super(playerId, ActionType.ALL_IN, 0);
    }
}
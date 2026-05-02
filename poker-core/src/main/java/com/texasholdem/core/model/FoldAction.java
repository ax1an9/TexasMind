package com.texasholdem.core.model;

public final class FoldAction extends Action {
    public FoldAction(String playerId) {
        super(playerId, ActionType.FOLD, 0);
    }
}
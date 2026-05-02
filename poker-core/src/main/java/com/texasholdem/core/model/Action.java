package com.texasholdem.core.model;

import java.util.Objects;

public abstract class Action {
    private final String playerId;
    private final ActionType type;
    private final int amount;

    protected Action(String playerId, ActionType type, int amount) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.type = Objects.requireNonNull(type, "type");
        this.amount = amount;
    }

    public String getPlayerId() {
        return playerId;
    }

    public ActionType getType() {
        return type;
    }

    public int getAmount() {
        return amount;
    }
}
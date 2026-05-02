package com.texasholdem.core.model;

public final class GameConfig {
    private final int smallBlind;
    private final int bigBlind;
    private final int maxPlayers;
    private final int startingChips;

    public GameConfig(int smallBlind, int bigBlind, int maxPlayers, int startingChips) {
        if (smallBlind <= 0 || bigBlind <= 0 || maxPlayers < 2 || startingChips <= 0) {
            throw new IllegalArgumentException("Invalid game config");
        }
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.maxPlayers = maxPlayers;
        this.startingChips = startingChips;
    }

    public int getSmallBlind() {
        return smallBlind;
    }

    public int getBigBlind() {
        return bigBlind;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getStartingChips() {
        return startingChips;
    }
}
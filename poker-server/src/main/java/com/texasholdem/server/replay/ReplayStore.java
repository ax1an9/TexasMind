package com.texasholdem.server.replay;

public interface ReplayStore {
    void saveHand(String sessionId, HandRecord hand);
    void saveSession(GameSessionRecord session);
}

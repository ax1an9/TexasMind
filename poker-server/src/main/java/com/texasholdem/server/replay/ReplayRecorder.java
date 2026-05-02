package com.texasholdem.server.replay;

import com.texasholdem.core.model.*;
import com.texasholdem.server.session.PlayerConnection;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ReplayRecorder {
    private final Map<String, GameSessionRecord> sessions = new ConcurrentHashMap<>();
    private final ReplayStore replayStore;

    public ReplayRecorder(ReplayStore replayStore) {
        this.replayStore = replayStore;
    }

    public void onHandStarted(String sessionId, String roomId, int handNumber,
                               GameState state, Map<String, PlayerConnection> connections) {
        sessions.computeIfAbsent(sessionId, id -> {
            GameSessionRecord record = new GameSessionRecord();
            record.setSessionId(id);
            record.setRoomId(roomId);
            record.setStartTime(java.time.Instant.now().toString());
            List<PlayerRecord> players = new ArrayList<>();
            for (PlayerConnection conn : connections.values()) {
                PlayerRecord pr = new PlayerRecord();
                pr.setSeatId(conn.getSeatId());
                pr.setPlayerName(conn.getUserId());
                pr.setAiAgent(conn.isAiAgent());
                players.add(pr);
            }
            record.setPlayers(players);
            return record;
        });

        GameSessionRecord session = sessions.get(sessionId);
        HandRecord hand = new HandRecord();
        hand.setHandNumber(handNumber);
        hand.setStartTime(java.time.Instant.now().toString());

        // Record hole cards
        Map<String, List<String>> holeCards = new HashMap<>();
        for (PlayerState p : state.getPlayers()) {
            holeCards.put(p.getSeatId(), p.getHoleCards().stream()
                    .map(c -> c.getRank().getValue() + c.getSuit().name().substring(0, 1))
                    .collect(java.util.stream.Collectors.toList()));
        }
        hand.setHoleCards(holeCards);

        hand.setDealerSeatId(state.getPlayers().get(state.getDealerPosition()).getSeatId());
        hand.setActions(new ArrayList<>());
        session.getHands().add(hand);
    }

    public void onActionTaken(String sessionId, int handNumber, Action action, GamePhase phase) {
        GameSessionRecord session = sessions.get(sessionId);
        if (session == null) return;

        HandRecord hand = session.getHands().stream()
                .filter(h -> h.getHandNumber() == handNumber)
                .findFirst().orElse(null);
        if (hand == null) return;

        ActionRecord ar = new ActionRecord();
        ar.setSeatId(action.getPlayerId());
        ar.setActionType(action.getType().name());
        ar.setAmount(action.getAmount());
        ar.setPhase(phase.name());
        ar.setTimestampMs(System.currentTimeMillis());
        hand.getActions().add(ar);
    }

    public void onHandCompleted(String sessionId, int handNumber, GameState state,
                                 Map<String, Integer> finalChips) {
        GameSessionRecord session = sessions.get(sessionId);
        if (session == null) return;

        HandRecord hand = session.getHands().stream()
                .filter(h -> h.getHandNumber() == handNumber)
                .findFirst().orElse(null);
        if (hand == null) return;

        hand.setEndTime(java.time.Instant.now().toString());
        hand.setTotalPot(state.getPot().getTotalPot());

        // Record board
        hand.setBoard(state.getBoard().stream()
                .map(c -> c.getRank().getValue() + c.getSuit().name().substring(0, 1))
                .collect(java.util.stream.Collectors.toList()));

        // Record final chips
        session.setFinalChips(finalChips);
        session.setEndTime(java.time.Instant.now().toString());

        replayStore.saveHand(sessionId, hand);
    }
}

package com.texasholdem.server.room;

import com.texasholdem.ai.BuiltinAgent;
import com.texasholdem.ai.HintAdvisor;
import com.texasholdem.ai.HintResult;
import com.texasholdem.common.protocol.*;
import com.texasholdem.core.engine.GameEngine;
import com.texasholdem.core.model.*;
import com.texasholdem.server.replay.ReplayRecorder;
import com.texasholdem.server.service.BroadcastService;
import com.texasholdem.server.stats.PlayerStatsService;
import com.texasholdem.server.service.GameStateProjection;
import com.texasholdem.server.session.PlayerConnection;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameRoom {
    private static final Logger log = LoggerFactory.getLogger(GameRoom.class);
    private final String roomId;
    private final String name;
    private final GameConfig gameConfig;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, PlayerConnection> connections = new LinkedHashMap<>();
    private final Map<String, Integer> playerChips = new LinkedHashMap<>();
    private final Set<String> readyPlayers = new LinkedHashSet<>();
    private final GameEngine engine = new GameEngine();
    private final HintAdvisor hintAdvisor = new HintAdvisor();
    private final BuiltinAgent simpleAgent;
    private final BuiltinAgent grpcAgent;
    private final BroadcastService broadcastService;
    private final ReplayRecorder replayRecorder;
    private final PlayerStatsService statsService;
    private final ExecutorService aiExecutor;

    private GameState currentGameState;
    private RoomStatus status = RoomStatus.WAITING;
    private int dealerPosition = 0;
    private int handNumber = 0;
    private long sessionStartTime;
    private String sessionId;
    private String hostId;
    private Map<String, Integer> startingChipsThisHand = new HashMap<>();
    private Map<String, Integer> sessionStartingChips = new HashMap<>();
    private int lastPotAmount = 0;

    public GameRoom(String roomId, String name, GameConfig gameConfig,
                    BroadcastService broadcastService, ReplayRecorder replayRecorder,
                    PlayerStatsService statsService,
                    BuiltinAgent simpleAgent, BuiltinAgent grpcAgent) {
        this.roomId = roomId;
        this.name = name;
        this.gameConfig = gameConfig;
        this.broadcastService = broadcastService;
        this.replayRecorder = replayRecorder;
        this.statsService = statsService;
        this.simpleAgent = simpleAgent;
        this.grpcAgent = grpcAgent;
        this.aiExecutor = Executors.newCachedThreadPool();
    }

    public boolean join(PlayerConnection connection) {
        lock.lock();
        try {
            if (status != RoomStatus.WAITING && status != RoomStatus.GAME_OVER) return false;
            if (connections.size() >= gameConfig.getMaxPlayers()) return false;
            if (connections.containsKey(connection.getUserId())) return false;

            connections.put(connection.getUserId(), connection);
            playerChips.put(connection.getUserId(), gameConfig.getStartingChips());

            // First human player becomes host and is auto-ready
            if (hostId == null && !connection.isAiAgent()) {
                hostId = connection.getUserId();
                readyPlayers.add(connection.getUserId());
            }

            // Bots are auto-ready
            if (connection.isAiAgent()) {
                readyPlayers.add(connection.getUserId());
            }

            broadcastRoomState();
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean removePlayer(String userId) {
        lock.lock();
        try {
            if (!connections.containsKey(userId)) return false;
            connections.remove(userId);
            playerChips.remove(userId);
            readyPlayers.remove(userId);

            // Transfer host if needed
            if (userId.equals(hostId)) {
                hostId = connections.entrySet().stream()
                        .filter(e -> !e.getValue().isAiAgent())
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);
            }

            // Close room only when no human players remain
            long humanCount = connections.values().stream().filter(c -> !c.isAiAgent()).count();
            if (humanCount == 0) {
                connections.clear();
                status = RoomStatus.CLOSED;
            }

            broadcastRoomState();
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void setReady(String userId, boolean ready) {
        lock.lock();
        try {
            if (status != RoomStatus.WAITING && status != RoomStatus.GAME_OVER) return;
            if (!connections.containsKey(userId)) return;
            if (connections.get(userId).isAiAgent()) return;

            if (ready) {
                readyPlayers.add(userId);
            } else {
                readyPlayers.remove(userId);
            }
            broadcastRoomState();
        } finally {
            lock.unlock();
        }
    }

    public boolean addBot(String requestedBy, String agentType) {
        lock.lock();
        try {
            if (!requestedBy.equals(hostId)) return false;
            if (status != RoomStatus.WAITING && status != RoomStatus.GAME_OVER) return false;

            if (agentType == null) agentType = "simple";

            if ("react".equals(agentType) && grpcAgent == null) {
                broadcastService.sendToUser(requestedBy, "/queue/error",
                        new ErrorMessage("ReAct Agent is not available. Please check the Python agent server.", "AGENT_UNAVAILABLE"));
                return false;
            }

            long botCount = connections.values().stream().filter(PlayerConnection::isAiAgent).count();
            if (connections.size() >= gameConfig.getMaxPlayers()) return false;

            int botNum = (int) botCount + 1;
            String botId = "Bot_" + botNum;
            while (connections.containsKey(botId)) {
                botNum++;
                botId = "Bot_" + botNum;
            }

            PlayerConnection bot = new PlayerConnection(botId, botId, true, agentType);
            connections.put(botId, bot);
            playerChips.put(botId, gameConfig.getStartingChips());
            readyPlayers.add(botId);

            broadcastRoomState();
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean removeBot(String requestedBy, String botId) {
        lock.lock();
        try {
            if (!requestedBy.equals(hostId)) return false;
            if (status != RoomStatus.WAITING && status != RoomStatus.GAME_OVER) return false;

            PlayerConnection bot = connections.get(botId);
            if (bot == null || !bot.isAiAgent()) return false;

            connections.remove(botId);
            playerChips.remove(botId);
            readyPlayers.remove(botId);

            broadcastRoomState();
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void startNewHand(String requestedBy) {
        lock.lock();
        try {
            if (!requestedBy.equals(hostId)) return;
            if (status == RoomStatus.CLOSED) return;
            if (connections.size() < 2) return;

            // Check all non-bot players are ready
            for (Map.Entry<String, PlayerConnection> entry : connections.entrySet()) {
                if (!entry.getValue().isAiAgent() && !readyPlayers.contains(entry.getKey())) {
                    return; // Not everyone is ready
                }
            }

            // Reset game over state
            if (status == RoomStatus.GAME_OVER) {
                status = RoomStatus.WAITING;
                handNumber = 0;
                dealerPosition = 0;
                sessionStartingChips.clear();
                // Reset chips for everyone
                for (String pid : playerChips.keySet()) {
                    playerChips.put(pid, gameConfig.getStartingChips());
                }
            }

            handNumber++;
            if (handNumber == 1) {
                sessionId = roomId + "_" + System.currentTimeMillis();
                sessionStartTime = System.currentTimeMillis();
                sessionStartingChips.clear();
                for (Map.Entry<String, Integer> entry : playerChips.entrySet()) {
                    sessionStartingChips.put(entry.getKey(), entry.getValue());
                }
            }

            List<PlayerState> seats = new ArrayList<>();
            List<String> seatOrder = new ArrayList<>(connections.keySet());
            startingChipsThisHand.clear();
            for (String userId : seatOrder) {
                int chips = playerChips.getOrDefault(userId, gameConfig.getStartingChips());
                startingChipsThisHand.put(userId, chips);
                seats.add(new PlayerState(userId, chips));
            }

            lastPotAmount = 0;
            long seed = System.nanoTime();
            currentGameState = engine.startNewHand(gameConfig, seats, dealerPosition, seed, handNumber);
            status = RoomStatus.PLAYING;

            if (replayRecorder != null) {
                replayRecorder.onHandStarted(sessionId, roomId, handNumber, currentGameState, connections);
            }

            broadcastGameState();
            handleCurrentPlayer();
        } finally {
            lock.unlock();
        }
    }

    public void applyAction(String userId, Action action) {
        lock.lock();
        try {
            if (currentGameState == null || status != RoomStatus.PLAYING) return;

            PlayerState current = currentGameState.getCurrentPlayer();
            if (current == null || !current.getSeatId().equals(userId)) return;

            try {
                currentGameState = engine.applyAction(currentGameState, action);
                if (currentGameState.getPhase() != GamePhase.SETTLED) {
                    lastPotAmount = currentGameState.getPot().getTotalPot();
                }
                if (currentGameState.getPhase() == GamePhase.SETTLED) {
                    int total = 0;
                    for (PlayerState p : currentGameState.getPlayers()) {
                        total += startingChipsThisHand.getOrDefault(p.getSeatId(), 0) - p.getChips();
                    }
                    lastPotAmount = total;
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                broadcastService.sendToUser(userId, "/queue/error",
                        new ErrorMessage(e.getMessage(), "INVALID_ACTION"));
                return;
            }

            if (replayRecorder != null) {
                replayRecorder.onActionTaken(sessionId, handNumber, action, currentGameState.getPhase());
            }

            if (currentGameState.getPhase() == GamePhase.SETTLED) {
                handleSettled();
            } else {
                broadcastGameState();
                handleCurrentPlayer();
            }
        } finally {
            lock.unlock();
        }
    }

    public HintResult getHint(String userId) {
        lock.lock();
        try {
            if (currentGameState == null) return null;
            PlayerState self = currentGameState.getPlayers().stream()
                    .filter(p -> p.getSeatId().equals(userId))
                    .findFirst().orElse(null);
            if (self == null) return null;
            return hintAdvisor.analyze(currentGameState, self);
        } finally {
            lock.unlock();
        }
    }

    private void handleSettled() {
        status = RoomStatus.WAITING;
        // All players stay ready between hands — host can start next round immediately

        Map<String, Integer> finalChips = new HashMap<>();
        for (PlayerState p : currentGameState.getPlayers()) {
            playerChips.put(p.getSeatId(), p.getChips());
            finalChips.put(p.getSeatId(), p.getChips());
        }

        if (replayRecorder != null) {
            replayRecorder.onHandCompleted(sessionId, handNumber, currentGameState, finalChips);
        }

        if (statsService != null) {
            try {
                statsService.processHand(sessionId, handNumber, currentGameState, connections);
            } catch (Exception e) {
                log.warn("Failed to process player stats: {}", e.getMessage());
            }
        }

        broadcastGameState();
        broadcastGameResult();

        boolean anyBusted = false;
        for (PlayerState p : currentGameState.getPlayers()) {
            if (p.getChips() <= 0 && connections.containsKey(p.getSeatId())) {
                log.info("Player {} busted with {} chips", p.getSeatId(), p.getChips());
                anyBusted = true;
            }
        }

        if (anyBusted) {
            status = RoomStatus.GAME_OVER;
            broadcastSessionSummary();
        } else {
            dealerPosition = (dealerPosition + 1) % connections.size();
        }

        broadcastRoomState();
    }

    private void broadcastRoomState() {
        RoomStateMessage msg = buildRoomStateMessage();
        broadcastService.sendToRoom(roomId, msg);
    }

    public void sendRoomStateToUser(String userId) {
        RoomStateMessage msg = buildRoomStateMessage();
        broadcastService.sendToUser(userId, "/queue/room-state", msg);
    }

    public void fillRoomCreatedMessage(RoomCreatedMessage msg) {
        RoomStateMessage state = buildRoomStateMessage();
        msg.setHostId(state.getHostId());
        msg.setPlayers(state.getPlayers());
        msg.setCanStart(state.isCanStart());
        msg.setMaxPlayers(state.getMaxPlayers());
    }

    private RoomStateMessage buildRoomStateMessage() {
        List<RoomStateMessage.PlayerSlot> slots = new ArrayList<>();
        for (Map.Entry<String, PlayerConnection> entry : connections.entrySet()) {
            String pid = entry.getKey();
            PlayerConnection conn = entry.getValue();
            boolean isReady = readyPlayers.contains(pid);
            boolean isHost = pid.equals(hostId);
            slots.add(new RoomStateMessage.PlayerSlot(pid, isReady, conn.isAiAgent(), isHost, conn.getAgentType()));
        }

        boolean allReady = true;
        for (Map.Entry<String, PlayerConnection> entry : connections.entrySet()) {
            if (!entry.getValue().isAiAgent() && !readyPlayers.contains(entry.getKey())) {
                allReady = false;
                break;
            }
        }
        long humanCount = connections.values().stream().filter(c -> !c.isAiAgent()).count();
        boolean canStart = allReady && humanCount >= 1 && connections.size() >= 2;

        return new RoomStateMessage(roomId, name, hostId, slots, canStart,
                gameConfig.getMaxPlayers());
    }

    private void broadcastSessionSummary() {
        log.info("Broadcasting session summary for room {}, {} hands played", roomId, handNumber);
        List<SessionSummaryMessage.PlayerSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : playerChips.entrySet()) {
            String seatId = entry.getKey();
            int chips = entry.getValue();
            int starting = sessionStartingChips.getOrDefault(seatId, gameConfig.getStartingChips());
            summaries.add(new SessionSummaryMessage.PlayerSummary(
                    seatId, starting, chips, chips - starting, chips <= 0));
        }

        long duration = System.currentTimeMillis() - sessionStartTime;
        SessionSummaryMessage msg = new SessionSummaryMessage(roomId, handNumber, duration, summaries);
        broadcastService.sendToRoom(roomId, msg);
    }

    private void broadcastGameState() {
        Map<String, String> agentTypeMap = new HashMap<>();
        for (Map.Entry<String, PlayerConnection> entry : connections.entrySet()) {
            if (entry.getValue().getAgentType() != null) {
                agentTypeMap.put(entry.getKey(), entry.getValue().getAgentType());
            }
        }
        for (Map.Entry<String, PlayerConnection> entry : connections.entrySet()) {
            String userId = entry.getKey();
            GameStateMessage msg = GameStateProjection.toPlayerView(roomId, currentGameState, userId, agentTypeMap);
            broadcastService.sendToUser(userId, "/queue/game-state", msg);
        }
    }

    private void broadcastGameResult() {
        Map<String, Integer> chipChanges = getChipChanges();
        String winnerId = findWinner(chipChanges);
        GameResultMessage result = new GameResultMessage();
        result.setGameId(roomId + "_" + handNumber);
        result.setWinnerSeatId(winnerId);
        result.setPotAmount(lastPotAmount);
        result.setChipChanges(chipChanges);
        broadcastService.sendToRoom(roomId, result);
    }

    private String findWinner(Map<String, Integer> chipChanges) {
        if (chipChanges == null || chipChanges.isEmpty()) return null;
        String bestId = null;
        int bestGain = Integer.MIN_VALUE;
        for (Map.Entry<String, Integer> entry : chipChanges.entrySet()) {
            if (entry.getValue() > bestGain) {
                bestGain = entry.getValue();
                bestId = entry.getKey();
            }
        }
        return bestId;
    }

    private Map<String, Integer> getChipChanges() {
        Map<String, Integer> changes = new HashMap<>();
        for (PlayerState p : currentGameState.getPlayers()) {
            int initial = startingChipsThisHand.getOrDefault(p.getSeatId(), gameConfig.getStartingChips());
            changes.put(p.getSeatId(), p.getChips() - initial);
        }
        return changes;
    }

    private void handleCurrentPlayer() {
        PlayerState current = currentGameState.getCurrentPlayer();
        if (current == null) return;

        PlayerConnection conn = connections.get(current.getSeatId());
        if (conn != null && conn.isAiAgent()) {
            BuiltinAgent agent = selectAgent(conn);
            aiExecutor.submit(() -> {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                Action aiAction = agent.decide(currentGameState, current);
                applyAction(current.getSeatId(), aiAction);
            });
        } else if (conn != null) {
            ActionRequiredMessage msg = new ActionRequiredMessage();
            msg.setGameId(roomId + "_" + handNumber);
            msg.setSeatId(current.getSeatId());
            msg.setTimeLimitMs(30000);
            broadcastService.sendToUser(current.getSeatId(), "/queue/action-required", msg);
        }
    }

    private BuiltinAgent selectAgent(PlayerConnection conn) {
        if ("react".equals(conn.getAgentType()) && grpcAgent != null) {
            return grpcAgent;
        }
        return simpleAgent;
    }

    public String getRoomId() { return roomId; }
    public String getName() { return name; }
    public GameConfig getGameConfig() { return gameConfig; }
    public RoomStatus getStatus() { return status; }
    public String getHostId() { lock.lock(); try { return hostId; } finally { lock.unlock(); } }
    public int getPlayerCount() { lock.lock(); try { return connections.size(); } finally { lock.unlock(); } }
    public Set<String> getPlayerIds() { lock.lock(); try { return new LinkedHashSet<>(connections.keySet()); } finally { lock.unlock(); } }
}

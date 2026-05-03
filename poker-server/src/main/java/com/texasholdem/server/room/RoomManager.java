package com.texasholdem.server.room;

import com.texasholdem.ai.BuiltinAgent;
import com.texasholdem.common.protocol.RoomInfo;
import com.texasholdem.core.model.GameConfig;
import com.texasholdem.server.replay.ReplayRecorder;
import com.texasholdem.server.service.BroadcastService;
import com.texasholdem.server.stats.PlayerStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RoomManager {
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final AtomicInteger roomCounter = new AtomicInteger(0);
    private final BroadcastService broadcastService;
    private final ReplayRecorder replayRecorder;
    private final PlayerStatsService statsService;
    private final BuiltinAgent simpleAgent;
    private final BuiltinAgent grpcAgent;

    public RoomManager(BroadcastService broadcastService, ReplayRecorder replayRecorder,
                       PlayerStatsService statsService,
                       BuiltinAgent simpleAgent,
                       @Autowired(required = false) @Qualifier("grpcAgent") BuiltinAgent grpcAgent) {
        this.broadcastService = broadcastService;
        this.replayRecorder = replayRecorder;
        this.statsService = statsService;
        this.simpleAgent = simpleAgent;
        this.grpcAgent = grpcAgent;
    }

    public GameRoom createRoom(String name, int maxPlayers, int smallBlind, int bigBlind, int startingChips) {
        String roomId = "room_" + roomCounter.incrementAndGet();
        GameConfig config = new GameConfig(smallBlind, bigBlind, maxPlayers, startingChips);
        GameRoom room = new GameRoom(roomId, name, config, broadcastService, replayRecorder,
                statsService, simpleAgent, grpcAgent);
        rooms.put(roomId, room);
        return room;
    }

    public GameRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public List<RoomInfo> listRooms() {
        List<RoomInfo> list = new ArrayList<>();
        for (GameRoom room : rooms.values()) {
            list.add(new RoomInfo(room.getRoomId(), room.getName(),
                    room.getPlayerCount(), room.getGameConfig().getMaxPlayers(),
                    room.getStatus().name()));
        }
        return list;
    }

    public void removeRoom(String roomId) {
        rooms.remove(roomId);
    }
}

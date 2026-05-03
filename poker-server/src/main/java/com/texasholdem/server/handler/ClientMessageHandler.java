package com.texasholdem.server.handler;

import com.texasholdem.ai.HintResult;
import com.texasholdem.common.protocol.*;
import com.texasholdem.core.model.*;
import com.texasholdem.server.room.GameRoom;
import com.texasholdem.server.room.RoomManager;
import com.texasholdem.server.service.BroadcastService;
import com.texasholdem.server.session.PlayerConnection;
import com.texasholdem.server.stats.PlayerProfile;
import com.texasholdem.server.stats.PlayerStatsService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Controller
public class ClientMessageHandler {
    private final RoomManager roomManager;
    private final BroadcastService broadcastService;
    private final PlayerStatsService statsService;

    public ClientMessageHandler(RoomManager roomManager, BroadcastService broadcastService,
                                PlayerStatsService statsService) {
        this.roomManager = roomManager;
        this.broadcastService = broadcastService;
        this.statsService = statsService;
    }

    @MessageMapping("/lobby/rooms")
    public void requestRoomList(Principal principal) {
        broadcastService.sendToUser(principal.getName(), "/queue/room-list",
                new RoomListMessage(roomManager.listRooms()));
    }

    @MessageMapping("/room/create")
    public void createRoom(CreateRoomRequest request, Principal principal) {
        String userId = principal.getName();
        GameRoom room = roomManager.createRoom(
                request.getRoomName(),
                request.getMaxPlayers(),
                request.getSmallBlind(),
                request.getBigBlind(),
                request.getStartingChips()
        );

        room.join(new PlayerConnection(userId, userId, false));

        broadcastService.sendToLobby(new RoomListMessage(roomManager.listRooms()));
        RoomCreatedMessage createdMsg = new RoomCreatedMessage(room.getRoomId(), room.getName());
        room.fillRoomCreatedMessage(createdMsg);
        broadcastService.sendToUser(userId, "/queue/room-created", createdMsg);
    }

    @MessageMapping("/room/state")
    public void requestRoomState(JoinRoomRequest request, Principal principal) {
        String userId = principal.getName();
        GameRoom room = roomManager.getRoom(request.getRoomId());
        if (room == null) return;
        room.sendRoomStateToUser(userId);
    }

    @MessageMapping("/room/join")
    public void joinRoom(JoinRoomRequest request, Principal principal) {
        String userId = principal.getName();
        GameRoom room = roomManager.getRoom(request.getRoomId());
        if (room == null) {
            broadcastService.sendToUser(userId, "/queue/error",
                    new ErrorMessage("Room not found", "ROOM_NOT_FOUND"));
            return;
        }

        boolean joined = room.join(new PlayerConnection(userId, userId, false));
        if (!joined) {
            broadcastService.sendToUser(userId, "/queue/error",
                    new ErrorMessage("Cannot join room", "JOIN_FAILED"));
            return;
        }

        broadcastService.sendToLobby(new RoomListMessage(roomManager.listRooms()));
        room.sendRoomStateToUser(userId);
    }

    @MessageMapping("/room/leave")
    public void leaveRoom(LeaveRoomRequest request, Principal principal) {
        String userId = principal.getName();
        GameRoom room = roomManager.getRoom(request.getRoomId());
        if (room == null) return;

        room.removePlayer(userId);
        broadcastService.sendToLobby(new RoomListMessage(roomManager.listRooms()));
    }

    @MessageMapping("/room/ready")
    public void setReady(ReadyRequest request, Principal principal) {
        String userId = principal.getName();
        GameRoom room = roomManager.getRoom(request.getRoomId());
        if (room == null) return;
        room.setReady(userId, true);
    }

    @MessageMapping("/room/unready")
    public void setUnready(ReadyRequest request, Principal principal) {
        String userId = principal.getName();
        GameRoom room = roomManager.getRoom(request.getRoomId());
        if (room == null) return;
        room.setReady(userId, false);
    }

    @MessageMapping("/room/add-bot")
    public void addBot(AddBotRequest request, Principal principal) {
        String userId = principal.getName();
        GameRoom room = roomManager.getRoom(request.getRoomId());
        if (room == null) return;
        room.addBot(userId, request.getAgentType());
    }

    @MessageMapping("/room/remove-bot")
    public void removeBot(RemoveBotRequest request, Principal principal) {
        String userId = principal.getName();
        GameRoom room = roomManager.getRoom(request.getRoomId());
        if (room == null) return;
        room.removeBot(userId, request.getBotId());
    }

    @MessageMapping("/room/start")
    public void startGame(JoinRoomRequest request, Principal principal) {
        String userId = principal.getName();
        GameRoom room = roomManager.getRoom(request.getRoomId());
        if (room == null) return;
        room.startNewHand(userId);
    }

    @MessageMapping("/game/action")
    public void playerAction(PlayerActionRequest request, Principal principal) {
        String userId = principal.getName();
        GameRoom room = roomManager.getRoom(request.getRoomId());
        if (room == null) return;

        Action action = createAction(userId, request.getAction(), request.getAmount());
        if (action == null) {
            broadcastService.sendToUser(userId, "/queue/error",
                    new ErrorMessage("Invalid action: " + request.getAction(), "INVALID_ACTION"));
            return;
        }

        room.applyAction(userId, action);
    }

    @MessageMapping("/game/hint")
    public void requestHint(HintRequest request, Principal principal) {
        String userId = principal.getName();
        GameRoom room = roomManager.getRoom(request.getRoomId());
        if (room == null) return;

        HintResult result = room.getHint(userId);
        if (result != null) {
            HintResultMessage msg = new HintResultMessage(
                    request.getRoomId(),
                    result.getSuggestedAction(),
                    result.getHandStrength(),
                    result.getPotOdds(),
                    result.getReasoning()
            );
            broadcastService.sendToUser(userId, "/queue/hint-result", msg);
        }
    }

    @MessageMapping("/player/stats")
    public void requestPlayerStats(PlayerStatsRequest request, Principal principal) {
        String userId = principal.getName();
        String targetId = request.getPlayerId() != null ? request.getPlayerId() : userId;
        PlayerProfile profile = statsService.getProfile(targetId);
        if (profile == null) {
            broadcastService.sendToUser(userId, "/queue/error",
                    new ErrorMessage("Player not found", "PLAYER_NOT_FOUND"));
            return;
        }

        PlayerStatsMessage msg = new PlayerStatsMessage();
        msg.setPlayerId(profile.getId());
        msg.setDisplayName(profile.getDisplayName());
        msg.setAllTimeHands(profile.getAllTime().getHandsPlayed());
        msg.setRecentHands(profile.getRecent().getHandsPlayed());
        msg.setSessionHands(profile.getCurrentSession().getHandsPlayed());

        Map<String, Double> publicStats = new HashMap<>();
        publicStats.put("vpip", profile.getAllTime().getVpip().getValue());
        publicStats.put("pfr", profile.getAllTime().getPfr().getValue());
        publicStats.put("handsPlayed", (double) profile.getAllTime().getHandsPlayed());
        msg.setPublicStats(publicStats);

        Map<String, Double> privateStats = new HashMap<>();
        privateStats.put("threeBet", profile.getAllTime().getThreeBet().getValue());
        privateStats.put("af", profile.getAllTime().getAf().getValue());
        privateStats.put("wtsd", profile.getAllTime().getWtsd().getValue());
        privateStats.put("wsd", profile.getAllTime().getWsd().getValue());
        privateStats.put("foldToCbet", profile.getAllTime().getFoldToCbet().getValue());
        msg.setPrivateStats(privateStats);

        broadcastService.sendToUser(userId, "/queue/player-stats", msg);
    }

    @MessageMapping("/player/style")
    public void requestPlayerStyle(PlayerStatsRequest request, Principal principal) {
        String userId = principal.getName();
        String targetId = request.getPlayerId() != null ? request.getPlayerId() : userId;
        PlayerProfile profile = statsService.getProfile(targetId);
        if (profile == null) return;

        double vpip = profile.getAllTime().getVpip().getValue();
        double pfr = profile.getAllTime().getPfr().getValue();

        PlayerStyleMessage msg = new PlayerStyleMessage();
        msg.setPlayerId(targetId);

        String tightness = vpip < 0.14 ? "tight" : vpip < 0.23 ? "medium" : "loose";
        double pfrVpipRatio = vpip > 0 ? pfr / vpip : 0;
        String aggressiveness = pfrVpipRatio > 0.75 ? "aggressive" : "passive";

        msg.setTightness(tightness);
        msg.setAggressiveness(aggressiveness);
        msg.setPrimaryStyle(classifyStyle(tightness, aggressiveness));
        msg.setConfidence(Math.min(1.0, profile.getAllTime().getHandsPlayed() / 100.0));

        broadcastService.sendToUser(userId, "/queue/player-style", msg);
    }

    private String classifyStyle(String tightness, String aggressiveness) {
        if ("tight".equals(tightness) && "aggressive".equals(aggressiveness)) return "TAG";
        if ("tight".equals(tightness) && "passive".equals(aggressiveness)) return "Rock";
        if ("loose".equals(tightness) && "aggressive".equals(aggressiveness)) return "LAG";
        if ("loose".equals(tightness) && "passive".equals(aggressiveness)) return "Calling Station";
        return "Unknown";
    }

    private Action createAction(String playerId, String actionType, int amount) {
        switch (actionType.toUpperCase()) {
            case "FOLD": return new FoldAction(playerId);
            case "CHECK": return new CheckAction(playerId);
            case "CALL": return new CallAction(playerId);
            case "BET": return new BetAction(playerId, amount);
            case "RAISE": return new RaiseAction(playerId, amount);
            case "ALL_IN": return new AllInAction(playerId);
            default: return null;
        }
    }
}

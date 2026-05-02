package com.texasholdem.server.service;

import com.texasholdem.common.protocol.ServerMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class BroadcastService {
    private final SimpMessagingTemplate messagingTemplate;

    public BroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendToRoom(String roomId, ServerMessage message) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
    }

    public void sendToUser(String userId, String destination, ServerMessage message) {
        messagingTemplate.convertAndSendToUser(userId, destination, message);
    }

    public void sendToLobby(ServerMessage message) {
        messagingTemplate.convertAndSend("/topic/lobby", message);
    }
}

package com.texasholdem.server.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class FileReplayStore implements ReplayStore {
    private final ObjectMapper mapper;
    private final String basePath;

    public FileReplayStore() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.basePath = "data/replays";
    }

    @Override
    public void saveHand(String sessionId, HandRecord hand) {
        try {
            Path dir = Paths.get(basePath, sessionId);
            Files.createDirectories(dir);
            File file = dir.resolve(String.format("hand-%03d.json", hand.getHandNumber())).toFile();
            File tmp = new File(file.getAbsolutePath() + ".tmp");
            mapper.writeValue(tmp, hand);
            tmp.renameTo(file);
        } catch (IOException e) {
            System.err.println("Failed to save replay: " + e.getMessage());
        }
    }

    @Override
    public void saveSession(GameSessionRecord session) {
        try {
            Path dir = Paths.get(basePath, session.getSessionId());
            Files.createDirectories(dir);
            File file = dir.resolve("session.json").toFile();
            mapper.writeValue(file, session);
        } catch (IOException e) {
            System.err.println("Failed to save session replay: " + e.getMessage());
        }
    }
}

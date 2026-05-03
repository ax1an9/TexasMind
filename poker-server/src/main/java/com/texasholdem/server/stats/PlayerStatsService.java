package com.texasholdem.server.stats;

import com.texasholdem.core.model.*;
import com.texasholdem.server.session.PlayerConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
public class PlayerStatsService {
    private final PlayerProfileRepository repository;

    public PlayerStatsService(PlayerProfileRepository repository) {
        this.repository = repository;
    }

    public void processHand(String sessionId, int handNumber, GameState state,
                            Map<String, PlayerConnection> connections) {
        for (PlayerState player : state.getPlayers()) {
            String playerId = player.getSeatId();
            PlayerConnection conn = connections.get(playerId);
            if (conn != null && conn.isAiAgent()) continue;

            PlayerProfile profile = repository.findById(playerId).orElseGet(() -> {
                PlayerProfile p = new PlayerProfile();
                p.setId(playerId);
                p.setDisplayName(playerId);
                p.setCreatedAt(Instant.now().toString());
                return p;
            });

            StatsCalculator.HandStatsUpdate update = StatsCalculator.analyzeHand(state, playerId);
            String handId = sessionId + "_" + handNumber;

            applyUpdate(profile.getAllTime(), update, handId);
            applyUpdate(profile.getRecent(), update, handId);
            applyUpdate(profile.getCurrentSession(), update, handId);

            profile.setUpdatedAt(Instant.now().toString());
            repository.save(profile);
        }
    }

    private void applyUpdate(PlayerProfile.StatsWindow window,
                             StatsCalculator.HandStatsUpdate update, String handId) {
        window.setHandsPlayed(window.getHandsPlayed() + 1);

        if (update.isVpip()) window.getVpip().increment();
        else window.getVpip().addOpportunity();

        if (update.isPfr()) window.getPfr().increment();
        else window.getPfr().addOpportunity();

        if (update.isThreeBetOpportunity()) {
            if (update.isThreeBet()) window.getThreeBet().increment();
            else window.getThreeBet().addOpportunity();
        }

        if (update.getPostFlopActions() > 0) {
            window.setAggressiveActions(window.getAggressiveActions() + update.getAggressiveActions());
            StatEntry af = window.getAf();
            af.setCount(window.getAggressiveActions());
            af.setOpportunities(af.getOpportunities() + update.getPostFlopActions());
            af.setValue(af.getOpportunities() > 0
                    ? (double) af.getCount() / af.getOpportunities() : 0.0);
        }

        if (update.isReachedShowdown()) window.getWtsd().increment();
        else window.getWtsd().addOpportunity();

        if (update.isReachedShowdown()) {
            if (update.isWonAtShowdown()) window.getWsd().increment();
            else window.getWsd().addOpportunity();
        }

        if (update.isFacesCbet()) {
            if (update.isFoldedToCbet()) window.getFoldToCbet().increment();
            else window.getFoldToCbet().addOpportunity();
        }

        window.getHandIds().add(handId);
        if (window.getWindowSize() > 0 && window.getHandIds().size() > window.getWindowSize()) {
            window.getHandIds().remove(0);
        }
    }

    public PlayerProfile getProfile(String playerId) {
        return repository.findById(playerId).orElse(null);
    }
}

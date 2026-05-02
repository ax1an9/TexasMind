package com.texasholdem.app;

import com.texasholdem.ai.SimpleHoldemAgent;
import com.texasholdem.core.engine.GameEngine;
import com.texasholdem.core.model.Action;
import com.texasholdem.core.model.GameConfig;
import com.texasholdem.core.model.GamePhase;
import com.texasholdem.core.model.GameState;
import com.texasholdem.core.model.PlayerState;

import java.util.ArrayList;
import java.util.List;

public final class Phase1DemoApplication {
    public static void main(String[] args) {
        GameConfig config = new GameConfig(1, 2, 6, 200);
        List<PlayerState> seats = new ArrayList<PlayerState>();
        seats.add(new PlayerState("hero", 200));
        seats.add(new PlayerState("bot", 200));

        GameEngine engine = new GameEngine();
        GameState state = engine.startNewHand(config, seats, 0, 42L, 1);
        SimpleHoldemAgent agent = new SimpleHoldemAgent();

        System.out.println("Phase 1 demo hand " + state.getHandNumber());
        while (state.getPhase() != GamePhase.SETTLED) {
            PlayerState current = state.getCurrentPlayer();
            if (current == null) {
                break;
            }
            Action action = agent.decide(state, current);
            System.out.println(current.getSeatId() + " -> " + action.getType() + " " + action.getAmount());
            state = engine.applyAction(state, action);
        }

        for (PlayerState player : state.getPlayers()) {
            System.out.println(player.getSeatId() + " chips=" + player.getChips());
        }
    }
}
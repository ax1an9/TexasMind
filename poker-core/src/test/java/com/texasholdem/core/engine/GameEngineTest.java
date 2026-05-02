package com.texasholdem.core.engine;

import com.texasholdem.core.model.Action;
import com.texasholdem.core.model.CallAction;
import com.texasholdem.core.model.Card;
import com.texasholdem.core.model.CheckAction;
import com.texasholdem.core.model.FoldAction;
import com.texasholdem.core.model.GameConfig;
import com.texasholdem.core.model.GamePhase;
import com.texasholdem.core.model.GameState;
import com.texasholdem.core.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameEngineTest {
    @Test
    void settlesByFold() {
        GameEngine engine = new GameEngine();
        GameConfig config = new GameConfig(1, 2, 6, 200);
        List<PlayerState> seats = Arrays.asList(new PlayerState("p1", 200), new PlayerState("p2", 200));

        GameState state = engine.startNewHand(config, seats, 0, 7L, 1);
        Action action = new FoldAction(state.getCurrentPlayer().getSeatId());
        state = engine.applyAction(state, action);

        assertEquals(GamePhase.SETTLED, state.getPhase());
        assertEquals(400, state.getPlayers().get(0).getChips() + state.getPlayers().get(1).getChips());
    }

    @Test
    void reachesShowdownAndAwardsWinner() {
        GameEngine engine = new GameEngine();
        GameConfig config = new GameConfig(1, 2, 6, 200);
        List<PlayerState> seats = Arrays.asList(new PlayerState("p1", 200), new PlayerState("p2", 200));

        List<Card> deck = Arrays.asList(
                Card.of("Ah"), Card.of("Kc"),
                Card.of("As"), Card.of("Kd"),
                Card.of("Qh"), Card.of("Jh"), Card.of("Th"),
                Card.of("2c"), Card.of("3d"), Card.of("4s"), Card.of("5c"));

        GameState state = engine.startNewHand(config, seats, 0, 7L, 1);
        state = new GameState(state.getPhase(), state.getBoard(), state.getPlayers(), state.getPot(),
                state.getActionHistory(),
                state.getDealerPosition(), state.getCurrentPlayerPosition(), state.getCurrentBet(),
                state.getHandNumber(),
                state.getRoundNumber(), deck, 0);

        while (state.getPhase() != GamePhase.SETTLED) {
            PlayerState current = state.getCurrentPlayer();
            if (current == null) {
                break;
            }
            if (state.getCurrentBet() == current.getRoundContribution()) {
                state = engine.applyAction(state, new CheckAction(current.getSeatId()));
            } else {
                state = engine.applyAction(state, new CallAction(current.getSeatId()));
            }
        }

        assertEquals(GamePhase.SETTLED, state.getPhase());
    }
}
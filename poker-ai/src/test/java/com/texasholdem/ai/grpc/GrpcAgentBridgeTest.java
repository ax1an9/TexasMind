package com.texasholdem.ai.grpc;

import com.texasholdem.ai.BuiltinAgent;
import com.texasholdem.core.model.*;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import poker_agent.PokerAgentGrpc;
import poker_agent.PokerAgentOuterClass;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class GrpcAgentBridgeTest {

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() throws Exception {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void testDecideCallsGrpcAndReturnsAction() throws Exception {
        // Arrange: fake server that returns CHECK
        String uniqueName = InProcessServerBuilder.generateName();

        server = InProcessServerBuilder.forName(uniqueName)
                .addService(new PokerAgentGrpc.PokerAgentImplBase() {
                    @Override
                    public void makeDecision(
                            PokerAgentOuterClass.DecisionRequest request,
                            StreamObserver<PokerAgentOuterClass.ActionResponse> responseObserver) {
                        PokerAgentOuterClass.ActionResponse response =
                                PokerAgentOuterClass.ActionResponse.newBuilder()
                                        .setActionType(PokerAgentOuterClass.ActionType.CHECK)
                                        .setAmount(0)
                                        .build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();

        channel = InProcessChannelBuilder.forName(uniqueName)
                .directExecutor()
                .build();

        GrpcAgentConfig config = new GrpcAgentConfig();
        config.setTimeoutMs(4000);

        GrpcAgentBridge bridge = new GrpcAgentBridge(config, channel);

        // Act
        GameState state = createSimpleGameState(GamePhase.PRE_FLOP, 0);
        PlayerState self = state.getPlayers().get(0);
        Action result = bridge.decide(state, self);

        // Assert
        assertNotNull(result);
        assertEquals(ActionType.CHECK, result.getType());
        assertEquals("seat-0", result.getPlayerId());
    }

    @Test
    void testFallbackOnTimeout() throws Exception {
        // Arrange: use a server name that nobody listens on to simulate timeout
        String nonexistentServer = InProcessServerBuilder.generateName();

        // Create a channel to a nonexistent in-process server (will fail immediately)
        channel = InProcessChannelBuilder.forName(nonexistentServer)
                .directExecutor()
                .build();

        GrpcAgentConfig config = new GrpcAgentConfig();
        config.setTimeoutMs(1);

        // Set up a fallback agent that returns FOLD
        final AtomicBoolean fallbackCalled = new AtomicBoolean(false);
        BuiltinAgent fallbackAgent = new BuiltinAgent() {
            @Override
            public Action decide(GameState state, PlayerState self) {
                fallbackCalled.set(true);
                return new FoldAction(self.getSeatId());
            }
        };
        config.setFallbackAgent(fallbackAgent);

        GrpcAgentBridge bridge = new GrpcAgentBridge(config, channel);

        // Act
        GameState state = createSimpleGameState(GamePhase.PRE_FLOP, 0);
        PlayerState self = state.getPlayers().get(0);
        Action result = bridge.decide(state, self);

        // Assert: fallback was called and returned FOLD
        assertTrue(fallbackCalled.get(), "Fallback agent should have been called");
        assertNotNull(result);
        assertEquals(ActionType.FOLD, result.getType());
        assertEquals("seat-0", result.getPlayerId());
    }

    // --- Helper methods ---

    private GameState createSimpleGameState(GamePhase phase, int currentBet) {
        PlayerState self = new PlayerState("seat-0", 1000)
                .withHoleCards(Arrays.asList(
                        new Card(Rank.ACE, Suit.SPADES),
                        new Card(Rank.KING, Suit.SPADES)));
        PlayerState opponent = new PlayerState("seat-1", 800);

        List<PlayerState> players = Arrays.asList(self, opponent);
        return new GameState(
                phase,
                Collections.<Card>emptyList(),
                players,
                PotInfo.empty(),
                Collections.<Action>emptyList(),
                0, 0, currentBet, 1, 1,
                Collections.<Card>emptyList(), 0);
    }
}

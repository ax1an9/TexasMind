package com.texasholdem.ai.grpc;

import com.texasholdem.ai.BuiltinAgent;
import com.texasholdem.ai.SimpleHoldemAgent;
import com.texasholdem.core.model.*;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poker_agent.PokerAgentGrpc;
import poker_agent.PokerAgentOuterClass;

import java.util.concurrent.TimeUnit;

/**
 * A {@link BuiltinAgent} that delegates decisions to a remote Python gRPC agent.
 * Falls back to a local agent (or fold) on timeout or error.
 */
public class GrpcAgentBridge implements BuiltinAgent {

    private static final Logger log = LoggerFactory.getLogger(GrpcAgentBridge.class);
    private static final String DEFAULT_GAME_ID = "default";

    private final GrpcAgentConfig config;
    private final ManagedChannel channel;
    private final PokerAgentGrpc.PokerAgentBlockingStub baseStub;

    public GrpcAgentBridge(GrpcAgentConfig config, ManagedChannel channel) {
        this.config = config;
        this.channel = channel;
        this.baseStub = PokerAgentGrpc.newBlockingStub(channel);
    }

    @Override
    public Action decide(GameState state, PlayerState self) {
        try {
            PokerAgentOuterClass.DecisionRequest request =
                    ProtoAdapter.buildDecisionRequest(DEFAULT_GAME_ID, state, self);
            PokerAgentGrpc.PokerAgentBlockingStub callStub =
                    baseStub.withDeadlineAfter(config.getTimeoutMs(), TimeUnit.MILLISECONDS);
            PokerAgentOuterClass.ActionResponse response = callStub.makeDecision(request);

            log.info("[REACT] seat={} -> {} {} (path={}, confidence={}, latency={}ms)",
                    self.getSeatId(),
                    response.getActionType(),
                    response.getAmount(),
                    response.getExecutionPath(),
                    response.getConfidence(),
                    response.getDecisionLatencyMs());
            if (!response.getReasoning().isEmpty()) {
                log.info("[REACT] reasoning: {}", response.getReasoning());
            }
            if (log.isDebugEnabled() && !response.getRawOutput().isEmpty()) {
                log.debug("[REACT] raw_output:\n{}", response.getRawOutput());
            }

            return toAction(response, self.getSeatId());
        } catch (StatusRuntimeException e) {
            log.warn("[REACT] gRPC call failed, using fallback: {}", e.getMessage());
            return fallback(state, self);
        } catch (Exception e) {
            log.warn("[REACT] unexpected error, using fallback: {}", e.getMessage());
            return fallback(state, self);
        }
    }

    private Action toAction(PokerAgentOuterClass.ActionResponse response, String playerId) {
        switch (response.getActionType()) {
            case FOLD:
                return new FoldAction(playerId);
            case CHECK:
                return new CheckAction(playerId);
            case CALL:
                return new CallAction(playerId);
            case BET:
                return new BetAction(playerId, response.getAmount());
            case RAISE:
                return new RaiseAction(playerId, response.getAmount());
            case ALL_IN:
                return new AllInAction(playerId);
            default:
                return new FoldAction(playerId);
        }
    }

    private Action fallback(GameState state, PlayerState self) {
        if (config.getFallbackAgent() != null) {
            return config.getFallbackAgent().decide(state, self);
        }
        return new FoldAction(self.getSeatId());
    }

    /**
     * Shuts down the underlying gRPC channel.
     */
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}

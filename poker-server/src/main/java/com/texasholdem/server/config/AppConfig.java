package com.texasholdem.server.config;

import com.texasholdem.ai.BuiltinAgent;
import com.texasholdem.ai.SimpleHoldemAgent;
import com.texasholdem.ai.grpc.GrpcAgentBridge;
import com.texasholdem.ai.grpc.GrpcAgentConfig;
import poker_agent.PokerAgentGrpc;
import poker_agent.PokerAgentOuterClass;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

@Configuration
public class AppConfig {
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Value("${agent.type:simple}")
    private String agentType;

    @Value("${agent.grpc.host:localhost}")
    private String grpcHost;

    @Value("${agent.grpc.port:9090}")
    private int grpcPort;

    @Value("${agent.grpc.timeout-ms:30000}")
    private int grpcTimeoutMs;

    private ManagedChannel grpcChannel;

    @Bean
    public BuiltinAgent simpleAgent() {
        log.info("Creating SimpleHoldemAgent");
        return new SimpleHoldemAgent();
    }

    @Bean(name = "grpcAgent")
    public BuiltinAgent grpcAgent() {
        if (!"grpc".equals(agentType)) {
            return null;
        }
        log.info("Creating GrpcAgentBridge -> {}:{}", grpcHost, grpcPort);
        GrpcAgentConfig config = new GrpcAgentConfig();
        config.setHost(grpcHost);
        config.setPort(grpcPort);
        config.setTimeoutMs(grpcTimeoutMs);
        config.setFallbackAgent(new SimpleHoldemAgent());

        grpcChannel = ManagedChannelBuilder.forAddress(grpcHost, grpcPort)
                .usePlaintext()
                .build();
        return new GrpcAgentBridge(config, grpcChannel);
    }

    @Bean
    public BuiltinAgent builtinAgent() {
        return simpleAgent();
    }

    public boolean isGrpcAgentAvailable() {
        if (grpcChannel == null)
            return false;
        try {
            PokerAgentGrpc.PokerAgentBlockingStub stub = PokerAgentGrpc.newBlockingStub(grpcChannel)
                    .withDeadlineAfter(2, TimeUnit.SECONDS);
            PokerAgentOuterClass.PingResponse resp = stub.ping(PokerAgentOuterClass.PingRequest.newBuilder().build());
            return resp.getSuccess();
        } catch (Exception e) {
            log.debug("gRPC agent health check failed: {}", e.getMessage());
            return false;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (grpcChannel != null) {
            grpcChannel.shutdown();
        }
    }
}

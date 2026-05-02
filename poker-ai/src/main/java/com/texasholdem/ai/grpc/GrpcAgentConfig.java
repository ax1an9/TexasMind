package com.texasholdem.ai.grpc;

import com.texasholdem.ai.BuiltinAgent;

/**
 * Configuration for {@link GrpcAgentBridge}.
 */
public class GrpcAgentConfig {

    private String host = "localhost";
    private int port = 9090;
    private int timeoutMs = 4000;
    private BuiltinAgent fallbackAgent;

    public GrpcAgentConfig() {}

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public BuiltinAgent getFallbackAgent() {
        return fallbackAgent;
    }

    public void setFallbackAgent(BuiltinAgent fallbackAgent) {
        this.fallbackAgent = fallbackAgent;
    }
}

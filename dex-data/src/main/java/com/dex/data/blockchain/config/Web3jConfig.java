package com.dex.blockchain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Web3j配置
 */
@Component
@ConfigurationProperties(prefix = "web3j")
public class Web3jConfig {
    private String clientAddress;
    private boolean adminClient;
    private long pollingInterval;

    public String getClientAddress() {
        return clientAddress;
    }

    public void setClientAddress(String clientAddress) {
        this.clientAddress = clientAddress;
    }

    public boolean isAdminClient() {
        return adminClient;
    }

    public void setAdminClient(boolean adminClient) {
        this.adminClient = adminClient;
    }

    public long getPollingInterval() {
        return pollingInterval;
    }

    public void setPollingInterval(long pollingInterval) {
        this.pollingInterval = pollingInterval;
    }
}

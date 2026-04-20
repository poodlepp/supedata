package com.dex.infrastructure.blockchain.config;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.websocket.WebSocketService;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 以太坊主网 Web3j 配置
 */
@Configuration
public class MainnetWeb3jConfig {

    @Value("${blockchain.mainnet.rpc-url:https://mainnet.infura.io/v3/YOUR_INFURA_KEY}")
    private String mainnetRpcUrl;

    @Value("${blockchain.mainnet.ws-url:wss://mainnet.infura.io/ws/v3/YOUR_INFURA_KEY}")
    private String mainnetWsUrl;

    @Value("${blockchain.mainnet.connect-timeout-ms:2000}")
    private long connectTimeoutMs;

    @Value("${blockchain.mainnet.read-timeout-ms:4000}")
    private long readTimeoutMs;

    @Value("${blockchain.mainnet.call-timeout-ms:5000}")
    private long callTimeoutMs;

    @Value("${web3j.polling-interval:15000}")
    private long pollingInterval;

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService web3jScheduledExecutorService() {
        return Executors.newScheduledThreadPool(2);
    }

    @Bean
    @Primary
    public Web3j web3j(ScheduledExecutorService web3jScheduledExecutorService) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .callTimeout(Duration.ofMillis(callTimeoutMs))
                .build();
        return Web3j.build(new HttpService(mainnetRpcUrl, httpClient, false), pollingInterval, web3jScheduledExecutorService);
    }

    @Bean(destroyMethod = "close")
    public WebSocketService mainnetWebSocketService() throws Exception {
        return new WebSocketService(mainnetWsUrl, false);
    }

    @Bean("wsWeb3j")
    public Web3j wsWeb3j(@Qualifier("mainnetWebSocketService") WebSocketService webSocketService) {
        return Web3j.build(webSocketService);
    }
}

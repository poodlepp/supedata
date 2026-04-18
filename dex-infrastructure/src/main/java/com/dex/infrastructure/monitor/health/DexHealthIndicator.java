package com.dex.infrastructure.monitor.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;

/**
 * 自定义健康检查
 */
@Component
public class DexHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;
    private final Web3j web3j;

    public DexHealthIndicator(JdbcTemplate jdbcTemplate, Web3j web3j) {
        this.jdbcTemplate = jdbcTemplate;
        this.web3j = web3j;
    }

    @Override
    public Health health() {
        try {
            Integer db = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            String clientVersion = web3j.web3ClientVersion().send().getWeb3ClientVersion();
            return Health.up()
                    .withDetail("database", db != null && db == 1 ? "ok" : "unknown")
                    .withDetail("web3", clientVersion == null ? "unreachable" : clientVersion)
                    .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}

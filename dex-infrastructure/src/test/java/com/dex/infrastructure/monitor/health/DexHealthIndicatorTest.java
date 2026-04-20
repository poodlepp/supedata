package com.dex.infrastructure.monitor.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.jdbc.core.JdbcTemplate;
import org.web3j.protocol.Web3j;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DexHealthIndicatorTest {

    @Test
    void shouldStayUpAndDeferWeb3Checks() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Web3j web3j = mock(Web3j.class);

        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        DexHealthIndicator indicator = new DexHealthIndicator(jdbcTemplate, web3j);
        Health health = indicator.health();

        assertEquals("UP", health.getStatus().getCode());
        assertEquals("ok", health.getDetails().get("database"));
        assertEquals("deferred", health.getDetails().get("web3"));
        assertEquals("/api/blockchain/status", health.getDetails().get("web3CheckEndpoint"));
    }

    @Test
    void shouldReturnDownWhenDatabaseFails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Web3j web3j = mock(Web3j.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenThrow(new RuntimeException("db down"));

        DexHealthIndicator indicator = new DexHealthIndicator(jdbcTemplate, web3j);
        Health health = indicator.health();

        assertEquals("DOWN", health.getStatus().getCode());
    }
}

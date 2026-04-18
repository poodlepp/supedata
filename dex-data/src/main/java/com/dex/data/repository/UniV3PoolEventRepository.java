package com.dex.data.repository;

import com.dex.data.entity.UniV3PoolEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class UniV3PoolEventRepository {

    private static final RowMapper<UniV3PoolEvent> ROW_MAPPER = (rs, rowNum) -> {
        UniV3PoolEvent event = new UniV3PoolEvent();
        event.setId(rs.getLong("id"));
        event.setEventUid(rs.getString("event_uid"));
        event.setChainId(rs.getLong("chain_id"));
        event.setPoolAddress(rs.getString("pool_address"));
        event.setPoolName(rs.getString("pool_name"));
        event.setEventType(rs.getString("event_type"));
        event.setBlockNumber(rs.getLong("block_number"));
        event.setBlockHash(rs.getString("block_hash"));
        event.setTransactionHash(rs.getString("transaction_hash"));
        event.setTransactionIndex(rs.getInt("transaction_index"));
        event.setLogIndex(rs.getInt("log_index"));
        event.setBlockTime(rs.getLong("block_time"));
        event.setSender(rs.getString("sender"));
        event.setRecipient(rs.getString("recipient"));
        event.setOwnerAddress(rs.getString("owner_address"));
        event.setTickLower((Integer) rs.getObject("tick_lower"));
        event.setTickUpper((Integer) rs.getObject("tick_upper"));
        event.setAmount(rs.getString("amount"));
        event.setAmount0(rs.getString("amount0"));
        event.setAmount1(rs.getString("amount1"));
        event.setSqrtPriceX96(rs.getString("sqrt_price_x96"));
        event.setLiquidity(rs.getString("liquidity"));
        event.setTick((Integer) rs.getObject("tick"));
        event.setPaid0(rs.getString("paid0"));
        event.setPaid1(rs.getString("paid1"));
        event.setSummary(rs.getString("summary"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        event.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        return event;
    };

    private final JdbcTemplate jdbcTemplate;

    public void insertIgnore(UniV3PoolEvent event) {
        jdbcTemplate.update(
                """
                INSERT IGNORE INTO uni_v3_pool_event(
                    event_uid, chain_id, pool_address, pool_name, event_type,
                    block_number, block_hash, transaction_hash, transaction_index, log_index, block_time,
                    sender, recipient, owner_address, tick_lower, tick_upper,
                    amount, amount0, amount1, sqrt_price_x96, liquidity, tick, paid0, paid1, summary
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                event.getEventUid(),
                event.getChainId(),
                event.getPoolAddress().toLowerCase(),
                event.getPoolName(),
                event.getEventType(),
                event.getBlockNumber(),
                event.getBlockHash(),
                event.getTransactionHash(),
                event.getTransactionIndex(),
                event.getLogIndex(),
                event.getBlockTime(),
                event.getSender(),
                event.getRecipient(),
                event.getOwnerAddress(),
                event.getTickLower(),
                event.getTickUpper(),
                event.getAmount(),
                event.getAmount0(),
                event.getAmount1(),
                event.getSqrtPriceX96(),
                event.getLiquidity(),
                event.getTick(),
                event.getPaid0(),
                event.getPaid1(),
                event.getSummary()
        );
    }

    public List<UniV3PoolEvent> findRecentEvents(Long chainId, String poolAddress, String eventType, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM uni_v3_pool_event
                WHERE chain_id = ? AND pool_address = ?
                """);
        Object[] args;
        if (eventType != null && !eventType.isBlank()) {
            sql.append(" AND event_type = ? ");
            sql.append(" ORDER BY block_number DESC, transaction_index DESC, log_index DESC LIMIT ? ");
            args = new Object[]{chainId, poolAddress.toLowerCase(), eventType, limit};
        } else {
            sql.append(" ORDER BY block_number DESC, transaction_index DESC, log_index DESC LIMIT ? ");
            args = new Object[]{chainId, poolAddress.toLowerCase(), limit};
        }
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args);
    }

    public Map<String, Long> countByType(Long chainId, String poolAddress) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT event_type, COUNT(1) AS cnt
                FROM uni_v3_pool_event
                WHERE chain_id = ? AND pool_address = ?
                GROUP BY event_type
                ORDER BY event_type
                """,
                chainId,
                poolAddress.toLowerCase()
        );
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            counts.put((String) row.get("event_type"), ((Number) row.get("cnt")).longValue());
        }
        return counts;
    }

    public Long findLatestBlockTime(Long chainId, String poolAddress) {
        return jdbcTemplate.query(
                """
                SELECT block_time FROM uni_v3_pool_event
                WHERE chain_id = ? AND pool_address = ?
                ORDER BY block_number DESC, transaction_index DESC, log_index DESC
                LIMIT 1
                """,
                rs -> rs.next() ? rs.getLong("block_time") : null,
                chainId,
                poolAddress.toLowerCase()
        );
    }

    public List<BlockHashRow> findRecentBlockHashes(Long chainId, String poolAddress, long minBlockNumber) {
        return jdbcTemplate.query(
                """
                SELECT block_number, MAX(block_hash) AS block_hash
                FROM uni_v3_pool_event
                WHERE chain_id = ? AND pool_address = ? AND block_number >= ?
                GROUP BY block_number
                ORDER BY block_number ASC
                """,
                (rs, rowNum) -> new BlockHashRow(rs.getLong("block_number"), rs.getString("block_hash")),
                chainId,
                poolAddress.toLowerCase(),
                minBlockNumber
        );
    }

    public void deleteFromBlock(Long chainId, String poolAddress, long fromBlockInclusive) {
        jdbcTemplate.update(
                "DELETE FROM uni_v3_pool_event WHERE chain_id = ? AND pool_address = ? AND block_number >= ?",
                chainId,
                poolAddress.toLowerCase(),
                fromBlockInclusive
        );
    }

    public long countAll(Long chainId, String poolAddress) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM uni_v3_pool_event WHERE chain_id = ? AND pool_address = ?",
                Long.class,
                chainId,
                poolAddress.toLowerCase()
        );
        return count == null ? 0L : count;
    }

    public record BlockHashRow(long blockNumber, String blockHash) {}
}

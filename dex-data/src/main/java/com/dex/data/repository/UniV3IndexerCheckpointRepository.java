package com.dex.data.repository;

import com.dex.data.entity.UniV3IndexerCheckpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UniV3IndexerCheckpointRepository {

    private static final RowMapper<UniV3IndexerCheckpoint> ROW_MAPPER = (rs, rowNum) -> {
        UniV3IndexerCheckpoint checkpoint = new UniV3IndexerCheckpoint();
        checkpoint.setId(rs.getLong("id"));
        checkpoint.setChainId(rs.getLong("chain_id"));
        checkpoint.setPoolAddress(rs.getString("pool_address"));
        checkpoint.setPoolName(rs.getString("pool_name"));
        checkpoint.setStartBlock(rs.getLong("start_block"));
        checkpoint.setLastScannedBlock(rs.getLong("last_scanned_block"));
        checkpoint.setLastCommittedBlock(rs.getLong("last_committed_block"));
        checkpoint.setLastCommittedBlockHash(rs.getString("last_committed_block_hash"));
        checkpoint.setConfirmations(rs.getInt("confirmations"));
        checkpoint.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        checkpoint.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return checkpoint;
    };

    private final JdbcTemplate jdbcTemplate;

    public UniV3IndexerCheckpoint find(Long chainId, String poolAddress) {
        return jdbcTemplate.query(
                """
                SELECT * FROM uni_v3_indexer_checkpoint
                WHERE chain_id = ? AND pool_address = ?
                LIMIT 1
                """,
                ROW_MAPPER,
                chainId,
                poolAddress.toLowerCase()
        ).stream().findFirst().orElse(null);
    }

    public void insert(UniV3IndexerCheckpoint checkpoint) {
        jdbcTemplate.update(
                """
                INSERT INTO uni_v3_indexer_checkpoint(
                    chain_id, pool_address, pool_name, start_block,
                    last_scanned_block, last_committed_block, last_committed_block_hash, confirmations
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                checkpoint.getChainId(),
                checkpoint.getPoolAddress().toLowerCase(),
                checkpoint.getPoolName(),
                checkpoint.getStartBlock(),
                checkpoint.getLastScannedBlock(),
                checkpoint.getLastCommittedBlock(),
                checkpoint.getLastCommittedBlockHash(),
                checkpoint.getConfirmations()
        );
    }

    public void updateScanProgress(Long chainId, String poolAddress, long lastScannedBlock) {
        jdbcTemplate.update(
                """
                UPDATE uni_v3_indexer_checkpoint
                SET last_scanned_block = ?
                WHERE chain_id = ? AND pool_address = ?
                """,
                lastScannedBlock,
                chainId,
                poolAddress.toLowerCase()
        );
    }

    public void updateCommittedBlock(Long chainId, String poolAddress, long blockNumber, String blockHash) {
        jdbcTemplate.update(
                """
                UPDATE uni_v3_indexer_checkpoint
                SET last_scanned_block = ?, last_committed_block = ?, last_committed_block_hash = ?
                WHERE chain_id = ? AND pool_address = ?
                """,
                blockNumber,
                blockNumber,
                blockHash,
                chainId,
                poolAddress.toLowerCase()
        );
    }

    public void rollbackTo(Long chainId, String poolAddress, long blockNumber) {
        jdbcTemplate.update(
                """
                UPDATE uni_v3_indexer_checkpoint
                SET last_scanned_block = ?, last_committed_block = ?, last_committed_block_hash = NULL
                WHERE chain_id = ? AND pool_address = ?
                """,
                blockNumber,
                blockNumber,
                chainId,
                poolAddress.toLowerCase()
        );
    }
}

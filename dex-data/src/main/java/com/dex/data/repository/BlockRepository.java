package com.dex.data.repository;

import com.dex.data.entity.Block;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 区块数据访问层
 */
@Mapper
public interface BlockRepository {
    @Select("""
            SELECT id, block_number, block_hash, timestamp
            FROM blocks
            WHERE block_number = #{blockNumber}
            LIMIT 1
            """)
    Block findByBlockNumber(Long blockNumber);
}

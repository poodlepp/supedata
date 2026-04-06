package com.dex.data.repository;

import com.dex.data.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 区块数据访问层
 */
@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {
    Block findByBlockNumber(Long blockNumber);
}

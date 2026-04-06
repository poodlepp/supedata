package com.dex.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 区块实体
 */
@Entity
@Table(name = "blocks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Block {
    @Id
    private Long id;

    @Column(unique = true)
    private Long blockNumber;

    private String blockHash;

    private Long timestamp;
}

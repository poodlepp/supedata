package com.dex.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 价格快照 DTO
 * 
 * 用于 API 响应，包含价格和元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceSnapshot {

    /**
     * 交易对（如 "ETH/USDT"）
     */
    private String pair;

    /**
     * 价格
     */
    private Double price;

    /**
     * 时间戳（毫秒）
     */
    private Long timestamp;

    /**
     * 网络名称
     */
    private String network;

    /**
     * 数据来源
     */
    private String source;

    /**
     * 是否为最新数据
     */
    private Boolean isLatest;
}

package com.dex.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Swap 事件响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwapEventSnapshot {

    private String pair;
    private String pairAddress;
    private String txHash;
    private Long blockNumber;
    private Long timestamp;
    private String sender;
    private String recipient;
    private String tokenInSymbol;
    private String tokenOutSymbol;
    private Double amountIn;
    private Double amountOut;
    private Double price;
    private String side;
}

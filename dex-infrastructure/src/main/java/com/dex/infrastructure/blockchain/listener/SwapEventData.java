package com.dex.infrastructure.blockchain.listener;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Uniswap V2 Swap 事件快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwapEventData {

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

package com.dex.infrastructure.blockchain.listener;

/**
 * Swap 事件监听器接口
 */
public interface SwapEventListener {

    /**
     * 收到新的 Swap 事件时回调
     */
    void onSwapEvent(SwapEventData eventData);

    /**
     * 发生错误时回调
     */
    void onError(Exception e);
}

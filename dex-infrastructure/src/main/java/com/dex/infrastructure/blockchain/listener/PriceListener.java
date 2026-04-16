package com.dex.infrastructure.blockchain.listener;

/**
 * 价格监听器接口
 * 
 * 设计模式：观察者模式
 * - 定义价格变化的回调接口
 * - 支持多个监听器同时监听
 * - 解耦价格源和消费者
 */
public interface PriceListener {

    /**
     * 当价格更新时调用
     * 
     * @param pair 交易对（如 "ETH/USDT"）
     * @param price 新价格
     * @param timestamp 时间戳
     */
    void onPriceUpdate(String pair, Double price, Long timestamp);

    /**
     * 当发生错误时调用
     */
    void onError(String pair, Exception e);

    /**
     * 获取监听的交易对
     */
    String getPair();
}

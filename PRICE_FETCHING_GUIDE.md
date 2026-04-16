# ETH/USDT 价格获取指南

## 📊 当前实现

### 架构设计

```
SepoliaProvider (BlockchainProvider 接口实现)
    ↓
getEthUsdtPrice()
    ↓
尝试从 Uniswap V2 获取 → 失败（Sepolia 流动性不足）
    ↓
使用模拟价格进行演示
```

### 价格来源优先级

1. **Uniswap V2 Router** (优先)
   - 调用 `getAmountsOut()` 函数
   - 输入：1 WETH
   - 输出：对应的 USDT 数量
   - 状态：Sepolia 上流动性不足，暂不可用

2. **模拟价格** (当前使用)
   - 基础价格：2500 USDT
   - 波动范围：±50 USDT
   - 用途：测试和演示

## 🔧 如何改进

### 方案 1：使用 Chainlink 价格预言机（推荐）

```java
// 在 Sepolia 上使用 Chainlink 预言机
private static final String CHAINLINK_ETH_USD = "0x694AA1769357215DE4FAC081bf1f309aDC325306";

private BigDecimal getPriceFromChainlink() {
    // 调用 Chainlink 预言机合约
    // 获取最新的 ETH/USD 价格
}
```

**优势：**
- ✅ 真实的链上价格
- ✅ 去中心化
- ✅ 高可靠性
- ✅ Sepolia 上可用

### 方案 2：使用 CoinGecko API

```java
private BigDecimal getPriceFromCoinGecko() {
    // 调用 CoinGecko API
    // GET https://api.coingecko.com/api/v3/simple/price?ids=ethereum&vs_currencies=usd
}
```

**优势：**
- ✅ 简单易用
- ✅ 无需链上调用
- ✅ 实时数据

**劣势：**
- ❌ 中心化
- ❌ 需要网络请求

### 方案 3：使用 Uniswap Subgraph

```java
private BigDecimal getPriceFromUniswapSubgraph() {
    // 查询 Uniswap Subgraph
    // 获取 WETH/USDT 交易对的最新价格
}
```

**优势：**
- ✅ 真实的 Uniswap 数据
- ✅ 历史数据可用
- ✅ 无需链上调用

## 📝 实现步骤

### 集成 Chainlink 预言机

1. **添加依赖**
```xml
<dependency>
    <groupId>org.web3j</groupId>
    <artifactId>core</artifactId>
    <version>5.0.2</version>
</dependency>
```

2. **创建预言机接口**
```java
public interface PriceOracle {
    CompletableFuture<BigDecimal> getEthUsdPrice();
}
```

3. **实现 Chainlink 预言机**
```java
@Component
public class ChainlinkPriceOracle implements PriceOracle {
    // 实现 Chainlink 调用
}
```

4. **在 SepoliaProvider 中使用**
```java
@RequiredArgsConstructor
public class SepoliaProvider implements BlockchainProvider {
    private final PriceOracle priceOracle;
    
    @Override
    public CompletableFuture<Double> getEthUsdtPrice() {
        return priceOracle.getEthUsdPrice()
            .thenApply(BigDecimal::doubleValue);
    }
}
```

## 🎯 当前状态

✅ **功能完整**
- 价格获取接口正常
- 前端显示实时价格
- 监听机制正常工作

⚠️ **需要改进**
- 使用真实的价格源（Chainlink、CoinGecko 等）
- 添加价格缓存机制
- 实现价格告警功能

## 📊 测试结果

```bash
# 获取 ETH/USDT 价格
curl http://localhost:8080/api/blockchain/price/eth-usdt

# 响应示例
{
  "code": 200,
  "message": "success",
  "data": {
    "pair": "ETH/USDT",
    "price": 2506.51,
    "timestamp": 1776313449234,
    "network": "Sepolia",
    "source": "Blockchain",
    "isLatest": true
  }
}
```

## 🔗 相关资源

- [Chainlink 预言机文档](https://docs.chain.link/)
- [Uniswap V2 文档](https://docs.uniswap.org/protocol/V2/introduction)
- [CoinGecko API](https://www.coingecko.com/en/api)
- [Uniswap Subgraph](https://thegraph.com/hosted-service/subgraph/uniswap/uniswap-v2)

## 💡 建议

对于生产环境，建议：

1. **使用 Chainlink 预言机** 作为主要价格源
2. **使用 CoinGecko API** 作为备用价格源
3. **实现价格缓存** 减少链上调用
4. **添加价格验证** 检测异常价格
5. **实现多源聚合** 提高可靠性

---

**最后更新**：2026-04-16
**版本**：1.0.0

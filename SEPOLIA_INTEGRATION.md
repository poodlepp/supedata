# Sepolia 集成完整指南

## 📋 项目概述

本项目集成了 Sepolia 测试网络，实现了：
1. ✅ 连接 Sepolia RPC
2. ✅ 监听 ETH/USDT 最新价格
3. ✅ 前端 Demo 页面展示

## 🏗️ 架构设计

### 后端架构（Java + Spring Boot）

```
dex-infrastructure/
├── blockchain/
│   ├── config/
│   │   └── Web3jConfig.java              # Web3j 配置
│   ├── provider/
│   │   ├── BlockchainProvider.java       # 区块链提供者接口
│   │   └── SepoliaProvider.java          # Sepolia 实现
│   └── listener/
│       ├── PriceListener.java            # 价格监听器接口
│       └── EthUsdtPriceListener.java     # ETH/USDT 监听实现

dex-business/
├── service/
│   └── blockchain/
│       ├── BlockchainService.java        # 业务服务接口
│       └── SepoliaService.java           # Sepolia 服务实现

dex-api/
├── controller/
│   └── BlockchainController.java         # REST API 控制器
└── dto/
    └── PriceSnapshot.java                # 价格快照 DTO
```

### 前端架构（Vue3 + Pinia）

```
dex-frontend/
├── src/
│   ├── views/
│   │   └── BlockchainDemo.vue            # Sepolia Demo 页面
│   ├── components/
│   │   ├── PriceMonitor.vue              # 价格监听组件
│   │   └── BlockchainStatus.vue          # 区块链状态组件
│   ├── api/
│   │   └── blockchain.js                 # 区块链 API 调用
│   ├── stores/
│   │   └── blockchainStore.js            # Pinia 状态管理
│   └── router/
│       └── index.js                      # 路由配置
```

## 🎯 设计模式

### 1. 观察者模式（Observer Pattern）
- **应用**：`PriceListener` 接口和 `EthUsdtPriceListener` 实现
- **优势**：解耦价格源和消费者，支持多个监听器同时监听
- **代码**：
  ```java
  public interface PriceListener {
      void onPriceUpdate(String pair, Double price, Long timestamp);
      void onError(String pair, Exception e);
  }
  ```

### 2. 策略模式（Strategy Pattern）
- **应用**：`BlockchainProvider` 接口支持多链实现
- **优势**：易于扩展新的区块链网络（Mainnet、Polygon 等）
- **代码**：
  ```java
  public interface BlockchainProvider {
      CompletableFuture<Double> getEthUsdtPrice();
      String getNetworkName();
  }
  ```

### 3. 工厂模式（Factory Pattern）
- **应用**：`Web3jConfig` 创建不同网络的 Web3j 实例
- **优势**：集中管理 Web3j 实例创建和配置
- **代码**：
  ```java
  @Bean(name = "sepoliaWeb3j")
  public Web3j sepoliaWeb3j() {
      return Web3j.build(new HttpService(sepoliaRpcUrl));
  }
  ```

### 4. 依赖注入（Dependency Injection）
- **应用**：Spring 管理所有组件的生命周期
- **优势**：松耦合、易于测试、易于维护
- **代码**：
  ```java
  @Service
  @RequiredArgsConstructor
  public class SepoliaService implements BlockchainService {
      private final BlockchainProvider blockchainProvider;
  }
  ```

## 🔌 API 端点

### 获取 ETH/USDT 价格
```bash
GET /api/blockchain/price/eth-usdt

Response:
{
  "code": 0,
  "data": {
    "pair": "ETH/USDT",
    "price": 2500.50,
    "timestamp": 1713268800000,
    "network": "Sepolia",
    "source": "Blockchain",
    "isLatest": true
  }
}
```

### 获取区块链状态
```bash
GET /api/blockchain/status

Response:
{
  "code": 0,
  "data": true
}
```

### 获取最新区块号
```bash
GET /api/blockchain/block

Response:
{
  "code": 0,
  "data": 5123456
}
```

### 启动价格监听
```bash
POST /api/blockchain/listener/start

Response:
{
  "code": 0,
  "data": "Price listener started"
}
```

### 停止价格监听
```bash
POST /api/blockchain/listener/stop

Response:
{
  "code": 0,
  "data": "Price listener stopped"
}
```

## 🚀 快速开始

### 1. 配置 Sepolia RPC

编辑 `dex-api/src/main/resources/application.yml`：

```yaml
blockchain:
  sepolia:
    rpc-url: https://sepolia.infura.io/v3/YOUR_INFURA_KEY
    enabled: true
```

或设置环境变量：
```bash
export SEPOLIA_RPC_URL=https://sepolia.infura.io/v3/YOUR_INFURA_KEY
```

### 2. 启动后端服务

```bash
cd /Users/lipengyi/solFour/supedata
/Users/lipengyi/.m2/maven/bin/mvn spring-boot:run -pl dex-api
```

### 3. 启动前端服务

```bash
cd dex-frontend
npm install
npm run dev
```

### 4. 访问 Demo 页面

打开浏览器访问：`http://localhost:5173/blockchain`

## 📊 前端 Demo 页面功能

### 实时显示
- ✅ 连接状态（已连接/未连接）
- ✅ ETH/USDT 最新价格
- ✅ 最新区块号
- ✅ 监听状态（监听中/已停止）

### 交互功能
- ✅ 启动监听按钮（开始实时监听价格）
- ✅ 停止监听按钮（停止监听）
- ✅ 自动刷新（每 5 秒更新一次）

### 信息展示
- ✅ 系统信息面板
- ✅ 价格走势图表（预留）
- ✅ 错误提示

## 🔄 数据流

```
1. 前端初始化
   ↓
2. 调用 /api/blockchain/status 检查连接
   ↓
3. 调用 /api/blockchain/price/eth-usdt 获取价格
   ↓
4. 用户点击"启动监听"
   ↓
5. 调用 POST /api/blockchain/listener/start
   ↓
6. 后端启动 EthUsdtPriceListener
   ↓
7. 监听器每 10 秒轮询一次价格
   ↓
8. 前端每 5 秒刷新一次价格显示
   ↓
9. 用户点击"停止监听"
   ↓
10. 调用 POST /api/blockchain/listener/stop
```

## 🛠️ 扩展指南

### 添加新的区块链网络

1. 创建新的 Provider 实现：
```java
@Component
public class PolygonProvider implements BlockchainProvider {
    // 实现接口方法
}
```

2. 在配置中添加新网络：
```yaml
blockchain:
  polygon:
    rpc-url: https://polygon-rpc.com
    enabled: true
```

3. 创建对应的 Service：
```java
@Service
public class PolygonService implements BlockchainService {
    // 实现业务逻辑
}
```

### 添加新的价格源

1. 创建新的 PriceListener 实现：
```java
public class UniswapPriceListener implements PriceListener {
    // 实现监听逻辑
}
```

2. 在 Service 中注册监听器：
```java
priceListener.subscribe(new UniswapPriceListener());
```

## 📝 代码质量

### 优雅性体现

✨ **分层清晰**
- 基础设施层（blockchain）
- 业务逻辑层（service）
- API 层（controller）

✨ **设计模式**
- 观察者模式：解耦监听器
- 策略模式：支持多链
- 工厂模式：集中创建
- 依赖注入：松耦合

✨ **易于扩展**
- 接口驱动设计
- 配置外部化
- 支持多链、多价格源

✨ **代码复用**
- 通用接口
- 具体实现可插拔
- 前后端分离

✨ **前后端分离**
- API 驱动
- 前端独立
- 易于维护

## 🐛 常见问题

### Q: 如何获取 Infura Key？
A: 访问 https://infura.io，注册账户后创建项目，获取 Sepolia RPC URL

### Q: 价格为什么显示 0？
A: 检查 RPC URL 是否正确，以及合约地址是否有效

### Q: 如何修改监听间隔？
A: 编辑 `EthUsdtPriceListener.java` 中的 `POLL_INTERVAL_SECONDS`

## 📚 参考资源

- [Web3j 官方文档](https://docs.web3j.io/)
- [Sepolia 测试网络](https://sepolia.dev/)
- [Infura 文档](https://docs.infura.io/)
- [Vue3 官方文档](https://vuejs.org/)
- [Pinia 官方文档](https://pinia.vuejs.org/)

---

**最后更新**：2026-04-16
**版本**：1.0.0

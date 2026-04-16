# 🚀 快速开始指南

## 📦 已完成的功能

✅ Sepolia 网络连接  
✅ ETH/USDT 价格监听  
✅ 前端 Demo 页面  
✅ 完整的 API 接口  
✅ 优雅的架构设计  

## 🔧 配置步骤

### 1️⃣ 获取 Infura Key
访问 https://infura.io，创建项目，获取 Sepolia RPC URL

### 2️⃣ 设置环境变量
```bash
export SEPOLIA_RPC_URL=https://sepolia.infura.io/v3/YOUR_KEY
```

### 3️⃣ 构建项目
```bash
cd /Users/lipengyi/solFour/supedata
/Users/lipengyi/.m2/maven/bin/mvn clean install -DskipTests
```

## 🎯 启动服务

### 后端（在终端 1）
```bash
cd /Users/lipengyi/solFour/supedata
/Users/lipengyi/.m2/maven/bin/mvn spring-boot:run -pl dex-api
```

### 前端（在终端 2）
```bash
cd /Users/lipengyi/solFour/supedata/dex-frontend
npm install
npm run dev
```

## 🌐 访问应用

打开浏览器访问：
- **Demo 页面**：http://localhost:5173/blockchain
- **后端 API**：http://localhost:8080/api/blockchain

## 📊 API 快速参考

| 操作 | 命令 |
|------|------|
| 获取价格 | `curl http://localhost:8080/api/blockchain/price/eth-usdt` |
| 检查连接 | `curl http://localhost:8080/api/blockchain/status` |
| 获取区块 | `curl http://localhost:8080/api/blockchain/block` |
| 启动监听 | `curl -X POST http://localhost:8080/api/blockchain/listener/start` |
| 停止监听 | `curl -X POST http://localhost:8080/api/blockchain/listener/stop` |

## 📁 关键文件位置

| 文件 | 位置 |
|------|------|
| Web3j 配置 | `dex-infrastructure/src/main/java/com/dex/infrastructure/blockchain/config/Web3jConfig.java` |
| 区块链提供者 | `dex-infrastructure/src/main/java/com/dex/infrastructure/blockchain/provider/` |
| 业务服务 | `dex-business/src/main/java/com/dex/business/service/blockchain/` |
| API 控制器 | `dex-api/src/main/java/com/dex/api/controller/BlockchainController.java` |
| Demo 页面 | `dex-frontend/src/views/BlockchainDemo.vue` |
| 状态管理 | `dex-frontend/src/stores/blockchainStore.js` |

## 🎨 前端功能

- 🔗 连接状态指示
- 💰 实时价格显示
- 📦 区块号显示
- ▶️ 启动/停止监听
- 📊 自动刷新（5 秒）
- 📈 价格走势图表（预留）

## 🔄 工作流程

```
1. 打开 Demo 页面
   ↓
2. 检查连接状态（自动）
   ↓
3. 显示当前价格（自动）
   ↓
4. 点击"启动监听"
   ↓
5. 后端开始轮询价格
   ↓
6. 前端每 5 秒刷新显示
   ↓
7. 点击"停止监听"停止
```

## 🛠️ 常见问题

**Q: 价格显示为 0？**  
A: 检查 RPC URL 是否正确，合约地址是否有效

**Q: 连接失败？**  
A: 确保 Infura Key 有效，网络连接正常

**Q: 如何修改监听间隔？**  
A: 编辑 `EthUsdtPriceListener.java` 中的 `POLL_INTERVAL_SECONDS`

## 📚 详细文档

- 完整指南：`SEPOLIA_INTEGRATION.md`
- 实现总结：`IMPLEMENTATION_SUMMARY.md`
- 本文件：`QUICK_START.md`

## ✨ 设计亮点

✨ 分层清晰（基础设施 → 业务 → API）  
✨ 设计模式（观察者、策略、工厂、DI）  
✨ 易于扩展（支持多链、多价格源）  
✨ 前后端分离（API 驱动）  
✨ 异步非阻塞（CompletableFuture）  

---

**准备好了吗？开始探索吧！** 🚀

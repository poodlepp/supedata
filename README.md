# DEX Aggregator Platform

一个完整的 DEX 数据聚合平台，包含后端 API 和前端应用。

## 🎯 项目概述

DEX Aggregator 是一个实时 DEX 数据聚合、价格路由和流动性分析平台。

**核心功能：**
- 📊 实时价格数据展示
- 🛣️ 智能路由查询
- 💧 流动性分析
- 📈 交易量统计
- ⚡ 系统监控

## 🏗️ 技术栈

### 后端
- **Java 21 LTS** + **Spring Boot 3.3**
- **Web3j** - 区块链交互
- **Kafka** - 消息队列
- **Redis** - 缓存
- **MySQL** - 数据库
- **Prometheus** - 监控

### 前端
- **Vue 3** + **Vite**
- **Element Plus** - UI 组件库
- **ECharts** - 图表库
- **Pinia** - 状态管理
- **Axios** - HTTP 请求

## 📁 项目结构

```
supedata/
├── dex-api/              # REST API 服务
├── dex-blockchain/       # 区块链交互
├── dex-business/         # 业务逻辑
├── dex-common/           # 通用模块
├── dex-data/             # 数据层
├── dex-monitor/          # 监控
├── dex-scheduler/        # 定时任务
├── dex-frontend/         # Vue 前端应用
└── pom.xml               # Maven 配置
```

## 🚀 快速开始

### 前置要求

- Java 21 JDK
- Maven 3.8+
- Node.js 18+

### 启动步骤

**1. 构建后端**
```bash
cd supedata
mvn clean install -DskipTests
```

**2. 启动后端 API（新终端）**
```bash
mvn spring-boot:run -pl dex-api -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

**3. 启动前端（新终端）**
```bash
cd dex-frontend
npm install
npm run dev
```

**4. 打开浏览器**
访问 http://localhost:5173

## 📍 访问地址

| 服务 | 地址 |
|------|------|
| 前端应用 | http://localhost:5173 |
| 后端 API | http://localhost:8080 |
| 健康检查 | http://localhost:8080/actuator/health |

## 📚 文档

- [快速启动指南](./QUICK_START.md) - 详细的启动步骤
- [调试指南](./LOCAL_DEBUG.md) - 本地开发调试
- [启动清单](./CHECKLIST.md) - 启动前检查
- [项目概览](./PROJECT_OVERVIEW.txt) - 项目架构
- [架构设计](./dex-aggregator-architecture.md) - 详细架构

## 🧪 测试 API

```bash
# 健康检查
curl http://localhost:8080/actuator/health

# 获取价格
curl http://localhost:8080/api/v1/prices/ETH-USDC

# 查询路由
curl "http://localhost:8080/api/v1/routes/best?from=ETH&to=USDC"

# 流动性信息
curl http://localhost:8080/api/v1/liquidity/pool-address

# 交易量统计
curl "http://localhost:8080/api/v1/statistics/volume?pair=ETH-USDC"
```

## 🐛 调试

### 后端调试
在 IDE 中打开 `dex-api/src/main/java/com/dex/api/DexApiApplication.java`，设置断点后以 Debug 模式运行。

### 前端调试
打开浏览器 DevTools (F12)，在 Sources 标签页设置断点。

## 📝 下一步

1. ✅ 框架搭建完成
2. ⏭️ 实现后端业务逻辑
3. ⏭️ 连接区块链 RPC
4. ⏭️ 配置数据库和缓存
5. ⏭️ 完善前端图表
6. ⏭️ 部署到生产环境

## 📞 需要帮助？

查看相关文档：
- 快速启动：[QUICK_START.md](./QUICK_START.md)
- 详细调试：[LOCAL_DEBUG.md](./LOCAL_DEBUG.md)
- 启动清单：[CHECKLIST.md](./CHECKLIST.md)

---

**祝你开发愉快！🎉**

# DEX Aggregator Platform

一个按阶段持续落地的 DEX 数据聚合平台，包含后端 API、多模块数据处理链路和前端验收看板。

## 当前落地范围（对应架构文档第十部分）

### 已落地
- **阶段 0：工程底座**
  - Maven 多模块结构整理完成
  - Docker Compose 管理 MySQL / Redis / Kafka / Zookeeper
  - `actuator/health` 与自定义健康检查
  - README / 启动脚本 / 初始化 SQL 对齐
- **阶段 1：单链读链与原始同步**
  - Ethereum Mainnet 连接检查
  - 最新区块接口
  - UniV3 pool checkpoint / 断点续传 / reorg window 回滚
- **阶段 2：DEX 协议索引与标准化**
  - UniV3 Pool 事件索引与入库
  - 标准化事件查询接口
  - 同池内按 `block -> tx -> log` 顺序入库
- **阶段 3：派生指标与数据服务**
  - 价格接口 / 流动性池接口 / 统计概览接口
  - 前端 Dashboard 与 Stage Monitor
- **阶段 4：报价与路由引擎（演示版）**
  - `/api/v1/routes/quote`
  - 直连池 + 两跳路径候选比较
  - 输出路径、gas、price impact、淘汰原因

### 下一步
- **阶段 5**：实时推送、告警、补数/回放
- **阶段 6**：多链适配器、插件化协议解析、Reorg-safe 修复、回测导出

## 技术栈

### 后端
- Java 21
- Spring Boot 3.3
- MyBatis
- MySQL
- Redis
- Kafka
- Web3j
- Micrometer / Prometheus

### 前端
- Vue 3
- Vite
- Element Plus
- Axios
- ECharts

## 项目结构

```text
supedata/
├── dex-api/              # REST API 服务
├── dex-business/         # 业务逻辑
├── dex-common/           # 通用模型/异常
├── dex-data/             # Repository / Entity / 数据处理
├── dex-infrastructure/   # 监控、调度、链上接入、Kafka
├── dex-frontend/         # Vue 前端应用
├── docker-compose.yml    # 本地依赖服务
├── init-db.sql           # 基础表与演示数据
├── sql/univ3_indexer_init.sql
├── start.sh
└── pom.xml
```

## 启动方式

### 1）启动基础设施

```bash
docker compose up -d
```

### 2）初始化 UniV3 表结构（如尚未创建）

```bash
docker exec -i dex-mysql mysql -uroot -proot dex_db < sql/univ3_indexer_init.sql
```

### 3）构建后端（本机无 Maven 时可用 Docker）

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -v "$HOME/.m2":/root/.m2 \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn clean test package
```

### 4）启动后端 API（本机无 Maven 时可用 Docker）

```bash
docker run --rm --name dex-api \
  --network host \
  -v "$PWD":/workspace \
  -v "$HOME/.m2":/root/.m2 \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -pl dex-api spring-boot:run
```

### 5）启动前端

```bash
cd dex-frontend
npm install
npm run dev
```

## 访问地址

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8080`
- 健康检查：`http://localhost:8080/actuator/health`
- 阶段进度：`http://localhost:8080/api/v1/stages/progress`
- UniV3 摘要：`http://localhost:8080/api/univ3/summary`
- 路由报价：`http://localhost:8080/api/v1/routes/quote?from=ETH&to=USDC&amountIn=1`

## 常用接口

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/prices
curl http://localhost:8080/api/v1/liquidity/pools
curl http://localhost:8080/api/v1/statistics/overview
curl http://localhost:8080/api/v1/stages/progress
curl "http://localhost:8080/api/v1/routes/quote?from=ETH&to=USDC&amountIn=1"
```

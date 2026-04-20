# DEX Aggregator Platform

一个按阶段持续落地的 DEX 数据聚合平台，当前已经完成 **阶段 0-5 的真实可用闭环**。

## 当前真实支持范围

- **链**：Ethereum Mainnet
- **协议**：Uniswap V3
- **交易对**：ETH/USDC、ETH/DAI
- **价格来源**：主网真实池状态
- **报价来源**：Uniswap V3 Quoter

## 阶段状态

### 已完成
- **阶段 0：工程底座**
  - Maven 多模块结构
  - Docker Compose 基础依赖
  - `actuator/health`
  - 初始化 SQL / 启动脚本
- **阶段 1：单链读链与原始同步**
  - Ethereum Mainnet 连接检查
  - 最新区块读取
  - 主网池状态读取
- **阶段 2：DEX 协议索引与标准化**
  - UniV3 Pool 事件索引与入库
  - checkpoint / reorg window
  - 同池内按 `block -> tx -> log` 顺序落库
- **阶段 3：派生指标与数据服务**
  - 真实价格接口
  - 真实流动性池接口
  - 真实统计概览接口
  - 移除伪 volume 输出
- **阶段 4：报价与路由引擎**
  - `/api/v1/routes/quote`
  - ETH/USDC、ETH/DAI 真实报价
  - 返回路径、pool、fee、滑点、块高等元信息
- **阶段 5：实时化与运维能力**
  - `/api/v1/ops/overview`
  - `/api/v1/ops/stream/prices`
  - `/api/v1/ops/replay`
  - Prometheus 指标与运维监控页

### 未完成
- **阶段 6**：多链、多协议、历史回测导出

## Review 文档

详细设计说明见：

- `dex-aggregator-architecture.md`

历史、已归档的 Sepolia / Demo 文档见：

- `docs/archive/legacy-sepolia-demo/`

## 项目结构

```text
supedata/
├── dex-api/
├── dex-business/
├── dex-common/
├── dex-data/
├── dex-infrastructure/
├── dex-frontend/
├── docs/
├── docker-compose.yml
├── init-db.sql
├── sql/
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

### 3）启动后端

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -pl dex-api spring-boot:run
```

### 4）启动前端

```bash
cd dex-frontend
npm install
npm run dev -- --host 127.0.0.1 --port 5173
```

## 当前接口

- 健康检查：`/actuator/health`
- 价格：`/api/v1/prices`
- 流动性池：`/api/v1/liquidity/pools`
- 统计概览：`/api/v1/statistics/overview`
- 报价：`/api/v1/routes/quote?from=ETH&to=USDC&amountIn=1`
- 运维总览：`/api/v1/ops/overview`
- 实时 SSE：`/api/v1/ops/stream/prices`
- 手动回放：`/api/v1/ops/replay?fromBlock=...`
- Prometheus：`/actuator/prometheus`
- Prometheus UI：`http://localhost:9090`
- UniV3 索引：`/api/univ3/*`

## 监控栈

- Grafana：`http://localhost:3000`，负责看板、告警可视化和多数据源查询入口
- Prometheus：`http://localhost:9090`，负责抓取 Spring Boot `/actuator/prometheus` 指标、规则计算和告警触发
- Alertmanager：`http://localhost:9093`，负责 Prometheus 告警聚合、抑制和后续通知分发
- Thanos Query：`http://localhost:10903`，负责统一查询 Prometheus 实时数据和对象存储中的长期数据
- MinIO Console：`http://localhost:9001`，负责本地对象存储，承接 Thanos 的长期指标块

## 监控拓扑说明

- 当前 `docker compose` 启动的是**本地单机的生产式监控拓扑**：组件职责按生产环境拆开，但不等同于真正的高可用生产集群
- Spring Boot 应用只暴露 `/actuator/prometheus` 指标端点，不内嵌 Prometheus 采集职责
- Prometheus 通过 `host.docker.internal:8080` 抓取后端指标，再由 Grafana/Thanos 查询展示
- 如果后续进入真实生产环境，需要继续补齐多副本、远端对象存储、持久卷治理、告警通知渠道和权限控制

## 注意事项

- 当前支持范围是**少量交易对 + 真实主网数据**，不是全量聚合器
- 为抗 RPC 限流，真实池与真实报价带有短 TTL 缓存
- `volume` 当前明确不输出伪值
- 应用只暴露指标端点，Prometheus 通过 `docker compose up -d` 独立启动并抓取 `http://host.docker.internal:8080/actuator/prometheus`

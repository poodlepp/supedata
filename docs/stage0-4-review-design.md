# 阶段 0-4 详细设计文档（Review 版）

## 1. 文档目的

本文档用于支持对当前 DEX Aggregator 项目 **阶段 0-4** 的技术 review。重点不是“计划做什么”，而是说明：

- 当前已经完成了哪些能力
- 每个阶段的设计目标、实现边界和取舍
- 关键模块如何协作
- 当前真实支持范围是什么
- 已知限制与后续演进方向是什么

当前 review 基线：

- **链**：Ethereum Mainnet
- **协议**：Uniswap V3
- **真实支持交易对**：ETH/USDC、ETH/DAI
- **真实报价来源**：Uniswap V3 Quoter
- **阶段状态**：0-4 已完成；5-6 未完成

---

## 2. 总体架构

项目采用多模块 Maven 结构，按“接口层 → 业务层 → 数据层 / 基础设施层”分层：

```text
supedata/
├── dex-api/              # REST API、Controller、DTO、启动入口
├── dex-business/         # 业务编排、价格/统计/路由服务
├── dex-common/           # 通用模型、响应结构、异常
├── dex-data/             # Entity、Mapper/Repository、索引数据访问
├── dex-infrastructure/   # Web3j、链上访问、UniV3 实时读池与索引器
├── dex-frontend/         # Vue 3 前端验收控制台
├── sql/                  # 初始化 SQL 与索引表结构
└── docs/                 # 设计与归档文档
```

### 2.1 设计原则

1. **先做少量真实闭环，再扩范围**
   - 不追求一开始支持很多币种/很多协议
   - 先把 ETH/USDC、ETH/DAI 做成真实可验证链路

2. **索引层与服务层分离**
   - UniV3 事件索引是一条独立链路
   - 价格/统计/报价服务可直接读主网状态，不强依赖索引任务完成

3. **宁可少，不造假**
   - 对无法真实稳定给出的指标（如当前 volume）明确标记 unsupported
   - 不再用 demo 表和伪估算冒充真实能力

4. **抗限流优先于绝对实时**
   - 主网 RPC 存在 429 限流风险
   - 对真实池与真实报价增加短 TTL 缓存和回退逻辑，保证接口稳定性

---

## 3. 阶段 0：工程底座

### 3.1 目标

建立一个可持续扩展的工程底座，使后续读链、索引、报价、前端验收都可在统一工程中演进。

### 3.2 已完成内容

- Maven 多模块结构
- Spring Boot API 服务入口
- Docker Compose 基础依赖：MySQL / Redis / Kafka / Zookeeper
- `actuator/health` 健康检查
- 初始化 SQL 与本地启动脚本
- 前端 Vue 3 + Vite 基础壳

### 3.3 设计说明

- `dex-api` 负责 HTTP 暴露与装配
- `dex-business` 负责编排业务语义，避免 controller 直接拼装链上逻辑
- `dex-infrastructure` 放 Web3j、链上 provider、UniV3 索引器、真实读池服务
- `dex-data` 同时承接索引数据表访问和实体定义

### 3.4 Review 要点

- 模块边界总体清晰
- 当前仍有部分历史文件名/类名沿用旧阶段命名，需要后续继续归一
- 但不影响阶段 0 功能成立

---

## 4. 阶段 1：单链读链与原始同步

### 4.1 目标

建立 Ethereum Mainnet 的稳定读链能力，并能获取原始区块、池状态、链上调用结果。

### 4.2 已完成内容

- Mainnet Web3j 接入
- 最新区块读取
- 主网池状态读取（slot0 / liquidity / fee）
- Quoter 调用
- 应用层对 ETH 输入统一归一为 WETH

### 4.3 关键实现

核心类：

- `MainnetWeb3jConfig`
- `UniV3RealPoolService`
- `BlockchainProvider` / 相关主网 provider

### 4.4 关键数据流

```text
HTTP Request
  -> Price / Route / Liquidity Service
    -> UniV3RealPoolService
      -> Web3j eth_call / eth_getBlockByNumber
        -> Ethereum Mainnet RPC
```

### 4.5 稳定性设计

为避免连续请求触发 RPC 限流：

- 真实池快照：15s TTL 缓存
- 真实报价：15s TTL 缓存
- 当 RPC 429 / 临时失败时：优先回退最近成功结果

### 4.6 Review 结论

- 单链读链链路成立
- 当前读链范围 intentionally 小，但数据真实
- 已满足阶段 1 目标

---

## 5. 阶段 2：DEX 协议索引与标准化

### 5.1 目标

对 Uniswap V3 Pool 事件进行索引、标准化、入库，并保证同 pool 内顺序正确。

### 5.2 已完成内容

- UniV3 指定池事件抓取与入库
- checkpoint / 断点续传
- reorg window 回滚保护
- 标准化事件查询接口
- 同一 pool 内按 `block -> tx -> log` 顺序落库

### 5.3 关键实现

核心类/对象：

- `UniV3PoolIndexerService`
- `UniV3PoolService`
- `UniV3PoolEvent`
- `UniV3PoolEventRepository`
- `UniV3IndexerCheckpointRepository`

### 5.4 顺序保证

这是设计里的硬约束：

- 同 pool：严格串行，按 blockNumber / transactionIndex / logIndex 排序
- 不允许在同 pool 内乱序入库
- 为后续 volume、回放、事件审计提供基础可信性

### 5.5 当前边界

- 当前主要聚焦少量池索引
- 索引能力已存在，但阶段 3/4 不再强依赖索引成功才返回主网真实价格/报价
- 这是为了让“实时服务可用性”和“异步索引完整性”解耦

### 5.6 Review 结论

- 事件索引机制成立
- 顺序约束满足要求
- 阶段 2 达标

---

## 6. 阶段 3：派生指标与数据服务

### 6.1 目标

对外提供真实数据接口，而不是样例表拼接接口。

### 6.2 本次返工前的问题

原实现存在以下问题：

- `prices` / `liquidity_pools` / `routes` 依赖本地样例表
- 统计概览带有明显 demo 性质
- volume 用估算值假装真实值

### 6.3 本次返工后的设计

#### 价格服务

类：`PriceService`

- 不再读取样例价格表
- 改为从 `UniV3RealPoolService` 获取真实池快照
- 当前仅输出：
  - ETH-USDC
  - ETH-DAI

#### 流动性池服务

类：`LiquidityService`

- 不再读取 demo 池表
- 直接返回真实支持范围内的池快照
- 当前 2 个池：
  - Uniswap V3 USDC/WETH 0.05%
  - Uniswap V3 DAI/WETH 0.30%

#### 统计服务

类：`StatisticsService`

- `overview`：基于真实池快照计算概览
- `volume`：当前明确返回 unsupported

### 6.4 为什么 volume 暂时不做

真实 volume 需要：

- 完整交易事件聚合
- 时间窗统计
- 重放 / 补数
- 对事件丢失、重组、回补有明确定义

当前若仓促给值，极易再次退回 demo。故本阶段选择：

- **先移除假 volume**
- 等索引、补数、时间窗聚合链路完善后再开放

### 6.5 Review 结论

- 阶段 3 已从“样例表接口”升级为“真实池数据接口”
- 指标范围较小，但满足真实可用标准

---

## 7. 阶段 4：报价与路由引擎

### 7.1 目标

提供真实可验证的报价结果，并输出可 review 的路径元信息。

### 7.2 本次返工前的问题

原阶段 4 存在以下问题：

- 路由依赖本地样例池表
- gas 成本写死
- 路径比较属于 demo 评分
- 没有真实 quoter 调用

### 7.3 本次返工后的设计

类：`RouteService`

依赖：`UniV3RealPoolService.quoteExactInputSingle`

当前设计选择：

- 只做 **真实单池报价**
- 不做伪多跳路径搜索
- 支持少量交易对，但输出必须真实

### 7.4 输出字段

当前 `/api/v1/routes/quote` 返回：

- `path`
- `viable`
- `amountOut`
- `grossAmountOut`
- `gasCostUsd`
- `priceImpactPct`
- `netScore`
- `poolAddress`
- `poolName`
- `fee`
- `blockNumber`
- `blockTimestamp`
- `source`
- `dex`

### 7.5 ETH / WETH 处理

用户通常输入 ETH，但 Uniswap V3 池和 Quoter 实际使用 WETH。
因此在服务层做了统一归一：

- `ETH` -> `WETH`
- 对外展示仍保留 ETH 语义

### 7.6 当前支持范围

- ETH -> USDC
- ETH -> DAI
- WETH -> USDC
- WETH -> DAI

### 7.7 Review 结论

- 报价链路已真实化
- 当前不是“完整聚合器”，而是“真实单池报价器 + 可 review 元信息”
- 这是符合当前阶段目标的收敛版本

---

## 8. 前端设计（阶段 0-4 验收台）

### 8.1 目标

前端不是营销页面，而是交付验收控制台。

### 8.2 当前页面职责

- `Dashboard.vue`
  - 展示价格快照、阶段概览、真实池信息
- `Monitor.vue`
  - 展示阶段状态、overview、volume 边界
- `RouteDemo.vue`（保留文件名，但页面文案已去 demo 化）
  - 展示真实报价结果与候选路径信息
- `BlockchainDemo.vue`（保留文件名，但页面文案已去 demo 化）
  - 展示主链连接与监听状态
- `UniV3Explorer.vue`
  - 展示索引事件流、同步进度、事件统计

### 8.3 本次收口内容

- 去掉用户可见的 demo / 演示版口径
- 路由页面 token 列表收敛为当前真实支持范围
- 前端生产构建通过

---

## 9. 已知问题与技术债

### 9.1 仍然存在的限制

1. `gasCostUsd` 仍是简化估值，不是真实 gas oracle
2. `volume` 暂未开放
3. 报价当前仅支持单池 exactInputSingle
4. 当前真实支持池数量有限
5. `RouteDemo.vue` / `BlockchainDemo.vue` 文件名仍保留历史命名，后续可重命名

### 9.2 为什么现在不继续扩

因为当前目标是：

- 先把阶段 0-4 做到真实可 review
- 而不是边扩范围边引入新的假实现

---

## 10. 后续建议（阶段 5-6）

### 阶段 5

- WebSocket / SSE 实时推送
- 告警与监控
- 索引补数 / 回放能力
- volume 真实时间窗统计

### 阶段 6

- 多链抽象
- 多协议适配器
- 真正的多池多跳路由
- 可回放的历史报价/回测导出

---

## 11. Review 建议清单

review 时建议重点检查：

1. `UniV3RealPoolService` 是否职责过重，是否需要继续拆分
2. 真实池缓存 TTL 是否合理
3. RPC provider 的限流策略是否需要进一步平台化
4. `gasCostUsd` 是否应单独抽象为 gas service
5. 索引层与实时服务层是否需要更明确的契约边界
6. 前端页面文件名是否需要统一去历史命名

---

## 12. 最终结论

当前项目阶段 0-4 已达到如下状态：

- **不是 demo**
- **不是大而全**
- **而是少量交易对、真实主网数据、稳定可验收**

这是一个适合继续做 review 和下一阶段扩展的基线版本。

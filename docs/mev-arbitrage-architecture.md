# MEV / 套利系统架构图

这是一版偏工程落地的 `MEV / DEX 套利系统` 架构图，主线以：

- 原子套利
- Backrun 搜索

为主，不包含 sandwich。

## 架构图

```mermaid
flowchart TB
    subgraph S0["数据源与外部执行环境"]
        direction LR
        SRC1["自建执行节点\nReth / Geth"]
        SRC2["WebSocket 订阅\nnewHeads / logs / pendingTx"]
        SRC3["私有 Relay / Builder\nFlashbots 等"]
    end

    subgraph S1["实时接入与标准化"]
        direction LR
        ING1["区块订阅器"]
        ING2["Mempool 订阅器"]
        ING3["事件解码器\nSwap / Mint / Burn / Sync"]
    end

    subgraph S2["热状态与索引层"]
        direction LR
        ST1["池子白名单"]
        ST2["池状态缓存\nV2 reserve\nV3 slot0 / tick / liquidity"]
        ST3["Token / Path 索引"]
        ST4["本地价格与 Gas 快照"]
    end

    subgraph S3["机会发现层"]
        direction LR
        FIND1["候选生成器\n同对跨池 / 跨 DEX / 多跳闭环"]
        FIND2["粗筛器\n价差阈值 / 流动性 / 黑名单"]
        FIND3["路径搜索器\n2 跳 / 3 跳 / 闭环"]
    end

    subgraph S4["精确模拟与风控层"]
        direction LR
        SIM1["本地 EVM 模拟器\nrevm / fork"]
        SIM2["收益模型\ngross - fee - impact - gas - tip"]
        SIM3["风控检查\n滑点 / 库存 / 失败率 / 限额"]
    end

    subgraph S5["执行层"]
        direction LR
        EX1["Bundle 构造器"]
        EX2["私有提交器\nrelay / builder routing"]
        EX3["链上套利合约"]
    end

    subgraph S6["监控、复盘与研究层"]
        direction LR
        OBS1["机会日志"]
        OBS2["成交 / 失败 / 未命中统计"]
        OBS3["回放与 PnL 分析"]
        OBS4["Prometheus / Grafana"]
    end

    SRC1 --> SRC2
    SRC1 --> ING3
    SRC2 --> ING1
    SRC2 --> ING2
    ING1 --> ING3
    ING2 --> ING3

    ING3 --> ST2
    ST1 --> FIND1
    ST2 --> FIND1
    ST3 --> FIND3
    ST4 --> SIM2

    FIND1 --> FIND2
    FIND2 --> FIND3
    FIND3 --> SIM1
    ST2 --> SIM1

    SIM1 --> SIM2
    SIM2 --> SIM3
    SIM3 --> EX1
    EX1 --> EX2
    EX2 --> EX3
    SRC3 --> EX2

    FIND2 --> OBS1
    SIM2 --> OBS1
    SIM3 --> OBS2
    EX2 --> OBS2
    EX3 --> OBS2
    OBS1 --> OBS3
    OBS2 --> OBS3
    OBS2 --> OBS4
    OBS3 --> OBS4
```

## 阅读顺序

建议按下面顺序理解：

1. 从上往下看主链路：
   `数据源 -> 接入 -> 热状态 -> 搜索 -> 模拟 -> 执行`
2. 再看最底部的 `监控、复盘与研究层`
3. 最后再回头看两条侧向支撑关系：
   - `私有 Relay / Builder -> 执行层`
   - `热状态 / Gas 快照 -> 搜索与模拟`

## 核心数据流

1. 节点实时推送区块、日志和 pending 交易。
2. 接入层把原始事件标准化后，持续刷新热池状态。
3. 搜索层只在白名单池子和预建索引上生成候选机会。
4. 模拟层在接近目标区块状态的上下文里重放路径，计算真实净收益。
5. 只有通过收益阈值和风控阈值的候选机会才进入执行层。
6. 执行层通过私有 relay / builder 提交 bundle，避免公开 mempool 暴露。
7. 全流程写入日志、统计、回放与 PnL 分析，支撑后续调参与风控。

## 设计调整说明

- 主链路改为纵向分层，避免原图从左到右过长，在 Markdown 预览里更容易被压扁。
- 每一层内部统一用横向排布，让节点宽高比例更接近，减少某一列过高、某一列过窄的问题。
- 把 `监控、复盘与研究` 单独放到底部，避免它与交易主链混成同一条业务路径。
- 把 `私有 Relay / Builder` 视为外部执行环境，而不是内部处理步骤，关系更准确。

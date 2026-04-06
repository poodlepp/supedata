# DEX数据聚合平台 - 顶层架构设计

## 一、技术栈选型

### 核心框架版本
```
Java版本：JDK 21 LTS（长期支持，2026年9月前免费）
Spring Boot：3.3.x（最新稳定版，支持JDK 21）
Spring Cloud：2023.0.x（对应Spring Boot 3.3）
```

**为什么选这个版本组合：**
- JDK 21是LTS版本，生产环境稳定性最好
- Spring Boot 3.3.x是3.x系列最新稳定版，性能优化完善
- Spring Cloud 2023.0.x完全兼容，微服务治理成熟

### 依赖库版本
```
Web3j：4.11.x（最新稳定，支持最新EVM）
Kafka：3.7.x（消息队列，事件驱动）
Redis：7.2.x（缓存层）
MySQL：8.0.x（数据持久化）
Lombok：1.18.x（代码简化）
MapStruct：1.5.x（对象映射）
```

---

## 二、项目结构设计

### 多模块Maven项目结构
```
dex-aggregator/
├── pom.xml                          # 父POM，版本管理
├── dex-common/                      # 通用模块
│   ├── pom.xml
│   ├── src/main/java/
│   │   └── com/dex/common/
│   │       ├── constant/            # 常量定义
│   │       ├── exception/           # 异常类
│   │       ├── util/                # 工具类
│   │       ├── model/               # 通用DTO/VO
│   │       └── config/              # 通用配置
│   └── src/main/resources/
│
├── dex-blockchain/                  # 区块链交互模块
│   ├── pom.xml
│   ├── src/main/java/
│   │   └── com/dex/blockchain/
│   │       ├── config/              # Web3j配置
│   │       ├── service/             # 链交互服务
│   │       │   ├── Web3jService.java
│   │       │   ├── BlockchainService.java
│   │       │   └── EventListenerService.java
│   │       ├── client/              # RPC客户端
│   │       ├── contract/            # 合约交互
│   │       └── model/               # 区块链数据模型
│   └── src/main/resources/
│
├── dex-data/                        # 数据层模块
│   ├── pom.xml
│   ├── src/main/java/
│   │   └── com/dex/data/
│   │       ├── entity/              # JPA实体
│   │       ├── repository/          # 数据访问层
│   │       ├── service/             # 数据服务
│   │       │   ├── BlockScanService.java
│   │       │   ├── EventProcessService.java
│   │       │   └── DataCacheService.java
│   │       └── mapper/              # MyBatis/MapStruct映射
│   └── src/main/resources/
│       └── db/migration/            # Flyway数据库迁移
│
├── dex-business/                    # 业务逻辑模块
│   ├── pom.xml
│   ├── src/main/java/
│   │   └── com/dex/business/
│   │       ├── service/
│   │       │   ├── PriceService.java        # 价格计算
│   │       │   ├── RouteService.java        # 路由优化
│   │       │   ├── LiquidityService.java    # 流动性分析
│   │       │   └── StatisticsService.java   # 统计服务
│   │       ├── calculator/         # 计算引擎
│   │       ├── algorithm/          # 算法实现
│   │       └── model/              # 业务模型
│   └── src/main/resources/
│
├── dex-api/                         # API服务模块
│   ├── pom.xml
│   ├── src/main/java/
│   │   └── com/dex/api/
│   │       ├── controller/          # REST控制器
│   │       ├── websocket/           # WebSocket推送
│   │       ├── interceptor/         # 拦截器
│   │       ├── filter/              # 过滤器
│   │       └── advice/              # 全局异常处理
│   ├── src/main/resources/
│   │   ├── application.yml          # 主配置
│   │   ├── application-dev.yml      # 开发环境
│   │   ├── application-prod.yml     # 生产环境
│   │   └── logback-spring.xml       # 日志配置
│   └── src/test/java/
│
├── dex-scheduler/                   # 定时任务模块
│   ├── pom.xml
│   ├── src/main/java/
│   │   └── com/dex/scheduler/
│   │       ├── task/                # 定时任务
│   │       ├── config/              # 调度配置
│   │       └── listener/            # 任务监听
│   └── src/main/resources/
│
└── dex-monitor/                     # 监控模块
    ├── pom.xml
    ├── src/main/java/
    │   └── com/dex/monitor/
    │       ├── metrics/             # Prometheus指标
    │       ├── health/              # 健康检查
    │       └── config/              # 监控配置
    └── src/main/resources/
```

---

## 三、分层架构设计

```
┌─────────────────────────────────────────────────────┐
│                   API Layer                         │
│  REST Controller + WebSocket + GraphQL              │
├─────────────────────────────────────────────────────┤
│                 Service Layer                       │
│  PriceService │ RouteService │ StatisticsService   │
├─────────────────────────────────────────────────────┤
│              Business Logic Layer                   │
│  Calculator │ Algorithm │ EventProcessor            │
├─────────────────────────────────────────────────────┤
│                 Data Access Layer                   │
│  Repository │ Cache │ BlockchainClient             │
├─────────────────────────────────────────────────────┤
│              Infrastructure Layer                   │
│  Web3j │ Kafka │ Redis │ MySQL │ Prometheus        │
└─────────────────────────────────────────────────────┘
```

---

## 四、核心模块设计

### 1. dex-blockchain（区块链交互）
**职责**：Web3j集成、RPC调用、合约交互、事件监听

**关键类**：
- `Web3jConfig`：Web3j连接池配置
- `BlockchainService`：区块链基础服务
- `ContractService`：合约调用封装
- `EventListenerService`：事件监听和处理

**依赖**：
```xml
<dependency>
    <groupId>org.web3j</groupId>
    <artifactId>core</artifactId>
    <version>4.11.0</version>
</dependency>
```

### 2. dex-data（数据层）
**职责**：数据持久化、缓存管理、区块扫描

**关键类**：
- `BlockScanService`：区块扫描（增量同步、断点续传）
- `EventProcessService`：事件解析和存储
- `DataCacheService`：Redis缓存管理
- `Repository`：数据访问对象

**依赖**：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.33</version>
</dependency>
```

### 3. dex-business（业务逻辑）
**职责**：价格计算、路由优化、流动性分析、统计计算

**关键类**：
- `PriceCalculator`：AMM价格计算（基于Uniswap V2）
- `RouteOptimizer`：多跳路由搜索（图算法）
- `LiquidityAnalyzer`：TVL、APY计算
- `StatisticsCalculator`：交易量、成交额统计

**设计模式**：
- 策略模式：支持不同DEX的价格计算
- 工厂模式：创建不同类型的计算器

### 4. dex-api（API服务）
**职责**：REST API、WebSocket推送、请求处理

**关键端点**：
```
GET  /api/v1/prices/{pair}              # 获取交易对价格
GET  /api/v1/routes/best                # 获取最优路由
GET  /api/v1/liquidity/{pool}           # 获取流动性信息
GET  /api/v1/statistics/volume          # 获取交易量统计
WS   /ws/prices                         # WebSocket价格推送
```

### 5. dex-scheduler（定时任务）
**职责**：定时扫描、数据更新、缓存刷新

**关键任务**：
- 区块扫描任务（每12秒）
- 价格缓存更新（每30秒）
- 统计数据计算（每分钟）
- 健康检查（每5秒）

---

## 五、数据库设计（MySQL）

### 核心表结构
```sql
-- 区块表
CREATE TABLE blocks (
    id BIGINT PRIMARY KEY,
    block_number BIGINT UNIQUE,
    block_hash VARCHAR(255),
    timestamp BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 交易表
CREATE TABLE transactions (
    id BIGINT PRIMARY KEY,
    tx_hash VARCHAR(255) UNIQUE,
    block_number BIGINT,
    from_address VARCHAR(255),
    to_address VARCHAR(255),
    value DECIMAL(38,0),
    gas_price DECIMAL(38,0),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (block_number) REFERENCES blocks(block_number)
);

-- 事件表
CREATE TABLE events (
    id BIGINT PRIMARY KEY,
    event_type VARCHAR(100),
    contract_address VARCHAR(255),
    tx_hash VARCHAR(255),
    block_number BIGINT,
    log_index INT,
    data JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_contract_block (contract_address, block_number)
);

-- 交易对表
CREATE TABLE trading_pairs (
    id BIGINT PRIMARY KEY,
    pair_address VARCHAR(255) UNIQUE,
    token0_address VARCHAR(255),
    token1_address VARCHAR(255),
    reserve0 DECIMAL(38,0),
    reserve1 DECIMAL(38,0),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 价格缓存表
CREATE TABLE price_cache (
    id BIGINT PRIMARY KEY,
    pair_address VARCHAR(255),
    price DECIMAL(38,18),
    timestamp BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pair_time (pair_address, timestamp)
);

-- 扫描进度表
CREATE TABLE scan_progress (
    id INT PRIMARY KEY,
    chain_id INT,
    last_scanned_block BIGINT,
    last_confirmed_block BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

---

## 六、缓存策略（Redis）

```
Key设计：
- price:{pair_address}              # 交易对价格（TTL: 30s）
- route:{from}:{to}                 # 路由缓存（TTL: 5m）
- liquidity:{pool_address}          # 流动性信息（TTL: 1m）
- scan:progress:{chain_id}          # 扫描进度（持久化）
- statistics:volume:{pair}:{period} # 交易量统计（TTL: 1h）
```

---

## 七、消息队列设计（Kafka）

### Topic设计
```
blockchain-events          # 区块链事件流
├── Partition 0: Swap事件
├── Partition 1: Mint事件
└── Partition 2: Burn事件

price-updates             # 价格更新流
├── Partition 0: 实时价格
└── Partition 1: 历史价格

statistics-tasks          # 统计任务流
└── Partition 0: 统计计算任务
```

### 消费者组
```
event-processor-group     # 事件处理消费者
price-calculator-group    # 价格计算消费者
statistics-group          # 统计计算消费者
```

---

## 八、监控指标（Prometheus）

### 关键指标
```
# 区块扫描
dex_scan_block_height           # 当前扫描高度
dex_scan_lag_seconds            # 扫描延迟（秒）
dex_scan_errors_total           # 扫描错误总数

# RPC调用
dex_rpc_requests_total          # RPC请求总数
dex_rpc_latency_ms              # RPC延迟（毫秒）
dex_rpc_errors_total            # RPC错误总数

# 事件处理
dex_events_processed_total      # 处理事件总数
dex_events_processing_lag_ms    # 事件处理延迟

# 价格计算
dex_price_calculations_total    # 价格计算总数
dex_price_cache_hits_total      # 缓存命中数
dex_price_cache_misses_total    # 缓存未命中数

# 业务指标
dex_active_pairs_count          # 活跃交易对数
dex_tvl_usd                     # 总锁定价值
dex_volume_24h_usd              # 24小时交易量
```

---

## 九、配置管理

### application.yml 结构
```yaml
spring:
  application:
    name: dex-aggregator
  profiles:
    active: dev
  
  datasource:
    url: jdbc:mysql://localhost:3306/dex_db
    username: root
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        format_sql: true
  
  redis:
    host: localhost
    port: 6379
    timeout: 2000ms
    jedis:
      pool:
        max-active: 20
        max-idle: 10
  
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      acks: all
      retries: 3
    consumer:
      group-id: dex-aggregator
      auto-offset-reset: earliest

web3j:
  client-address: http://localhost:8545
  admin-client: false
  polling-interval: 15000

blockchain:
  chain-id: 1
  network: ethereum
  contracts:
    uniswap-v2-router: "0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D"
    uniswap-v2-factory: "0x5C69bEe701ef814a2B6a3EDD4B1652CB9cc5aA6f"

logging:
  level:
    root: INFO
    com.dex: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
```

---

## 十、部署架构

### Docker Compose 编排
```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: dex_db
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  redis:
    image: redis:7.2
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

  kafka:
    image: confluentinc/cp-kafka:7.7.0
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
    ports:
      - "9092:9092"
    depends_on:
      - zookeeper

  zookeeper:
    image: confluentinc/cp-zookeeper:7.7.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin

  dex-api:
    build: ./dex-api
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_PASSWORD: root
    depends_on:
      - mysql
      - redis
      - kafka
    restart: always

volumes:
  mysql_data:
  redis_data:
```

---

## 十一、开发流程

### 本地开发环境启动
```bash
# 1. 启动基础设施
docker-compose up -d

# 2. 创建数据库和表
mysql -u root -p < db/schema.sql

# 3. 启动应用
mvn clean install
mvn spring-boot:run -pl dex-api -Dspring-boot.run.arguments="--spring.profiles.active=dev"

# 4. 验证服务
curl http://localhost:8080/actuator/health
```

---

## 十二、关键设计决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| Java版本 | JDK 21 LTS | 长期支持，性能最优 |
| Spring Boot | 3.3.x | 最新稳定，GraalVM支持 |
| 数据库 | MySQL 8.0 | 成熟稳定，JSON支持 |
| 缓存 | Redis 7.2 | 高性能，支持Stream |
| 消息队列 | Kafka 3.7 | 高吞吐，事件驱动 |
| ORM | Spring Data JPA | 简化开发，性能可控 |
| 构建工具 | Maven | 企业级标准 |
| 容器化 | Docker | 一致性部署 |
| 监控 | Prometheus + Grafana | 开源成熟方案 |

---

## 十三、后续扩展方向

1. **多链支持**：抽象Chain接口，支持BSC、Polygon等
2. **GraphQL API**：补充REST API，提供灵活查询
3. **WebSocket推送**：实时价格、交易量更新
4. **链下订单簿**：支持限价单、聚合交易
5. **MEV检测**：识别三明治攻击、套利机会
6. **性能优化**：批量RPC、并行扫描、分片存储


# DEX Aggregator Platform

一个完整的 DEX 数据聚合平台，包含后端 API、数据处理模块和前端应用。

## 项目概述

DEX Aggregator 是一个用于实时价格展示、路由查询、流动性分析和统计监控的多模块项目。

## 技术栈

### 后端
- Java 21
- Spring Boot 3.3
- MySQL
- Redis
- Kafka
- Prometheus

### 前端
- Vue 3
- Vite
- Element Plus
- ECharts
- Axios

## 项目结构

```text
supedata/
├── dex-api/              # REST API 服务
├── dex-business/         # 业务逻辑
├── dex-common/           # 通用模块
├── dex-data/             # 数据层与链上数据处理
├── dex-infrastructure/   # 调度与监控
├── dex-frontend/         # Vue 前端应用
├── docker-compose.yml    # 本地依赖服务
├── init-db.sql           # 数据库初始化脚本
└── pom.xml               # Maven 父工程
```

## 快速启动

### 前置要求
- Java 21 JDK
- Maven 3.8+
- Node.js 18+
- Docker & Docker Compose

### 1. 启动基础设施

```bash
docker-compose up -d
```

### 2. 构建后端

```bash
mvn clean install -DskipTests
```

### 3. 启动后端 API

```bash
mvn spring-boot:run -pl dex-api -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

### 4. 启动前端

```bash
cd dex-frontend
npm install
npm run dev
```

### 5. 访问地址
- 前端：`http://localhost:5173`
- 后端：`http://localhost:8080`
- 健康检查：`http://localhost:8080/actuator/health`

## 常用接口

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/prices/ETH-USDC
curl http://localhost:8080/api/v1/liquidity/pools
curl "http://localhost:8080/api/v1/routes/best?from=ETH&to=USDC"
```

## 保留文档

- `README.md`：项目总览与启动方式
- `dex-aggregator-architecture.md`：系统架构设计说明

## 架构说明

详细架构设计请查看 `dex-aggregator-architecture.md`。

# DEX Aggregator Backend Practice Project

[![Language: English](https://img.shields.io/badge/Language-English-0A66C2)](./README.md)
[![语言：中文](https://img.shields.io/badge/语言-中文-0A66C2)](./README.zh-CN.md)

A staged DEX backend practice project focused on building a real, explainable data and routing pipeline instead of a mock demo. The repository currently delivers a working Stage 0-5 loop: indexing, derived data, routing, realtime ops, and a production-style local monitoring stack.

## At a Glance

- **Chain**: Ethereum Mainnet
- **Protocol**: Uniswap V3
- **Pairs in scope**: `ETH/USDC`, `ETH/DAI`, `DAI/USDC`
- **Pricing source**: real mainnet pool state
- **Quote source**: Uniswap V3 Quoter
- **Frontend**: Vue 3 verification console
- **Backend**: Spring Boot multi-module service
- **Ops stack**: Prometheus, Grafana, Alertmanager, Thanos, MinIO

## What This Project Is

- A backend-focused DEX practice project built around real mainnet-backed data
- A staged learning repo that emphasizes indexing, serving, routing, replay, and observability
- A system that tries to make route selection and operational state explainable

## What This Project Is Not

- Not a full-featured production aggregator
- Not a multi-chain or multi-protocol platform yet
- Not a live trading bot or MEV executor
- Not a fake dashboard driven by fabricated metrics

## Why This Project Exists

This project is designed to practice the core knowledge areas behind a DEX data backend:

- **On-chain ingestion**: blocks, logs, checkpoints, reorg window handling
- **Protocol normalization**: turning raw Uniswap V3 events into stable internal models
- **Serving layer design**: prices, liquidity views, statistics, route snapshots
- **Routing and quoting**: multi-candidate search, gas/slippage-aware ranking, explainable route selection
- **Realtime operations**: SSE, replay, monitoring, alerting, observability

## Current Stage Coverage

### Completed

- **Stage 0. Engineering foundation**
  - Maven multi-module structure
  - Docker Compose base dependencies
  - health endpoints
  - bootstrap SQL and startup scripts
- **Stage 1. Single-chain access and raw sync**
  - Ethereum mainnet connectivity checks
  - latest block access
  - real pool state reads
- **Stage 2. DEX protocol indexing and normalization**
  - Uniswap V3 pool event indexing
  - checkpoint and reorg window
  - deterministic event ordering by `block -> tx -> log`
- **Stage 3. Derived metrics and data services**
  - real price APIs
  - liquidity pool APIs
  - statistics overview APIs
  - removed fake volume output
- **Stage 4. Quote and routing engine**
  - route quote and compare APIs
  - layered beam-search candidate expansion
  - gas, fee, price impact, freshness, split-route comparison
- **Stage 5. Realtime ops and monitoring**
  - ops overview, SSE price stream, replay entry
  - Prometheus metrics
  - Grafana dashboards
  - Alertmanager and Thanos local topology

### Intentionally Not in Scope Yet

- multi-chain support
- multi-protocol plugin architecture
- historical backtesting exports
- production-grade HA deployment
- live MEV execution

## Product Surfaces

The repository is easier to understand if you view it as three connected product surfaces:

- **Data surface**
  - prices, liquidity pools, statistics overview
- **Routing surface**
  - quote, compare, split-route analysis, route explanation fields
- **Ops surface**
  - overview snapshots, SSE streaming, replay, monitoring dashboards

## Knowledge Map

| Domain | Practiced in this repo |
|---|---|
| Blockchain backend basics | block scanning, RPC integration, event ingestion |
| Data modeling | normalized pool/event models, serving-layer snapshots |
| Search and ranking | route candidate generation, pruning, scoring |
| Realtime systems | Kafka, SSE, scheduled refresh, replay |
| Backend engineering | caching, API layering, tests, replay safety |
| Observability | metrics, alerts, dashboards, long-term metrics topology |

## Repository Structure

```text
supedata/
├── dex-api/                 # REST controllers, actuator, ops endpoints
├── dex-business/            # routing, pricing, statistics, domain services
├── dex-common/              # shared models and utilities
├── dex-data/                # data access layer
├── dex-infrastructure/      # blockchain clients, scheduler, monitoring, Kafka
├── dex-frontend/            # Vue verification console
├── monitoring/              # Prometheus / Grafana / Thanos / Alertmanager config
├── sql/                     # SQL initialization scripts
├── docker-compose.yml
├── init-db.sql
└── dex-aggregator-architecture.md
```

## Frontend Pages

- **Dashboard**
  - statistics overview, stage summary, retained metric boundaries, Stage 3 candle view
- **Route Demo**
  - quote comparison, score breakdown, split-route comparison, elimination reasons
- **Monitor**
  - realtime ops overview, SSE snapshot, replay history, monitoring stack entry points

## Quick Start

### 1. Start infrastructure

```bash
docker compose up -d
```

### 2. Initialize Uniswap V3 tables if needed

```bash
docker exec -i dex-mysql mysql -uroot -proot dex_db < sql/univ3_indexer_init.sql
```

### 3. Start backend

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -pl dex-api spring-boot:run
```

### 4. Start frontend

```bash
cd dex-frontend
npm install
npm run dev -- --host 127.0.0.1 --port 5173
```

## Core APIs

### Data APIs

- `GET /actuator/health`
- `GET /api/v1/prices`
- `GET /api/v1/liquidity/pools`
- `GET /api/v1/statistics/overview`

### Routing APIs

- `GET /api/v1/routes/quote?from=ETH&to=USDC&amountIn=1`
- `GET /api/v1/routes/compare?from=ETH&to=USDC&amountIn=1`

### Ops APIs

- `GET /api/v1/ops/overview`
- `GET /api/v1/ops/stream/prices`
- `POST /api/v1/ops/replay?fromBlock=...`

### Monitoring Endpoints

- `GET /actuator/prometheus`
- Prometheus UI: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Alertmanager: `http://localhost:9093`
- Thanos Query: `http://localhost:10903`
- MinIO Console: `http://localhost:9001`

## Monitoring Stack

The monitoring setup follows a **production-style local topology**:

- **Spring Boot**
  - exposes metrics through `/actuator/prometheus`
- **Prometheus**
  - scrapes application metrics
  - evaluates alert rules
  - stores short-to-mid-term local TSDB data
- **Alertmanager**
  - groups, suppresses, and routes alerts
- **Thanos Sidecar / Store / Query / Compactor**
  - provide a unified query path and long-term metrics architecture
- **MinIO**
  - acts as the local S3-compatible object storage for Thanos blocks
- **Grafana**
  - visualizes Prometheus/Thanos data through dashboards and Explore

This is **not** a true HA production cluster. It is a single-node topology designed to expose the right architectural boundaries.

## What Makes It Interesting

- Real mainnet-backed pricing and quoting instead of fabricated demo data
- Explainable route ranking with gas, fee, split-route, and freshness considerations
- Replay and ops surfaces that move the project beyond a basic CRUD/indexer demo
- Monitoring architecture that separates metric production, scraping, alerting, storage, and visualization

## Architecture Entry Points

- High-level staged design:
  - [dex-aggregator-architecture.md](./dex-aggregator-architecture.md)
- MEV / arbitrage system sketch:
  - [docs/mev-arbitrage-architecture.md](./docs/mev-arbitrage-architecture.md)

## Architecture Document

For the staged design and implementation details, see:

- [dex-aggregator-architecture.md](./dex-aggregator-architecture.md)

## Practical Notes

- The current scope is intentionally narrow: a few real trading pairs with real mainnet data
- Real pool and quote reads use short TTL caching to reduce RPC pressure
- Fake `volume` output is intentionally excluded
- Prometheus scrapes the backend from Docker through `http://host.docker.internal:8080/actuator/prometheus`

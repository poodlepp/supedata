#!/usr/bin/env bash
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8080}"
WEB_BASE="${WEB_BASE:-http://localhost:5173}"

echo "[1/6] Health"
curl -fsS "$API_BASE/actuator/health" | python3 -m json.tool

echo "[2/6] Prices"
curl -fsS "$API_BASE/api/v1/prices" | python3 -m json.tool

echo "[3/6] Pools"
curl -fsS "$API_BASE/api/v1/liquidity/pools" | python3 -m json.tool

echo "[4/6] Stage progress"
curl -fsS "$API_BASE/api/v1/stages/progress" | python3 -m json.tool

echo "[5/6] Route quote"
curl -fsS "$API_BASE/api/v1/routes/quote?from=ETH&to=USDC&amountIn=1" | python3 -m json.tool

echo "[6/6] Frontend entry"
curl -fsS "$WEB_BASE" | head -20

echo "Verification complete."

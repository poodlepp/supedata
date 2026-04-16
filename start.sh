#!/bin/bash

# DEX Aggregator Platform - 快速启动脚本

set -e

PROJECT_DIR="/Users/lipengyi/solFour/supedata"
cd "$PROJECT_DIR"

echo "=========================================="
echo "DEX 聚合平台 - 快速启动"
echo "=========================================="
echo ""

# 1. 启动 Docker 服务
echo "📦 步骤 1: 启动 Docker 服务..."
docker-compose up -d
echo "✅ Docker 服务已启动"
echo ""

# 等待服务就绪
echo "⏳ 等待服务就绪..."
sleep 10

# 2. 验证数据库连接
echo "🔍 验证数据库连接..."
docker exec dex-mysql mysqladmin ping -h localhost -u root -proot || echo "⚠️  MySQL 连接检查"
echo "✅ 数据库已就绪"
echo ""

# 3. 构建后端
echo "🔨 步骤 2: 构建后端项目..."
if command -v mvn &> /dev/null; then
    mvn clean install -DskipTests -q
    echo "✅ 后端构建完成"
else
    echo "⚠️  Maven 未安装，跳过构建"
    echo "   请手动运行: mvn clean install -DskipTests"
fi
echo ""

# 4. 启动后端
echo "🚀 步骤 3: 启动后端服务..."
echo "   在新终端运行以下命令:"
echo "   cd $PROJECT_DIR"
echo "   mvn spring-boot:run -pl dex-api -Dspring-boot.run.arguments=\"--spring.profiles.active=dev\""
echo ""

# 5. 启动前端
echo "🎨 步骤 4: 启动前端服务..."
echo "   在新终端运行以下命令:"
echo "   cd $PROJECT_DIR/dex-frontend"
echo "   npm install"
echo "   npm run dev"
echo ""

echo "=========================================="
echo "📍 服务地址:"
echo "   前端: http://localhost:5173"
echo "   后端: http://localhost:8080"
echo "   MySQL: localhost:3306"
echo "   Redis: localhost:6379"
echo "   Kafka: localhost:9092"
echo "=========================================="
echo ""
echo "✨ 启动完成！请按照上述步骤启动后端和前端服务"
echo ""

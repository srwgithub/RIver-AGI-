#!/bin/bash

echo "========================================"
echo "      RIver AGI 启动脚本"
echo "========================================"

# 检查 Java 版本
echo "1. 检查 Java 环境..."
java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
echo "   Java 版本: $java_version"

# 检查 Maven 版本
echo "2. 检查 Maven 环境..."
mvn_version=$(mvn -v 2>&1 | head -n 1)
echo "   Maven: $mvn_version"

# 检查 Docker 是否运行
echo "3. 检查 Docker 服务..."
if docker info > /dev/null 2>&1; then
    echo "   Docker: 运行中"
else
    echo "   Docker: 未运行，请先启动 Docker"
    exit 1
fi

# 启动依赖服务
echo ""
echo "4. 启动 Docker Compose 依赖服务..."
cd backend
docker-compose up -d

echo ""
echo "5. 等待依赖服务启动..."
sleep 10

# 编译后端项目
echo ""
echo "6. 编译后端项目..."
mvn clean compile -q

if [ $? -eq 0 ]; then
    echo "   编译成功!"
else
    echo "   编译失败!"
    exit 1
fi

# 运行后端
echo ""
echo "7. 启动后端服务..."
nohup mvn spring-boot:run > ../backend.log 2>&1 &
BACKEND_PID=$!
echo "   后端 PID: $BACKEND_PID"
echo "   日志文件: ../backend.log"

# 等待后端启动
echo ""
echo "8. 等待后端服务启动..."
sleep 20

# 启动前端
echo ""
echo "9. 启动前端服务..."
cd ../frontend
npm install -q

nohup npm run dev > ../frontend.log 2>&1 &
FRONTEND_PID=$!
echo "   前端 PID: $FRONTEND_PID"
echo "   日志文件: ../frontend.log"

echo ""
echo "========================================"
echo "      RIver AGI 启动完成!"
echo "========================================"
echo ""
echo "后端服务: http://localhost:8080"
echo "前端服务: http://localhost:3000"
echo "API文档:  http://localhost:8080/swagger-ui.html"
echo ""
echo "默认账号: admin / admin123"
echo ""
echo "停止服务命令:"
echo "  kill $BACKEND_PID"
echo "  kill $FRONTEND_PID"
echo "  cd backend && docker-compose down"
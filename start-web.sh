#!/bin/bash

# 财务回收对账系统 - Web界面启动脚本

echo "========================================"
echo "  财务回收对账系统 - Web界面"
echo "========================================"
echo ""

# 检查是否安装了Maven
if ! command -v mvn &> /dev/null
then
    echo "❌ 未检测到Maven，请先安装Maven"
    echo "   访问 https://maven.apache.org/download.cgi"
    exit 1
fi

echo "✓ 检测到Maven"
echo ""

# 检查Java版本
if ! command -v java &> /dev/null
then
    echo "❌ 未检测到Java，请先安装Java 17+"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Java版本过低（当前: $JAVA_VERSION），需要Java 17+"
    exit 1
fi

echo "✓ 检测到Java $JAVA_VERSION"
echo ""

# 编译项目
echo "📦 正在编译项目..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ 编译失败，请检查错误信息"
    exit 1
fi

echo "✓ 编译完成"
echo ""

# 启动Web服务
echo "🚀 正在启动Web服务..."
echo "   访问地址: http://localhost:8080"
echo "   按 Ctrl+C 可停止服务"
echo ""
echo "========================================"
echo ""

mvn spring-boot:run -Dspring-boot.run.mainClass="com.company.recycle.web.WebApplication"

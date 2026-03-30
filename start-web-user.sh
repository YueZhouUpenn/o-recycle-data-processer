#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

JAR="o-recycle-data-processer-1.0.0.jar"
if [[ ! -f "$JAR" ]]; then
  echo "[错误] 未找到 $JAR，请将本脚本与打好的 jar 放在同一目录。"
  exit 1
fi

JAVA_BIN="java"
if [[ -x "./jre/bin/java" ]]; then
  JAVA_BIN="./jre/bin/java"
fi

echo "========================================"
echo "  财务回收对账系统 - Web 界面"
echo "========================================"
echo ""
echo "正在启动，请稍候..."
echo "看到 Started WebApplication 后，浏览器打开: http://localhost:8080"
echo "按 Ctrl+C 停止服务。"
echo ""

exec "$JAVA_BIN" -cp "$JAR" com.company.recycle.web.WebApplication

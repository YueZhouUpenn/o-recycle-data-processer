@echo off
chcp 65001 >nul
setlocal

echo ========================================
echo   财务回收对账系统 - Web界面
echo ========================================
echo.

REM 检查Maven
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ❌ 未检测到Maven，请先安装Maven
    echo    访问 https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

echo ✓ 检测到Maven
echo.

REM 检查Java
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ❌ 未检测到Java，请先安装Java 17+
    pause
    exit /b 1
)

echo ✓ 检测到Java
echo.

REM 编译项目
echo 📦 正在编译项目...
call mvn clean compile -q

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ❌ 编译失败，请检查错误信息
    pause
    exit /b 1
)

echo ✓ 编译完成
echo.

REM 启动Web服务
echo 🚀 正在启动Web服务...
echo    访问地址: http://localhost:8080
echo    按 Ctrl+C 可停止服务
echo.
echo ========================================
echo.

call mvn spring-boot:run -Dspring-boot.run.mainClass="com.company.recycle.web.WebApplication"

pause

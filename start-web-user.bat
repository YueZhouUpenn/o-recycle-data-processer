@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

REM 优先使用同目录下的便携 JRE（若已放入 jre 文件夹），否则使用系统已安装的 Java
set "JAVA_EXE=java"
if exist "%~dp0jre\bin\java.exe" set "JAVA_EXE=%~dp0jre\bin\java.exe"

set "JAR=o-recycle-data-processer-1.0.0.jar"
if not exist "%~dp0%JAR%" (
    echo [错误] 未找到 %JAR%
    echo 请将本脚本与打好的 jar 放在同一文件夹后再运行。
    pause
    exit /b 1
)

echo ========================================
echo   财务回收对账系统 - Web 界面
echo ========================================
echo.
echo 正在启动，请稍候...
echo 看到 "Started WebApplication" 后，用浏览器打开: http://localhost:8080
echo 关闭本窗口将停止服务。
echo.

"%JAVA_EXE%" -cp "%~dp0%JAR%" com.company.recycle.web.WebApplication
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [错误] 启动失败。若提示找不到 java，请安装 Java 17，或将便携 JRE 解压到本目录下的 jre 文件夹。
    pause
)

@echo off
REM AI Model Proxy Forward - Startup Script
echo ========================================
echo   AI Model Proxy Forward
echo ========================================
echo.
java -version >nul 2>&1
if errorlevel 1 (
    echo Error: Java not found. Please install JDK 17+
    pause
    exit /b 1
)
if "%1"=="" (
    echo Usage: start.bat server^|agent [config]
    echo Example: start.bat server config\server.yaml
    pause
    exit /b 1
)
set SCRIPT_DIR=%~dp0
set CONFIG_DIR=%SCRIPT_DIR%config
set LIBS_DIR=%SCRIPT_DIR%libs
if "%1"=="server" (
    echo Starting server...
    if "%2"=="" (
        java -jar "%LIBS_DIR%\forward-server-*.jar" --config "%CONFIG_DIR%\server.yaml"
    ) else (
        java -jar "%LIBS_DIR%\forward-server-*.jar" --config "%2"
    )
) else if "%1"=="agent" (
    echo Starting agent...
    if "%2"=="" (
        java -jar "%LIBS_DIR%\forward-agent-*.jar" --config "%CONFIG_DIR%\agent.yaml"
    ) else (
        java -jar "%LIBS_DIR%\forward-agent-*.jar" --config "%2"
    )
)
pause
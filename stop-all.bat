@echo off
title Money Transfer Platform - Stop All
echo ============================================
echo   Stopping all services...
echo ============================================

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8761 :8080 :8081 :8082 :8083 :8084 :8085 :8086 :8087" ^| findstr "LISTENING"') do (
    echo Killing PID %%a
    taskkill /PID %%a /F >nul 2>&1
)

echo.
echo   All services stopped.
echo ============================================
pause

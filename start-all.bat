@echo off
title Money Transfer Platform - Launcher
echo ============================================
echo   Money Transfer Platform - Start All
echo ============================================
echo.

echo [1/9] Starting Eureka Server (port 8761)...
start "Eureka Server - 8761" cmd /k "cd /d %~dp0 && call gradlew.bat :eureka-server:bootRun"
timeout /t 10 /nobreak > nul

echo [2/9] Starting API Gateway (port 8080)...
start "API Gateway - 8080" cmd /k "cd /d %~dp0 && call gradlew.bat :api-gateway:bootRun"
timeout /t 3 /nobreak > nul

echo [3/9] Starting Auth Service (port 8081)...
start "Auth Service - 8081" cmd /k "cd /d %~dp0 && call gradlew.bat :auth-service:bootRun"
timeout /t 3 /nobreak > nul

echo [4/9] Starting Account Service (port 8082)...
start "Account Service - 8082" cmd /k "cd /d %~dp0 && call gradlew.bat :account-service:bootRun"
timeout /t 3 /nobreak > nul

echo [5/9] Starting KYC Service (port 8083)...
start "KYC Service - 8083" cmd /k "cd /d %~dp0 && call gradlew.bat :kyc-service:bootRun"
timeout /t 3 /nobreak > nul

echo [6/9] Starting Limit Service (port 8084)...
start "Limit Service - 8084" cmd /k "cd /d %~dp0 && call gradlew.bat :limit-service:bootRun"
timeout /t 3 /nobreak > nul

echo [7/9] Starting Transaction Service (port 8085)...
start "Transaction Service - 8085" cmd /k "cd /d %~dp0 && call gradlew.bat :transaction-service:bootRun"
timeout /t 3 /nobreak > nul

echo [8/9] Starting Internal Transfer Service (port 8086)...
start "Internal Transfer Service - 8086" cmd /k "cd /d %~dp0 && call gradlew.bat :internal-transfer-service:bootRun"
timeout /t 3 /nobreak > nul

echo [9/9] Starting External Transfer Service (port 8087)...
start "External Transfer Service - 8087" cmd /k "cd /d %~dp0 && call gradlew.bat :external-transfer-service:bootRun"

echo.
echo ============================================
echo   All services launched!
echo ============================================
echo.
echo   Eureka Dashboard: http://localhost:8761
echo   API Gateway:      http://localhost:8080
echo.
echo   Close this window anytime.
echo   To stop all services, run stop-all.bat
echo ============================================
pause

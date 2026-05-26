@echo off
title Money Transfer Platform - Launcher
echo ============================================
echo   Money Transfer Platform - Start All
echo ============================================
echo.



echo [3/9] Starting Auth Service (port 8081)...
start "Auth Service - 8081" cmd /k "cd /d %~dp0auth-service && mvn spring-boot:run"
timeout /t 3 /nobreak > nul

echo [4/9] Starting Account Service (port 8082)...
start "Account Service - 8082" cmd /k "cd /d %~dp0account-service && mvn spring-boot:run"
timeout /t 3 /nobreak > nul

echo [5/9] Starting KYC Service (port 8083)...
start "KYC Service - 8083" cmd /k "cd /d %~dp0kyc-service && mvn spring-boot:run"
timeout /t 3 /nobreak > nul

echo [6/9] Starting Limit Service (port 8084)...
start "Limit Service - 8084" cmd /k "cd /d %~dp0limit-service && mvn spring-boot:run"
timeout /t 3 /nobreak > nul

echo [7/9] Starting Transaction Service (port 8085)...
start "Transaction Service - 8085" cmd /k "cd /d %~dp0transaction-service && mvn spring-boot:run"
timeout /t 3 /nobreak > nul

echo [8/9] Starting Internal Transfer Service (port 8086)...
start "Internal Transfer Service - 8086" cmd /k "cd /d %~dp0internal-transfer-service && mvn spring-boot:run"
timeout /t 3 /nobreak > nul

echo [9/9] Starting External Transfer Service (port 8087)...
start "External Transfer Service - 8087" cmd /k "cd /d %~dp0external-transfer-service && mvn spring-boot:run"

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

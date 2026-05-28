# ============================================================
# Money Transfer Platform - SAGA DEMO Deployment Script
# Minimal stack to demo event-driven transfer saga
# ============================================================

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "============================================" -ForegroundColor Cyan
Write-Host " Money Transfer - Saga Demo Deployment" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# ----------------------------------------------------------
# Step 1: Build Gradle (only modules needed for demo)
# ----------------------------------------------------------
Write-Host "`n[1/4] Building Gradle modules..." -ForegroundColor Yellow
.\gradlew.bat clean bootJar -x test `
    -x :onboarding-service:bootJar `
    -x :internal-transfer-service:bootJar `
    -x :external-transfer-service:bootJar `
    -x :napas-simulator:bootJar
if ($LASTEXITCODE -ne 0) { Write-Host "Gradle build failed!" -ForegroundColor Red; exit 1 }
Write-Host "Gradle build completed." -ForegroundColor Green

# ----------------------------------------------------------
# Step 2: Build Docker images
# ----------------------------------------------------------
Write-Host "`n[2/4] Building Docker images..." -ForegroundColor Yellow

$demoServices = @(
    @{ name = "eureka-server";       port = 8761 },
    @{ name = "auth-service";        port = 8081 },
    @{ name = "account-service";     port = 8082 },
    @{ name = "kyc-service";         port = 8083 },
    @{ name = "limit-service";       port = 8084 },
    @{ name = "transaction-service"; port = 8085 },
    @{ name = "api-gateway";         port = 8080 }
)

foreach ($svc in $demoServices) {
    Write-Host "  Building $($svc.name)..." -ForegroundColor Gray
    docker build -f Dockerfile-demo --build-arg SERVICE_NAME=$($svc.name) --build-arg SERVICE_PORT=$($svc.port) -t "$($svc.name):latest" .
    if ($LASTEXITCODE -ne 0) { Write-Host "Docker build failed for $($svc.name)!" -ForegroundColor Red; exit 1 }
}

Write-Host "  Building money-transfer-web..." -ForegroundColor Gray
docker build -t money-transfer-web:latest ./money-transfer-web
if ($LASTEXITCODE -ne 0) { Write-Host "Frontend Docker build failed!" -ForegroundColor Red; exit 1 }

Write-Host "All images built." -ForegroundColor Green

# ----------------------------------------------------------
# Step 3: Start demo stack
# ----------------------------------------------------------
Write-Host "`n[3/4] Starting demo stack..." -ForegroundColor Yellow
docker compose -f docker-compose-demo.yml up -d
if ($LASTEXITCODE -ne 0) { Write-Host "docker compose up failed!" -ForegroundColor Red; exit 1 }
Write-Host "Containers started." -ForegroundColor Green

# ----------------------------------------------------------
# Step 4: Summary
# ----------------------------------------------------------
Write-Host "`n[4/4] Stack is starting up..." -ForegroundColor Yellow
Write-Host "      Keycloak ~60s, Java services ~30-60s after Keycloak" -ForegroundColor Gray
Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host " Demo Stack Ready!" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "-- URLs -------------------------------------" -ForegroundColor Cyan
Write-Host "Frontend:        http://localhost:3000" -ForegroundColor White
Write-Host "Keycloak Admin:  http://localhost:9090  (admin/admin)" -ForegroundColor White
Write-Host ""
Write-Host "-- Demo Accounts ----------------------------" -ForegroundColor Cyan
Write-Host "Sender:    1000000001  /  password: 123456" -ForegroundColor White
Write-Host "Receiver:  1000000002  /  password: 123456" -ForegroundColor White
Write-Host ""
Write-Host "-- Saga Rollback Test -----------------------" -ForegroundColor Cyan
Write-Host "Set receiver account = FORCE_FAIL" -ForegroundColor White
Write-Host "  => Saga runs compensation: sender gets refunded" -ForegroundColor White
Write-Host ""
Write-Host "-- Management -------------------------------" -ForegroundColor Gray
Write-Host "  Logs:      docker compose -f docker-compose-demo.yml logs -f" -ForegroundColor Gray
Write-Host "  Stop:      docker compose -f docker-compose-demo.yml down" -ForegroundColor Gray
Write-Host "  Reset data: docker compose -f docker-compose-demo.yml down -v" -ForegroundColor Gray

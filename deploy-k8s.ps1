# ============================================================
# Money Transfer Platform - Kubernetes Deployment Script
# Runtime: Docker Desktop Kubernetes
# ============================================================

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "============================================" -ForegroundColor Cyan
Write-Host " Money Transfer Platform - K8s Deployment" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# ----------------------------------------------------------
# Step 1: Build all Gradle modules
# ----------------------------------------------------------
Write-Host "`n[1/8] Building Gradle modules..." -ForegroundColor Yellow
.\gradlew.bat clean bootJar
if ($LASTEXITCODE -ne 0) { Write-Host "Gradle build failed!" -ForegroundColor Red; exit 1 }
Write-Host "Gradle build completed." -ForegroundColor Green

# ----------------------------------------------------------
# Step 2: Build Docker images for all 7 services
# ----------------------------------------------------------
Write-Host "`n[2/8] Building Docker images..." -ForegroundColor Yellow

$services = @(
    @{ name = "auth-service";              port = 8081 },
    @{ name = "account-service";           port = 8082 },
    @{ name = "kyc-service";               port = 8083 },
    @{ name = "limit-service";             port = 8084 },
    @{ name = "transaction-service";       port = 8085 },
    @{ name = "internal-transfer-service"; port = 8086 },
    @{ name = "external-transfer-service"; port = 8087 }
)

foreach ($svc in $services) {
    Write-Host "  Building $($svc.name)..." -ForegroundColor Gray
    docker build --build-arg SERVICE_NAME=$($svc.name) --build-arg SERVICE_PORT=$($svc.port) -t "$($svc.name):latest" .
    if ($LASTEXITCODE -ne 0) { Write-Host "Docker build failed for $($svc.name)!" -ForegroundColor Red; exit 1 }
}
Write-Host "All Docker images built." -ForegroundColor Green

# ----------------------------------------------------------
# Step 3: Create namespace
# ----------------------------------------------------------
Write-Host "`n[3/8] Creating namespace..." -ForegroundColor Yellow
kubectl apply -f k8s/namespace.yml
Write-Host "Namespace created." -ForegroundColor Green

# ----------------------------------------------------------
# Step 4: Deploy PostgreSQL
# ----------------------------------------------------------
Write-Host "`n[4/8] Deploying PostgreSQL..." -ForegroundColor Yellow
kubectl apply -f k8s/postgres/
Write-Host "PostgreSQL manifests applied." -ForegroundColor Green

# ----------------------------------------------------------
# Step 5: Wait for PostgreSQL to be ready
# ----------------------------------------------------------
Write-Host "`n[5/8] Waiting for PostgreSQL to be ready..." -ForegroundColor Yellow
kubectl wait --for=condition=ready pod -l app=postgresql -n money-transfer --timeout=120s
Write-Host "PostgreSQL is ready." -ForegroundColor Green

# ----------------------------------------------------------
# Step 6: Deploy all microservices
# ----------------------------------------------------------
Write-Host "`n[6/8] Deploying microservices..." -ForegroundColor Yellow
kubectl apply -f k8s/services/
Write-Host "Microservice manifests applied." -ForegroundColor Green

# ----------------------------------------------------------
# Step 7: Deploy Kong Gateway
# ----------------------------------------------------------
Write-Host "`n[7/8] Deploying Kong Gateway..." -ForegroundColor Yellow
kubectl apply -f k8s/kong/
Write-Host "Kong Gateway manifests applied." -ForegroundColor Green

# ----------------------------------------------------------
# Step 8: Wait for all pods to be ready
# ----------------------------------------------------------
Write-Host "`n[8/8] Waiting for all pods to be ready (timeout 5min)..." -ForegroundColor Yellow
kubectl wait --for=condition=ready pod --all -n money-transfer --timeout=300s
Write-Host "All pods are ready!" -ForegroundColor Green

# ----------------------------------------------------------
# Summary
# ----------------------------------------------------------
Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host " Deployment Complete!" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
kubectl get pods -n money-transfer
Write-Host ""
Write-Host "Kong Proxy:  http://localhost:30000" -ForegroundColor White
Write-Host "Kong Admin:  http://localhost:30001" -ForegroundColor White
Write-Host ""
Write-Host "Test: curl -X POST http://localhost:30000/api/v1/auth/login -H 'Content-Type: application/json' -d '{""username"":""nguyen.vana"",""password"":""123456""}'" -ForegroundColor Gray

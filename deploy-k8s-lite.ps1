# ============================================================
# Money Transfer Platform - Lite K8s Test (kind)
# Deploys: PostgreSQL + account-service + Kong
# Runtime: kind (Kubernetes IN Docker) — lighter than Docker Desktop K8s
# ============================================================

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "============================================" -ForegroundColor Cyan
Write-Host " K8s Lite Test (kind) - account-service + Kong" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# ----------------------------------------------------------
# Step 1: Create kind cluster (skip if already exists)
# ----------------------------------------------------------
Write-Host "`n[1/5] Setting up kind cluster..." -ForegroundColor Yellow
$clusters = & kind get clusters 2>$null
if ($clusters -contains "money-transfer") {
    Write-Host "  Cluster 'money-transfer' already exists, skipping." -ForegroundColor Gray
} else {
    kind create cluster --config kind-config.yaml
    if ($LASTEXITCODE -ne 0) { Write-Host "kind cluster creation failed!" -ForegroundColor Red; exit 1 }
    Write-Host "  Cluster created." -ForegroundColor Green
}
kubectl cluster-info --context kind-money-transfer

# ----------------------------------------------------------
# Step 2: Build account-service JAR + Docker image
# ----------------------------------------------------------
Write-Host "`n[2/5] Building account-service..." -ForegroundColor Yellow
.\gradlew.bat :account-service:clean :account-service:bootJar -x test
if ($LASTEXITCODE -ne 0) { Write-Host "Gradle build failed!" -ForegroundColor Red; exit 1 }

docker build --build-arg SERVICE_NAME=account-service --build-arg SERVICE_PORT=8082 -t account-service:latest .
if ($LASTEXITCODE -ne 0) { Write-Host "Docker build failed!" -ForegroundColor Red; exit 1 }
Write-Host "  Build complete." -ForegroundColor Green

# ----------------------------------------------------------
# Step 3: Load image into kind cluster
# (kind does not use local Docker daemon directly)
# ----------------------------------------------------------
Write-Host "`n[3/5] Loading images into kind cluster..." -ForegroundColor Yellow
kind load docker-image account-service:latest --name money-transfer
kind load docker-image postgres:16-alpine --name money-transfer
kind load docker-image kong:3.9 --name money-transfer
Write-Host "  Images loaded." -ForegroundColor Green

# ----------------------------------------------------------
# Step 4: Deploy with Helm
# ----------------------------------------------------------
Write-Host "`n[4/5] Deploying with Helm (lite)..." -ForegroundColor Yellow
helm upgrade --install money-transfer ./helm-chart `
    -f ./helm-chart/values-lite.yaml `
    --namespace money-transfer `
    --create-namespace `
    --kube-context kind-money-transfer
if ($LASTEXITCODE -ne 0) { Write-Host "Helm deploy failed!" -ForegroundColor Red; exit 1 }
Write-Host "  Helm release deployed." -ForegroundColor Green

# ----------------------------------------------------------
# Step 5: Wait for pods
# ----------------------------------------------------------
Write-Host "`n[5/5] Waiting for pods..." -ForegroundColor Yellow
kubectl wait --for=condition=ready pod -l app=postgresql -n money-transfer --timeout=120s --context kind-money-transfer
kubectl wait --for=condition=ready pod -l app=account-service -n money-transfer --timeout=180s --context kind-money-transfer
kubectl wait --for=condition=ready pod -l app=kong -n money-transfer --timeout=120s --context kind-money-transfer

Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host " Deployment Complete!" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
kubectl get pods -n money-transfer --context kind-money-transfer
Write-Host ""
Write-Host "Kong API: http://localhost:30000" -ForegroundColor White
Write-Host ""
Write-Host "Test:" -ForegroundColor Gray
Write-Host '  POST http://localhost:30000/api/v1/accounts/inquiry' -ForegroundColor Gray
Write-Host '  Body: {"accountNo":"1000000001"}' -ForegroundColor Gray

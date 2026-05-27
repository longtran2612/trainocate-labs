# ============================================================
# Money Transfer Platform - Full Deployment (kind)
# Deploys: PostgreSQL + Kong + 8 microservices
# Runtime: kind (Kubernetes IN Docker)
# ============================================================

$ErrorActionPreference = "Continue"
Set-Location $PSScriptRoot

function Invoke-Step {
    param([string]$Cmd)
    Invoke-Expression $Cmd
    if ($LASTEXITCODE -ne 0) { Write-Host "FAILED: $Cmd" -ForegroundColor Red; exit 1 }
}

$CLUSTER_NAME = "money-transfer"
$NAMESPACE    = "money-transfer"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host " Money Transfer - Full kind Deployment" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# ----------------------------------------------------------
# Step 1: Create kind cluster (skip if already exists)
# ----------------------------------------------------------
Write-Host "`n[1/6] Setting up kind cluster..." -ForegroundColor Yellow
$existing = kind get clusters 2>&1
if ("$existing" -match $CLUSTER_NAME) {
    Write-Host "  Cluster '$CLUSTER_NAME' already exists, skipping create." -ForegroundColor Gray
} else {
    kind create cluster --config kind-config.yaml
    if ($LASTEXITCODE -ne 0) { Write-Host "kind cluster creation failed!" -ForegroundColor Red; exit 1 }
    Write-Host "  Cluster created." -ForegroundColor Green
}
kubectl cluster-info --context "kind-$CLUSTER_NAME"

# ----------------------------------------------------------
# Step 2: Build all JARs
# ----------------------------------------------------------
Write-Host "`n[2/6] Building all service JARs..." -ForegroundColor Yellow
.\gradlew.bat clean bootJar -x test `
    -x :eureka-server:bootJar `
    -x :api-gateway:bootJar
if ($LASTEXITCODE -ne 0) { Write-Host "Gradle build failed!" -ForegroundColor Red; exit 1 }
Write-Host "  All JARs built." -ForegroundColor Green

# ----------------------------------------------------------
# Step 3: Build Docker images
# ----------------------------------------------------------
Write-Host "`n[3/6] Building Docker images..." -ForegroundColor Yellow

$services = @(
    @{ name = "auth-service";              port = 8081 },
    @{ name = "account-service";           port = 8082 },
    @{ name = "kyc-service";               port = 8083 },
    @{ name = "limit-service";             port = 8084 },
    @{ name = "transaction-service";       port = 8085 },
    @{ name = "internal-transfer-service"; port = 8086 },
    @{ name = "external-transfer-service"; port = 8087 },
    @{ name = "napas-simulator";           port = 8088 }
)

foreach ($svc in $services) {
    Write-Host "  Building $($svc.name)..." -ForegroundColor Gray
    docker build --build-arg SERVICE_NAME=$($svc.name) `
                 --build-arg SERVICE_PORT=$($svc.port) `
                 -t "$($svc.name):latest" . --quiet
    if ($LASTEXITCODE -ne 0) { Write-Host "Docker build failed for $($svc.name)!" -ForegroundColor Red; exit 1 }
}
Write-Host "  All images built." -ForegroundColor Green

# ----------------------------------------------------------
# Step 4: Load images into kind cluster
# ----------------------------------------------------------
Write-Host "`n[4/6] Loading images into kind cluster..." -ForegroundColor Yellow

# Infrastructure images (pull from registry first if not cached)
$infraImages = @("postgres:16-alpine", "kong:3.9")
foreach ($img in $infraImages) {
    Write-Host "  Loading $img..." -ForegroundColor Gray
    docker pull $img --quiet 2>$null
    kind load docker-image $img --name $CLUSTER_NAME
}

# Service images
foreach ($svc in $services) {
    Write-Host "  Loading $($svc.name)..." -ForegroundColor Gray
    kind load docker-image "$($svc.name):latest" --name $CLUSTER_NAME
}
Write-Host "  All images loaded." -ForegroundColor Green

# ----------------------------------------------------------
# Step 5: Deploy with Helm
# ----------------------------------------------------------
Write-Host "`n[5/6] Deploying with Helm..." -ForegroundColor Yellow
helm upgrade --install money-transfer ./helm-chart `
    --namespace $NAMESPACE `
    --create-namespace `
    --kube-context "kind-$CLUSTER_NAME" `
    --timeout 10m
if ($LASTEXITCODE -ne 0) { Write-Host "Helm deploy failed!" -ForegroundColor Red; exit 1 }
Write-Host "  Helm release deployed." -ForegroundColor Green

# ----------------------------------------------------------
# Step 6: Wait for pods
# ----------------------------------------------------------
Write-Host "`n[6/6] Waiting for all pods to be ready..." -ForegroundColor Yellow

kubectl wait --for=condition=ready pod -l app=postgresql `
    -n $NAMESPACE --timeout=120s --context "kind-$CLUSTER_NAME"

foreach ($svc in $services) {
    Write-Host "  Waiting for $($svc.name)..." -ForegroundColor Gray
    kubectl wait --for=condition=ready pod -l app=$($svc.name) `
        -n $NAMESPACE --timeout=240s --context "kind-$CLUSTER_NAME"
}

kubectl wait --for=condition=ready pod -l app=kong `
    -n $NAMESPACE --timeout=120s --context "kind-$CLUSTER_NAME"

# ----------------------------------------------------------
# Summary
# ----------------------------------------------------------
Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host " Deployment Complete!" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
kubectl get pods -n $NAMESPACE --context "kind-$CLUSTER_NAME"
Write-Host ""
Write-Host "Kong API:   http://localhost:30000" -ForegroundColor White
Write-Host "Kong Admin: http://localhost:30001" -ForegroundColor White
Write-Host ""
Write-Host "Quick test:" -ForegroundColor Gray
Write-Host '  Invoke-RestMethod -Method POST http://localhost:30000/api/v1/auth/login -ContentType "application/json" -Body ''{"username":"1000000001","password":"123456"}''' -ForegroundColor Gray
Write-Host ""
Write-Host "Teardown: .\teardown-kind.ps1" -ForegroundColor Gray

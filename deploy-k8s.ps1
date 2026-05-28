# ============================================================
# Money Transfer Platform - Kubernetes Deployment Script
# Uses Helm chart for deployment
# ============================================================

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "============================================" -ForegroundColor Cyan
Write-Host " Money Transfer Platform - Helm Deployment" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# ----------------------------------------------------------
# Step 1: Build all Gradle modules
# ----------------------------------------------------------
Write-Host "`n[1/6] Building Gradle modules..." -ForegroundColor Yellow
.\gradlew.bat clean bootJar -x test -x :eureka-server:bootJar -x :api-gateway:bootJar
if ($LASTEXITCODE -ne 0) { Write-Host "Gradle build failed!" -ForegroundColor Red; exit 1 }
Write-Host "Gradle build completed." -ForegroundColor Green

# ----------------------------------------------------------
# Step 2: Build Docker images for all 8 services
# ----------------------------------------------------------
Write-Host "`n[2/6] Building Docker images (backend)..." -ForegroundColor Yellow

$services = @(
    @{ name = "auth-service";              port = 8081 },
    @{ name = "account-service";           port = 8082 },
    @{ name = "kyc-service";               port = 8083 },
    @{ name = "limit-service";             port = 8084 },
    @{ name = "transaction-service";       port = 8085 },
    @{ name = "internal-transfer-service"; port = 8086 },
    @{ name = "external-transfer-service"; port = 8087 },
    @{ name = "napas-simulator";           port = 8088 },
    @{ name = "onboarding-service";        port = 8090; jreVersion = 21 }
)

foreach ($svc in $services) {
    Write-Host "  Building $($svc.name)..." -ForegroundColor Gray
    $jreVersion = if ($svc.jreVersion) { $svc.jreVersion } else { 25 }
    docker build --build-arg SERVICE_NAME=$($svc.name) --build-arg SERVICE_PORT=$($svc.port) --build-arg JRE_VERSION=$jreVersion -t "$($svc.name):latest" .
    if ($LASTEXITCODE -ne 0) { Write-Host "Docker build failed for $($svc.name)!" -ForegroundColor Red; exit 1 }
}
Write-Host "All backend Docker images built." -ForegroundColor Green

# ----------------------------------------------------------
# Step 3: Build Frontend Docker image
# ----------------------------------------------------------
Write-Host "`n[3/6] Building Frontend Docker image..." -ForegroundColor Yellow
docker build -t money-transfer-web:latest ./money-transfer-web
if ($LASTEXITCODE -ne 0) { Write-Host "Frontend Docker build failed!" -ForegroundColor Red; exit 1 }
Write-Host "Frontend Docker image built." -ForegroundColor Green

# ----------------------------------------------------------
# Step 4: Deploy with Helm
# ----------------------------------------------------------
Write-Host "`n[4/6] Deploying with Helm..." -ForegroundColor Yellow
helm upgrade --install money-transfer ./helm-chart --namespace money-transfer --create-namespace
if ($LASTEXITCODE -ne 0) { Write-Host "Helm deploy failed!" -ForegroundColor Red; exit 1 }
Write-Host "Helm release deployed." -ForegroundColor Green

# ----------------------------------------------------------
# Step 5: Wait for pods
# ----------------------------------------------------------
Write-Host "`n[5/6] Waiting for pods to be ready (timeout 5min)..." -ForegroundColor Yellow

Write-Host "  Waiting for PostgreSQL..." -ForegroundColor Gray
kubectl wait --for=condition=ready pod -l app=postgresql -n money-transfer --timeout=120s

Write-Host "  Waiting for Redis..." -ForegroundColor Gray
kubectl wait --for=condition=ready pod -l app=redis -n money-transfer --timeout=60s

Write-Host "  Waiting for Kafka..." -ForegroundColor Gray
kubectl wait --for=condition=ready pod -l app=kafka -n money-transfer --timeout=120s

foreach ($svc in $services) {
    Write-Host "  Waiting for $($svc.name)..." -ForegroundColor Gray
    kubectl wait --for=condition=ready pod -l app=$($svc.name) -n money-transfer --timeout=180s
}

Write-Host "  Waiting for Kong..." -ForegroundColor Gray
kubectl wait --for=condition=ready pod -l app=kong -n money-transfer --timeout=120s

Write-Host "  Waiting for Frontend..." -ForegroundColor Gray
kubectl wait --for=condition=ready pod -l app=money-transfer-web -n money-transfer --timeout=60s

Write-Host "  Waiting for Jaeger..." -ForegroundColor Gray
kubectl wait --for=condition=ready pod -l app=jaeger -n money-transfer --timeout=60s

Write-Host "  Waiting for Elasticsearch..." -ForegroundColor Gray
kubectl wait --for=condition=ready pod -l app=elasticsearch -n money-transfer --timeout=180s

Write-Host "  Waiting for Logstash..." -ForegroundColor Gray
kubectl wait --for=condition=ready pod -l app=logstash -n money-transfer --timeout=120s

Write-Host "  Waiting for Kibana..." -ForegroundColor Gray
kubectl wait --for=condition=ready pod -l app=kibana -n money-transfer --timeout=120s

Write-Host "  Waiting for Prometheus..." -ForegroundColor Gray
kubectl wait --for=condition=ready pod -l app=prometheus -n money-transfer --timeout=60s

Write-Host "  Waiting for Grafana..." -ForegroundColor Gray
kubectl wait --for=condition=ready pod -l app=grafana -n money-transfer --timeout=60s

Write-Host "  Waiting for Filebeat DaemonSet..." -ForegroundColor Gray
kubectl rollout status daemonset/filebeat -n money-transfer --timeout=60s

Write-Host "All pods are ready!" -ForegroundColor Green

# ----------------------------------------------------------
# Step 6: Summary
# ----------------------------------------------------------
Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host " Deployment Complete!" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
kubectl get pods -n money-transfer
Write-Host ""
Write-Host "── Application ────────────────────────────────" -ForegroundColor Cyan
Write-Host "Kong Proxy (API): http://localhost:30000" -ForegroundColor White
Write-Host "Kong Admin:       http://localhost:30001" -ForegroundColor White
Write-Host "Frontend (Web):   http://localhost:30080" -ForegroundColor White
Write-Host ""
Write-Host "── Observability ──────────────────────────────" -ForegroundColor Cyan
Write-Host "Jaeger UI:        http://localhost:30686" -ForegroundColor White
Write-Host "Kibana (Logs):    http://localhost:30601" -ForegroundColor White
Write-Host "Prometheus:       http://localhost:30090" -ForegroundColor White
Write-Host "Grafana:          http://localhost:30030  (admin/admin)" -ForegroundColor White
Write-Host ""
Write-Host "── Helm commands ──────────────────────────────" -ForegroundColor Gray
Write-Host "  helm status money-transfer -n money-transfer" -ForegroundColor Gray
Write-Host "  helm history money-transfer -n money-transfer" -ForegroundColor Gray
Write-Host "  helm uninstall money-transfer -n money-transfer" -ForegroundColor Gray

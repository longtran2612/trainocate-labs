# ============================================================
# Quick Redeploy - Rebuild & restart only changed services
# Usage:
#   .\redeploy.ps1                    → rebuild ALL services
#   .\redeploy.ps1 auth-service       → rebuild 1 service
#   .\redeploy.ps1 auth-service kyc-service  → rebuild multiple
#   .\redeploy.ps1 frontend           → rebuild frontend only
#   .\redeploy.ps1 helm               → only re-apply Helm values (no rebuild)
# ============================================================

param(
    [Parameter(Position=0, ValueFromRemainingArguments)]
    [string[]]$Targets
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$allBackend = @(
    "auth-service", "account-service", "kyc-service", "limit-service",
    "transaction-service", "internal-transfer-service",
    "external-transfer-service", "napas-simulator"
)

$rebuildBackend = @()
$rebuildFrontend = $false
$helmOnly = $false

if (-not $Targets -or $Targets.Count -eq 0) {
    $rebuildBackend = $allBackend
    $rebuildFrontend = $true
} else {
    foreach ($t in $Targets) {
        if ($t -eq "frontend" -or $t -eq "web") {
            $rebuildFrontend = $true
        } elseif ($t -eq "helm") {
            $helmOnly = $true
        } elseif ($t -in $allBackend) {
            $rebuildBackend += $t
        } else {
            Write-Host "Unknown target: $t" -ForegroundColor Red
            Write-Host "Available: $($allBackend -join ', '), frontend, helm" -ForegroundColor Gray
            exit 1
        }
    }
}

Write-Host "============================================" -ForegroundColor Cyan
Write-Host " Quick Redeploy" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# --- Helm only mode ---
if ($helmOnly) {
    Write-Host "`n[1/1] Re-applying Helm values..." -ForegroundColor Yellow
    helm upgrade money-transfer ./helm-chart --namespace money-transfer
    Write-Host "Done." -ForegroundColor Green
    exit 0
}

# --- Gradle build (only if backend services need rebuild) ---
if ($rebuildBackend.Count -gt 0) {
    Write-Host "`n[1/4] Building Gradle..." -ForegroundColor Yellow
    .\gradlew.bat clean bootJar -x test -x :eureka-server:bootJar -x :api-gateway:bootJar
    if ($LASTEXITCODE -ne 0) { Write-Host "Gradle build failed!" -ForegroundColor Red; exit 1 }

    Write-Host "`n[2/4] Building Docker images..." -ForegroundColor Yellow
    foreach ($svc in $rebuildBackend) {
        Write-Host "  $svc" -ForegroundColor Gray
        docker build -t "${svc}:latest" .
        if ($LASTEXITCODE -ne 0) { Write-Host "Docker build failed for $svc!" -ForegroundColor Red; exit 1 }
    }
}

# --- Frontend rebuild ---
if ($rebuildFrontend) {
    Write-Host "`n[3/4] Building Frontend..." -ForegroundColor Yellow
    docker build -t money-transfer-web:latest ./money-transfer-web
    if ($LASTEXITCODE -ne 0) { Write-Host "Frontend build failed!" -ForegroundColor Red; exit 1 }
}

# --- Restart deployments to pick up new images ---
Write-Host "`n[4/4] Restarting pods..." -ForegroundColor Yellow

foreach ($svc in $rebuildBackend) {
    kubectl rollout restart deployment/$svc -n money-transfer
}
if ($rebuildFrontend) {
    kubectl rollout restart deployment/money-transfer-web -n money-transfer
}

# Wait for rollout
foreach ($svc in $rebuildBackend) {
    Write-Host "  Waiting for $svc..." -ForegroundColor Gray
    kubectl rollout status deployment/$svc -n money-transfer --timeout=180s
}
if ($rebuildFrontend) {
    Write-Host "  Waiting for frontend..." -ForegroundColor Gray
    kubectl rollout status deployment/money-transfer-web -n money-transfer --timeout=60s
}

Write-Host "`n============================================" -ForegroundColor Green
Write-Host " Redeploy Complete!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
kubectl get pods -n money-transfer

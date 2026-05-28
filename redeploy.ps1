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
    @{ name = "auth-service";              port = 8081 },
    @{ name = "account-service";           port = 8082 },
    @{ name = "kyc-service";               port = 8083 },
    @{ name = "limit-service";             port = 8084 },
    @{ name = "transaction-service";       port = 8085 },
    @{ name = "internal-transfer-service"; port = 8086 },
    @{ name = "external-transfer-service"; port = 8087 },
    @{ name = "napas-simulator";           port = 8088 }
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
        } else {
            $svcEntry = $allBackend | Where-Object { $_.name -eq $t }
            if ($svcEntry) {
                $rebuildBackend += $svcEntry
            } else {
                Write-Host "Unknown target: $t" -ForegroundColor Red
                Write-Host "Available: $($allBackend.name -join ', '), frontend, helm" -ForegroundColor Gray
                exit 1
            }
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
        Write-Host "  $($svc.name)" -ForegroundColor Gray
        docker build --build-arg SERVICE_NAME=$($svc.name) --build-arg SERVICE_PORT=$($svc.port) -t "$($svc.name):latest" .
        if ($LASTEXITCODE -ne 0) { Write-Host "Docker build failed for $($svc.name)!" -ForegroundColor Red; exit 1 }
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
    kubectl rollout restart deployment/$($svc.name) -n money-transfer
}
if ($rebuildFrontend) {
    kubectl rollout restart deployment/money-transfer-web -n money-transfer
}

# Wait for rollout
foreach ($svc in $rebuildBackend) {
    Write-Host "  Waiting for $($svc.name)..." -ForegroundColor Gray
    kubectl rollout status deployment/$($svc.name) -n money-transfer --timeout=180s
}
if ($rebuildFrontend) {
    Write-Host "  Waiting for frontend..." -ForegroundColor Gray
    kubectl rollout status deployment/money-transfer-web -n money-transfer --timeout=60s
}

Write-Host "`n============================================" -ForegroundColor Green
Write-Host " Redeploy Complete!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
kubectl get pods -n money-transfer

# ============================================================
# Money Transfer Platform - Kubernetes Teardown Script
# Uninstalls Helm release and deletes namespace
# ============================================================

Write-Host "Uninstalling Helm release..." -ForegroundColor Yellow
helm uninstall money-transfer --namespace money-transfer 2>$null

Write-Host "Deleting namespace and all remaining resources..." -ForegroundColor Yellow
kubectl delete namespace money-transfer --ignore-not-found

Write-Host "`nTeardown complete." -ForegroundColor Green
Write-Host ""
Write-Host "Note: Docker images are still cached locally." -ForegroundColor Gray
Write-Host "To remove all images:" -ForegroundColor Gray
Write-Host "  docker rmi auth-service account-service kyc-service limit-service transaction-service internal-transfer-service external-transfer-service napas-simulator money-transfer-web" -ForegroundColor Gray

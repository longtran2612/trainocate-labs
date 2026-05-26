# ============================================================
# Money Transfer Platform - Kubernetes Teardown Script
# Deletes the entire money-transfer namespace and all resources
# ============================================================

Write-Host "Deleting money-transfer namespace and all resources..." -ForegroundColor Yellow
kubectl delete namespace money-transfer

Write-Host "Teardown complete." -ForegroundColor Green
Write-Host ""
Write-Host "Note: Docker images are still cached locally." -ForegroundColor Gray
Write-Host "To remove images: docker rmi auth-service account-service kyc-service limit-service transaction-service internal-transfer-service external-transfer-service" -ForegroundColor Gray

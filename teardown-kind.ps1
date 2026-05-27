# ============================================================
# Teardown kind cluster for Money Transfer
# ============================================================

Write-Host "Deleting kind cluster 'money-transfer'..." -ForegroundColor Yellow
kind delete cluster --name money-transfer
Write-Host "Done. All pods, services, and volumes deleted." -ForegroundColor Green

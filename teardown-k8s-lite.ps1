# ============================================================
# Teardown kind cluster for Money Transfer Lite
# ============================================================

Write-Host "Deleting kind cluster 'money-transfer'..." -ForegroundColor Yellow
kind delete cluster --name money-transfer
Write-Host "Done." -ForegroundColor Green

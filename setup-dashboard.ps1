Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Kubernetes Dashboard Setup" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# Step 1: Deploy Kubernetes Dashboard v2.7.0
Write-Host "`n[1/4] Deploying Kubernetes Dashboard v2.7.0..." -ForegroundColor Yellow
kubectl apply -f https://raw.githubusercontent.com/kubernetes/dashboard/v2.7.0/aio/deploy/recommended.yaml
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: Failed to deploy dashboard" -ForegroundColor Red; exit 1 }

# Step 2: Patch Service to NodePort 30090
Write-Host "`n[2/4] Exposing dashboard on NodePort 30090..." -ForegroundColor Yellow
$patchFile = [System.IO.Path]::GetTempFileName() + ".json"
@'
{"spec":{"type":"NodePort","ports":[{"port":443,"targetPort":8443,"nodePort":30090,"protocol":"TCP"}]}}
'@ | Out-File -FilePath $patchFile -Encoding utf8 -NoNewline
kubectl patch svc kubernetes-dashboard -n kubernetes-dashboard --type=merge --patch-file $patchFile
Remove-Item $patchFile -ErrorAction SilentlyContinue
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: Failed to patch service" -ForegroundColor Red; exit 1 }

# Step 3: Create admin ServiceAccount + ClusterRoleBinding
Write-Host "`n[3/4] Creating admin user..." -ForegroundColor Yellow
$adminManifest = @"
apiVersion: v1
kind: ServiceAccount
metadata:
  name: admin-user
  namespace: kubernetes-dashboard
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: admin-user
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: cluster-admin
subjects:
- kind: ServiceAccount
  name: admin-user
  namespace: kubernetes-dashboard
"@
$adminManifest | kubectl apply -f -
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: Failed to create admin user" -ForegroundColor Red; exit 1 }

# Step 3b: Enable skip-login for dev environment
$skipLoginPatch = '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--enable-skip-login"},{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--token-ttl=0"}]'
$pf2 = [System.IO.Path]::GetTempFileName() + ".json"
$skipLoginPatch | Out-File -FilePath $pf2 -Encoding utf8 -NoNewline
kubectl patch deployment kubernetes-dashboard -n kubernetes-dashboard --type=json --patch-file $pf2 2>&1 | Out-Null
Remove-Item $pf2 -ErrorAction SilentlyContinue

# Step 4: Wait for pods and generate long-lived Secret token
Write-Host "`n[4/4] Waiting for dashboard pods..." -ForegroundColor Yellow
kubectl wait --for=condition=ready pod -l k8s-app=kubernetes-dashboard -n kubernetes-dashboard --timeout=120s
if ($LASTEXITCODE -ne 0) { Write-Host "WARNING: Dashboard pod not ready yet, continuing..." -ForegroundColor DarkYellow }

# Create a Secret-based token (works with Dashboard v2.7.0 on K8s 1.24+)
$tokenSecret = @'
apiVersion: v1
kind: Secret
metadata:
  name: admin-user-token
  namespace: kubernetes-dashboard
  annotations:
    kubernetes.io/service-account.name: admin-user
type: kubernetes.io/service-account-token
'@
$tokenSecret | kubectl apply -f -
Start-Sleep -Seconds 3

$TOKEN = kubectl get secret admin-user-token -n kubernetes-dashboard -o jsonpath='{.data.token}'
$TOKEN = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($TOKEN))
if (-not $TOKEN) { Write-Host "ERROR: Failed to get token" -ForegroundColor Red; exit 1 }

Write-Host "`n============================================" -ForegroundColor Green
Write-Host "  Dashboard Ready!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  URL: https://localhost:30090" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Token (copy & paste vao Dashboard):" -ForegroundColor Yellow
Write-Host ""
Write-Host $TOKEN -ForegroundColor White
Write-Host ""
Write-Host "  Huong dan dang nhap:" -ForegroundColor Gray
Write-Host "  1. Mo browser: https://localhost:30090" -ForegroundColor Gray
Write-Host "  2. Chon 'Token'" -ForegroundColor Gray
Write-Host "  3. Paste token o tren" -ForegroundColor Gray
Write-Host "  4. Click 'Sign in'" -ForegroundColor Gray
Write-Host ""
Write-Host "  Neu browser canh bao HTTPS: chon Advanced -> Proceed" -ForegroundColor DarkYellow
Write-Host ""

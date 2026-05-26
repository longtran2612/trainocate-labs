# Hướng dẫn Demo Money Transfer Platform trên Kubernetes

## Yêu cầu trước khi demo

1. **Docker Desktop** đang chạy, **Kubernetes enabled** (Settings → Kubernetes → ✅ Enable Kubernetes)
2. **RAM ≥ 6GB** cho Docker Desktop (Settings → Resources → Memory)
3. **PostgreSQL local** đang tắt (tránh conflict port 5432)
4. Terminal: **PowerShell**

---

## Bước 1: Deploy toàn bộ (1 lệnh)

```powershell
cd E:\microservice\trainocate-labs
.\deploy-k8s.ps1
```

Script tự động chạy 8 bước:
1. `gradlew clean bootJar` — build 9 service
2. `docker build` × 7 images
3. Tạo namespace `money-transfer`
4. Deploy PostgreSQL (5 databases)
5. Đợi PostgreSQL ready
6. Deploy 7 microservices
7. Deploy Kong Gateway
8. Đợi tất cả pods ready

⏱ Tổng thời gian: ~5-8 phút

---

## Bước 2: Kiểm tra pods

```powershell
kubectl get pods -n money-transfer
```

Kết quả mong đợi — **9 pods, tất cả Running 1/1**:
```
NAME                                         READY   STATUS
postgresql-xxx                               1/1     Running
kong-xxx                                     1/1     Running
auth-service-xxx                             1/1     Running
account-service-xxx                          1/1     Running
kyc-service-xxx                              1/1     Running
limit-service-xxx                            1/1     Running
transaction-service-xxx                      1/1     Running
internal-transfer-service-xxx                1/1     Running
external-transfer-service-xxx                1/1     Running
```

---

## Bước 3: Kiểm tra databases

```powershell
kubectl exec deployment/postgresql -n money-transfer -- psql -U postgres -c "\l"
```

Kết quả: 5 databases — `auth`, `account_db`, `kyc_db`, `limit_db`, `transaction_db`

---

## Bước 4: Kiểm tra Kong routes

```powershell
curl http://localhost:30001/routes
```

5 routes: `auth-route`, `account-route`, `kyc-route`, `limit-route`, `transaction-route`

---

## Bước 5: Demo APIs qua Kong (port 30000)

### 5.1 — Login lấy JWT token

```powershell
curl -X POST http://localhost:30000/api/v1/auth/login `
  -H "Content-Type: application/json" `
  -d '{"username":"nguyen.vana","password":"123456"}'
```

Copy `token` từ response.

### 5.2 — Inquiry tài khoản

```powershell
curl -X POST http://localhost:30000/api/v1/accounts/inquiry `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer <TOKEN>" `
  -d '{"accountNo":"1000000001"}'
```

### 5.3 — Kiểm tra KYC

```powershell
curl -X GET http://localhost:30000/api/v1/kyc/verify/11111111-1111-1111-1111-111111111111 `
  -H "Authorization: Bearer <TOKEN>"
```

### 5.4 — Chuyển khoản nội bộ (full flow)

```powershell
curl -X POST http://localhost:30000/api/v1/transactions/transfer `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer <TOKEN>" `
  -d '{
    "senderAccountNo":"1000000001",
    "receiverAccountNo":"1000000002",
    "amount":500000,
    "bankCode":"970406",
    "description":"Test chuyen khoan noi bo"
  }'
```

Flow: Kong → transaction-service → internal-transfer-service → (account + kyc + limit)

### 5.5 — Check số dư sau chuyển khoản

```powershell
curl -X POST http://localhost:30000/api/v1/accounts/inquiry `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer <TOKEN>" `
  -d '{"accountNo":"1000000001"}'
```

Số dư giảm 500,000 VND.

---

## Bước 6: Demo Kubernetes features

### Scale service

```powershell
kubectl scale deployment account-service -n money-transfer --replicas=3
kubectl get pods -n money-transfer -l app=account-service
```

### Xem logs

```powershell
kubectl logs deployment/transaction-service -n money-transfer --tail=50
```

### Self-healing — xóa pod, K8s tự tạo lại

```powershell
kubectl delete pod -l app=auth-service -n money-transfer
kubectl get pods -n money-transfer -w
```

### Xem tất cả services

```powershell
kubectl get svc -n money-transfer
```

---

## Bước 7: Dùng Postman (nếu demo trên UI)

- Import file `MoneyTransfer.postman_collection.json`
- Đổi variable `k8s_url` = `http://localhost:30000`
- Chạy các request trong **Section 10 (K8s Kong)** hoặc dùng `k8s_url` thay `base_url`

---

## Dọn dẹp sau demo

```powershell
.\teardown-k8s.ps1
```

Xóa toàn bộ namespace + pods. Docker images vẫn cache local.

---

## Test accounts

| User | Username | Password | Account No | Balance | KYC |
|------|----------|----------|------------|---------|-----|
| Nguyen Van A | nguyen.vana | 123456 | 1000000001 | 50,000,000 VND | TIER_2 (verified) |
| Tran Van B | tran.vanb | 123456 | 1000000002 | 100,000,000 VND | TIER_1 (verified) |
| Le Thi C | le.thic | 123456 | 1000000003 | 200,000,000 VND | TIER_0 (pending) |

---

## Kiến trúc trên K8s

```
Client (curl/Postman)
    │
    ▼ port 30000
┌──────────┐
│   Kong   │  (API Gateway, rate-limiting, correlation-id)
└──────────┘
    │ K8s DNS
    ▼
┌─────────────────────────────────────────────┐
│  transaction-service (:8085)                │
│    ├── internal-transfer-service (:8086)    │
│    │     ├── account-service (:8082)        │
│    │     ├── kyc-service (:8083)            │
│    │     └── limit-service (:8084)          │
│    └── external-transfer-service (:8087)    │
│          ├── account-service                │
│          ├── kyc-service                    │
│          └── limit-service                  │
├─────────────────────────────────────────────┤
│  auth-service (:8081)                       │
└─────────────────────────────────────────────┘
    │
    ▼
┌──────────────┐
│  PostgreSQL  │  (5 databases, 1 pod)
└──────────────┘
```

## Điểm nhấn khi trình bày

- **Không Eureka** — K8s DNS tự discovery (`http://account-service:8082`)
- **Kong thay Spring Cloud Gateway** — declarative YAML, plugin ecosystem
- **1 lệnh deploy** — `deploy-k8s.ps1` build + deploy tất cả
- **Self-healing** — xóa pod, K8s tự restart
- **Scale** — tăng replicas cho bất kỳ service nào
- **Spring Profile** — `application-k8s.yml` tách biệt, local dev vẫn dùng Eureka

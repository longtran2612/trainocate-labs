# Onboarding Service — Camunda Workflow Engine

## Vấn đề trước khi có onboarding-service

FE phải tự gọi 5 API theo thứ tự, xử lý lỗi giữa chừng, và retry từ đầu nếu bất kỳ bước nào fail:

```
FE → POST /api/v1/auth/register
FE → POST /api/v1/accounts
FE → POST /api/v1/auth/update-username
FE → POST /api/v1/kyc/verify
FE → POST /api/v1/limits/init
```

---

## Giải pháp: 1 API duy nhất

```
FE → POST /api/v1/onboarding
         → trả về processInstanceId ngay lập tức

FE polling: GET /api/v1/onboarding/{id}
         → { status: "RUNNING" | "COMPLETED" | "FAILED" }

FE retry:   POST /api/v1/onboarding/{id}/retry
         → Camunda re-run đúng bước bị lỗi, không chạy lại từ đầu
```

---

## Camunda BPMN Process

```
START → [REGISTER_USER] → [CREATE_ACCOUNT] → [UPDATE_USERNAME] → [VERIFY_KYC] → [INIT_LIMITS] → END
```

Mỗi service task có `camunda:asyncBefore="true"` → Camunda tạo **job** trước khi thực thi.
Khi delegate throw exception → job fail → Camunda tạo **incident** → process dừng lại với state được lưu trong DB.

---

## Process Variables

| Bước | Variable được tạo |
|---|---|
| Input | `password`, `phone`, `email`, `cif`, `fullName`, `dob`, `address`, `mobile`, `currency`, `idNumber`, `idType` |
| Sau REGISTER_USER | `userId`, `username` |
| Sau CREATE_ACCOUNT | `accountNo` |
| Sau VERIFY_KYC | `kycTier` |

---

## API

### Start onboarding

```bash
POST /api/v1/onboarding
Content-Type: application/json

{
  "password": "Abc@12345",
  "phone": "0912345678",
  "email": "user@example.com",
  "cif": "CIF001234",
  "fullName": "Nguyen Van A",
  "dob": "1990-01-15",
  "address": "123 Nguyen Hue, Q1, HCMC",
  "mobile": "0912345678",
  "currency": "VND",
  "idNumber": "079090001234",
  "idType": "CCCD"
}
```

Response `202 Accepted`:
```json
{
  "success": true,
  "data": {
    "processInstanceId": "abc123-...",
    "status": "STARTED"
  }
}
```

### Poll status

```bash
GET /api/v1/onboarding/{processInstanceId}
```

Khi đang chạy:
```json
{
  "data": {
    "status": "RUNNING",
    "currentActivity": "verifyKycTask",
    "variables": { "userId": "...", "accountNo": "1000000099" }
  }
}
```

Khi fail:
```json
{
  "data": {
    "status": "FAILED",
    "currentActivity": "verifyKycTask",
    "errorMessage": "KYC verification failed: idNumber không hợp lệ",
    "variables": { "userId": "...", "accountNo": "..." }
  }
}
```

Khi hoàn thành:
```json
{
  "data": {
    "status": "COMPLETED",
    "variables": {
      "userId": "uuid-...",
      "username": "1000000099",
      "accountNo": "1000000099",
      "kycTier": "TIER_1"
    }
  }
}
```

### Retry bước bị lỗi

```bash
POST /api/v1/onboarding/{processInstanceId}/retry
```

Camunda sẽ tăng `retryCount` của failed job lên 1 và job executor sẽ re-execute đúng bước đó.
**Các bước đã hoàn thành không bị chạy lại.**

---

## Demo Partial Resume

### Kịch bản: lỗi ở bước VERIFY_KYC

1. Start onboarding với `idNumber` sai → REGISTER_USER ✓ → CREATE_ACCOUNT ✓ → UPDATE_USERNAME ✓ → **VERIFY_KYC ✗**

2. `GET /api/v1/onboarding/{id}` trả `status: "FAILED", currentActivity: "verifyKycTask"`

3. Bước REGISTER_USER và CREATE_ACCOUNT đã hoàn thành, account đã được tạo trong DB

4. Gọi `POST /api/v1/onboarding/{id}/retry` — Camunda chỉ retry **VERIFY_KYC**, không tạo lại user/account

---

## Cấu trúc Code

```
onboarding-service/
├── build.gradle                         ← Java 21 toolchain override (Camunda 7.22 yêu cầu)
└── src/main/
    ├── java/.../onboarding/
    │   ├── OnboardingServiceApplication.java
    │   ├── client/                       ← Feign clients (local DTO mirrors)
    │   │   ├── AuthServiceClient.java
    │   │   ├── AccountServiceClient.java
    │   │   ├── KycServiceClient.java
    │   │   └── LimitServiceClient.java
    │   ├── controller/OnboardingController.java
    │   ├── delegate/                     ← JavaDelegate (1 per BPMN step)
    │   │   ├── RegisterUserDelegate.java
    │   │   ├── CreateAccountDelegate.java
    │   │   ├── UpdateUsernameDelegate.java
    │   │   ├── VerifyKycDelegate.java
    │   │   └── InitLimitsDelegate.java
    │   ├── dto/                          ← Request/Response DTOs + ApiResponse<T>
    │   └── service/OnboardingService.java
    └── resources/
        ├── application.yml
        └── onboarding-process.bpmn       ← BPMN 2.0 process definition
```

---

## Camunda Tables (PostgreSQL `onboarding_db`)

| Prefix | Mục đích |
|---|---|
| `ACT_RU_*` | Runtime: process instances, executions, jobs, incidents đang active |
| `ACT_HI_*` | History: toàn bộ process instances, activities, variables đã hoàn thành |
| `ACT_GE_*` | General: byte arrays, resources (BPMN files) |
| `ACT_RE_*` | Repository: process definitions đã deploy |

**Kiểm tra process đang chạy:**
```sql
SELECT id_, process_def_id_, start_time_ FROM act_hi_procinst ORDER BY start_time_ DESC LIMIT 10;
```

**Kiểm tra incidents:**
```sql
SELECT id_, activity_id_, incident_msg_ FROM act_ru_incident;
```

**Kiểm tra variables:**
```sql
SELECT name_, text_ FROM act_hi_varinst WHERE proc_inst_id_ = 'your-process-instance-id';
```

---

## K8s Readiness — ⚠️ CHƯA SẴN SÀNG

`onboarding-service` **chưa có cấu hình k8s**. Cần làm:

### 1. Tạo `application-k8s.yml`

```yaml
# onboarding-service/src/main/resources/application-k8s.yml
eureka:
  client:
    enabled: false

spring:
  datasource:
    url: jdbc:postgresql://postgresql:5432/onboarding_db
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:123456}
  cloud:
    openfeign:
      client:
        config:
          auth-service:
            url: http://auth-service:8081
          account-service:
            url: http://account-service:8082
          kyc-service:
            url: http://kyc-service:8083
          limit-service:
            url: http://limit-service:8084

camunda.bpm:
  database:
    schema-update: false   # dùng true chỉ khi deploy lần đầu
```

### 2. Thêm vào `helm-chart/values.yaml`

```yaml
# Trong postgresql.databases — thêm:
  - onboarding_db

# Trong kong.routes — thêm:
  - service: onboarding-service
    port: 8090
    path: /api/v1/onboarding

# Trong services — thêm:
  - name: onboarding-service
    port: 8090
    hasDb: true
    dbName: onboarding_db
```

### 3. Thêm vào `deploy-k8s.ps1`

```powershell
$services = @(
    ...
    @{ name = "onboarding-service"; port = 8090 }   # thêm dòng này
)
```

### 4. Giới hạn replicas với Camunda

```yaml
# Không scale onboarding-service > 1 replica
# Camunda 7 job executor dùng DB locking (optimistic lock ACT_RU_JOB)
# Multi-replica hoạt động nhưng gây OptimisticLockingException noise trong logs
# Giữ replicas: 1 cho production, hoặc configure cluster-aware job executor
```

> **Lưu ý Java version:** `onboarding-service/build.gradle` đã override toolchain về Java 21.
> Dockerfile dùng Java 25 cho toàn project — cần kiểm tra image base của Dockerfile.
> Nếu dùng `eclipse-temurin:25`, Camunda 7.22 vẫn có thể bị lỗi reflection khi chạy.
> Giải pháp an toàn: dùng multi-stage Dockerfile riêng cho onboarding-service với `eclipse-temurin:21`.

---

## Tóm tắt: Muốn chạy k8s cần làm

| Task | Effort |
|---|---|
| Tạo `application-k8s.yml` cho onboarding-service | ~10 phút |
| Cập nhật `values.yaml` (db, route, service) | ~5 phút |
| Cập nhật `deploy-k8s.ps1` | ~2 phút |
| Dockerfile riêng cho onboarding với Java 21 base image | ~15 phút |
| Test smoke trên Kind/local k8s | ~30 phút |

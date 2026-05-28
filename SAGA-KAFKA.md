# Kafka-based Saga Orchestration — Transfer Flow

## Vấn đề trước khi refactor

`transaction-service` gọi trực tiếp các service (account, kyc, limit) qua **REST/OpenFeign đồng bộ**:

- Tight coupling: saga bị gắn chặt với uptime của từng service
- Không có message persistence: nếu service chết giữa chừng, toàn bộ flow mất
- Không tận dụng Kafka vốn đã có trong hệ thống

---

## Kiến trúc sau refactor

```
transaction-service (Orchestrator)
    │
    ├─► saga-kyc-command ──► kyc-service ──► saga-kyc-reply
    ├─► saga-account-command ─► account-service ─► saga-account-reply
    └─► saga-limit-command ──► limit-service ──► saga-limit-reply
```

Response về FE vẫn **đồng bộ** (CompletableFuture + timeout 30s) — FE không cần thay đổi.

---

## Kafka Topics (6 topics, auto-created)

| Topic | Producer | Consumer |
|---|---|---|
| `saga-kyc-command` | transaction-service | kyc-service |
| `saga-kyc-reply` | kyc-service | transaction-service |
| `saga-account-command` | transaction-service | account-service |
| `saga-account-reply` | account-service | transaction-service |
| `saga-limit-command` | transaction-service | limit-service |
| `saga-limit-reply` | limit-service | transaction-service |

---

## Message Schema

```java
// Orchestrator → Participant
SagaCommand {
    String sagaId;        // UUID, key của Kafka message
    String txId;          // transaction ID
    String stepName;      // "KYC_CHECK", "DEBIT_SENDER", ...
    String action;        // "EXECUTE" | "COMPENSATE"
    Map<String, Object> payload;
}

// Participant → Orchestrator
SagaReply {
    String sagaId;
    String txId;
    String stepName;
    String status;        // "SUCCESS" | "FAILED"
    String errorCode;
    String errorMessage;
    Map<String, Object> payload;
}
```

---

## Flow chuyển khoản nội bộ (Happy Path)

```
1. KYC_CHECK       ──► kyc-service        (validate KYC sender)
2. LIMIT_CHECK     ──► limit-service      (validate transfer limit)
3. BALANCE_CHECK   ──► account-service    (validate available balance)
4. DEBIT_SENDER    ──► account-service    [compensable] trừ tiền sender
5. CONSUME_LIMIT   ──► limit-service      [compensable] ghi nhận limit đã dùng
6. CREDIT_RECEIVER ──► account-service    cộng tiền receiver
```

Khi tất cả 6 bước thành công: `saga_state = COMPLETED`

---

## Compensation Flow (Rollback)

Chỉ các bước **compensable** mới cần rollback:

| Forward Step | Compensation Step | Service |
|---|---|---|
| DEBIT_SENDER | REFUND_SENDER | account-service |
| CONSUME_LIMIT | RELEASE_LIMIT | limit-service |

**Ví dụ:** CREDIT_RECEIVER thất bại →

```
COMPENSATING → RELEASE_LIMIT ✓ → REFUND_SENDER ✓ → COMPENSATED
```

---

## Trạng thái Saga (SagaStatus)

| Status | Ý nghĩa |
|---|---|
| `RUNNING` | Đang thực thi forward steps |
| `COMPLETED` | Tất cả 6 bước thành công |
| `COMPENSATING` | Đang chạy compensation |
| `COMPENSATED` | Đã rollback xong |
| `COMPENSATION_FAILED` | Rollback thất bại (cần manual intervention) |

---

## Test Rollback — Magic Account `FORCE_FAIL`

Gửi request với `receiverAccountNo = "FORCE_FAIL"` để bắt buộc CREDIT_RECEIVER thất bại:

```bash
POST /api/v1/transactions/transfer
{
  "referenceId": "TEST-ROLLBACK-001",
  "senderAccountNo": "1000000001",
  "receiverAccountNo": "FORCE_FAIL",
  "amount": 100000
}
```

**Expected flow:**
```
KYC✓ → LIMIT✓ → BALANCE✓ → DEBIT✓ → CONSUME✓ → CREDIT✗
  → COMPENSATING → RELEASE_LIMIT✓ → REFUND_SENDER✓
  → COMPENSATED
```

**Verify DB:**
```sql
SELECT status, step_count FROM saga_state WHERE saga_id = '...';
-- status = COMPENSATED, step_count = 8

SELECT step_name, status FROM saga_step WHERE saga_id = '...' ORDER BY created_at;
-- 6 forward + RELEASE_LIMIT + REFUND_SENDER
```

Số dư sender không thay đổi, limit không bị consume.

---

## Cấu trúc Code

```
transaction-service/
└── saga/
    ├── dto/
    │   ├── SagaCommand.java
    │   └── SagaReply.java
    ├── SagaKafkaOrchestrator.java    ← core state machine
    ├── SagaCleanupScheduler.java     ← timeout stuck sagas (60s)
    └── SagaSyncOrchestrator.java     ← @Deprecated (REST-based, kept for reference)

account-service/saga/SagaCommandConsumer.java   ← handles BALANCE_CHECK, DEBIT, CREDIT, REFUND
kyc-service/saga/SagaCommandConsumer.java       ← handles KYC_CHECK
limit-service/saga/SagaCommandConsumer.java     ← handles LIMIT_CHECK, CONSUME, RELEASE
```

---

## Kafka Consumer Groups

| Service | Consumer Group | Isolation |
|---|---|---|
| transaction-service | `transaction-service-saga` | reply consumers |
| kyc-service | `kyc-service-saga` | command consumer |
| limit-service | `limit-service-saga` | command consumer |
| account-service | `account-service-saga` | saga command consumer (tách biệt với CDC consumer) |
| account-service (CDC) | `account-service-cqrs` | Debezium CDC — không bị ảnh hưởng |

account-service dùng **2 separate** `KafkaListenerContainerFactory`:
- `kafkaListenerContainerFactory` — dành cho CDC
- `sagaKafkaListenerContainerFactory` — dành cho saga command

---

## K8s Readiness — ⚠️ CHƯA SẴN SÀNG

Kafka saga **chưa hoạt động trên k8s** vì:

| Gap | Cần làm |
|---|---|
| Không có Kafka trong k8s cluster | Deploy Bitnami Kafka (StatefulSet hoặc Helm chart) |
| `application-k8s.yml` của transaction/kyc/limit/account thiếu `spring.kafka.bootstrap-servers` | Thêm `spring.kafka.bootstrap-servers: kafka:9092` vào từng file |
| k8s values.yaml không có Kafka | Thêm Kafka service vào Helm chart hoặc manifest riêng |
| initContainer chờ Kafka | Thêm wait-for-kafka initContainer cho 4 service dùng Kafka |

**Thêm vào `application-k8s.yml` của transaction/kyc/limit/account:**
```yaml
spring:
  kafka:
    bootstrap-servers: kafka:9092
```

**Bitnami Kafka cho k8s (đơn giản nhất):**
```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install kafka bitnami/kafka \
  --namespace money-transfer \
  --set replicaCount=1 \
  --set controller.replicaCount=1 \
  --set listeners.client.protocol=PLAINTEXT
```

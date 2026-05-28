# Phân tích CQRS + Debezium CDC cho account-service — các điểm cần sửa theo enterprise

> Mục tiêu chính: **Kafka/Debezium/consumer có sự cố thì hệ thống không được trả balance sai như balance đúng**.  
> Nguyên tắc: **PostgreSQL/ledger là nguồn sự thật**, Redis chỉ là **read model/cache bất đồng bộ**.

---

## 1. Kết luận nhanh

Luồng hiện tại trong demo là **đúng để chứng minh CQRS + CDC**:

```text
Command API debit/credit
  -> ghi PostgreSQL
  -> PostgreSQL WAL
  -> Debezium Connector
  -> Kafka topic account_db.public.accounts
  -> AccountEventConsumer
  -> Redis read model

Query API
  -> đọc Redis
  -> nếu miss thì fallback PostgreSQL
```

Nhưng nếu dùng theo hướng **enterprise/banking**, cần sửa các điểm sau:

| Hạng mục | Hiện tại | Cần sửa |
|---|---|---|
| Source of truth balance | PostgreSQL là nơi ghi, Redis là nơi đọc | Giữ PostgreSQL/ledger là source of truth tuyệt đối. Redis không được dùng để quyết định debit/credit |
| Kafka down | Redis có thể stale | Query critical phải fallback PostgreSQL hoặc trả lỗi, không trả Redis stale như dữ liệu đúng |
| Consumer ack | Có xu hướng `finally ack` | Chỉ ack khi update Redis thành công hoặc đã đưa lỗi sang DLT/DLQ |
| Business event | Dùng CDC từ bảng `accounts` | Nên thêm `outbox_events` để phát business event ổn định |
| Idempotency | Chưa rõ version/event id | Consumer phải idempotent bằng `version`, `updated_at`, `event_id`, hoặc Debezium LSN |
| Observability | Chưa có freshness/lag check | Cần monitor Kafka Connect, consumer lag, Debezium lag, Redis last sync |
| Delete/tombstone | Có giữ tombstone | Cần xử lý delete event rõ ràng trong consumer |
| Recovery | Có thể replay topic | Cần runbook rebuild Redis + kiểm soát WAL/replication slot |

---

## 2. Vấn đề lớn nhất: Kafka down thì Redis sẽ stale

Trong CQRS async, Redis không cập nhật ngay khi DB commit. Nếu Kafka, Kafka Connect, Debezium connector hoặc consumer bị down, Redis có thể giữ balance cũ.

Ví dụ:

```text
T0: Account A balance trong PostgreSQL = 5,000,000
T1: Debit 100,000 thành công trong PostgreSQL -> balance thật = 4,900,000
T2: Kafka down / consumer down
T3: Redis vẫn đang có balance = 5,000,000
T4: Query đọc Redis và trả 5,000,000
```

Nếu API trả kết quả này như balance đúng thì là **sai nghiệp vụ**.

Vì vậy cần phân loại API:

| API | Có được đọc Redis khi Kafka down không? | Hành vi đúng |
|---|---:|---|
| `debit` | Không | Luôn check/update PostgreSQL trong transaction |
| `credit` | Không | Luôn update PostgreSQL/ledger trong transaction |
| `transfer` | Không | Luôn lock account + check balance thật trong DB |
| `check-balance` dùng cho hiển thị UI | Có thể, nếu có đánh dấu stale | Trả `source=REDIS`, `stale=true`, hoặc fallback DB |
| `check-balance` dùng trước giao dịch | Không | Bắt buộc đọc PostgreSQL hoặc trong cùng transaction |
| `inquiry` không critical | Có thể | Trả kèm metadata `lastSyncedAt`, `source`, `stale` |

---

## 3. Nguyên tắc enterprise nên áp dụng

### 3.1. Command side phải strong consistency

Debit/credit/transfer không được dựa vào Redis.

Luồng đúng:

```text
Client
  -> AccountCommandService
      -> BEGIN TRANSACTION
          -> SELECT account FOR UPDATE
          -> check available_balance từ PostgreSQL
          -> insert ledger_entries
          -> update accounts.balance / available_balance
          -> insert outbox_events
      -> COMMIT
  -> trả kết quả thành công/thất bại
```

Mẫu SQL/logic:

```sql
BEGIN;

SELECT account_no, balance, available_balance, version
FROM accounts
WHERE account_no = :accountNo
FOR UPDATE;

-- validate available_balance >= amount

INSERT INTO ledger_entries (
    transaction_id,
    account_no,
    amount,
    direction,
    balance_before,
    balance_after,
    created_at
) VALUES (...);

UPDATE accounts
SET balance = balance - :amount,
    available_balance = available_balance - :amount,
    version = version + 1,
    updated_at = now()
WHERE account_no = :accountNo;

INSERT INTO outbox_events (
    event_id,
    aggregate_type,
    aggregate_id,
    event_type,
    payload,
    created_at
) VALUES (...);

COMMIT;
```

Điểm quan trọng:

- Redis không nằm trong transaction này.
- Kafka không nằm trong transaction này.
- Nếu Kafka down, giao dịch tiền vẫn đúng vì đã commit vào DB.
- Event sẽ được Debezium phát lại sau khi hạ tầng phục hồi, miễn là WAL/replication slot còn giữ được dữ liệu.

---

### 3.2. Redis chỉ là read model, phải có freshness metadata

Redis value hiện tại không nên chỉ chứa balance. Nên thêm metadata:

```json
{
  "accountNo": "1000000001",
  "balance": "4900000.00",
  "availableBalance": "4900000.00",
  "currency": "VND",
  "version": 25,
  "lastEventId": "evt-123",
  "lastDbUpdatedAt": "2026-05-28T10:15:30+07:00",
  "lastSyncedAt": "2026-05-28T10:15:31+07:00",
  "source": "CDC"
}
```

Query response cũng nên có metadata:

```json
{
  "accountNo": "1000000001",
  "balance": "4900000.00",
  "availableBalance": "4900000.00",
  "currency": "VND",
  "source": "REDIS",
  "stale": false,
  "lastSyncedAt": "2026-05-28T10:15:31+07:00"
}
```

Khi read model bị stale:

```json
{
  "accountNo": "1000000001",
  "balance": "4900000.00",
  "availableBalance": "4900000.00",
  "currency": "VND",
  "source": "POSTGRESQL",
  "stale": false,
  "fallbackReason": "READ_MODEL_STALE"
}
```

Hoặc nếu policy là strict và PostgreSQL không đọc được:

```json
{
  "code": "BALANCE_TEMPORARILY_UNAVAILABLE",
  "message": "Unable to guarantee fresh balance at this time"
}
```

Không nên trả Redis stale mà không nói gì.

---

## 4. Luồng query an toàn khi Kafka down

### 4.1. Quy tắc đề xuất

Với `check-balance`, nên có policy như sau:

```text
Nếu endpoint là critical hoặc yêu cầu fresh=true:
    -> đọc PostgreSQL
    -> refresh Redis nếu cần

Nếu endpoint là non-critical:
    -> kiểm tra Redis freshness
    -> nếu Redis fresh: trả Redis
    -> nếu Redis stale: fallback PostgreSQL
    -> nếu PostgreSQL lỗi: trả lỗi, không trả stale balance như đúng
```

### 4.2. Flow chi tiết

```text
POST /api/v1/accounts/check-balance
  |
  v
AccountQueryService
  |
  +-- check ReadModelFreshnessService
  |     |
  |     +-- Kafka/consumer healthy?
  |     +-- consumer lag <= threshold?
  |     +-- lastSyncedAt <= threshold?
  |
  +-- nếu fresh
  |     -> đọc Redis
  |     -> trả source=REDIS, stale=false
  |
  +-- nếu stale/unhealthy
        -> đọc PostgreSQL
        -> warm Redis nếu đọc thành công
        -> trả source=POSTGRESQL, stale=false
        -> nếu PostgreSQL lỗi thì trả 503, không trả Redis stale như dữ liệu đúng
```

### 4.3. Pseudocode Java

```java
public BalanceResponse checkBalance(CheckBalanceRequest request, boolean freshRequired) {
    String accountNo = request.getAccountNo();

    boolean readModelFresh = readModelFreshnessService.isFresh(accountNo);

    if (!freshRequired && readModelFresh) {
        AccountReadModel cached = redisRepository.findByAccountNo(accountNo);
        if (cached != null && !readModelFreshnessService.isStale(cached)) {
            return BalanceResponse.fromRedis(cached, false);
        }
    }

    // Fallback an toàn khi Kafka/Debezium/consumer down hoặc Redis miss/stale
    Account account = accountRepository.findByAccountNo(accountNo)
        .orElseThrow(() -> new AccountNotFoundException(accountNo));

    // Warm lại Redis nhưng không để lỗi Redis làm fail response nếu đã đọc DB thành công
    try {
        redisRepository.save(AccountReadModel.from(account));
    } catch (Exception e) {
        log.warn("Warm Redis failed after PostgreSQL fallback, accountNo={}", accountNo, e);
    }

    return BalanceResponse.fromPostgres(account, false, "READ_MODEL_STALE_OR_MISS");
}
```

Với endpoint cực kỳ nhạy cảm, có thể bỏ hoàn toàn Redis:

```java
public BalanceResponse checkFreshBalance(CheckBalanceRequest request) {
    Account account = accountRepository.findByAccountNo(request.getAccountNo())
        .orElseThrow(() -> new AccountNotFoundException(request.getAccountNo()));

    return BalanceResponse.fromPostgres(account, false, "FRESH_REQUIRED");
}
```

---

## 5. Cần thêm ReadModelFreshnessService

### 5.1. Nhiệm vụ

Service này quyết định Redis có đủ tin cậy để đọc không.

Nó nên kiểm tra:

1. Kafka broker reachable hay không.
2. Kafka Connect/Debezium connector có `RUNNING` không.
3. Consumer group lag có vượt threshold không.
4. Lần cuối consumer update Redis là khi nào.
5. Redis item có `lastSyncedAt`, `version`, `updatedAt` hợp lệ không.

### 5.2. Threshold đề xuất

| Môi trường | Redis stale threshold | Hành vi |
|---|---:|---|
| Demo/local | 5-10 giây | Log warning, fallback DB |
| Production non-critical | 1-3 giây hoặc theo SLA | Fallback DB hoặc trả stale flag |
| Production critical | 0 giây | Luôn DB/transaction |

Ví dụ config:

```yaml
account:
  read-model:
    max-staleness-ms: 2000
    fallback-to-postgres-when-stale: true
    return-stale-cache: false
```

### 5.3. Pseudocode

```java
@Service
public class ReadModelFreshnessService {

    private final Duration maxStaleness = Duration.ofMillis(2000);

    public boolean isFresh(AccountReadModel model) {
        if (model == null || model.getLastSyncedAt() == null) {
            return false;
        }

        Duration age = Duration.between(model.getLastSyncedAt(), Instant.now());
        if (age.compareTo(maxStaleness) > 0) {
            return false;
        }

        return isCdcPipelineHealthy();
    }

    public boolean isCdcPipelineHealthy() {
        // Check cached health status updated by scheduled job:
        // - Kafka broker reachable
        // - Debezium connector RUNNING
        // - consumer lag under threshold
        // - last consumer success time under threshold
        return true;
    }
}
```

---

## 6. Sửa Kafka consumer: không ack bừa

### 6.1. Không nên làm

Không nên dùng pattern này trong production:

```java
@KafkaListener(topics = "account_db.public.accounts")
public void consume(String message, Acknowledgment ack) {
    try {
        // parse event, update Redis
    } catch (Exception e) {
        log.error("Failed to consume account CDC event", e);
    } finally {
        ack.acknowledge();
    }
}
```

Vấn đề:

- Redis lỗi vẫn ack.
- Message mất khỏi consumer group.
- Redis không được update.
- Query có thể trả balance cũ.

### 6.2. Nên làm

Chỉ ack khi xử lý thành công.

```java
@KafkaListener(topics = "account_db.public.accounts")
public void consume(String message, Acknowledgment ack) {
    try {
        DebeziumAccountPayload payload = parser.parse(message);
        accountReadModelUpdater.apply(payload); // update Redis idempotent
        ack.acknowledge();
    } catch (RetryableException e) {
        log.warn("Retryable CDC consume error, will retry", e);
        throw e; // để Spring Kafka retry, không ack
    } catch (Exception e) {
        log.error("Non-retryable CDC consume error", e);
        throw e; // route sang DLT bằng error handler
    }
}
```

Kết hợp Spring Kafka error handler / retry topic / DLT:

```text
account_db.public.accounts
  -> retry 1
  -> retry 2
  -> retry 3
  -> account_db.public.accounts.DLT
```

Sau khi message vào DLT:

- Alert.
- Không coi read model là healthy.
- Query critical fallback PostgreSQL.
- Có job/manual tool replay DLT sau khi fix lỗi.

---

## 7. Consumer phải idempotent

Kafka consumer có thể xử lý lại cùng một message. Đây là bình thường trong mô hình at-least-once.

Redis updater nên có logic:

```text
Nếu incoming.version <= currentRedis.version:
    bỏ qua event cũ/duplicate
Ngược lại:
    update Redis
```

Ví dụ:

```java
public void apply(DebeziumAccountPayload event) {
    String key = "account:" + event.getAccountNo();

    AccountReadModel current = redisRepository.findByKey(key);

    if (current != null && event.getVersion() <= current.getVersion()) {
        log.info("Skip old/duplicate account event, accountNo={}, eventVersion={}, currentVersion={}",
            event.getAccountNo(), event.getVersion(), current.getVersion());
        return;
    }

    AccountReadModel next = AccountReadModel.builder()
        .accountNo(event.getAccountNo())
        .balance(event.getBalance())
        .availableBalance(event.getAvailableBalance())
        .version(event.getVersion())
        .lastDbUpdatedAt(event.getUpdatedAt())
        .lastSyncedAt(Instant.now())
        .source("CDC")
        .build();

    redisRepository.save(next);
}
```

Nếu bảng `accounts` chưa có `version`, nên thêm:

```sql
ALTER TABLE accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT now();
```

Mỗi lần update balance:

```sql
UPDATE accounts
SET balance = :newBalance,
    available_balance = :newAvailableBalance,
    version = version + 1,
    updated_at = now()
WHERE account_no = :accountNo;
```

---

## 8. Nên thêm Outbox Pattern cho business event

Không nên để service khác phụ thuộc trực tiếp vào CDC raw của bảng `accounts`. Schema bảng thay đổi sẽ ảnh hưởng consumer.

Nên thêm bảng:

```sql
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

Trong cùng transaction debit/credit:

```text
update accounts
insert ledger_entries
insert outbox_events
commit
```

Debezium đọc `outbox_events` và phát Kafka topic:

```text
account.events.AccountDebited
account.events.AccountCredited
account.events.TransferCompleted
```

Phân tách rõ:

```text
Raw table CDC từ accounts
  -> dùng nội bộ để sync Redis read model

Outbox business events
  -> dùng cho các service khác: notification, transaction history, fraud, audit
```

---

## 9. Kafka down thì chuyện gì xảy ra?

### 9.1. Nếu Kafka broker down

```text
PostgreSQL vẫn commit debit/credit bình thường
Debezium/Kafka Connect không publish được event
Redis không được update
Query Redis có nguy cơ stale
```

Hành vi đúng:

- Command vẫn dùng DB transaction nên balance thật không sai.
- Query critical fallback PostgreSQL.
- Query non-critical có thể trả stale flag hoặc fallback PostgreSQL.
- Không được coi Redis là fresh.

### 9.2. Nếu Debezium/Kafka Connect down

```text
PostgreSQL vẫn ghi WAL
Replication slot giữ lại WAL chưa được consume
Khi Debezium chạy lại, nó tiếp tục từ offset/LSN gần nhất
```

Rủi ro:

- Nếu connector down lâu, WAL bị giữ lại nhiều.
- Disk PostgreSQL có thể đầy.
- Cần monitor replication slot lag.

Nên monitor PostgreSQL:

```sql
SELECT
    slot_name,
    active,
    restart_lsn,
    confirmed_flush_lsn,
    pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) AS retained_wal
FROM pg_replication_slots;
```

### 9.3. Nếu AccountEventConsumer down

```text
Kafka vẫn nhận event
Consumer group lag tăng
Redis không được update
```

Hành vi đúng:

- Monitor consumer lag.
- Nếu lag vượt threshold, mark read model unhealthy.
- Query fallback PostgreSQL.
- Khi consumer chạy lại, nó consume backlog và update Redis.

---

## 10. Thiết kế API response để tránh hiểu nhầm balance

### 10.1. Response nên có `source` và `freshness`

```json
{
  "accountNo": "1000000001",
  "balance": "4900000.00",
  "availableBalance": "4900000.00",
  "currency": "VND",
  "source": "REDIS",
  "stale": false,
  "lastSyncedAt": "2026-05-28T10:15:31+07:00"
}
```

Khi fallback PostgreSQL:

```json
{
  "accountNo": "1000000001",
  "balance": "4900000.00",
  "availableBalance": "4900000.00",
  "currency": "VND",
  "source": "POSTGRESQL",
  "stale": false,
  "fallbackReason": "KAFKA_UNHEALTHY"
}
```

Khi không thể đảm bảo dữ liệu đúng:

```json
{
  "code": "BALANCE_TEMPORARILY_UNAVAILABLE",
  "message": "Balance cannot be guaranteed fresh at this time"
}
```

### 10.2. Không nên trả response kiểu này khi Kafka down

```json
{
  "accountNo": "1000000001",
  "balance": "5000000.00"
}
```

Vì client sẽ tưởng đây là balance đúng tại thời điểm hiện tại.

---

## 11. Đề xuất sửa cấu trúc code

Hiện tại:

```text
account-service/
  command/AccountCommandService.java
  query/AccountQueryService.java
  readmodel/AccountReadModel.java
  event/AccountEventConsumer.java
```

Nên bổ sung:

```text
account-service/
  domain/
    Account.java
    LedgerEntry.java
    OutboxEvent.java

  command/
    AccountCommandService.java
    TransferCommandService.java

  query/
    AccountQueryService.java
    ReadModelFreshnessService.java
    BalanceReadPolicy.java

  readmodel/
    AccountReadModel.java
    AccountRedisRepository.java
    AccountReadModelUpdater.java

  event/
    DebeziumAccountPayload.java
    AccountEventConsumer.java
    AccountEventDeadLetterHandler.java

  outbox/
    OutboxEventRepository.java
    AccountEventFactory.java

  monitoring/
    KafkaHealthIndicator.java
    DebeziumHealthClient.java
    ConsumerLagMonitor.java
```

---

## 12. Checklist thay đổi cụ thể

### 12.1. Database

- [ ] Thêm bảng `ledger_entries` nếu chưa có.
- [ ] Thêm bảng `outbox_events`.
- [ ] Thêm cột `version` vào `accounts`.
- [ ] Thêm cột `updated_at` vào `accounts`.
- [ ] Đảm bảo debit/credit update `version = version + 1`.
- [ ] Đảm bảo command dùng transaction.
- [ ] Đảm bảo debit dùng row lock hoặc optimistic locking phù hợp.

### 12.2. Command service

- [ ] Không đọc Redis để quyết định debit/credit.
- [ ] Debit/credit phải check balance thật trong PostgreSQL.
- [ ] Ghi `ledger_entries` trong cùng transaction.
- [ ] Ghi `outbox_events` trong cùng transaction.
- [ ] Có transaction id/reference id để chống duplicate request.

### 12.3. Query service

- [ ] Thêm `ReadModelFreshnessService`.
- [ ] `check-balance` critical: đọc PostgreSQL.
- [ ] `check-balance` non-critical: đọc Redis nếu fresh, fallback PostgreSQL nếu stale.
- [ ] Response thêm `source`, `stale`, `lastSyncedAt`, `fallbackReason`.
- [ ] Khi Kafka/consumer unhealthy: không trả Redis như balance đúng.

### 12.4. Redis read model

- [ ] Thêm `version`.
- [ ] Thêm `lastDbUpdatedAt`.
- [ ] Thêm `lastSyncedAt`.
- [ ] Thêm `lastEventId` nếu có.
- [ ] Updater bỏ qua event cũ/duplicate.

### 12.5. Kafka consumer

- [ ] Bỏ `finally ack`.
- [ ] Chỉ ack sau khi update Redis thành công.
- [ ] Thêm retry.
- [ ] Thêm DLT/DLQ.
- [ ] Alert khi có message vào DLT.
- [ ] Không mark read model healthy nếu DLT chưa xử lý.

### 12.6. Debezium/Kafka

- [ ] Tách topic raw CDC và business event.
- [ ] Dùng outbox table cho business event.
- [ ] Monitor connector status.
- [ ] Monitor replication slot WAL retained.
- [ ] Monitor consumer lag.
- [ ] Production Kafka nên có replication factor >= 3.
- [ ] Topic quan trọng nên có `min.insync.replicas` phù hợp.

---

## 13. Luồng đề xuất cuối cùng

```text
                            ┌────────────────────────┐
                            │        Client          │
                            └───────────┬────────────┘
                                        │
                 ┌──────────────────────┴──────────────────────┐
                 │                                             │
                 v                                             v
       ┌──────────────────────┐                    ┌──────────────────────┐
       │ Command API           │                    │ Query API             │
       │ debit/credit/transfer │                    │ check/inquiry         │
       └───────────┬──────────┘                    └───────────┬──────────┘
                   │                                           │
                   v                                           v
       ┌──────────────────────┐                    ┌──────────────────────┐
       │ PostgreSQL TX         │                    │ Freshness Check       │
       │ - lock account        │                    │ Kafka/Debezium/Lag    │
       │ - update balance      │                    └───────────┬──────────┘
       │ - insert ledger       │                                │
       │ - insert outbox       │                 ┌──────────────┴──────────────┐
       └───────────┬──────────┘                 │                             │
                   │                            fresh                         stale
                   v                             │                             │
       ┌──────────────────────┐                 v                             v
       │ PostgreSQL WAL        │       ┌──────────────────┐          ┌──────────────────┐
       └───────────┬──────────┘       │ Redis Read Model │          │ PostgreSQL        │
                   │                  └──────────────────┘          │ source of truth   │
                   v                                                 └──────────────────┘
       ┌──────────────────────┐
       │ Debezium CDC          │
       └───────────┬──────────┘
                   │
                   v
       ┌──────────────────────┐
       │ Kafka Topics          │
       │ - raw account CDC     │
       │ - outbox events       │
       │ - retry/DLT           │
       └───────────┬──────────┘
                   │
                   v
       ┌──────────────────────┐
       │ Consumers             │
       │ - update Redis        │
       │ - idempotent          │
       │ - retry/DLT           │
       └──────────────────────┘
```

---

## 14. Chính sách trả balance khuyến nghị

| Tình huống | Redis | PostgreSQL | Kết quả nên trả |
|---|---|---|---|
| Bình thường | Fresh | OK | Trả Redis, `source=REDIS`, `stale=false` |
| Redis miss | Không có | OK | Trả PostgreSQL, warm Redis |
| Kafka down | Có nhưng stale | OK | Trả PostgreSQL, `fallbackReason=KAFKA_UNHEALTHY` |
| Debezium down | Có nhưng stale | OK | Trả PostgreSQL, `fallbackReason=CDC_UNHEALTHY` |
| Consumer lag cao | Có nhưng stale | OK | Trả PostgreSQL, `fallbackReason=CONSUMER_LAG_HIGH` |
| Redis lỗi | Lỗi | OK | Trả PostgreSQL |
| PostgreSQL lỗi, Redis fresh | Fresh | Lỗi | Có thể trả Redis cho non-critical, nhưng phải ghi `source=REDIS`, có policy rõ |
| PostgreSQL lỗi, Redis stale | Stale | Lỗi | Trả 503, không trả balance stale như đúng |
| Debit/transfer | Không dùng | OK | Luôn dùng PostgreSQL transaction |

---

## 15. Test case cần bổ sung

### Case 1: Kafka down sau debit

1. Start hệ thống bình thường.
2. Check balance account `1000000001`.
3. Stop Kafka hoặc Kafka Connect.
4. Gọi debit 100,000.
5. Gọi `check-balance`.

Expected:

```text
- Debit thành công vì PostgreSQL commit.
- Redis có thể vẫn là balance cũ.
- API check-balance không được trả Redis cũ như balance đúng.
- API phải fallback PostgreSQL hoặc trả lỗi nếu không đảm bảo fresh.
```

### Case 2: Consumer Redis update lỗi

1. Stop Redis hoặc inject exception trong consumer.
2. Gọi debit.
3. Event vào Kafka nhưng consumer xử lý lỗi.

Expected:

```text
- Consumer không ack bừa.
- Message được retry hoặc đưa vào DLT.
- Read model bị mark unhealthy.
- Query fallback PostgreSQL.
```

### Case 3: Duplicate event

1. Replay Kafka topic từ offset cũ.
2. Consumer nhận lại event version thấp hơn.

Expected:

```text
- Consumer bỏ qua event cũ.
- Redis không bị rollback balance.
```

### Case 4: Consumer lag cao

1. Pause consumer.
2. Thực hiện nhiều debit/credit.
3. Resume hoặc query trong lúc lag cao.

Expected:

```text
- Health báo consumer lag high.
- Query fallback PostgreSQL.
- Khi lag về 0, Redis được coi là fresh lại.
```

---

## 16. Mức ưu tiên triển khai

### Ưu tiên P0 — phải làm trước

1. Command không dùng Redis để validate balance.
2. Query không trả Redis khi read model stale/unhealthy.
3. Bỏ `finally ack` trong Kafka consumer.
4. Thêm fallback PostgreSQL cho `check-balance` khi Kafka/Debezium/consumer lag.
5. Thêm `version` và idempotent update Redis.

### Ưu tiên P1 — nên làm sớm

1. Thêm `ledger_entries`.
2. Thêm `outbox_events`.
3. Thêm DLT/DLQ.
4. Thêm health check Kafka Connect/Debezium.
5. Thêm monitor consumer lag.
6. Thêm response metadata `source`, `stale`, `lastSyncedAt`.

### Ưu tiên P2 — production hardening

1. Kafka cluster 3 broker.
2. Topic replication factor >= 3.
3. `min.insync.replicas` phù hợp.
4. Alert replication slot retained WAL.
5. Runbook rebuild Redis từ Kafka/PostgreSQL.
6. Dashboard Grafana/Prometheus cho CDC lag, consumer lag, Redis sync time.

---

## 17. Kết luận

Luồng demo CQRS + Debezium hiện tại là hợp lý để học và trình bày pattern. Tuy nhiên để đạt mức enterprise, đặc biệt với balance/tài khoản, cần đổi tư duy:

```text
Redis nhanh nhưng không authoritative.
Kafka/Debezium giúp đồng bộ bất đồng bộ nhưng có thể trễ/down.
PostgreSQL/ledger mới là nguồn sự thật.
Nếu không chứng minh được Redis còn fresh, không được trả Redis như balance đúng.
```

Thiết kế an toàn nhất:

```text
Debit/Credit/Transfer  -> PostgreSQL transaction
Check balance critical -> PostgreSQL hoặc Redis only when verified fresh
Check balance UI       -> Redis nếu fresh, fallback PostgreSQL nếu stale
Kafka down             -> Không sai tiền, chỉ degrade query/read model
Consumer lỗi           -> Retry/DLT, không ack bừa
```

---

## 18. Tài liệu tham khảo

- Debezium PostgreSQL Connector: https://debezium.io/documentation/reference/stable/connectors/postgresql.html
- Debezium Outbox Event Router: https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html
- Spring Kafka Retry Topic / DLT: https://docs.spring.io/spring-kafka/reference/retrytopic.html
- Spring Kafka Exactly Once Semantics: https://docs.spring.io/spring-kafka/reference/kafka/exactly-once.html
- Confluent Kafka Delivery Semantics: https://docs.confluent.io/kafka/design/delivery-semantics.html

# CQRS + Debezium CDC — Demo trên account-service

## Tổng quan kiến trúc

Pattern **CQRS (Command Query Responsibility Segregation)** tách biệt luồng **ghi** và **đọc**:

- **Command side** — ghi vào PostgreSQL, đồng thời trigger WAL event
- **Debezium CDC** — bắt thay đổi từ PostgreSQL WAL → Kafka
- **Query side** — đọc từ Redis read model, được cập nhật bất đồng bộ qua Kafka

```
┌────────────────────────────────────────────────────────────────────────────┐
│                            account-service                                  │
│                                                                             │
│  POST /debit                   AccountCommandService                        │
│  POST /credit      ──────────► (ghi vào PostgreSQL)                        │
│  POST /           (Command)           │                                     │
│  PUT  /                               ▼                                     │
│                             [PostgreSQL WAL event]                          │
│                                       │                                     │
│                              Debezium Connector                             │
│                                       │                                     │
│                        Kafka topic: account_db.public.accounts              │
│                                       │                                     │
│                            AccountEventConsumer                             │
│                             (parse → save Redis)                            │
│                                       │                                     │
│  GET  /                               ▼                                     │
│  POST /inquiry     ◄──────── AccountQueryService                           │
│  POST /check-balance  (Query)  (đọc từ Redis "account:{no}")               │
│  POST /get-customer-info       (fallback → PostgreSQL nếu miss)            │
│                                                                             │
└────────────────────────────────────────────────────────────────────────────┘
```

### Eventual Consistency
Sau khi Command (debit/credit) hoàn thành, có một khoảng trễ nhỏ (~50-200ms) trước khi
Redis read model được cập nhật. Đây chính là **eventual consistency** — đặc trưng của CQRS
với async event propagation.

---

## Yêu cầu

- Docker Desktop đang chạy
- Port 5432, 6379, 9092, 9094, 8083, 8082 còn trống

---

## Bước 1: Khởi động infrastructure

```bash
docker compose up -d postgres redis kafka kafka-connect
```

Đợi tất cả healthy (~60 giây):

```bash
docker compose ps
# postgres       ← healthy
# mt-redis       ← healthy
# mt-kafka       ← healthy
# mt-kafka-connect ← healthy (lâu nhất, ~60s)
```

---

## Bước 2: Đăng ký Debezium connector

```bash
sh docker/debezium/register-connector.sh
```

Verify connector đang chạy:

```bash
curl -s http://localhost:8083/connectors/account-connector/status | python -m json.tool
```

Expected output:
```json
{
  "name": "account-connector",
  "connector": { "state": "RUNNING" },
  "tasks": [{ "state": "RUNNING" }]
}
```

---

## Bước 3: Khởi động account-service

```bash
./gradlew :account-service:bootRun
```

---

## Bước 4: Demo luồng CQRS

### 4.1 — Mở terminal theo dõi Kafka topic (real-time CDC events)

```bash
docker exec mt-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic account_db.public.accounts \
  --from-beginning
```

Để terminal này mở để xem CDC events khi có thay đổi.

---

### 4.2 — Query (đọc từ Redis)

Đọc balance tài khoản `1000000001` — lần đầu sẽ **cache MISS**, fallback về PostgreSQL và warm Redis:

```bash
curl -s -X POST http://localhost:8082/api/v1/accounts/check-balance \
  -H "Content-Type: application/json" \
  -d '{"accountNo":"1000000001"}' | python -m json.tool
```

Log của service sẽ in:
```
[QUERY] Cache MISS for accountNo=1000000001, falling back to PostgreSQL
```

Gọi lần 2 — **cache HIT**:
```
[QUERY] Balance cache HIT for accountNo=1000000001
```

---

### 4.3 — Command (Debit) → observe CDC event → Redis updated

**Bước 1**: Debit 100,000 VND:

```bash
curl -s -X POST http://localhost:8082/api/v1/accounts/debit \
  -H "Content-Type: application/json" \
  -d '{"accountNo":"1000000001","amount":100000}' | python -m json.tool
```

**Bước 2**: Quan sát terminal Kafka — CDC event xuất hiện ngay:
```json
{
  "account_no": "1000000001",
  "balance": "4900000.00",
  "available_balance": "4900000.00",
  ...
}
```

Log của service:
```
[COMMAND] Debit executed: accountNo=1000000001, amount=100000, newBalance=4900000.00 → CDC → Redis
Read model synced via Debezium CDC: accountNo=1000000001, balance=4900000.00
```

**Bước 3**: Kiểm tra Redis đã cập nhật:

```bash
docker exec -it mt-redis redis-cli GET "account:1000000001"
```

---

### 4.4 — Chứng minh eventual consistency

Để demo rõ hơn, sau khi debit xong, **đọc Redis trước khi consumer kịp xử lý**:
Trong thực tế với latency thấp (~100ms), Redis thường đã cập nhật rồi. Nhưng có thể
thấy log `[QUERY] Cache HIT` với balance cũ rồi sau đó balance mới.

---

### 4.5 — Credit (Saga compensation refund)

```bash
curl -s -X POST http://localhost:8082/api/v1/accounts/credit \
  -H "Content-Type: application/json" \
  -d '{"accountNo":"1000000001","amount":100000,"referenceId":"REFUND-001"}' | python -m json.tool
```

---

## Bước 5: Kiểm tra trực tiếp các store

### Redis read model:
```bash
# Xem toàn bộ keys account
docker exec mt-redis redis-cli KEYS "account:*"

# Đọc read model cụ thể
docker exec mt-redis redis-cli GET "account:1000000001"

# Xem dưới dạng đẹp hơn
docker exec mt-redis redis-cli GET "account:1000000001" | python -m json.tool
```

### PostgreSQL write store:
```bash
docker exec mt-postgres psql -U postgres -d account_db \
  -c "SELECT account_no, balance, available_balance FROM accounts WHERE account_no='1000000001';"
```

### Kafka topic (toàn bộ lịch sử CDC):
```bash
docker exec mt-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic account_db.public.accounts \
  --from-beginning \
  --max-messages 10
```

---

## Cấu trúc code

```
account-service/
└── src/main/java/vn/trainocate/moneytransfer/account/
    ├── command/
    │   └── AccountCommandService.java      ← WRITE: createAccount, debit, credit, update
    ├── query/
    │   └── AccountQueryService.java        ← READ: Redis hit → PG fallback
    ├── readmodel/
    │   ├── AccountReadModel.java           ← POJO Redis JSON (key: account:{no})
    │   └── AccountRedisRepository.java     ← RedisTemplate<String, AccountReadModel>
    ├── event/
    │   ├── DebeziumAccountPayload.java     ← CDC flat JSON (ExtractNewRecordState)
    │   └── AccountEventConsumer.java       ← @KafkaListener, manual ack
    ├── config/
    │   ├── RedisConfig.java                ← Jackson2JsonRedisSerializer
    │   └── KafkaConsumerConfig.java        ← MANUAL ack ContainerFactory
    └── controller/
        └── AccountController.java          ← Routes Command ↔ Query
```

### AccountController — điểm vào
```
Commands  →  AccountCommandService  →  PostgreSQL  →  WAL
                                                        ↓
                                                    Debezium
                                                        ↓
                                               Kafka topic
                                                        ↓
                                          AccountEventConsumer  →  Redis

Queries   →  AccountQueryService  →  Redis (hit)
                                  ↘  PostgreSQL (miss, warm cache)
```

---

## Debezium Connector config

File: `docker/debezium/register-connector.sh`

| Config | Giá trị | Lý do |
|--------|---------|-------|
| `plugin.name` | `pgoutput` | Built-in PostgreSQL logical decoding (không cần cài thêm) |
| `decimal.handling.mode` | `string` | Giữ precision BigDecimal, không mất chữ số |
| `time.precision.mode` | `connect` | Timestamp → epoch millis (dễ parse hơn microseconds) |
| `transforms.unwrap.type` | `ExtractNewRecordState` | Unwrap Debezium envelope → flat JSON |
| `transforms.unwrap.drop.tombstones` | `false` | Giữ tombstone messages khi DELETE |

### Tại sao cần `wal_level=logical`?

PostgreSQL mặc định dùng `wal_level=replica` — chỉ đủ cho streaming replication.
Debezium cần `wal_level=logical` để đọc được nội dung row-level changes (INSERT/UPDATE/DELETE)
từ WAL thông qua logical decoding plugin.

---

## Kafka Consumer — thiết kế

### Manual Acknowledgment (quan trọng)
```java
@KafkaListener(topics = "account_db.public.accounts")
public void consume(String message, Acknowledgment ack) {
    try {
        // xử lý...
    } catch (Exception e) {
        log.error("...");
        // KHÔNG re-throw — để ack vẫn chạy
    } finally {
        ack.acknowledge();  // luôn luôn ack
    }
}
```

Nếu không ack, Kafka sẽ redeliver message vô hạn → infinite loop.
Nếu xử lý lỗi thì log ra, chấp nhận eventual inconsistency tạm thời (Debezium
sẽ retry khi reconnect).

---

## Troubleshooting

### Connector không start được
```bash
# Kiểm tra logs kafka-connect
docker logs mt-kafka-connect --tail 50

# Thường gặp: PostgreSQL chưa bật wal_level=logical
# Fix: restart postgres (đã được fix trong docker-compose.yml với command: postgres -c wal_level=logical)
docker compose restart postgres
docker compose restart kafka-connect
sh docker/debezium/register-connector.sh
```

### Redis không có data
```bash
# Kiểm tra consumer đang chạy
docker logs $(docker ps -q --filter name=account-service) | grep "Read model synced"

# Kiểm tra Kafka topic có messages không
docker exec mt-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic account_db.public.accounts --from-beginning --max-messages 5
```

### account-service không kết nối được Kafka
```bash
# Khi chạy local (không phải Docker), Kafka exposed ở port 9094
# application.yml: bootstrap-servers: localhost:9094
# Kiểm tra port:
docker exec mt-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
```

---

## So sánh: Trước và sau CQRS

| | Trước (AccountService) | Sau (CQRS) |
|---|---|---|
| **Đọc balance** | PostgreSQL (mỗi request) | Redis O(1) |
| **Ghi debit/credit** | PostgreSQL | PostgreSQL (không đổi) |
| **Consistency** | Strong (sync) | Eventual (async ~100ms) |
| **Read scalability** | Tăng tải DB | Redis handle read spikes |
| **Audit trail** | Không có | Kafka topic = event log |
| **Recovery** | Restart service | Consumer replay từ `--from-beginning` |

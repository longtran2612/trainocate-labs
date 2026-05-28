# Observability — Jaeger + ELK + Prometheus/Grafana

## Kiến trúc tổng quan

```
┌──────────────────────────────────────────────────────────────────┐
│                    Money Transfer Platform                        │
│                                                                  │
│  auth  account  kyc  limit  transaction  transfer  onboarding   │
│   │        │     │     │        │            │          │        │
└───┼────────┼─────┼─────┼────────┼────────────┼──────────┼────────┘
    │        │     │     │        │            │          │
    ├── /actuator/prometheus ──────────────────┴──────────┤
    │                                              Metrics │
    │                                                      ▼
    │                                              Prometheus :9090
    │                                                      │
    │                                              Grafana :3000
    │
    ├── stdout (ECS JSON) ─── Filebeat ─── Logstash :5044
    │         Logs                                     │
    │                                           Elasticsearch :9200
    │                                                  │
    │                                           Kibana :5601
    │
    └── OTel Agent (javaagent) ─── OTLP HTTP ──► Jaeger :4318
              Traces                                   │
                                               Jaeger UI :16686
```

---

## 1. Distributed Tracing — OpenTelemetry + Jaeger

### Cơ chế

Không dùng Zipkin hay Brave. Toàn bộ tracing được xử lý bởi **OpenTelemetry Java Agent** — một `.jar` attach vào JVM, **không cần thay đổi code**.

```
JVM startup:
  java -javaagent:/app/otel-javaagent.jar -jar app.jar

Agent tự động instrument:
  - Spring MVC (HTTP request/response span)
  - Spring Kafka (producer/consumer span)
  - JDBC (query span)
  - OpenFeign (client span, propagate trace header)
  - Scheduler, async methods
```

### Dockerfile (multi-stage)

```dockerfile
# Stage 1: lấy OTel agent từ image chính thức
FROM ghcr.io/open-telemetry/opentelemetry-java-instrumentation/opentelemetry-javaagent:2.14.0 AS otel-agent

# Stage 2: runtime
ARG JRE_VERSION=25
FROM eclipse-temurin:${JRE_VERSION}-jre-alpine
COPY --from=otel-agent /javaagent.jar /app/otel-javaagent.jar
# ...
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -javaagent:/app/otel-javaagent.jar -jar app.jar"]
```

### Cấu hình (env vars, không cần Spring config)

| Env var | Giá trị | Mô tả |
|---|---|---|
| `OTEL_SERVICE_NAME` | `auth-service` | Tên service hiện trên Jaeger UI |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://jaeger:4318` | Jaeger OTLP HTTP endpoint |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `http/protobuf` | Giao thức export |
| `OTEL_TRACES_SAMPLER` | `parentbased_always_on` | Sample 100% traces |

Agent cũng inject `trace_id` / `span_id` vào MDC → tự động xuất hiện trong ECS JSON logs.

### Jaeger UI

```
http://localhost:16686
```

**Tìm trace:**
1. Service dropdown → chọn service (vd: `transaction-service`)
2. Click "Find Traces"
3. Click vào trace → xem waterfall span qua các service

**Ví dụ trace khi gọi `POST /api/v1/transactions`:**
```
transaction-service  [POST /api/v1/transactions]     120ms
  ├── kafka publish [saga.transfer.command]             2ms
  │
  account-service    [kafka consume saga command]      45ms
  │   └── postgresql [SELECT/UPDATE account]            5ms
  │
  kyc-service        [kafka consume saga command]      30ms
  │
  limit-service      [kafka consume saga command]      25ms
  │
  transaction-service [kafka consume saga replies]     10ms
```

---

## 2. Logging — ELK Stack (Elasticsearch + Logstash + Kibana)

### Spring Boot → ECS JSON

Spring Boot 3.4+ hỗ trợ structured logging native, **không cần dependency bổ sung**:

```yaml
# application.yml
logging:
  structured:
    format:
      console: ecs   # Elastic Common Schema JSON
```

Output mỗi log line là một JSON object:
```json
{
  "@timestamp": "2024-01-15T10:23:45.123Z",
  "log.level": "INFO",
  "log.logger": "vn.trainocate.transaction.service.SagaService",
  "message": "Saga COMPLETED for transactionId=TXN-001",
  "service.name": "transaction-service",
  "process.pid": 1,
  "process.thread.name": "kafka-consumer-1",
  "trace.id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span.id": "00f067aa0ba902b7"
}
```

`trace.id` và `span.id` được OTel agent inject qua MDC → xuất hiện tự động trong mọi log line.

### Pipeline: Filebeat → Logstash → Elasticsearch

```
Container stdout
      │
      ▼ (Docker JSON log file)
/var/lib/docker/containers/<id>/<id>-json.log
      │
      ▼
Filebeat (đọc container logs, thêm Docker metadata)
      │  output.logstash: hosts: ["logstash:5044"]
      ▼
Logstash (parse ECS JSON, promote fields)
      │  index: "money-transfer-YYYY.MM.dd"
      ▼
Elasticsearch :9200
      │
      ▼
Kibana :5601
```

**Filebeat** (`docker/filebeat/filebeat.yml`):
```yaml
filebeat.inputs:
  - type: container
    paths: ['/var/lib/docker/containers/*/*.log']
    processors:
      - add_docker_metadata:
          host: "unix:///var/run/docker.sock"
```

**Logstash pipeline** (`docker/logstash/pipeline/logstash.conf`):
```ruby
input { beats { port => 5044 } }

filter {
  json { source => "message" target => "ecs" }
  mutate {
    add_field => {
      "trace_id"     => "%{[ecs][trace.id]}"
      "service_name" => "%{[ecs][service.name]}"
      "log_level"    => "%{[ecs][log.level]}"
    }
  }
}

output {
  elasticsearch {
    hosts => ["elasticsearch:9200"]
    index => "money-transfer-%{+YYYY.MM.dd}"
  }
}
```

### Kibana — Tìm log

```
http://localhost:5601
```

**Setup lần đầu:**
1. Menu → Stack Management → Index Patterns → Create
2. Pattern: `money-transfer-*`, Time field: `@timestamp`
3. Menu → Discover → chọn index pattern vừa tạo

**Filter theo trace:**
```
trace_id : "4bf92f3577b34da6a3ce929d0e0e4736"
```

**Filter theo service:**
```
service_name : "transaction-service" AND log_level : "ERROR"
```

**Correlation với Jaeger:**
- Copy `trace.id` từ Jaeger span
- Paste vào Kibana Discover filter → thấy toàn bộ log của request đó trên tất cả services

---

## 3. Metrics — Prometheus + Grafana

### Spring Boot actuator

Mỗi service expose metrics tại `/actuator/prometheus` (Micrometer + Prometheus format):
- `http_server_requests_seconds` — latency histogram theo path/status
- `jvm_memory_used_bytes` — JVM heap/non-heap
- `kafka_consumer_fetch_manager_records_consumed_total` — Kafka throughput
- `hikaricp_connections_active` — connection pool
- Custom tags: `application=<service-name>` trên tất cả metrics

### Prometheus

```
http://localhost:9090
```

Config scrape mỗi 15s tất cả 9 services (`docker/prometheus/prometheus.yml`):
```yaml
scrape_configs:
  - job_name: money-transfer
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - auth-service:8081
          # ... 8 services khác
```

**Query ví dụ:**
```promql
# Request rate (req/s) theo service
rate(http_server_requests_seconds_count[1m])

# P99 latency theo service
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))

# Error rate
rate(http_server_requests_seconds_count{status=~"5.."}[1m])
  / rate(http_server_requests_seconds_count[1m])

# Kafka consumer lag
kafka_consumer_fetch_manager_records_lag
```

### Grafana

```
http://localhost:3000
admin / admin
```

Datasource Prometheus được provision tự động qua `docker/grafana/provisioning/datasources/datasources.yml`.

**Dashboards đề xuất import (từ grafana.com):**

| Dashboard ID | Tên | Mô tả |
|---|---|---|
| `4701` | JVM Micrometer | JVM heap, GC, thread |
| `11378` | Spring Boot Stats | HTTP req, error rate, latency |
| `7589` | Kafka Overview | Consumer lag, throughput |
| `1860` | Node Exporter Full | Host CPU/Memory/Disk |

**Import dashboard:**
1. Grafana → Dashboards → Import
2. Nhập Dashboard ID → Load → Chọn Prometheus datasource → Import

---

## 4. Ports tổng hợp

| Tool | Port | Mô tả |
|---|---|---|
| Jaeger UI | `16686` | Xem distributed traces |
| Jaeger OTLP HTTP | `4318` | Nhận traces từ OTel agent |
| Elasticsearch | `9200` | Log storage (API) |
| Kibana | `5601` | Log visualization |
| Logstash | `5044` | Beats input |
| Prometheus | `9090` | Metrics storage + query |
| Grafana | `3000` | Metrics dashboards |

---

## 5. Chạy local (docker-compose)

```bash
# Khởi động toàn bộ stack (lần đầu build ~5 phút vì OTel agent ~14MB)
docker-compose up -d

# Kiểm tra status
docker-compose ps

# Xem log service cụ thể (ECS JSON format)
docker-compose logs -f auth-service

# Kiểm tra metrics endpoint
curl http://localhost:8081/actuator/prometheus | head -20
```

### Checklist verify

- [ ] Prometheus `http://localhost:9090/targets` → tất cả 9 services `State: UP`
- [ ] Grafana `http://localhost:3000` → Add datasource Prometheus → Test & Save
- [ ] Gọi `POST /api/v1/transactions` → Jaeger UI → tìm trace `transaction-service`
- [ ] Kibana → Discover → filter `trace_id: <id từ Jaeger>` → thấy log từ nhiều services

---

## 6. Kubernetes (Helm)

```bash
helm upgrade --install money-transfer ./helm-chart \
  --namespace money-transfer --create-namespace
```

NodePorts:

| Tool | NodePort |
|---|---|
| Prometheus | `30090` |
| Grafana | `30030` |
| Jaeger UI | `30686` |
| Kibana | `30601` |

Prometheus tự discover pods qua annotation (không cần config tĩnh):
```yaml
# Mỗi pod trong helm-chart/templates/microservices.yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/path: "/actuator/prometheus"
  prometheus.io/port: "8081"
```

Disable observability (tiết kiệm tài nguyên):
```bash
helm upgrade money-transfer ./helm-chart \
  -f ./helm-chart/values-lite.yaml \
  --namespace money-transfer
```

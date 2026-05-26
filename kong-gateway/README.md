# Kong Gateway (Alternative to Spring Cloud Gateway)

Kong Gateway chạy ở port **8000** (proxy) thay thế cho Spring Cloud Gateway ở port 8080.

## So sánh

| Feature | Spring Cloud Gateway (port 8080) | Kong Gateway (port 8000) |
|---------|----------------------------------|--------------------------|
| Config | `application.yml` (Java) | `kong.yml` (declarative YAML) |
| Discovery | Eureka (`lb://service-name`) | Static upstream (`host.docker.internal:port`) |
| Plugins | Custom Java filters | Rate limiting, correlation-id, auth (built-in) |
| Deployment | Spring Boot JAR | Docker container |
| Admin API | N/A | REST API at port 8001 |
| Dashboard | N/A | Kong Manager at port 8002 |

## Cách chạy

### Yêu cầu
- Docker Desktop đang chạy
- Các microservice đã start trên localhost (port 8081-8087)

### Start Kong
```bash
cd kong-gateway
docker compose up -d
```

### Verify
```bash
# Health check
curl http://localhost:8001/status

# List routes
curl http://localhost:8001/routes

# Test qua Kong
curl -X POST http://localhost:8000/api/v1/accounts/inquiry \
  -H "Content-Type: application/json" \
  -d '{"accountNo": "1000000001"}'
```

### Stop
```bash
docker compose down
```

## Route mapping

| Path | Upstream Service | Port |
|------|-----------------|------|
| `/api/v1/auth/**` | auth-service | 8081 |
| `/api/v1/accounts/**` | account-service | 8082 |
| `/api/v1/kyc/**` | kyc-service | 8083 |
| `/api/v1/limits/**` | limit-service | 8084 |
| `/api/v1/transactions/**` | transaction-service | 8085 |

## Built-in plugins

- **correlation-id**: Tự động thêm `X-Correlation-ID` header cho mỗi request
- **rate-limiting**: auth-service 30 req/min, transaction-service 60 req/min

## Lưu ý

- Kong chạy DB-less mode (declarative config) — không cần PostgreSQL cho Kong
- Sau khi sửa `kong.yml`, reload config:
  ```bash
  docker compose restart kong
  ```
- Kong **không dùng Eureka** — upstream là static IP:port. Nếu service port thay đổi, phải cập nhật `kong.yml`
- Cả hai gateway có thể chạy song song: Spring Gateway (8080) + Kong (8000)

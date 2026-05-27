# Docker Deployment Guide

## Architecture

```
Client :3000 → nginx → api-gateway :8080 → Eureka → microservices → PostgreSQL
```

| Container              | Port | Description              |
|------------------------|------|--------------------------|
| `mt-postgres`          | 5432 | PostgreSQL + 5 databases |
| `mt-eureka`            | 8761 | Eureka Service Discovery |
| `mt-auth`              | 8081 | Auth Service             |
| `mt-account`           | 8082 | Account Service          |
| `mt-kyc`               | 8083 | KYC Service              |
| `mt-limit`             | 8084 | Limit Service            |
| `mt-transaction`       | 8085 | Transaction Service      |
| `mt-internal-transfer` | 8086 | Internal Transfer        |
| `mt-external-transfer` | 8087 | External Transfer        |
| `mt-napas`             | 8088 | NAPAS Simulator          |
| `mt-gateway`           | 8080 | API Gateway              |
| `mt-web`               | 3000 | React Frontend (nginx)   |

All containers share the `money-transfer-net` Docker network.  
Frontend (nginx) proxies `/api/*` → `api-gateway:8080` → Eureka discovery → individual services.

## Prerequisites

- Docker Desktop installed and running
- Java 25 + Gradle (for building JARs)
- At least 6 GB RAM allocated to Docker

## Quick Start

### 1. Build JARs

```powershell
cd E:\microservice\trainocate-labs
.\gradlew.bat bootJar
```

### 2. Start Backend

```powershell
docker compose up -d --build
```

Wait for all services to be healthy:

```powershell
docker compose ps
```

### 3. Start Frontend

```powershell
docker compose -f docker-compose.fe.yml up -d --build
```

### 4. Access

- Frontend: http://localhost:3000
- API Gateway: http://localhost:8080
- Eureka Dashboard: http://localhost:8761

## Demo Accounts

| Account No   | Name         | Password |
|--------------|--------------|----------|
| 1000000001   | Nguyen Van A | 123456   |
| 1000000002   | Tran Van B   | 123456   |
| 1000000003   | Le Thi C     | 123456   |

## Useful Commands

```powershell
# View logs for a specific service
docker compose logs -f auth-service

# Restart a single service
docker compose restart account-service

# Stop everything
docker compose down
docker compose -f docker-compose.fe.yml down

# Stop and remove volumes (reset databases)
docker compose down -v

# Rebuild a single service after code change
.\gradlew.bat :account-service:bootJar
docker compose up -d --build account-service
```

## Files

| File                              | Purpose                            |
|-----------------------------------|------------------------------------|
| `Dockerfile`                      | Shared Dockerfile for all services |
| `docker-compose.yml`              | Backend: PostgreSQL + 10 services  |
| `docker-compose.fe.yml`           | Frontend: React + nginx            |
| `docker/postgres/init-databases.sql` | Creates 5 PostgreSQL databases  |
| `money-transfer-web/Dockerfile`   | Multi-stage: npm build → nginx     |
| `money-transfer-web/nginx.conf`   | SPA routing + API proxy            |

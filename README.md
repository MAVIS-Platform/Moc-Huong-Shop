# Mộc Hương Shop Microservices (`moc-huong-shop-microservices`)

Kho mã nguồn mẫu chuẩn hóa (**Curated Reference Repository**) cho hệ sinh thái **Java 21 + Spring Boot 3.3.5 + Spring Cloud 2023.0.3**, miền nghiệp vụ thương mại điện tử **Mộc Hương Shop** (Nhang trầm hương, Trầm nụ thác khói, Phụ kiện xông trầm), được thiết kế tối ưu cho nền tảng phân tích & mô phỏng kiến trúc **MAVIS**.

---

## 1. Kiến trúc Tổng thể (C2 Container Diagram)

```text
                       [Customer / Staff / Admin]
                                   |
                         HTTP (Port 8080)
                                   v
                      +-------------------------+
                      |       api-gateway       | <---> [service-discovery (Eureka: 8761)]
                      |  Spring Cloud Gateway   |
                      |  JWT Filter + Corr-ID   |
                      +-------------------------+
                         /       |           \
           (lb://)      /        | (lb://)    \ (lb://)
                       v         v             v
          +---------------+ +---------------+ +-------------------+
          | identity-svc  | | catalog-svc   | |   order-service   |
          | (Internal8081)| | (Internal8082)| |  (Internal 8083)  |
          +---------------+ +---------------+ +-------------------+
                 |             |        |       |        |       \
                 v             v        v       |        |        \ (Async Kafka:
          [(identity_db)] [(catalog_db)][Redis] |        |         \  order.created,
                                                |        |          \ order.cancelled)
            Sync OpenFeign (CALL_SITE)          |        v           v
            @CircuitBreaker + @Retry            |   [(order_db)]  +--------------+
            order-service ──────────────────────+                 | Apache Kafka |
            --> catalog-service                                   |  3.7 KRaft   |
                                                                  +--------------+
                                                                         |
                                                                         v (Idempotent Consumer)
                                                                  +----------------------+
                                                                  | notification-service |
                                                                  |   (Internal 8084)    |
                                                                  +----------------------+
                                                                         |
                                                                         v
                                                                 [(notification_db)]
```

### Nguyên tắc thiết kế cốt lõi
- **Pure Java 21 (Không Lombok)**: Toàn bộ DTO/Event dùng Java `record`, Dependency Injection dùng Explicit Constructor Injection để JavaParser AST đọc sạch 100%.
- **Strict C3 Layering**: `@RestController` chỉ gọi `@Service`, không tiêm trực tiếp `@Repository`. Mọi lỗi trả về chuẩn RFC 7807 `ProblemDetail`.
- **Database-per-Service tuyệt đối**: 4 database PostgreSQL 16 độc lập (`identity_db`, `catalog_db`, `order_db`, `notification_db`) quản lý schema qua Flyway 10.
- **Scale-Safe cho MAVIS Studio (`1 -> 8`)**: Không dùng `container_name:`, 4 business services chỉ dùng `expose:` (không khóa cổng host cố định).

---

## 2. Chạy nhanh toàn bộ hệ thống (1 lệnh)

```bash
cp .env.example .env
docker compose up --build -d
```

Kiểm tra trạng thái dịch vụ & Eureka Dashboard:
- API Gateway: `http://localhost:8080/actuator/health`
- Eureka Dashboard: `http://localhost:8761`

---

## 3. Kịch bản kiểm thử End-to-End bằng `curl`

### Bước 1: Đăng nhập lấy JWT (`customer@mochuong.vn` / `MocHuong@123`)
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"customer@mochuong.vn","password":"MocHuong@123"}' | jq -r '.accessToken')
echo "$TOKEN"
```

### Bước 2: Xem danh mục & sản phẩm trầm hương (Public Route + Redis Cache-Aside)
```bash
curl -s http://localhost:8080/api/v1/categories | jq
curl -s http://localhost:8080/api/v1/products | jq
curl -s http://localhost:8080/api/v1/products/1 | jq
```

### Bước 3: Đặt hàng (Orchestration Saga + Idempotency-Key + Transactional Outbox)
```bash
curl -i -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-lab-001" \
  -d '{
    "shippingAddress": "123 Nguyen Hue, Quan 1, TP.HCM",
    "items": [
      { "productId": 1, "quantity": 2 },
      { "productId": 3, "quantity": 1 }
    ]
  }'
```

### Bước 4: Kiểm tra thông báo bất đồng bộ (Kafka Idempotent Consumer)
```bash
curl -s http://localhost:8080/api/v1/notifications/my \
  -H "Authorization: Bearer $TOKEN" | jq
```

### Bước 5: Thử nghiệm Stop & Scale trên MAVIS Studio
1. **Fail-Fast với Resilience4j CircuitBreaker**:
   ```bash
   docker compose stop catalog-service
   curl -i -X POST http://localhost:8080/api/v1/orders \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -H "Idempotency-Key: order-lab-degraded" \
     -d '{"shippingAddress":"Ha Noi","items":[{"productId":1,"quantity":1}]}'
   # Ket qua: HTTP 503 Service Unavailable (ProblemDetail) - khong treo thread!
   ```
2. **Scale ngang nhiều instance `catalog-service`**:
   ```bash
   docker compose up -d --scale catalog-service=3
   ```

---

## 4. Bảng ánh xạ 12 Chương Giáo trình Java Microservices MAVIS

| Chương | Chủ đề Giáo trình | Hiện thực cụ thể trong Repository |
|---|---|---|
| Ch. 1–2 | C1–C3 Architecture & Pure Java 21 | 6 modules tách biệt, `record` DTO, Constructor Injection, `Controller -> Service -> Repository` |
| Ch. 3 | Service Discovery & API Gateway | `service-discovery` (Eureka 8761) + `api-gateway` (Routes `lb://`, `CorrelationIdGatewayFilter`) |
| Ch. 4 | Centralized Auth & Anti-Spoofing | `identity-service` (BCrypt + JJWT `0.12.6`) + `JwtAuthenticationGatewayFilter` xóa header giả mạo |
| Ch. 5 | Database-per-Service & Flyway | 4 PostgreSQL DBs riêng biệt + Flyway 10 (`flyway-core` + `flyway-database-postgresql`) |
| Ch. 6 | Domain Model & Optimistic Locking | `Product` (`@Version`, `reserveStock`, `releaseStock`) & `Order` Aggregate (`OrderItem` snapshot) |
| Ch. 7 | Cache-Aside với Redis | `RedisCacheConfig` + `@Cacheable("products")` / `@CacheEvict("products")` tại `ProductService` |
| Ch. 8 | Synchronous IPC & Contract (`CALL_SITE`) | `CatalogFeignClient` (`@FeignClient(name="catalog-service")`) gọi trong `CatalogIntegrationService` |
| Ch. 9 | Resilience4j (CircuitBreaker, Retry, Bulkhead) | `spring-boot-starter-aop` + `@CircuitBreaker` / `@Retry` / `@Bulkhead` + Fallback `503` |
| Ch. 10 | Orchestration Saga & Compensation | `OrderSagaOrchestrator`: `PENDING` Semantic Lock, `reserveStock`, bù trừ ngược `releaseStock` -> `CANCELLED` |
| Ch. 11 | Transactional Outbox & Idempotent Consumer | `OutboxEvent` + `OutboxEventPublisher` (`orderId` key) -> Kafka KRaft -> `OrderEventConsumer` (`processed_events`) |
| Ch. 12 | Observability, RFC 7807 & Scale-Safe Docker | Actuator `/liveness` & `/readiness`, Micrometer OTel OTLP, `ProblemDetail`, Multi-stage non-root Dockerfiles |

---

## 5. Tài khoản Demo mặc định (Chỉ dùng cho Lab cục bộ)
- **ADMIN**: `admin@mochuong.vn` / `MocHuong@123`
- **STAFF**: `staff@mochuong.vn` / `MocHuong@123`
- **CUSTOMER**: `customer@mochuong.vn` / `MocHuong@123`

# 🏛️ ARCHITECTURE.md — MỘC HƯƠNG SHOP MICROSERVICES (CURATED JAVA LAB FOR MAVIS)

> **Tên kho mã nguồn mới**: `moc-huong-shop-microservices`
> **Vai trò**: Kho mã nguồn mẫu chuẩn hóa (Curated Reference Repository) cho khóa học **Java 21 & Spring Cloud Microservices (`JAVA-MS-V2`)** trên nền tảng **MAVIS**.
> **Miền nghiệp vụ (Domain)**: Cửa hàng trực tuyến **Mộc Hương Shop** chuyên kinh doanh trầm hương và hương liệu tự nhiên (*Nhang trầm không tăm, Trầm nụ thác khói, Lư xông đồng, Tinh dầu gỗ quý*).
> **Mục tiêu chất lượng**: Đạt trọn vẹn **10/10 tiêu chí** Giáo trình Java 12 Chương, tối ưu cho bộ phân tích tĩnh **JavaParser AST** (`C1–C3`, `3E Rules`, `CALL_SITE`, `Reverse BFS`), chạy mượt **Docker Runtime + OpenTelemetry** và sẵn sàng thực nghiệm **Stop / Scale (1 → 8)** trên **MAVIS Studio**.

---

## 1. STACK CÔNG NGHỆ & PHẦN MỀM CHỐT CỨNG (LOCKED VERSIONS)

Mọi module đều kế thừa từ **Root Multi-Module Maven `pom.xml`** để đảm bảo đồng bộ phiên bản tuyệt đối:

| Thành phần | Công nghệ / Phiên bản chốt cứng | Lý do kỹ thuật & Lưu ý sống còn |
|---|---|---|
| **Ngôn ngữ** | **Java 21 LTS** (`<java.version>21</java.version>`) | Dùng `record` DTO, pattern matching, switch expression. |
| **Framework Core** | **Spring Boot `3.3.5`** | Ổn định, hỗ trợ `ProblemDetail` (RFC 7807) & Virtual Threads. |
| **Microservices Cloud** | **Spring Cloud `2023.0.3`** | Khớp BOM 100% với Spring Boot 3.3.x (Gateway, Eureka, OpenFeign, LoadBalancer). |
| **Resilience** | **Resilience4j `2.2.0`** (`resilience4j-spring-boot3`) + **`spring-boot-starter-aop`** | **BẮT BUỘC có `spring-boot-starter-aop`** để `@CircuitBreaker`, `@Retry`, `@Bulkhead` hoạt động thật! |
| **Database & Migration** | **PostgreSQL `16-alpine`** + **Flyway** (`flyway-core` + **`flyway-database-postgresql`**) | **BẮT BUỘC có `flyway-database-postgresql`** cho Flyway 10 trong Spring Boot 3.3+! |
| **Cache** | **Redis `7-alpine`** (`spring-boot-starter-data-redis`) | Cache-Aside (`@Cacheable` / `@CacheEvict`) tại `catalog-service`. |
| **Message Broker** | **Apache Kafka `3.7.0` (KRaft mode — KHÔNG dùng Zookeeper)** | Nhẹ RAM (~300MB), khởi động nhanh trong MAVIS Sandbox. |
| **Security** | **JJWT `0.12.6`** (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`) + BCrypt | Stateless JWT ký `HMAC-SHA256`, xác thực tập trung ở `api-gateway`. |
| **Observability** | **Spring Boot Actuator** + **Micrometer Prometheus** + **`micrometer-tracing-bridge-otel`** + **`opentelemetry-exporter-otlp`** | Sẵn sàng `/actuator/health/liveness`, `/readiness`, `traceId` trong log và đẩy OTLP trace cho MAVIS. |
| **Code Style Rule** | **KHÔNG DÙNG LOMBOK (Pure Java 21)** | Dùng `record` cho DTO/Event và explicit Constructor Injection để bộ quét **JavaParser AST** của MAVIS đọc sạch 100% không bị thiếu getter/constructor ẩn. |

### 🚫 Danh mục CẮT BỎ hoàn toàn (So với bản thương mại gốc)
Để hệ thống tự chạy 100% offline/sandbox trong Docker mà không kẹt khóa ngoài:
- **KHÔNG** dùng Aiven Cloud DB, VNPay, Cloudinary, Gmail SMTP, Google Gemini AI, iText7 PDF, Apache POI Excel.
- **KHÔNG** chứa thư mục Frontend Next.js (`moc-huong-frontend`) trong repo backend lab này.
- **KHÔNG** commit bất kỳ file prompt, mật khẩu thật, hoặc API key nào lên Git.

---

## 2. KIẾN TRÚC TỔNG THỂ (C1–C3) & RANH GIỚI 6 MICROSERVICES

### 2.1. Sơ đồ Container (C2)

```text
                       [Customer / Staff / Admin]
                                   |
                         HTTPS / HTTP (Port 8080)
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
          |  (Port 8081)  | |  (Port 8082)  | |    (Port 8083)    |
          +---------------+ +---------------+ +-------------------+
                 |             |        |       |        |       \
                 v             v        v       |        |        \ (Async Kafka:
          [(identity_db)] [(catalog_db)][Redis] |        |         \  order.created,
                                                |        |          \ order.cancelled)
            Sync OpenFeign (CALL_SITE!)         |        v           v
            @CircuitBreaker + @Retry            |   [(order_db)]  +--------------+
            order-service ──────────────────────+                 | Apache Kafka |
            --> catalog-service                                   |   (KRaft)    |
                                                                  +--------------+
                                                                         |
                                                                         v (Idempotent Consumer)
                                                                  +----------------------+
                                                                  | notification-service |
                                                                  |     (Port 8084)      |
                                                                  +----------------------+
                                                                         |
                                                                         v
                                                                 [(notification_db)]
```

### 2.2. Kiểm chứng Topology cho MAVIS Static & Reverse BFS (`D2`, `D4`, `3E`)
- **Database-per-Service tuyệt đối**: 4 database container PostgreSQL độc lập (`identity-db`, `catalog-db`, `order-db`, `notification-db`) $\rightarrow$ **0 cảnh báo `SHARED_DATABASE`**.
- **Đồ thị tác động Reverse BFS giàu ngữ nghĩa**:
  - Target = **`catalog-service`**:
    - `Direct Callers (hop = 1)`: `order-service` (qua `CatalogFeignClient` có bằng chứng `CALL_SITE` rõ ràng tại `CatalogIntegrationService.java`), `api-gateway` (qua route `lb://catalog-service`).
    - Demo **MAVIS Studio Stop `catalog-service`**: `order-service` kích hoạt Resilience4j `@CircuitBreaker` + `@Retry` + `Fallback` trả về `503 Service Unavailable` nhanh chóng (*Fail-Fast*), không treo luồng.
  - Target = **`notification-service`**:
    - Không có dịch vụ nghiệp vụ nào gọi đồng bộ vào (`0 sync service callers` từ `order-service`).
    - Demo **MAVIS Studio Stop `notification-service`**: `order-service` vẫn đặt hàng thành công bình thường; sự kiện lưu an toàn trong Kafka và được tiêu thụ bù khi bật lại!
  - **Không có chu trình (`0 Cyclic Dependencies`)**: Đồ thị gọi xuôi là DAG thuần túy (`api-gateway -> order-service -> catalog-service`).

---

## 3. CHI TIẾT TỪNG MODULE & HỢP ĐỒNG API

### 3.1. Cấu trúc thư mục Root

```text
moc-huong-shop-microservices/
├── pom.xml                          # Parent POM (Java 21, Boot 3.3.5, Cloud 2023.0.3)
├── docker-compose.yml               # Full stack Sandbox (Healthchecks, Scale-safe, OTel-ready)
├── .env.example                     # Biến môi trường mẫu (không chứa secret thật)
├── .gitignore
├── README.md                        # Hướng dẫn chạy, curl test, bảng ánh xạ 12 chương Giáo trình
├── ARCHITECTURE.md
├── TASKS.md
├── service-discovery/               # Module 1: Eureka Server (8761)
├── api-gateway/                     # Module 2: Spring Cloud Gateway + JWT + Correlation ID (8080)
├── identity-service/                # Module 3: Auth, JWT Issuance, Users & RBAC (8081)
├── catalog-service/                 # Module 4: Nhang/Trầm Catalog + Stock + Redis Cache (8082)
├── order-service/                   # Module 5: Order Aggregate + Feign + Resilience4j + Saga + Outbox (8083)
└── notification-service/            # Module 6: Kafka Idempotent Consumer + Notification Log (8084)
```

> **Quy tắc package chuẩn**: Tất cả các lớp Java đặt dưới gốc `com.mochuong.<service>` (ví dụ `com.mochuong.catalog.controller`, `com.mochuong.catalog.service`, `com.mochuong.catalog.repository`, `com.mochuong.catalog.domain`, `com.mochuong.catalog.dto`, `com.mochuong.catalog.exception`, `com.mochuong.catalog.client`).
> **Quy tắc phân tầng C3 bắt buộc**: `@RestController` chỉ gọi `@Service` (tuyệt đối KHÔNG inject `@Repository` vào Controller). Mọi request/response đều dùng Java 21 `record` kèm `@Valid`. Mọi service đều có `GlobalExceptionHandler` (`@RestControllerAdvice`) trả `ProblemDetail` (RFC 7807).

---

### 3.2. Module 1: `service-discovery` (Port `8761`)
- Dependency: `spring-cloud-starter-netflix-eureka-server`, `spring-boot-starter-actuator`.
- Main class: `@EnableEurekaServer`.
- `application.yml`: `register-with-eureka: false`, `fetch-registry: false`, bật `/actuator/health/liveness` & `/readiness`.

---

### 3.3. Module 2: `api-gateway` (Port `8080`)
- Dependency: `spring-cloud-starter-gateway`, `spring-cloud-starter-netflix-eureka-client`, `spring-boot-starter-actuator`, `jjwt-*`, `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`.
- **Routes (`application.yml`)**:
  - `identity-service`: `/api/v1/auth/**`, `/api/v1/users/**` $\rightarrow$ `lb://identity-service`
  - `catalog-service`: `/api/v1/products/**`, `/api/v1/categories/**` $\rightarrow$ `lb://catalog-service`
  - `order-service`: `/api/v1/orders/**` $\rightarrow$ `lb://order-service`
  - `notification-service`: `/api/v1/notifications/**` $\rightarrow$ `lb://notification-service`
- **Global Filters**:
  1. `CorrelationIdGatewayFilter` (Order `-2`): Đọc hoặc sinh mới header `X-Correlation-Id` (UUID) gắn vào request và response.
  2. `JwtAuthenticationGatewayFilter` (Order `-1`):
     - Cho phép public: `POST /api/v1/auth/login`, `POST /api/v1/auth/register`, `GET /api/v1/products/**`, `GET /api/v1/categories/**`, `/actuator/health/**`.
     - Với route bảo vệ: kiểm tra `Authorization: Bearer <token>`, xác thực chữ ký bằng `${JWT_SECRET}` (HMAC-SHA256). Lỗi/thiếu trả `401 Unauthorized` dạng JSON `ProblemDetail`.
     - **Chống Header Spoofing**: Luôn gọi `headers.remove("X-User-Id")`, `headers.remove("X-User-Email")`, `headers.remove("X-User-Role")` trước khi gán giá trị giải mã từ JWT!

---

### 3.4. Module 3: `identity-service` (Internal Port `8081`, DB: `identity_db`)
- **Flyway Migration `V1__init_identity_schema.sql`**: Bảng `users` (`id UUID PK`, `email UNIQUE`, `password_hash`, `full_name`, `role VARCHAR(20)`, `created_at`).
- **Role Enum**: `CUSTOMER`, `STAFF`, `ADMIN`.
- **Seed Data (`V2__seed_demo_users.sql` hoặc `IdentityDataSeeder`)**:
  - `admin@mochuong.vn` (`ADMIN`)
  - `staff@mochuong.vn` (`STAFF`)
  - `customer@mochuong.vn` (`CUSTOMER`)
  - Mật khẩu demo mã hóa BCrypt (lấy từ biến môi trường hoặc `MocHuong@123` chỉ dành cho lab, ghi rõ trong README).
- **Endpoints**:
  - `POST /api/v1/auth/register` $\rightarrow$ `201 Created` (`AuthResponse` gồm `accessToken`, `userId`, `email`, `role`).
  - `POST /api/v1/auth/login` $\rightarrow$ `200 OK` (`AuthResponse`).
  - `GET /api/v1/users/me` $\rightarrow$ Đọc `X-User-Id` trả `UserProfileResponse` (`200 OK`).

---

### 3.5. Module 4: `catalog-service` (Internal Port `8082`, DB: `catalog_db`, Cache: `Redis`)
- **Domain**: Quản lý sản phẩm hương trầm Mộc Hương Shop.
- **Flyway Migration `V1__init_catalog_schema.sql` & `V2__seed_incense_products.sql`**:
  - Bảng `categories` (`id BIGSERIAL PK`, `code UNIQUE`, `name`, `description`).
  - Bảng `products` (`id BIGSERIAL PK`, `sku UNIQUE`, `name`, `description`, `unit_price NUMERIC(14,2)`, `stock_quantity INT`, `category_id BIGINT FK`, `version BIGINT NOT NULL DEFAULT 0`, `created_at`).
  - Seed sẵn 3 danh mục (*Nhang Trầm Hương*, *Trầm Nụ Thác Khói*, *Phụ Kiện Xông Trầm*) và 6 sản phẩm thực tế (ví dụ: `MH-NHANG-01` Nhang Trầm Không Tăm Cao Cấp, `MH-NU-01` Trầm Nụ Quế Lâm Hộp 40 Viên, `MH-LU-01` Lư Xông Trầm Đồng Cổ Điển...).
- **Entity `Product`**: Có `@Version private Long version;` (Optimistic Locking) và các phương thức domain `reserveStock(int qty)`, `releaseStock(int qty)` (tránh Anemic Domain Model).
- **Redis Cache-Aside**:
  - `@EnableCaching` + `RedisCacheManager` (TTL 10 phút, JSON serializer).
  - `@Cacheable(value = "products", key = "#id")` trên `ProductService.getById(Long id)`.
  - `@CacheEvict(value = "products", key = "#id")` trên `update`, `reserveStock`, `releaseStock`.
- **Endpoints**:
  - `GET /api/v1/categories`
  - `GET /api/v1/products` (hỗ trợ phân trang & lọc `categoryId`)
  - `GET /api/v1/products/{id}`
  - `POST /api/v1/products` (kiểm tra header `X-User-Role` phải là `ADMIN` hoặc `STAFF`, sai trả `403 Forbidden`)
  - `POST /api/v1/products/{id}/reserve` (`ReserveStockRequest(int quantity)` $\rightarrow$ `200 OK` hoặc `409 Conflict` nếu không đủ kho)
  - `POST /api/v1/products/{id}/release` (`ReleaseStockRequest(int quantity)` $\rightarrow$ `200 OK` dùng cho bù trừ Saga)

---

### 3.6. Module 5: `order-service` (Internal Port `8083`, DB: `order_db`)
Đây là **trái tim của các bài Lab C3, Reverse BFS, Saga, Outbox & Resilience4j**:
- **Flyway Migration `V1__init_order_schema.sql`**:
  - Bảng `orders` (`id UUID PK`, `customer_id UUID NOT NULL`, `customer_email VARCHAR(255)`, `shipping_address VARCHAR(500)`, `status VARCHAR(30)`, `total_amount NUMERIC(14,2)`, `idempotency_key VARCHAR(100) UNIQUE`, `failure_reason VARCHAR(500)`, `created_at`, `updated_at`).
  - Bảng `order_items` (`id BIGSERIAL PK`, `order_id UUID FK`, `product_id BIGINT`, `product_name_snapshot VARCHAR(200)`, `unit_price_snapshot NUMERIC(14,2)`, `quantity INT`).
  - Bảng `outbox_events` (`id UUID PK`, `aggregate_id VARCHAR(100)`, `topic VARCHAR(100)`, `payload TEXT`, `status VARCHAR(20)` [`PENDING`, `PUBLISHED`], `created_at`, `published_at`).
- **DDD Aggregate**: `Order` là Aggregate Root (tham chiếu `customerId` và `productId` bằng ID, lưu giá & tên snapshot trong `OrderItem`). Trạng thái `OrderStatus`: `PENDING` (Semantic Lock), `CONFIRMED`, `CANCELLED`.
- **Sync IPC (OpenFeign — Chuẩn `CALL_SITE`)**:
  - `CatalogFeignClient` với `@FeignClient(name = "catalog-service", path = "/api/v1/products")`:
    - `ProductSnapshotDto getProductById(@PathVariable("id") Long id);`
    - `void reserveStock(@PathVariable("id") Long id, @RequestBody StockAdjustmentRequest request);`
    - `void releaseStock(@PathVariable("id") Long id, @RequestBody StockAdjustmentRequest request);`
  - Cấu hình Feign timeouts trong `application.yml`: `connectTimeout: 2000`, `readTimeout: 3000`.
- **Resilience4j (`CatalogIntegrationService`)**:
  - Gắn `@CircuitBreaker(name = "catalogService", fallbackMethod = "getProductFallback")`, `@Retry(name = "catalogService")`, `@Bulkhead(name = "catalogService")` cho `fetchProduct(Long id)`.
  - Gắn `@CircuitBreaker(name = "catalogService", fallbackMethod = "reserveStockFallback")` cho `reserveStock(...)` (KHÔNG retry mù quáng cho hành động ghi chưa idempotent trừ khi chỉ bắt lỗi kết nối, hoặc giữ CircuitBreaker + Timeout gọn sạch).
  - Fallback ném `ServiceDegradedException` được `GlobalExceptionHandler` ánh xạ sang `HTTP 503 Service Unavailable` với `ProblemDetail` rõ ràng.
- **Orchestration Saga + Compensation + Transactional Outbox (`OrderSagaOrchestrator`)**:
  1. Kiểm tra `Idempotency-Key` header (nếu đã tồn tại trong `orders`, trả ngay kết quả cũ — tránh tạo trùng đơn).
  2. Lấy thông tin sản phẩm (tên + giá) từ `CatalogIntegrationService.fetchProduct(...)`.
  3. **T1 (Semantic Lock)**: Lưu `Order` với trạng thái `PENDING` vào `order_db`.
  4. **T2 (Reserve Stock)**: Duyệt từng item gọi `catalogIntegrationService.reserveStock(productId, qty)`, ghi nhớ danh sách `reservedItems`.
  5. **T3 (Pivot Transaction + Outbox)**: Nếu giữ kho đủ tất cả mặt hàng $\rightarrow$ chuyển `Order` thành `CONFIRMED`, đồng thời trong **cùng `@Transactional`** lưu `OutboxEvent` (`topic = "order.created"`, `status = PENDING`).
  6. **Compensation Path (Nếu bước T2 gặp lỗi hết kho hoặc `catalog-service` lỗi giữa chừng)**:
     - Duyệt ngược `reservedItems`, gọi `releaseStock` hoàn kho toàn bộ mặt hàng đã giữ trước đó.
     - Chuyển `Order` thành `CANCELLED` kèm `failureReason`, lưu `OutboxEvent` (`topic = "order.cancelled"`).
     - Ném ngoại lệ nghiệp vụ (`409 Conflict` nếu hết kho, `503 Service Unavailable` nếu dịch vụ suy giảm).
- **Outbox Relay (`OutboxEventPublisher`)**: `@Scheduled(fixedDelayString = "${outbox.poll-interval-ms:2000}")` đọc tối đa 50 bản ghi `PENDING`, gửi lên Kafka bằng `kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())` (dùng `orderId` làm Partition Key!), sau đó cập nhật `status = PUBLISHED`.
- **Endpoints**:
  - `POST /api/v1/orders` (nhận `X-User-Id`, `X-User-Email`, `Idempotency-Key` + `@Valid CreateOrderRequest` $\rightarrow$ `201 Created`).
  - `GET /api/v1/orders/{id}` (kiểm tra quyền sở hữu `order.customerId == X-User-Id` hoặc `ADMIN`/`STAFF`, chống IDOR $\rightarrow$ `403 Forbidden`).
  - `GET /api/v1/orders/my` (lấy danh sách đơn hàng của user hiện tại).
  - `POST /api/v1/orders/{id}/cancel` (hủy đơn đã xác nhận + hoàn kho + phát `order.cancelled`).

---

### 3.7. Module 6: `notification-service` (Internal Port `8084`, DB: `notification_db`)
- **Flyway Migration `V1__init_notification_schema.sql`**:
  - Bảng `processed_events` (`event_id UUID PK`, `topic VARCHAR(100)`, `processed_at TIMESTAMP`).
  - Bảng `notifications` (`id UUID PK`, `order_id UUID`, `recipient_email VARCHAR(255)`, `event_type VARCHAR(50)`, `message TEXT`, `created_at TIMESTAMP`).
- **Idempotent Kafka Consumers (`OrderEventConsumer`)**:
  - `@KafkaListener(topics = "order.created", groupId = "notification-group")`
  - `@KafkaListener(topics = "order.cancelled", groupId = "notification-group")`
  - Parse JSON payload (`OrderEventPayload` record có `eventId`, `orderId`, `customerEmail`, `totalAmount`, `status`, `reason`).
  - Kiểm tra `processedEventRepository.existsById(event.eventId())` $\rightarrow$ nếu tồn tại thì log và `return` ngay; ngược lại lưu cả `ProcessedEvent` và `NotificationRecord` trong **cùng `@Transactional`**.
- **Endpoints**:
  - `GET /api/v1/notifications/my` (lọc theo `X-User-Email` để sinh viên/giảng viên gọi kiểm chứng sự kiện đã được xử lý thực tế mà không cần Gmail SMTP!).

---

## 4. THIẾT KẾ DOCKER, HEALTHCHECK, OPENTELEMETRY & SCALE-SAFE (`1 → 8`)

### 4.1. Multi-Stage `Dockerfile` chuẩn cho cả 6 service
- Stage 1: `maven:3.9.9-eclipse-temurin-21-alpine` (build module gọn nhẹ).
- Stage 2: `eclipse-temurin:21-jre-alpine` + non-root user `appuser` + `wget` healthcheck tới `/actuator/health/readiness` + JVM flags `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`.

### 4.2. Quy tắc vàng trong `docker-compose.yml` cho MAVIS Studio
1. **KHÔNG khai báo `container_name:`** trên bất kỳ service ứng dụng nào.
2. **Chỉ phơi cổng host ở biên**:
   - `api-gateway`: `ports: ["8080:8080"]`
   - `service-discovery`: `ports: ["8761:8761"]` (tiện xem dashboard Eureka)
   - Các service nghiệp vụ (`identity-service`, `catalog-service`, `order-service`, `notification-service`): **CHỈ dùng `expose:`**, KHÔNG dùng `ports:` cố định để `docker compose up -d --scale catalog-service=4` chạy hoàn hảo!
3. **Healthcheck + `depends_on: condition: service_healthy`** cho mọi phụ thuộc hạ tầng (4 PostgreSQL DBs, Redis, Kafka KRaft, Eureka).
4. **Giới hạn tài nguyên an toàn (Sandbox-friendly)** và tách mạng `mavis-net` (bridge).
5. **Biến môi trường OpenTelemetry mặc định**: Truyền `OTEL_EXPORTER_OTLP_ENDPOINT=${OTEL_EXPORTER_OTLP_ENDPOINT:-http://otel-collector:4318}` và `MANAGEMENT_TRACING_SAMPLING_PROBABILITY=${TRACING_SAMPLING_PROBABILITY:-1.0}` (đặt `MANAGEMENT_OTLP_TRACING_EXPORT_ENABLED=${OTEL_EXPORT_ENABLED:-false}` mặc định để chạy local không báo spam log nếu chưa bật collector, và `MAVIS.Runner` chỉ cần bật `OTEL_EXPORT_ENABLED=true` là phun OTLP trace ngay!).
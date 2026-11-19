# 📋 TASKS.md — LỘ TRÌNH XÂY DỰNG `moc-huong-shop-microservices` (8 CHẶNG)

> **Quy tắc vận hành cho Antigravity IDE**:
> 1. Thực thi tuần tự từ **Chặng 1 → Chặng 8**, đọc kỹ `ARCHITECTURE.md` trước khi viết code.
> 2. Sau mỗi chặng **1 → 7**, chỉ thực hiện `git add -A && git commit --no-verify -m "..."`. **KHÔNG chạy `mvn test` hay `docker build` lắt nhắt sau từng chặng** để tối ưu tốc độ và hạn mức token.
> 3. Tại **Chặng 8 (Final Verification)**, chạy **một lần duy nhất** toàn bộ bước kiểm chứng (`mvn clean test`, `docker compose config`, kiểm tra 0 secret, kiểm tra 0 vi phạm C3/Shared DB) và sửa triệt để mọi lỗi (nếu có) trước khi chốt commit cuối cùng.
> 4. Tuyệt đối **KHÔNG dùng Lombok** — viết Java 21 thuần (`record` DTO + explicit Constructor Injection).

---

## Chặng 1: Khởi tạo Skeleton Multi-Module Maven & Tài liệu nền
- [x] Khởi tạo cấu trúc thư mục repo `moc-huong-shop-microservices` với `.gitignore` (chặn `target/`, `.env`, `.idea/`, `*.log`), `.env.example`.
- [x] Tạo Root `pom.xml` chuẩn **Java 21 + Spring Boot `3.3.5` + Spring Cloud `2023.0.3`**, khai báo đủ 6 `<modules>`:
  - `service-discovery`
  - `api-gateway`
  - `identity-service`
  - `catalog-service`
  - `order-service`
  - `notification-service`
- [x] Khai báo `<dependencyManagement>` tập trung cho Spring Cloud BOM, Resilience4j `2.2.0`, JJWT `0.12.6`.
- [x] **Commit 1**: `git commit --no-verify -m "chore: initialize multi-module Maven skeleton for moc-huong-shop-microservices"`

---

## Chặng 2: Hạ tầng Discovery & API Gateway (`service-discovery` + `api-gateway`)
- [x] Xây dựng `service-discovery`:
  - `pom.xml`, `ServiceDiscoveryApplication` (`@EnableEurekaServer`), `application.yml` (port `8761`, Actuator health probes).
- [x] Xây dựng `api-gateway`:
  - `pom.xml` (Spring Cloud Gateway, Eureka Client, Actuator, JJWT, OTel bridge).
  - `ApiGatewayApplication`, `application.yml` với 4 routes (`lb://identity-service`, `lb://catalog-service`, `lb://order-service`, `lb://notification-service`) và CORS config.
  - `JwtTokenVerifier`: xác thực JWT HMAC-SHA256 đọc từ `${JWT_SECRET}`.
  - `CorrelationIdGatewayFilter` (Order `-2`): sinh/chuyển tiếp `X-Correlation-Id`.
  - `JwtAuthenticationGatewayFilter` (Order `-1`): whitelist public routes, xác thực Bearer token, **xóa sạch header giả mạo `X-User-Id`/`X-User-Email`/`X-User-Role`** trước khi inject claims đã xác thực.
- [x] **Commit 2**: `git commit --no-verify -m "feat(infra): implement service-discovery and api-gateway with JWT and Correlation-ID"`

---

## Chặng 3: Dịch vụ Định danh & Phân quyền (`identity-service`)
- [x] Xây dựng `identity-service` (Port `8081`, DB `identity_db`):
  - `pom.xml` (Web, Data JPA, Validation, PostgreSQL, Flyway + `flyway-database-postgresql`, Spring Security Crypto / BCrypt, JJWT, Eureka Client, Actuator, OTel).
  - Flyway `V1__init_identity_schema.sql` tạo bảng `users`.
  - Domain: `UserAccount` entity, `UserRole` enum (`CUSTOMER`, `STAFF`, `ADMIN`).
  - DTO Java 21 `record`: `RegisterRequest`, `LoginRequest`, `AuthResponse`, `UserProfileResponse` (đủ `@Valid` annotation).
  - `JwtTokenIssuer` (phát hành JWT khớp secret và claims với `api-gateway`).
  - `IdentityDataSeeder` (chỉ seed 3 tài khoản demo `admin@mochuong.vn`, `staff@mochuong.vn`, `customer@mochuong.vn` nếu bảng rỗng).
  - Phân tầng C3 sạch: `AuthController` & `UserController` -> `IdentityService` -> `UserAccountRepository`.
  - `GlobalExceptionHandler` trả RFC 7807 `ProblemDetail` (`400`, `401`, `404`, `409`).
- [x] **Commit 3**: `git commit --no-verify -m "feat(identity): implement identity-service with JWT issuance, RBAC roles and Flyway"`

---

## Chặng 4: Dịch vụ Danh mục Trầm Hương & Redis Cache (`catalog-service`)
- [x] Xây dựng `catalog-service` (Port `8082`, DB `catalog_db`, Cache `Redis`):
  - `pom.xml` (Web, Data JPA, Validation, Data Redis, Cache, PostgreSQL, Flyway + `flyway-database-postgresql`, Eureka Client, Actuator, OTel).
  - Flyway `V1__init_catalog_schema.sql` & `V2__seed_incense_products.sql` (3 danh mục + 6 sản phẩm Mộc Hương Shop có SKU, giá VND, tồn kho thực tế).
  - Domain: `Category`, `Product` (có `@Version` Optimistic Locking, các hàm nghiệp vụ `reserveStock(int)`, `releaseStock(int)`).
  - `RedisCacheConfig` (TTL 10 phút, `GenericJackson2JsonRedisSerializer`, có cờ `app.cache.enabled` để Unit/Test không cần Redis thật).
  - DTO Java 21 `record`: `CategoryResponse`, `ProductResponse`, `CreateProductRequest`, `StockAdjustmentRequest`.
  - Phân tầng C3 sạch: `CategoryController`, `ProductController` -> `ProductService` (`@Cacheable` / `@CacheEvict`) -> `ProductRepository`, `CategoryRepository`.
  - Kiểm tra phân quyền RBAC trên `POST /api/v1/products` (`ADMIN`/`STAFF`, sai trả `403`).
  - `GlobalExceptionHandler` trả `ProblemDetail` (`400`, `403`, `404`, `409 InsufficientStockException`).
- [x] **Commit 4**: `git commit --no-verify -m "feat(catalog): implement catalog-service with incense domain, optimistic lock and Redis cache"`

---

## Chặng 5: Dịch vụ Đơn hàng, OpenFeign, Resilience4j, Saga & Outbox (`order-service`)
- [x] Xây dựng `order-service` (Port `8083`, DB `order_db`, Kafka Producer):
  - `pom.xml` (Web, Data JPA, Validation, OpenFeign, `resilience4j-spring-boot3`, **`spring-boot-starter-aop`**, Spring Kafka, PostgreSQL, Flyway + `flyway-database-postgresql`, Eureka Client, Actuator, OTel).
  - Flyway `V1__init_order_schema.sql` tạo 3 bảng `orders`, `order_items`, `outbox_events`.
  - Domain DDD Aggregate: `Order` (Aggregate Root, `OrderStatus`: `PENDING`, `CONFIRMED`, `CANCELLED`), `OrderItem` (lưu snapshot tên & giá), `OutboxEvent` (`OutboxStatus`: `PENDING`, `PUBLISHED`).
  - **OpenFeign Client (`CALL_SITE` chuẩn MAVIS)**: `CatalogFeignClient` (`@FeignClient(name = "catalog-service", path = "/api/v1/products")`) với 3 method `getProductById`, `reserveStock`, `releaseStock`.
  - **Resilience4j Wrapper (`CatalogIntegrationService`)**: `@CircuitBreaker`, `@Retry`, `@Bulkhead` + fallback ném `ServiceDegradedException` (`503`). Cấu hình đầy đủ trong `application.yml` kèm Feign connect/read timeout.
  - **Orchestration Saga + Compensation (`OrderSagaOrchestrator`)**:
    - Kiểm tra `Idempotency-Key`.
    - Lưu `Order` (`PENDING` Semantic Lock) -> Giữ kho từng item -> Xác nhận `CONFIRMED` + ghi `OutboxEvent` (`order.created`) nguyên tử.
    - Nếu lỗi giữa chừng -> Bù trừ gọi `releaseStock` cho tất cả item đã giữ -> Đánh dấu `CANCELLED` + ghi `OutboxEvent` (`order.cancelled`).
  - **Outbox Relay (`OutboxEventPublisher`)**: `@Scheduled` đẩy event `PENDING` lên Kafka với `orderId` làm Partition Key rồi đánh dấu `PUBLISHED`.
  - Phân tầng C3 sạch: `OrderController` -> `OrderService` / `OrderSagaOrchestrator` -> `OrderRepository`, `OutboxEventRepository`. Chống IDOR trên `GET /api/v1/orders/{id}`.
  - `GlobalExceptionHandler` trả `ProblemDetail` (`400`, `403`, `404`, `409`, `503`).
- [x] **Commit 5**: `git commit --no-verify -m "feat(order): implement order-service with OpenFeign, Resilience4j, Saga compensation and Outbox"`

---

## Chặng 6: Dịch vụ Thông báo Bất đồng bộ (`notification-service`)
- [x] Xây dựng `notification-service` (Port `8084`, DB `notification_db`, Kafka Consumer):
  - `pom.xml` (Web, Data JPA, Spring Kafka, PostgreSQL, Flyway + `flyway-database-postgresql`, Eureka Client, Actuator, OTel).
  - Flyway `V1__init_notification_schema.sql` tạo 2 bảng `processed_events` và `notifications`.
  - Domain: `ProcessedEvent`, `NotificationRecord`.
  - **Idempotent Kafka Consumer (`OrderEventConsumer`)**: Lắng nghe 2 topic `order.created` và `order.cancelled` (`groupId = "notification-group"`), kiểm tra `processedEventRepository.existsById(eventId)` trước khi lưu `NotificationRecord` trong cùng `@Transactional`.
  - Phân tầng C3 sạch: `NotificationController` (`GET /api/v1/notifications/my`) -> `NotificationService` -> `NotificationRepository`.
  - `GlobalExceptionHandler` trả `ProblemDetail`.
- [x] **Commit 6**: `git commit --no-verify -m "feat(notification): implement idempotent Kafka consumer notification-service"`

---

## Chặng 7: Containerization, Unit Tests, `docker-compose.yml` & `README.md`
- [x] Tạo **Multi-Stage `Dockerfile`** (non-root user `appuser`, `HEALTHCHECK` readiness, JVM container flags) cho cả 6 module.
- [x] Tạo `docker-compose.yml` chuẩn MAVIS Sandbox & Studio Scale (`1 → 8`):
  - 4 PostgreSQL `16-alpine` riêng (`identity-db`, `catalog-db`, `order-db`, `notification-db`) có `healthcheck`.
  - `redis:7-alpine` có `healthcheck`.
  - `apache/kafka:3.7.0` chạy KRaft mode (không Zookeeper) có `healthcheck`.
  - 6 ứng dụng Spring Boot có `depends_on: condition: service_healthy`, **KHÔNG `container_name`**, **chỉ phơi `ports` ở `api-gateway` (`8080`) và `service-discovery` (`8761`)**, 4 service nghiệp vụ chỉ dùng `expose`.
  - Truyền đủ biến môi trường Externalized Config & OpenTelemetry (`OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_EXPORT_ENABLED`).
- [x] Viết Unit Tests cốt lõi bằng JUnit 5 + Mockito (KHÔNG cần Docker/DB thật để chạy siêu nhanh):
  - `catalog-service`: Test `Product.reserveStock` / `releaseStock` & lỗi `InsufficientStockException`.
  - `order-service`: Test `OrderSagaOrchestrator` (cả nhánh thành công + nhánh thất bại gọi bù trừ `releaseStock` và chuyển trạng thái `CANCELLED`, plus test chống trùng `Idempotency-Key`).
  - `notification-service`: Test `OrderEventConsumer` chống xử lý trùng lặp `eventId` (Idempotency).
  - `api-gateway`: Test `JwtTokenVerifier` xác thực token hợp lệ & từ chối token sai chữ ký.
- [x] Viết `README.md` đầy đủ: sơ đồ C2, hướng dẫn chạy 1 lệnh `docker compose up --build`, kịch bản `curl` kiểm thử end-to-end (Login -> Xem sản phẩm -> Đặt hàng Saga -> Xem thông báo -> Thử nghiệm Stop `catalog-service` & Scale `catalog-service=3`), và bảng ánh xạ 12 chương Giáo trình Java MAVIS.
- [x] **Commit 7**: `git commit --no-verify -m "feat(devops): add multi-stage Dockerfiles, scale-safe docker-compose, unit tests and README"`

---

## Chặng 8: Kiểm chứng Tổng Lực Một Lần Duy Nhất (Final Verification)
- [x] Chạy `mvn clean test` trên toàn bộ 6 module -> **BẮT BUỘC PASS 100% (0 compile errors, 0 test failures)**. Nếu có lỗi, sửa triệt để rồi chạy lại cho đến khi `BUILD SUCCESS`.
- [x] Chạy `docker compose config --quiet` (nếu máy có Docker CLI) để xác minh cú pháp `docker-compose.yml` hợp lệ 100%.
- [x] Chạy kiểm toán tĩnh tự động (Audit Checklist):
  - Xác nhận **0 từ khóa Lombok** trong mã nguồn.
  - Xác nhận **0 `@RestController` tiêm trực tiếp `@Repository`**.
  - Xác nhận **4 DB tách biệt** (không trùng tên DB giữa các service).
  - Xác nhận **0 secret thật / password cứng** ngoài biến môi trường `${...}` hoặc giá trị demo local có ghi chú.
  - Xác nhận **0 `container_name:`** trong `docker-compose.yml`.
- [x] Nếu có chỉnh sửa sửa lỗi ở Chặng 8 -> **Commit 8**: `git commit --no-verify -m "fix(verify): resolve build/audit checks and finalize curated lab repo"`

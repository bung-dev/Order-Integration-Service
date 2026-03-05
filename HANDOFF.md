# HANDOFF.md

이 파일은 현재까지 수행한 작업을 정리한 인수인계 문서입니다.
다음 에이전트는 이 파일을 읽고 즉시 작업을 이어갈 수 있습니다.

---

## 1. 프로젝트 구조 요약

- **언어/프레임워크**: Java 17, Spring Boot 4.0.2, Gradle
- **핵심 아키텍처**: Transactional Outbox Pattern (주문 수신 → DB 저장 → SFTP 업로드)
- **DB**: MySQL(`local` 프로파일) / Oracle(`assignment` 프로파일)
- **작업 계획**: `task/TODO_LIST.md` (체크리스트), `task/STEP_*.md` (단계별 상세 가이드)
- **빌드/테스트**: `./gradlew build` / `./gradlew test`

---

## 2. 완료된 작업 전체 이력

### ✅ 단계 1: 기초 인프라 및 설정 리팩토링 (완료)

#### 1.1 @ConfigurationProperties 도입

**생성 파일:**
- `SftpProperties.java` (`com.inspien.common.config.properties`) — prefix: `sftp`
- `AppProperties.java` (`com.inspien.common.config.properties`) — prefix: `app`

**프로퍼티 키 변경 (중요):**

| 기존 키 | 새 키 |
|---------|-------|
| `application.key` | `app.key` |
| `order.max-retry` | `app.max-retry` |
| `order.participant-name` | `app.participant-name` |
| `file.out-dir` | `app.out-dir` |
| `outbox.retention-days` | `app.retention-days` |
| `shipment.scheduler.delay` | `app.scheduler-delay` |
| `shipment.scheduler.cleanup-delay` | `app.cleanup-delay` |

`sftp.*` 키는 변경 없음.

#### 1.2 예외 처리 체계 강화

- `SftpException.java` — `CustomException` 상속, `retryable(boolean)` 필드
- `OrderValidationException.java` — `CustomException` 상속
- `ErrorCode.java` — `SFTP_CONNECTION_FAIL(503)`, `INVALID_ORDER_DATA(400)`, `TENANT_NOT_FOUND(404)` 추가
- `GlobalExceptionHandler.java` — `SftpException` 전용 핸들러 추가
- `Outbox.java` — `@AllArgsConstructor`, `@NoArgsConstructor` 추가
- `OutboxRepository.java` — `BeanPropertyRowMapper<Outbox>` 교체

---

### ✅ 단계 2: 도메인 및 매핑 리팩토링 (완료)

#### 2.1 MapStruct 도입

**build.gradle:**
```gradle
implementation 'org.mapstruct:mapstruct:1.5.5.Final'
annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'
annotationProcessor 'org.projectlombok:lombok-mapstruct-binding:0.2.0'
```

**생성 파일:**
- `OrderDomainMapper.java` (`com.inspien.mapper`) — 4개 메서드
  - `toOutbox(Order)`, `toShipmentRow(Order)`, `toOrder(Outbox)`, `toShipmentRow(PendingOrderRow)`

> `toShipmentRow` 오버로드 2개 존재 → **메서드 참조 대신 람다 사용 필수**

#### 2.2 OrderCommandService 생성

- `OrderCommandService.java` — `@Transactional(propagation=REQUIRES_NEW)`, `saveOrdersWithId(List<Order>)`
- `OrderService` — 공통 로직 → `processOrderSave()` 통합

---

### ✅ 단계 3: 비즈니스 로직 고도화 및 전략 패턴 (완료, 미커밋)

#### 3.1 Strategy Pattern 적용

**생성 파일:**
- `OrderProcessor.java` (`com.inspien.order.strategy`) — 인터페이스: `process(List<Order>, int skippedCount)`
- `SyncOrderProcessor.java` — `@Service("syncOrderProcessor")`, DB저장 → 파일생성 → SFTP 업로드
- `AsyncOrderProcessor.java` — `@Service("asyncOrderProcessor")`, DB저장 → Outbox 저장

**수정 파일:**
- `OrderCommandService.java` — `AppProperties` 주입, `saveOrdersWithRetry(orders, skippedCount, Consumer)` 추가
  - 기존 `processOrderSave()` 재시도 루프 이전, DuplicateKey 재시도 포함
- `OrderService.java` — 의존성 9→5개, 수동 생성자(`@Qualifier`), `parseAndFlatten()` 추출
  - `saveOrders()`, `saveOrdersOutbox()`, `processOrderSave()`, `setSftpUploader()` 모두 제거

#### 3.2 SFTP Atomic Write

**수정 파일:**
- `SftpUploader.java` — `.tmp`로 업로드 후 `session.rename()`, 실패 시 `.tmp` 정리
  - `CustomException` → `SftpException(ErrorCode.SFTP_UPLOAD_FAIL, true)` 변경
  - SFTP v3 rename 원자성 미보장 주석 추가

**테스트:**
- `OrderServiceTest` — `@BeforeEach` 수동 생성자로 전환, 전략 위임 검증
- `SyncOrderProcessorTest` (신규) — 성공 / SFTP실패+파일삭제 / 빈주문예외
- `AsyncOrderProcessorTest` (신규) — Outbox저장성공 / 빈주문예외

**빌드/테스트:** `./gradlew test → BUILD SUCCESSFUL`

---

## 3. 현재 커밋 상태

```
b02e23f test: update OrderServiceTest and ShipmentServiceTest for new dependencies  ← 최신 커밋
c652058 refactor: replace manual builder streams with OrderDomainMapper in services
409998c feat: add OrderDomainMapper and OrderCommandService for step 2
124e039 build: add MapStruct dependencies to build.gradle
9760558 test: update OrderServiceTest and ShipmentServiceTest to mock AppProperties
e4c4c69 feat: strengthen exception handling in ErrorCode, CustomException, GlobalExceptionHandler
adae7f6 refactor: inject AppProperties into services
7519a23 refactor: inject SftpProperties into SftpConfig and SftpUploader
f6161fa refactor: add no-args constructor to Outbox and use BeanPropertyRowMapper
```

> ⚠️ **단계 3 작업은 커밋되지 않은 상태** (`git status`로 확인 후 커밋 필요)

---

## 4. TODO_LIST.md 현재 상태

| 단계 | 상태 |
|------|------|
| 단계 1: 기초 인프라 및 설정 리팩토링 | ✅ 완료 |
| 단계 2: 도메인 및 매핑 리팩토링 | ✅ 완료 |
| 단계 3: 비즈니스 로직 고도화 및 전략 패턴 | ✅ 완료 (미커밋) |
| 단계 4: 시스템 안정성 및 모니터링 | ⬜ 미착수 |
| 단계 5: 조회 기능 확장 및 멀티 테넌시 | ⬜ 미착수 |

---

## 5. 다음 에이전트가 해야 할 일

### 우선: 단계 3 커밋

```bash
git add src/main/java/com/inspien/order/strategy/
git add src/main/java/com/inspien/order/service/OrderCommandService.java
git add src/main/java/com/inspien/order/service/OrderService.java
git add src/main/java/com/inspien/receiver/sftp/SftpUploader.java
git add src/test/java/com/inspien/order/strategy/
git add src/test/java/com/inspien/order/service/OrderServiceTest.java
git add task/STEP_3_STRATEGY_SFTP.md task/TODO_LIST.md HANDOFF.md
git commit -m "feat: apply strategy pattern and SFTP atomic write for step 3"
```

### 그 다음: 단계 4 구현

상세 가이드: `task/STEP_4_STABILITY_MONITORING.md`

#### 4.1 Outbox DLQ (Dead Letter Queue)
- `OUTBOX_TB`에 `RETRY_COUNT INT DEFAULT 0`, `LAST_ERROR_MSG VARCHAR` 컬럼 추가 (DB 마이그레이션)
- `ShipmentService.runOutbox()` — SFTP 실패 시 `RETRY_COUNT` 증가, 최대치 초과 시 `PROCESSED=-1`(FAILED) 처리
- FAILED 상태 조회/수동 재처리 API 엔드포인트 추가

> 이때 `AsyncOrderProcessor`의 원자성 문제(Order 저장 성공 후 Outbox INSERT 실패 시 불일치)도 함께 보완

#### 4.2 시스템 모니터링 (Health Check)
- `Spring Boot Actuator` 의존성 추가 및 활성화
- `SftpHealthIndicator` — SFTP 연결 상태 → `/actuator/health`
- `BatchHealthIndicator` — 최근 배치 성공 여부

---

## 6. 핵심 주의사항

| 항목 | 내용 |
|------|------|
| **프로퍼티 네임스페이스** | `app.*` 사용 (`application.*`, `order.*`, `file.*` 더 이상 사용 안 함) |
| **MapStruct 오버로드** | `toShipmentRow` 2개 오버로드 → 메서드 참조 대신 람다 사용 필수 |
| **@Qualifier 방식** | `OrderService`는 `@RequiredArgsConstructor` 미사용, 수동 생성자에 `@Qualifier` 직접 선언 |
| **saveOrdersWithRetry 위치** | `OrderCommandService` — `SyncOrderProcessor`, `AsyncOrderProcessor` 양쪽에서 호출 |
| **SftpException 호환성** | `SftpException extends CustomException` → `ShipmentService catch(Exception e)` 호환 |
| **REQUIRES_NEW 트랜잭션** | `saveOrdersWithId`는 항상 새 트랜잭션 커밋 → `saveOrdersWithRetry`에는 `@Transactional` 없음 |
| **OrderMapper @Value 잔존** | `OrderMapper`의 `applicationKey`는 아직 `@Value("${application.key:}")` 사용 중 — 단계 4/5에서 교체 가능 |
| **원자성 미보장 (기존 이슈)** | `AsyncOrderProcessor`: Order 저장(REQUIRES_NEW 커밋) 후 Outbox INSERT 별도 트랜잭션 → 단계 4에서 보완 예정 |

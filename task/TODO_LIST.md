# Inspien 주문 처리 시스템 리팩토링 및 기능 추가 투두리스트 (TODO LIST)

본 문서는 `research.md`의 시스템 분석 내용과 `refactoring.md`의 개선 제안을 바탕으로 작성된 단계별 상세 작업 계획서입니다. 시스템의 안정성, 유지보수성, 확장성을 높이기 위해 우선순위에 따라 5단계로 나누어 진행합니다.

---

## 📅 단계 1: 기초 인프라 및 설정 리팩토링
시스템 전반의 안정성과 설정 관리의 효율성을 높이는 기초 작업입니다.

- [x] **1.1. 설정 정보의 중앙 관리 (`@ConfigurationProperties` 도입)**
    - [x] `SftpProperties.java` 생성: `sftp.host`, `port`, `username`, `password`, `remote-dir` 등 관리
    - [x] `AppProperties.java` 생성: `application.key`, `order.max-retry`, `order.participant-name`, `file.out-dir` 등 관리
    - [x] `ShipmentScheduler`, `OrderService`, `FileWriter`, `ShipmentService` 등에서 기존 `@Value` 사용 코드를 위 설정 객체 주입 방식으로 변경
- [x] **1.2. 예외 처리 체계 강화**
    - [x] 비즈니스 상황별 커스텀 예외 세분화 (`SftpException`, `OrderValidationException` 생성)
    - [x] `GlobalExceptionHandler`에서 예외별 HTTP 상태 코드 및 상세 에러 메시지 응답 로직 보강
    - [x] SFTP 전송 실패 시 '재시도 가능' 여부를 판단할 수 있는 예외 구분 로직 추가 (`SftpException.retryable`)

## 🛠️ 단계 2: 도메인 및 매핑 리팩토링
코드 중복을 제거하고 객체 간 변환을 자동화하여 생산성을 높이는 작업입니다.

- [x] **2.1. 도메인 모델 간 매핑 자동화 (MapStruct 도입)**
    - [x] `build.gradle`에 MapStruct 의존성 추가 및 설정
    - [x] `OrderDomainMapper` 인터페이스 정의: `Order` ↔ `Outbox`, `Order` ↔ `ShipmentRow`, `PendingOrderRow` → `ShipmentRow` 매핑 로직 선언
    - [x] 기존 `ShipmentService` 및 `OrderService` 내의 수동 매핑(`stream().map(...)`) 코드를 MapStruct 호출로 교체
- [x] **2.2. 서비스 계층 코드 중복 제거**
    - [x] `OrderCommandService` 생성: `saveOrders`와 `saveOrdersOutbox`의 공통 로직(ID 할당, DB 저장) 추출 (`REQUIRES_NEW` 트랜잭션)
    - [x] `OrderService`의 비대해진 로직을 역할별로 분리하여 가독성 향상 (`processOrderSave` 공통 메서드 도입)

## 🚀 단계 3: 비즈니스 로직 고도화 및 전략 패턴 도입
다양한 처리 모드를 유연하게 관리하고 SFTP 전송의 안정성을 확보하는 작업입니다.

- [x] **3.1. 주문 처리 전략 패턴(Strategy Pattern) 적용**
    - [x] `OrderProcessor` 인터페이스 정의 (메서드: `process(List<Order> orders, int skippedCount)`)
    - [x] `SyncOrderProcessor` (즉시 업로드)와 `AsyncOrderProcessor` (Outbox 저장) 구현체 생성
    - [x] `OrderService`에서 `@Qualifier`로 전략 주입, `parseAndFlatten()` 공통 메서드 추출
    - [x] `OrderCommandService.saveOrdersWithRetry()` 추가 (재시도 로직 단일화)
- [x] **3.2. 고도화된 SFTP 업로드 전략 (Atomic Write)**
    - [x] `SftpUploader` 수정: 파일을 `.tmp` 확장자로 먼저 업로드하는 로직 추가
    - [x] 업로드 완료 후 `rename` 기능을 통해 원본 확장자로 변경하여 불완전한 파일 처리 방지
    - [ ] (선택 사항) 대량 전송 시 파일 압축(`zip`) 기능 인터페이스 설계

## 📊 단계 4: 시스템 안정성 및 모니터링 기능 추가
운영 효율성을 높이고 장애 발생 시 빠른 대응이 가능하도록 하는 작업입니다.

- [ ] **4.1. Outbox 재처리 및 데드 레터 큐 (DLQ) 구현**
    - [ ] `OUTBOX_TB`에 `RETRY_COUNT`, `LAST_ERROR_MSG` 컬럼 추가 (DB 마이그레이션)
    - [ ] `runOutbox` 배치 로직 수정: 실패 시 `RETRY_COUNT` 증가, 최대 횟수 초과 시 상태를 `FAILED`로 변경
    - [ ] 실패 데이터(DLQ)를 별도로 조회하거나 수동 재처리할 수 있는 API 엔드포인트 추가
- [ ] **4.2. 시스템 모니터링 (Health Check)**
    - [ ] `Spring Boot Actuator` 의존성 추가 및 활성화
    - [ ] `SftpHealthIndicator` 구현: SFTP 서버 연결 상태를 실시간으로 체크하여 `/actuator/health`에 노출
    - [ ] 최근 배치 성공 여부를 관리하는 `BatchHealthIndicator` 추가

## 🌐 단계 5: 조회 기능 확장 및 멀티 테넌시 지원
사용자 편의성을 높이고 다양한 업체 환경에 대응할 수 있도록 확장하는 작업입니다.

- [ ] **5.1. 처리 이력 조회 및 주문 추적 API**
    - [ ] `orderId` 또는 `userId` 기반 주문 생명주기(접수 → DB 저장 → SFTP 전송 상태) 조회 API 개발
    - [ ] 로그 검색 편의를 위해 응답 객체에 `traceId` 포함
- [ ] **5.2. 멀티 테넌시(Multi-tenancy) 지원 기반 마련**
    - [ ] `applicantKey`별로 서로 다른 SFTP 서버나 파일 규격을 가질 수 있도록 설정 구조 확장
    - [ ] 업체별 설정을 동적으로 로드할 수 있는 `TenantConfigProvider` 설계

---
*본 투두리스트는 구현 과정에서 기술적 제약이나 요구사항 변경에 따라 세부 내용이 조정될 수 있습니다.*

---

## 🔍 구현 검토 보고서 (2026-03-04)

> 리드 에이전트 + 2개 검토 워커(worker-1: Step1, worker-2: Step2)가 병렬 검증한 결과입니다.

### 단계 1 검토 결과

#### 1.1 @ConfigurationProperties 도입
- ✅ `SftpProperties`: `@ConfigurationProperties(prefix="sftp")`, host/port/username/password/remote-dir 모두 포함
- ✅ `AppProperties`: `@ConfigurationProperties`, max-retry/participant-name/file.out-dir/retention-days 포함
- ✅ `ShipmentScheduler`, `OrderService`, `FileWriter`, `ShipmentService` 에서 `@Value` 완전 제거 확인
- ⚠️ **미조치**: `OrderMapper.java`에 `@Value("${application.key:}")` 잔존 — 태스크 명세 외 클래스이나 `AppProperties`에 `applicationKey`가 이미 존재하므로 향후 교체 권장

#### 1.2 예외 처리 체계
- ✅ `SftpException`: `retryable` 필드 포함, 재시도 가능 여부 구분 가능
- ✅ `OrderValidationException`: 생성 및 핸들러 연동 확인
- ✅ `GlobalExceptionHandler`: `SftpException`, `OrderValidationException` 별도 핸들러, HTTP 상태코드 적절히 매핑
- ✅ `ErrorCode`: 예외 유형 세분화 완료

---

### 단계 2 검토 결과

#### 2.1 MapStruct 도입
- ✅ `build.gradle`: mapstruct:1.5.5.Final / mapstruct-processor / lombok-mapstruct-binding 3개 의존성 추가
- ✅ `OrderDomainMapper`: `@Mapper(componentModel="spring")`, 4개 메서드 정의
  - `toOutbox(Order)`: processed=false constant, updated=LocalDateTime.now() expression
  - `toShipmentRow(Order)`: shipmentId source=orderId 매핑
  - `toOrder(Outbox)`: 역방향 매핑
  - `toShipmentRow(PendingOrderRow)`: shipmentId source=orderId 매핑
- ✅ `OrderService.saveOrdersOutbox()`: 수동 Outbox 빌더 스트림 제거, `orderDomainMapper::toOutbox` 사용
- ✅ `ShipmentService.run()`: 수동 `new ShipmentRow(...)` 제거, 람다 `p -> orderDomainMapper.toShipmentRow(p)` 사용 (오버로드 ambiguity 해소)
- ✅ `ShipmentService.runOutbox()`: 수동 `Order.builder()` 스트림 제거, `orderDomainMapper::toOrder` 사용

#### 2.2 OrderCommandService
- ✅ `OrderCommandService`: `@Service`, `@Transactional(propagation=REQUIRES_NEW)`, `saveOrdersWithId` 구현
- ✅ `OrderService`: `PlatformTransactionManager` 제거, `OrderCommandService` 주입
- ✅ `OrderService.processOrderSave()`: `DuplicateKeyException` 재시도 루프, `extraAction` Consumer 패턴으로 sync/outbox 분기
- ✅ `OrderServiceTest`: `PlatformTransactionManager` mock 제거, `OrderCommandService`/`OrderDomainMapper` mock 추가
- ✅ `ShipmentServiceTest`: `OrderDomainMapper` mock 추가, `toShipmentRow` thenAnswer stub 설정

---

### ⚠️ 발견된 잠재적 문제

| 심각도 | 위치 | 문제 | 권장 조치 |
|--------|------|------|-----------|
| **중** | `OrderService.saveOrdersOutbox()` | `OrderCommandService.saveOrdersWithId()`가 `REQUIRES_NEW`로 즉시 커밋 후 Outbox INSERT가 별도 컨텍스트에서 실행 → 주문 저장 성공 후 Outbox INSERT 실패 시 **원자성 미보장** (Outbox 패턴 핵심 요구사항) | Step 4(DLQ) 구현 시 보완하거나 `saveOrdersOutbox` 전용 트랜잭션 복구 |
| **중** | `OrderService.processOrderSave()` | `DuplicateKeyException` maxRetry 초과 시 기존 `ErrorCode.DUPLICATE_KEY.exception()` 대신 raw `DuplicateKeyException` throw → `GlobalExceptionHandler`가 처리 못하면 HTTP 500 노출 | `GlobalExceptionHandler`에 `DuplicateKeyException` 핸들러 추가 또는 `processOrderSave`에서 wrap |
| **저** | `OrderMapper.java` | `@Value("${application.key:}")` 잔존 | `AppProperties.getApplicationKey()` 주입으로 교체 |

### 전체 빌드 및 테스트
- ✅ `./gradlew test` — BUILD SUCCESSFUL, 전체 테스트 통과 확인

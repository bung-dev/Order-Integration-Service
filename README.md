# Integration Service (EAI)

온라인 쇼핑몰 주문 데이터를 안정적이고 빠르게 연계 처리하는 **Spring Boot 기반 EAI(Enterprise Application Integration) 서비스**입니다.  

본 서비스는 외부 시스템(SFTP)과의 동기적 결합을 제거하기 위해 **Outbox 패턴**을 도입하였으며, 정교한 트랜잭션 관리와 자동화된 데이터 관리(Cleanup) 기능을 제공합니다.

---

## 🚀 핵심 기능 및 비즈니스 로직

### 1. 주문 데이터 통합 및 수신
- **API 수신**: Base64 인코딩 및 EUC-KR 형식의 대용량 XML 주문 데이터를 REST API를 통해 수신합니다.
- **데이터 변환**: Jackson XML 라이브러리를 통해 계층형 데이터를 평면화(Flattening)하고, 유효성 검증을 수행합니다.

### 2. 고성능 아웃박스 패턴 (Outbox Pattern)
- **응답 속도 개선**: DB 저장(Local Transaction)과 파일 전송(External Integration)을 분리하여 클라이언트 응답 시간을 혁신적으로 개선하였습니다. (평균 **130ms**)
- **배치 비동기 전송**: `ShipmentScheduler`가 백그라운드에서 `OUTBOX_TB`의 미처리 데이터를 감시하여 파일 생성 및 SFTP 업로드를 수행합니다.

### 3. 안정적인 트랜잭션 관리
- **재시도 전략**: 중복 주문 번호 발생 시 `REQUIRES_NEW` 전파 속성을 활용하여 독립적인 트랜잭션 내에서 최대 50회(`order.max-retry`) 재시도를 수행, 데이터 유실을 방지합니다.

### 4. 지능형 데이터 관리 (Cleanup Logic)
- **DB 최적화**: 처리 완료(`PROCESSED = 1`) 후 7일이 경과한 아웃박스 데이터를 자동으로 삭제하여 DB 성능을 유지합니다.
- **범용성**: Java 레벨에서 날짜를 계산하여 전달함으로써 MySQL(Local) 및 Oracle(Assignment) 등 모든 DB 환경에서 완벽히 호환됩니다.

---

## 📈 성능 지표 (Performance Metrics)

| 처리 방식 | 평균 응답 시간 (Latency) | 개선 효과 |
| :--- | :---: | :---: |
| **기존 방식 (매번 전송)** | 3,700ms | - |
| **캐싱 처리 후** | 1,300ms | 약 3배 개선 |
| **아웃박스 패턴 적용** | **130ms** | **약 28배 개선** |

---

## 📊 데이터베이스 스키마 및 상태 전이

### 주요 테이블 정보
- **ORDER_TB**: 수신된 원천 주문 데이터 저장 (전송 상태: `STATUS`)
- **OUTBOX_TB**: SFTP 비동기 전송을 위한 큐 (처리 여부: `PROCESSED`, 최종 수정: `UPDATED`)
- **SHIPMENT_TB**: 운송 시스템 연계용 테이블

### 상태 흐름
1. `POST /api/order/outbox` 호출 → `OUTBOX_TB.PROCESSED = 0` (대기)
2. `ShipmentScheduler` 실행 → SFTP 전송 성공 → `OUTBOX_TB.PROCESSED = 1` & `UPDATED = 현재시각` (완료)
3. 7일 경과 후 `Cleanup` 배치 실행 → `OUTBOX_TB` 해당 데이터 삭제

---

## 🔌 API 명세

### 1. 주문 생성 (아웃박스 방식 - 권장)
- **Endpoint**: `POST /api/order/outbox`
- **Request Body**: `{ "base64Xml": "..." }`
- **Response**: `200 OK` (주문 저장 즉시 반환)

### 2. 주문 생성 (동기 방식)
- **Endpoint**: `POST /api/order`
- **Response**: `200 OK` (SFTP 전송 완료 후 반환)

---

## 🛠 설정 및 운영 (application.properties)
- `order.max-retry`: 중복 ID 발생 시 최대 재시도 횟수 (기본: 50)
- `shipment.scheduler.delay`: 배치 실행 주기 (기본: 300,000ms)
- `outbox.retention-days`: 완료 데이터 보존 기간 (기본: 7일)

---

## 📝 로깅 및 모니터링
- 모든 작업은 UUID 기반의 `traceId`가 MDC를 통해 로그에 기록되어 주문별 추적이 가능합니다.
- **애플리케이션 로그**: `./logs/app.log`
- **배치 로그**: `./logs/batch.log`

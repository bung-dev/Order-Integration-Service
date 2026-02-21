# Integration Service (EAI)

온라인 쇼핑몰 주문 데이터를 연계 처리하는 Spring Boot 기반 서비스입니다.  
주문 생성 API를 통해 데이터를 수신하여 파싱/검증 후 DB에 적재하고, 회계용 영수증 파일을 생성하여 SFTP로 전송합니다.  
또한, 배치 스케줄러를 통해 미전송 주문 데이터를 주기적으로 운송사 DB(Shipment)로 전달합니다.

## 🛠 기술 스택
- **Framework**: Spring Boot 4.0.2
- **Java**: JDK 17
- **Database**: Oracle (Main/Shipment), MySQL (Local Support)
- **Library**: Spring Integration SFTP, Jackson XML, Lombok, JDBC Template
- **Build**: Gradle

## 🏗 아키텍처
![아키텍처](https://github.com/user-attachments/assets/434f4ccd-de4d-4ac3-ba38-d342dfe9674a)

## 🚀 주요 기능
1. **주문 API 수신**: Base64 인코딩 및 EUC-KR 형식의 XML 데이터를 수신합니다.
2. **데이터 처리**: XML 파싱, 유효성 검증, 데이터 매핑(Flattening)을 수행합니다.
3. **영수증 생성 및 전송**: 주문 정보를 텍스트 파일로 생성하여 SFTP 서버로 전송합니다.
4. **배치 연계**: 5분 주기 스케줄링을 통해 `ORDER_TB`의 미전송 데이터를 `SHIPMENT_TB`로 적재합니다.
5. **트랜잭션 보장**: DB 적재, 파일 생성, SFTP 전송을 하나의 트랜잭션 단위로 관리하며, 실패 시 롤백 및 생성 파일 삭제를 수행합니다.

## 📋 데이터베이스 구조
### ORDER_TB (주문 테이블)
| 컬럼명 | 타입 | 설명 |
| :--- | :--- | :--- |
| APPLICANT_KEY | VARCHAR2 | 지원자 키 (LJH000009) |
| ORDER_ID | VARCHAR2(20) | 주문 번호 (PK) |
| USER_ID | VARCHAR2(20) | 사용자 ID |
| NAME | VARCHAR2(50) | 수령인 성명 |
| ADDRESS | VARCHAR2(200) | 배송지 주소 |
| ITEM_ID | VARCHAR2(20) | 상품 ID |
| ITEM_NAME | VARCHAR2(100) | 상품명 |
| PRICE | NUMBER | 상품 가격 |
| STATUS | CHAR(1) | 전송 상태 (N: 대기, Y: 완료) |

### SHIPMENT_TB (운송 테이블)
| 컬럼명 | 타입 | 설명 |
| :--- | :--- | :--- |
| SHIPMENT_ID | VARCHAR2(20) | 운송 ID (PK) |
| ORDER_ID | VARCHAR2(20) | 주문 번호 (FK) |
| ITEM_ID | VARCHAR2(20) | 상품 ID |
| APPLICANT_KEY | VARCHAR2 | 지원자 키 |
| ADDRESS | VARCHAR2(200) | 배송지 주소 |

## 🔌 API 명세
### POST `/api/order`
주문 데이터를 생성하고 영수증을 전송합니다.

**Request Body**
```json
{
  "base64Xml": "PEhFQURFUj4uLi48L0hFQURFUj48SVRFTT4uLi48L0lURU0+..."
}
```

**Response Body (Success)**
```json
{
  "success": true,
  "message": "주문 처리에 성공하였습니다.",
  "orderCount": 1,
  "skippedCount": 0,
  "retryCount": 1
}
```

## ⚙️ 설정 가이드 (application.properties)
주요 설정 항목은 다음과 같습니다:
- `application.key`: 고유 키
- `spring.datasource.*`: DB 접속 정보
- `sftp.*`: SFTP 서버 접속 및 경로 정보
- `order.max-retry`: 중복 ID 발생 시 최대 재시도 횟수
- `shipment.scheduler.delay`: 배치 실행 주기 (밀리초)


## 📝 로깅 및 모니터링
- `traceId` 기반 로깅을 통해 주문별 처리 흐름을 추적할 수 있습니다.
- 로그 저장 경로: `./logs/app.log` (애플리케이션), `./logs/batch.log` (배치)

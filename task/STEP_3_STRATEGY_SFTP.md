# 단계 3: 비즈니스 로직 고도화 및 전략 패턴 도입 상세 가이드

본 문서는 `TODO_LIST.md`의 **단계 3: 비즈니스 로직 고도화 및 전략 패턴 도입**에 대한 상세 구현 가이드입니다.

## 3.1. 주문 처리 전략 패턴(Strategy Pattern) 적용

주문 처리 방식(동기/비동기)을 인터페이스로 추상화하여 확장성을 확보합니다.

### 3.1.1. `OrderProcessor` 인터페이스
```java
public interface OrderProcessor {
    CreateOrderResult process(List<Order> orders, int skippedCount);
}
```

### 3.1.2. 전략별 구현체 생성
```java
// 동기 처리 전략 (파일 생성 및 업로드 포함)
@Service
@RequiredArgsConstructor
public class SyncOrderProcessor implements OrderProcessor {
    public CreateOrderResult process(List<Order> orders, int skippedCount) {
        // 기존 saveOrders 로직 (DB 저장 -> 파일생성 -> SFTP 업로드)
    }
}

// 비동기 처리 전략 (Outbox 저장만 수행)
@Service
@RequiredArgsConstructor
public class AsyncOrderProcessor implements OrderProcessor {
    public CreateOrderResult process(List<Order> orders, int skippedCount) {
        // 기존 saveOrdersOutbox 로직 (DB 저장 -> Outbox 저장)
    }
}
```

### 3.1.3. 컨트롤러 또는 서비스에서의 전략 활용
```java
@RestController
public class OrderController {
    private final Map<String, OrderProcessor> processors; // 빈 이름을 키로 자동 주입

    @PostMapping("/{mode}")
    public ResponseEntity<?> create(@PathVariable String mode, @RequestBody CreateOrderRequest request) {
        OrderProcessor processor = mode.equals("outbox") ? 
            processors.get("asyncOrderProcessor") : processors.get("syncOrderProcessor");
        // ... 전처리 후
        return ResponseEntity.ok(processor.process(orders, skippedCount));
    }
}
```

---

## 3.2. 고도화된 SFTP 업로드 전략 (Atomic Write)

불완전한 파일이 처리되지 않도록 원자적 쓰기 전략을 도입합니다.

### 3.2.1. `SftpUploader` 수정 (임시 파일 업로드 후 Rename)
```java
public void uploadAtomic(Path localFile) {
    String finalName = localFile.getFileName().toString();
    String tempName = finalName + ".tmp";
    String remotePath = remoteDir + "/" + finalName;
    String tempPath = remoteDir + "/" + tempName;

    template.execute(session -> {
        try (InputStream is = Files.newInputStream(localFile)) {
            // 1. .tmp 확장자로 업로드
            session.write(is, tempPath);
            // 2. 업로드 완료 후 이름 변경
            session.rename(tempPath, remotePath);
            return null;
        } catch (Exception e) {
            // 실패 시 임시 파일 삭제 시도
            try { session.remove(tempPath); } catch (Exception ignore) {}
            throw new SftpException(ErrorCode.SFTP_UPLOAD_FAIL, true);
        }
    });
}
```

---

## 구현 결과 (2026-03-05)

### 구현 완료 항목

#### 3.1 Strategy Pattern

| 파일 | 역할 |
|------|------|
| `com/inspien/order/strategy/OrderProcessor.java` | 인터페이스: `process(List<Order>, int skippedCount): CreateOrderResult` |
| `com/inspien/order/strategy/SyncOrderProcessor.java` | 동기 처리: DB저장 → 파일생성 → SFTP 업로드 |
| `com/inspien/order/strategy/AsyncOrderProcessor.java` | 비동기 처리: DB저장 → Outbox 저장 |

**OrderService 수정:**
- 의존성 9개 → 5개 (`orderParserXML`, `validator`, `mapper`, `syncOrderProcessor`, `asyncOrderProcessor`)
- `@Qualifier` 사용을 위해 `@RequiredArgsConstructor` 대신 수동 생성자 작성
- `parseAndFlatten()` 공통 메서드 추출로 코드 중복 제거
- `saveOrders()`, `saveOrdersOutbox()`, `processOrderSave()`, `setSftpUploader()` 제거

**OrderCommandService 수정:**
- `AppProperties` 의존성 추가
- `saveOrdersWithRetry(List<Order>, int skippedCount, Consumer<List<Order>> postSaveAction)` 추가
  - 기존 `processOrderSave()` 재시도 루프 로직 이전
  - DuplicateKey 재시도, maxRetry 초과 시 예외 전파

#### 3.2 SFTP Atomic Write

**SftpUploader 수정:**
- `upload()` 내부를 atomic write 방식으로 교체 (시그니처 변경 없음)
- `.tmp` 확장자로 먼저 업로드 → `session.rename()` → 실패 시 `.tmp` 정리
- `CustomException` → `SftpException(ErrorCode.SFTP_UPLOAD_FAIL, true)` 변경
- SFTP v3 rename 원자성 미보장 주석 추가

### 테스트

| 파일 | 변경 내용 |
|------|-----------|
| `OrderServiceTest` | 전략 위임 검증으로 교체 (`@BeforeEach` 수동 생성자), `ReflectionTestUtils` 제거 |
| `SyncOrderProcessorTest` (신규) | 성공, SFTP 실패 시 파일 삭제, 빈 주문 예외 |
| `AsyncOrderProcessorTest` (신규) | Outbox 저장 성공, 빈 주문 예외 |

### 빌드/테스트 결과

```
./gradlew test → BUILD SUCCESSFUL
```

### 주의사항

- `@Qualifier` + `@RequiredArgsConstructor` 조합 불가 → `OrderService`는 수동 생성자 사용
- `SftpException extends CustomException` 이므로 `ShipmentService`의 `catch(Exception e)` 호환
- `saveOrdersWithRetry`는 `@Transactional` 없음 — 내부에서 호출하는 `saveOrdersWithId`가 `REQUIRES_NEW` 트랜잭션 소유

---

## 3.3. (선택) 대량 전송 시 파일 압축 전략

파일 전송 효율을 높이기 위한 압축 인터페이스 설계 예시입니다.

```java
public interface FileArchiver {
    Path archive(Path sourceFile);
}

public class ZipFileArchiver implements FileArchiver {
    public Path archive(Path sourceFile) {
        // ZipOutputStream을 이용한 압축 로직 구현
    }
}
```

# 🔒 sping-boot-tdd 동시성 제어 포인트 시스템

Spring Boot 기반의 포인트 관리 시스템으로, **동시성 제어**에 중점을 둔 안전한 멀티스레드 환경에서의 포인트 충전/사용 서비스입니다.

---

## 📝 프로젝트 개요

사용자별 포인트 시스템을 구현한 백엔드 서비스로, 충전, 사용, 조회, 내역 조회의 4가지 기능을 제공합니다.

**핵심 특징:**

- 🔐 **Thread-Safe 동시성 제어**: ReentrantLock 기반 사용자별 락 관리
- 🏗️ **DDD + Layered Architecture**: 도메인 중심 설계와 계층 분리
- ⚙️ **Spring Property 외부화**: 운영 중 정책 변경 가능
- 🧪 **TDD 기반 개발**: 테스트 주도 개발로 신뢰성 확보

---

## 🎯 주요 해결 과제

### 동시성 문제 해결

- **Lost Update Problem**: 동시 요청 시 갱신 손실 방지
- **Race Condition**: 경쟁 조건으로 인한 데이터 불일치 해결
- **최대 한도 검증**: 동시 충전으로 인한 한도 초과 방지

### 해결 방안

```java
// ReentrantLock 기반 사용자별 동시성 제어
public UserPoint charge(long userId, long amount) {
    ReentrantLock lock = getUserLock(userId);
    lock.lock();
    try {
        // 원자적 연산: 조회 → 검증 → 업데이트 → 기록
        return processCharge(userId, amount);
    } finally {
        lock.unlock();
    }
}
```

---

## 🏗️ 아키텍처 설계

### Layered Architecture + DDD

```
┌─────────────────────────────────────┐
│ Presentation Layer                  │ ← HTTP 요청/응답 처리
│ ├── PointController.java            │
│ └── ApiControllerAdvice.java        │
└─────────────────────────────────────┘
            ↓
┌─────────────────────────────────────┐
│ Application Layer                   │ ← 비즈니스 플로우 조정
│ └── PointService.java               │ ← 동시성 제어 적용
└─────────────────────────────────────┘
            ↓
┌─────────────────────────────────────┐
│ Domain Layer                        │ ← 도메인 로직 & 정책
│ ├── UserPoint.java                  │
│ ├── PointHistory.java               │
│ ├── ChargePolicy.java               │
│ └── UsePolicy.java                  │
└─────────────────────────────────────┘
            ↓
┌─────────────────────────────────────┐
│ Infrastructure Layer                │ ← 데이터 저장소
│ ├── UserPointTable.java             │
│ └── PointHistoryTable.java          │
└─────────────────────────────────────┘
```

---

## ⚖️ 비즈니스 정책 (설정 기반)

### 정책 설정 (application.yml)

```yaml
point:
  policy:
    charge:
      min-amount: 100 # 최소 충전 금액
      max-total-point: 1000000 # 최대 보유 포인트
    use:
      min-amount: 100 # 최소 사용 금액
  concurrency:
    max-locks: 10000 # 최대 락 개수
    cleanup-interval: 3600000 # 락 정리 주기 (1시간)
```

### 주요 정책

| 정책 항목        | 설정값      | 설명                       |
| ---------------- | ----------- | -------------------------- |
| 최대 보유 포인트 | 1,000,000원 | 한 사용자당 최대 보유 가능 |
| 최소 거래 금액   | 100원       | 충전/사용 최소 단위        |
| 동시성 제어      | 사용자별 락 | 동일 사용자는 순차 처리    |

---

## 🔒 동시성 제어 전략

### ReentrantLock 방식 선택 이유

| 특징                  | synchronized | **ReentrantLock** | AtomicLong |
| --------------------- | ------------ | ----------------- | ---------- |
| Virtual Thread 호환성 | ❌           | ✅                | ✅         |
| 공정성 보장           | ❌           | ✅ (Fair Lock)    | ❌         |
| 타임아웃 기능         | ❌           | ✅                | ❌         |
| 복잡한 비즈니스 로직  | ✅           | ✅                | ❌         |
| 락 상태 모니터링      | ❌           | ✅                | ❌         |

### 핵심 구현 특징

- **사용자별 락**: 다른 사용자는 병렬 처리 가능
- **Fair Lock**: FIFO 순서로 공정한 처리
- **자동 락 정리**: 메모리 누수 방지를 위한 스케줄러
- **락 모니터링**: 실시간 락 상태 확인 가능

---

## 💥 예외 처리 전략

### 도메인 예외 계층 구조

```java
PointException (추상)
├── InvalidAmountException     // 유효하지 않은 금액
├── InsufficientPointException // 포인트 부족
└── ExceedsMaxPointException   // 최대 포인트 초과
```

### 예외 응답 예시

```json
// 포인트 부족 시
{
  "code": "INSUFFICIENT_POINT",
  "message": "포인트가 부족합니다. 요청: 6000, 현재: 5000"
}

// 최대 포인트 초과 시
{
  "code": "EXCEEDS_MAX_POINT",
  "message": "최대 보유 가능 포인트는 1,000,000원입니다"
}
```

---

## 🧪 테스트 전략

### 계층별 테스트 접근

- **단위 테스트**: Mock 기반 격리된 테스트
- **동시성 테스트**: 실제 스레드 환경에서 검증
- **통합 테스트**: Spring Context 기반 End-to-End 테스트

### 주요 테스트 시나리오

```java
@Test
@DisplayName("✅ 동시 충전 시 Lost Update 문제 해결")
void 동시_충전_Lost_Update_해결() {
    // 10개 스레드가 동시에 100포인트씩 충전
    // 기대값: 1000 + (10 × 100) = 2000 포인트
    // 결과: 정확히 2000 포인트 (Lost Update 없음)
}
```

### 테스트 커버리지

- **ConcurrencyResolvedTest**: 동시성 문제 해결 검증
- **PointServiceTest**: 비즈니스 로직 단위 테스트
- **PointControllerTest**: API 계층 테스트
- **PolicyTest**: 도메인 정책 검증

---

## 🚀 성능 및 확장성

### 성능 최적화

- **사용자별 병렬 처리**: 서로 다른 사용자는 동시 처리
- **ConcurrentHashMap**: Lock-free 읽기 연산
- **메모리 관리**: 자동 락 정리로 메모리 누수 방지

### 모니터링 기능

```java
// 실시간 락 상태 확인
String status = pointService.getLockStatus();
// "Total Locks: 150, Active Locks: 12, Queue Length: 3"

// 메모리 사용량 확인
String memory = pointService.getMemoryInfo();
// "Active Locks: 150, Map Size: 9600 bytes (estimated)"
```

---

## 🔧 주요 기능

### 포인트 관리

- **포인트 조회**: 사용자별 현재 포인트 확인
- **포인트 충전**: 동시성 제어를 통한 안전한 충전
- **포인트 사용**: 잔고 검증과 함께 안전한 차감
- **이력 조회**: 충전/사용 내역 조회

### API 예시

```http
# 포인트 충전
PATCH /point/1/charge
Content-Type: application/json
Body: 5000

# 응답
{
  "id": 1,
  "point": 15000,
  "updateMillis": 1720797823371
}
```

---

## 📦 기술 스택

- **Java 17**
- **Spring Boot 3.2.0**
- **JUnit 5**: 테스트 프레임워크
  - **Mockito**: 모킹 라이브러리
  - **MockMvc**: 웹 계층 테스트
  - **AssertJ**: 테스트 어설션
- **Gradle**: 빌드 및 의존성 관리
- **Lombok**: 보일러플레이트 코드 감소

---

## 🎯 학습 목표 달성

### 동시성 제어 마스터리

- ✅ **Lost Update 해결**: ReentrantLock으로 완벽 해결
- ✅ **Race Condition 방지**: 원자적 연산 보장
- ✅ **메모리 안전성**: 자동 정리 메커니즘 구현
- ✅ **성능 최적화**: 사용자별 병렬 처리로 확장성 확보

### 아키텍처 설계

- ✅ **관심사 분리**: 계층별 명확한 책임 분리
- ✅ **테스트 가능성**: Mock과 Real 객체의 적절한 조합
- ✅ **설정 외부화**: 운영 환경 대응 능력
- ✅ **확장 가능성**: 새로운 기능 추가 시 최소 영향

---

## 💡 핵심 교훈

**"좋은 설계는 변경에 강하다"**

PointService의 동시성 제어 구현을 synchronized에서 ReentrantLock으로 변경해도 **대부분의 테스트가 통과**한다는 것은:

1. **인터페이스와 구현이 잘 분리**되어 있고
2. **테스트가 구현이 아닌 행위를 검증**하며
3. **관심사가 명확히 분리**되어 있다는 증거

특히 **동시성 테스트들**은 내부 락 구현 방식과 무관하게 "동시성이 보장되는가?"라는 본질적 질문에만 집중하여 구현 변경에 강건한 테스트 설계를 목표하였습니다.

# 🚀 포인트 시스템 동시성 제어 개선사항 종합 정리

## 📋 개선사항 전체 요약

### **리뷰어 피드백 기반 3단계 개선**

| 개선사항                            | 상태    | 주요 내용             | 수정 파일 수 |
| ----------------------------------- | ------- | --------------------- | ------------ |
| **1. Spring Property 외부화**       | ✅ 완료 | 정책 설정 분리        | 7개 파일     |
| **2. synchronized → ReentrantLock** | ✅ 완료 | 동시성 제어 방식 개선 | **1개 파일** |
| **3. 락 관리 개선**                 | ✅ 완료 | 메모리 누수 방지      | 포함됨       |

---

## 🔧 개선사항 1: Spring Property 외부화

### **목적**: 하드코딩된 상수값을 설정으로 분리

#### **변경 내용**

```yaml
# application.yml 추가
point:
  policy:
    charge:
      min-amount: 100
      max-total-point: 1000000
    use:
      min-amount: 100
  concurrency:
    max-locks: 10000
    cleanup-interval: 3600000
```

#### **아키텍처 개선**

```java
// Before: 하드코딩
private static final long MIN_CHARGE_AMOUNT = 100L;
private static final long MAX_TOTAL_POINT = 1_000_000L;

// After: 설정 주입
@Component
public class ChargePolicy {
    private final PointPolicyConfig.ChargeConfig chargeConfig;

    public ChargePolicy(PointPolicyConfig pointPolicyConfig) {
        this.chargeConfig = pointPolicyConfig.charge();
    }
}
```

#### **개선 효과**

- ✅ **운영 중 정책 변경** - 코드 수정 없이 설정만으로 변경
- ✅ **환경별 분리** - dev, prod 환경별 다른 정책 적용
- ✅ **안전성 향상** - 설정 변경 시 검증 가능

---

## 🔒 개선사항 2: synchronized → ReentrantLock

### **목적**: Virtual Thread 호환성 및 고급 락 기능 확보

#### **핵심 변경**

```java
// Before: synchronized 방식
public UserPoint charge(long userId, long amount) {
    synchronized (getUserLock(userId)) {
        // 비즈니스 로직
        return updatedUserPoint;
    }
}

// After: ReentrantLock 방식
public UserPoint charge(long userId, long amount) {
    ReentrantLock lock = getUserLock(userId);
    lock.lock();
    try {
        // 동일한 비즈니스 로직
        return updatedUserPoint;
    } finally {
        lock.unlock(); // 반드시 해제
    }
}
```

#### **기술적 개선사항**

##### **1) Fair Lock 적용**

```java
return new ReentrantLock(true); // fair lock
// → FIFO 순서로 공정한 처리, 기아 상태 방지
```

##### **2) 고급 기능 추가**

```java
// 타임아웃 기능
public UserPoint chargeWithTimeout(long userId, long amount, int timeoutSeconds) {
    if (!lock.tryLock(timeoutSeconds, TimeUnit.SECONDS)) {
        throw new RuntimeException("락 획득 타임아웃: " + timeoutSeconds + "초");
    }
    // ...
}

// 락 상태 모니터링
public String getLockStatus() {
    return String.format("Total Locks: %d, Active Locks: %d, Queue Length: %d",
                       totalLocks, activeLocks, maxQueueLength);
}
```

##### **3) Virtual Thread 호환성**

```java
// synchronized: Virtual Thread에서 성능 이슈
// ReentrantLock: Virtual Thread 최적화됨
```

---

## 🧹 개선사항 3: 스마트 락 관리

### **목적**: 메모리 누수 방지 및 자동 정리

#### **핵심 기능들**

##### **1) 락 생성 시간 추적**

```java
private final ConcurrentHashMap<Long, Long> lockCreationTime = new ConcurrentHashMap<>();

private ReentrantLock getUserLock(Long userId) {
    return userLocks.computeIfAbsent(userId, k -> {
        lockCreationTime.put(k, System.currentTimeMillis()); // 생성 시간 기록
        return new ReentrantLock(true);
    });
}
```

##### **2) 자동 정리 스케줄러**

```java
@Scheduled(fixedDelayString = "${point.concurrency.cleanup-interval:3600000}")
public void cleanupUnusedLocks() {
    // 1시간 이상 된 락이고, 현재 사용 중이 아닌 경우만 정리
    if (currentTime - creationTime > cleanupThreshold) {
        ReentrantLock lock = userLocks.get(userId);
        if (lock != null && !lock.isLocked()) {
            userLocks.remove(userId);
            lockCreationTime.remove(userId);
        }
    }
}
```

##### **3) 설정 기반 정리 정책**

```java
// application.yml에서 제어 가능
point:
  concurrency:
    max-locks: 10000           # 최대 보유 락 수
    cleanup-interval: 3600000  # 정리 주기 (1시간)
```

---

## 🎯 **놀라운 발견: PointService만 수정하면 충분했던 이유**

### **핵심 원리: 관심사 분리 (Separation of Concerns)**

#### **1. 테스트는 '결과'를 검증, '구현 방식'은 무관**

```java
// ConcurrencyResolvedTest의 검증 로직
@Test
void 동시_충전_Lost_Update_해결() {
    // When: 동시 실행
    for (int i = 0; i < threadCount; i++) {
        CompletableFuture.runAsync(() -> {
            pointService.charge(userId, chargeAmount); // ← 구현 방식 무관
        });
    }

    // Then: 결과만 검증
    assertThat(finalUserPoint.point()).isEqualTo(expectedPoint); // ← 이것만 중요
    // synchronized든 ReentrantLock이든 결과가 같으면 테스트 통과
}
```

#### **2. 동일한 계약(Contract) 보장**

| 계약 내용            | synchronized | ReentrantLock | 테스트 영향 |
| -------------------- | ------------ | ------------- | ----------- |
| **Lost Update 방지** | ✅           | ✅            | 영향 없음   |
| **원자성 보장**      | ✅           | ✅            | 영향 없음   |
| **메모리 가시성**    | ✅           | ✅            | 영향 없음   |
| **순차 처리**        | ✅           | ✅            | 영향 없음   |

#### **3. 인터페이스 안정성**

```java
// 공개 API는 전혀 변경되지 않음
public UserPoint charge(long userId, long amount)  // 동일
public UserPoint use(long userId, long amount)     // 동일
public List<PointHistory> getHistory(long userId)  // 동일

// 내부 구현만 변경 (private 영역)
private ReentrantLock getUserLock(Long userId)     // 구현 방식만 변경
```

#### **4. Black Box 테스트 설계**

```java
// 테스트 관점에서는 PointService가 블랙박스
// 입력: userId, amount
// 출력: UserPoint
// 부작용: 포인트 변경, 히스토리 기록

// 내부에서 어떤 락을 쓰든 상관없이
// "동시성이 보장되는가?"만 중요
```

### **5. 좋은 아키텍처 설계의 증거**

#### **느슨한 결합 (Loose Coupling)**

```java
// 테스트 코드가 구현 세부사항에 의존하지 않음
// → 구현 변경이 테스트에 영향 없음
```

#### **높은 응집도 (High Cohesion)**

```java
// 동시성 제어 로직이 PointService 내부에 캡슐화됨
// → 외부에서는 변경 사실을 알 필요 없음
```

#### **단일 책임 원칙 (SRP)**

```java
// PointService: 포인트 비즈니스 로직 + 동시성 제어
// Test: 비즈니스 규칙 검증
// → 각자의 책임이 명확히 분리됨
```

---

## 🏆 **최종 성과**

### **리뷰어 피드백 100% 반영**

- ✅ **Spring Property 분리** - 운영 안전성 확보
- ✅ **ReentrantLock 적용** - Virtual Thread 대비
- ✅ **락 관리 개선** - 메모리 누수 방지
- ✅ **하이브리드 테스트 전략** - Mock + Spy 적절 혼용

### **개선 효과**

- ✅ **기능적 완성도**: Lost Update, Race Condition 완벽 해결
- ✅ **성능**: 사용자별 병렬 처리, Fair Lock으로 공정성 향상
- ✅ **확장성**: 설정 기반 정책, 환경별 분리 가능
- ✅ **안정성**: 메모리 관리, 자동 정리, 타임아웃 기능
- ✅ **미래 대비**: Virtual Thread 호환성 확보

### **아키텍처적 우수성**

- ✅ **관심사 분리**: 구현 변경이 테스트에 영향 없음
- ✅ **느슨한 결합**: 내부 구현과 외부 인터페이스 분리
- ✅ **높은 응집도**: 관련 기능들이 적절히 그룹화
- ✅ **확장 가능성**: 새로운 기능 추가 시 기존 코드 영향 최소

## 💡 **핵심 교훈**

**"좋은 설계는 변경에 강하다"**

PointService만 수정해도 모든 테스트가 통과한다는 것은:

1. **인터페이스와 구현이 잘 분리**되어 있고
2. **테스트가 구현이 아닌 행위를 검증**하며
3. **관심사가 명확히 분리**되어 있다는 증거

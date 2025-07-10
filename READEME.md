# 🔒 Java 동시성 제어 방식 비교 분석 보고서

## 📋 목차

1. [개요](#-개요)
2. [동시성 문제 분석](#-동시성-문제-분석)
3. [해결 방안 비교](#️-해결-방안-비교)

---

## 🎯 개요

### 프로젝트 배경

- **과제**: 포인트 시스템의 동시성 제어 구현
- **환경**: Java 17, Spring Boot 3.2.0, 인메모리 Map 기반 데이터 저장
- **목표**: 동일 사용자 대상 동시 요청 시 데이터 무결성 보장

### 검증한 동시성 제어 방식

1. **synchronized** 키워드
2. **ReentrantLock** 클래스
3. **AtomicLong** + ConcurrentHashMap
4. **개선된 ThreadSafe** 방식 (권장)

---

## 🚨 동시성 문제 분석

### 현재 구조의 문제점

```java
// 문제가 있는 기존 코드
public UserPoint charge(long userId, long amount) {
    UserPoint current = userPointTable.selectById(userId);     // 시점 A
    chargePolicy.validate(amount, current.point());            // 시점 B
    long newPoint = current.point() + amount;                  // 시점 C
    UserPoint updated = userPointTable.insertOrUpdate(userId, newPoint); // 시점 D
    pointHistoryTable.insert(userId, amount, CHARGE, now());   // 시점 E
    return updated;
}
```

### 발생 가능한 동시성 문제들

#### 1. **Lost Update Problem (갱신 손실)**

```
Thread 1: 현재값 1000 조회 → 1100으로 업데이트
Thread 2: 현재값 1000 조회 → 1200으로 업데이트
결과: 마지막 업데이트(1200)만 반영, Thread 1의 충전(100) 손실
```

#### 2. **Race Condition (경쟁 조건)**

```
시나리오: 사용자가 95만원 보유, 동시에 3만원씩 3번 충전 시도
Thread 1: 정책 검증 통과 (95만 + 3만 = 98만 ≤ 100만)
Thread 2: 정책 검증 통과 (95만 + 3만 = 98만 ≤ 100만)
Thread 3: 정책 검증 통과 (95만 + 3만 = 98만 ≤ 100만)
결과: 최대 한도(100만원) 초과 가능
```

#### 3. **Data Inconsistency (데이터 불일치)**

```
포인트 테이블: 정확하지 않은 값
히스토리 테이블: 모든 요청이 기록됨
결과: 포인트 잔액과 히스토리 합계 불일치
```

---

## 🛠️ 해결 방안 비교

### 1. synchronized 키워드

#### 구현 방식

```java
public UserPoint charge(long userId, long amount) {
    synchronized (lockManager.getUserSyncLock(userId)) {
        // 기존 비즈니스 로직 그대로
        return originalService.charge(userId, amount);
    }
}
```

#### 장점

- ✅ **구현 간단**: 기존 코드 변경 최소화
- ✅ **JVM 최적화**: 바이트코드 레벨 최적화 지원
- ✅ **데드락 위험 낮음**: JVM이 자동 관리
- ✅ **예외 안전성**: 예외 발생 시 자동 락 해제

#### 단점

- ❌ **성능 오버헤드**: 객체 모니터 락 비용
- ❌ **공정성 부재**: 대기 순서 보장 안됨
- ❌ **세밀한 제어 불가**: 타임아웃, 인터럽트 불가
- ❌ **락 상태 확인 불가**: 디버깅 어려움

#### 적합한 상황

- 간단한 동시성 제어
- 락 보유 시간이 짧은 경우
- 공정성이 중요하지 않은 경우

---

### 2. ReentrantLock 클래스

#### 구현 방식

```java
public UserPoint charge(long userId, long amount) {
    ReentrantLock lock = lockManager.getUserLock(userId);
    lock.lock();
    try {
        return originalService.charge(userId, amount);
    } finally {
        lock.unlock(); // 반드시 해제
    }
}
```

#### 장점

- ✅ **공정성 보장**: Fair Lock으로 FIFO 순서 처리
- ✅ **타임아웃 지원**: tryLock(timeout) 메서드
- ✅ **인터럽트 가능**: lockInterruptibly() 메서드
- ✅ **락 상태 확인**: isLocked(), getQueueLength() 등
- ✅ **조건부 대기**: Condition 객체 활용

#### 단점

- ❌ **코드 복잡성**: try-finally 블록 필수
- ❌ **메모리 오버헤드**: 추가 객체 생성
- ❌ **실수 위험성**: unlock() 누락 시 데드락
- ❌ **성능 비용**: synchronized 대비 약간의 오버헤드

#### 적합한 상황

- 공정성이 중요한 비즈니스
- 락 타임아웃 필요한 경우
- 복잡한 동기화 로직
- 락 상태 모니터링 필요시

---

### 3. AtomicLong + ConcurrentHashMap

#### 구현 방식

```java
private final ConcurrentHashMap<Long, AtomicLong> userPoints = new ConcurrentHashMap<>();

public UserPoint charge(long userId, long amount) {
    AtomicLong userPoint = userPoints.computeIfAbsent(userId, k -> new AtomicLong(0));

    long currentPoint, newPoint;
    do {
        currentPoint = userPoint.get();
        chargePolicy.validate(amount, currentPoint);
        newPoint = currentPoint + amount;
    } while (!userPoint.compareAndSet(currentPoint, newPoint));

    return new UserPoint(userId, newPoint, System.currentTimeMillis());
}
```

#### 장점

- ✅ **최고 성능**: Lock-Free 알고리즘
- ✅ **데드락 없음**: 락을 사용하지 않음
- ✅ **확장성 우수**: 멀티코어 환경에서 뛰어난 성능
- ✅ **메모리 효율**: 락 객체 불필요

#### 단점

- ❌ **복잡한 로직 제한**: 단순한 연산만 적용 가능
- ❌ **ABA 문제**: 값이 A→B→A로 변경되는 경우 감지 불가
- ❌ **디버깅 어려움**: 경쟁 상태 재현 어려움
- ❌ **원자성 제한**: 복합 연산(포인트 + 히스토리)의 원자성 보장 어려움

#### 적합한 상황

- 단순한 수치 연산
- 고성능이 중요한 경우
- 복잡한 비즈니스 로직이 없는 경우

---

### 4. 개선된 ThreadSafe 방식 (권장)

#### 구현 방식

```java
public UserPoint charge(long userId, long amount) {
    synchronized (lockManager.getUserSyncLock(userId)) {
        // 1. 현재 포인트 조회
        UserPoint currentUserPoint = userPointTable.selectById(userId);

        // 2. 정책 검증
        chargePolicy.validate(amount, currentUserPoint.point());

        // 3. 포인트 업데이트
        long newPoint = currentUserPoint.point() + amount;
        UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, newPoint);

        // 4. 히스토리 기록
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());

        return updatedUserPoint;
    }
}
```

#### 핵심 개선사항

- **사용자별 락**: 다른 사용자는 병렬 처리 가능
- **기존 로직 유지**: 복잡한 비즈니스 로직 그대로 활용
- **예외 처리 유지**: 기존 정책 검증과 예외 처리 그대로
- **히스토리 일관성**: 포인트 변경

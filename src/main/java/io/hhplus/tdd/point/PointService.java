package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.point.config.ConcurrencyConfig;
import io.hhplus.tdd.point.policy.ChargePolicy;
import io.hhplus.tdd.point.policy.UsePolicy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

/**
 * Thread-Safe 포인트 서비스 (개선된 버전)
 * 
 * 동시성 제어 전략:
 * 1. 사용자별 락으로 동일 사용자의 동시 요청은 순차 처리
 * 2. 서로 다른 사용자의 요청은 병렬 처리 가능
 * 3. 기존 비즈니스 로직(정책, 예외) 완전 보존
 * 4. 포인트 변경과 히스토리 기록의 원자성 보장
 *
 * 개선사항:
 * 1. synchronized → ReentrantLock 변경 (Virtual Thread 호환성)
 * 2. 락 생성 시간 추적 및 자동 정리 기능
 * 3. 설정 기반 락 관리 정책
 * 4. 더 정교한 락 정리 전략
 */
@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private final ChargePolicy chargePolicy;
    private final UsePolicy usePolicy;

    /**
     * ConcurrentHashMap의 동시성 보장 방식:
     * Segment-based Locking: 내부적으로 여러 세그먼트로 나누어 부분 락킹
     * Lock-free 읽기: 읽기 작업은 락 없이 수행
     * 원자적 연산: computeIfAbsent() 메서드가 원자적으로 실행
     * 
     * HashMap으로 동시 접근시 무한루프 (링크드 리스트 순환 참조), 데이터 손실, NullPointerException 발생 가능
     * ConcurrentHashMap 접근시 동시 접근 안전, 성능 최적화, 원자적 연산 보장
     */

    private final ConcurrencyConfig concurrencyConfig;
    
    // 사용자별 락 관리 (ReentrantLock 사용, ConcurrentHashMap으로 스레드 안전)
    private final ConcurrentHashMap<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();
    // 락 생성 시간 추적 (정리를 위함)
    private final ConcurrentHashMap<Long, Long> lockCreationTime = new ConcurrentHashMap<>();

    /**
     * Spring 의존성 주입을 위한 생성자
     * 
     * @param userPointTable 사용자 포인트 데이터 접근 객체
     * @param pointHistoryTable 포인트 히스토리 데이터 접근 객체  
     * @param chargePolicy 충전 정책 검증 객체
     * @param usePolicy 사용 정책 검증 객체
     */
    public PointService(UserPointTable userPointTable, 
                       PointHistoryTable pointHistoryTable,
                       ChargePolicy chargePolicy,
                       UsePolicy usePolicy,
                       ConcurrencyConfig concurrencyConfig) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
        this.chargePolicy = chargePolicy;
        this.usePolicy = usePolicy;
        this.concurrencyConfig = concurrencyConfig;
    }

    /**
     * 사용자별 ReentrantLock 획득
     * 
     * computeIfAbsent는 원자적으로 실행되어 동일한 사용자에 대해
     * 항상 같은 락 객체를 반환합니다.
     *
     * 개선사항:
     * 1. ReentrantLock 사용으로 Virtual Thread 호환성 확보
     * 2. 공정한 락 제공 (fair lock)
     * 3. 락 생성 시간 추적
     * 
     * @param userId 사용자 ID
     * @return 해당 사용자 전용 ReentrantLock
     */
    private ReentrantLock getUserLock(Long userId) {
        return userLocks.computeIfAbsent(userId, k -> {
            lockCreationTime.put(k, System.currentTimeMillis());
            return new ReentrantLock(true); // fair lock
        });
    }

    /**
     * 사용자의 포인트를 조회합니다.
     * 
     * 조회 연산은 동시성 제어를 적용하지 않습니다.
     * 
     * @param userId 사용자 ID
     * @return 사용자의 포인트 정보
     */
    public UserPoint getPoint(long userId) {
        return userPointTable.selectById(userId);
    }

    /**
     * 사용자의 포인트를 충전합니다. (ReentrantLock 적용)
     * 
    * 기존에는 동일 사용자에게 synchronized 블록을 사용하고 다른 사용자는 병렬 처리
     * synchronized는 각 객체에게 내장 모니터 락을 가지게 하고, 상호 배제(mutual exclusion)를 보장(동일한 락 객체에 대해 한 번에 하나의 스레드만 진입 가능)
     * Memory Visibility로 락 해제시 모든 변경사항이 다른 스레드에게 보임
     * 
     * 개선사항:
     * 1. ReentrantLock 사용으로 Virtual Thread 호환성
     * 2. try-finally 블록으로 확실한 락 해제
     * 3. 타임아웃 기능 (필요시 활성화 가능)
     * 
     * @param userId 사용자 ID
     * @param amount 충전할 금액
     * @return 충전 후 사용자 포인트 정보
     */
    public UserPoint charge(long userId, long amount) {
        ReentrantLock lock = getUserLock(userId);
        lock.lock(); // 또는 lock.tryLock(5, TimeUnit.SECONDS) 사용 가능
        try {
            // 1. 현재 포인트 조회
            UserPoint currentUserPoint = userPointTable.selectById(userId);
            
            // 2. 충전 정책 검증
            chargePolicy.validate(amount, currentUserPoint.point());
            
            // 3. 새로운 포인트 계산 및 업데이트
            long newPoint = currentUserPoint.point() + amount;
            UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, newPoint);
            
            // 4. 충전 히스토리 기록
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());
            
            return updatedUserPoint;
        } finally {
            lock.unlock(); // 반드시 해제
        }
    }

    /**
     * 사용자의 포인트를 사용합니다. (ReentrantLock 적용)
     * 
     * @param userId 사용자 ID
     * @param amount 사용할 금액
     * @return 사용 후 사용자 포인트 정보
     */
    public UserPoint use(long userId, long amount) {
        ReentrantLock lock = getUserLock(userId);
        lock.lock();
        try {
            // 1. 현재 포인트 조회
            UserPoint currentUserPoint = userPointTable.selectById(userId);
            
            // 2. 사용 정책 검증
            usePolicy.validate(amount, currentUserPoint.point());
            
            // 3. 새로운 포인트 계산 및 업데이트 (차감)
            long newPoint = currentUserPoint.point() - amount;
            UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, newPoint);
            
            // 4. 사용 히스토리 기록
            pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
            
            return updatedUserPoint;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 사용자의 포인트 사용/충전 내역을 조회합니다.
     * 
     * @param userId 사용자 ID
     * @return 사용자의 포인트 내역 리스트
     */
    public List<PointHistory> getHistory(long userId) {
        return pointHistoryTable.selectAllByUserId(userId);
    }

    // ==================== 개선된 락 관리 메서드들 ====================

    /**
     * 주기적 락 정리 (스케줄러)
     * 
     * 개선사항:
     * 1. @Scheduled로 자동 실행
     * 2. 설정 기반 정리 정책
     * 3. 사용 중인 락은 보호
     * 4. 로그를 통한 모니터링
     */
    @Scheduled(fixedDelayString = "${point.concurrency.cleanup-interval:3600000}") // 1시간마다
    public void cleanupUnusedLocks() {
        if (userLocks.size() <= concurrencyConfig.maxLocks()) {
            return; // 임계치 미만이면 정리 생략
        }
        
        long currentTime = System.currentTimeMillis();
        long cleanupThreshold = concurrencyConfig.cleanupInterval(); // 1시간
        int beforeSize = userLocks.size();
        int removedCount = 0;
        
        // 사용하지 않는 오래된 락들 정리
        for (var entry : lockCreationTime.entrySet()) {
            Long userId = entry.getKey();
            Long creationTime = entry.getValue();
            
            // 1시간 이상 된 락이고, 현재 사용 중이 아닌 경우만 정리
            if (currentTime - creationTime > cleanupThreshold) {
                ReentrantLock lock = userLocks.get(userId);
                if (lock != null && !lock.isLocked()) {
                    userLocks.remove(userId);
                    lockCreationTime.remove(userId);
                    removedCount++;
                }
            }
        }
        
        if (removedCount > 0) {
            System.out.printf("Lock cleanup completed: %d -> %d locks (removed: %d)%n", 
                            beforeSize, userLocks.size(), removedCount);
        }
    }

    /**
     * 현재 관리 중인 사용자 락 개수 반환 (모니터링용)
     * 
     * @return 활성 락 개수
     */
    public int getActiveLockCount() {
        return userLocks.size();
    }

    /**
     * 상세한 락 상태 정보 반환 (모니터링용)
     * 
     * @return 락 상태 정보
     */
    public String getLockStatus() {
        int totalLocks = userLocks.size();
        long activeLocks = userLocks.values().stream()
                .mapToLong(lock -> lock.isLocked() ? 1 : 0)
                .sum();
        
        return String.format("Total Locks: %d, Active Locks: %d, Queue Length: %d", 
                           totalLocks, activeLocks, getMaxQueueLength());
    }
    
    private int getMaxQueueLength() {
        return userLocks.values().stream()
                .mapToInt(ReentrantLock::getQueueLength)
                .max()
                .orElse(0);
    }

    /**
     * 특정 사용자의 락 해제 (관리자 기능)
     * 
     * 개선사항: 안전성 검증 추가
     * 
     * @param userId 락을 해제할 사용자 ID
     * @return 락이 존재했는지 여부
     */
    public boolean releaseLockForUser(Long userId) {
        ReentrantLock lock = userLocks.get(userId);
        if (lock != null && !lock.isLocked()) {
            userLocks.remove(userId);
            lockCreationTime.remove(userId);
            return true;
        }
        return false;
    }

    /**
     * 락 타임아웃을 사용한 안전한 충전 (선택적 기능)
     * 
     * @param userId 사용자 ID
     * @param amount 충전할 금액
     * @param timeoutSeconds 타임아웃 시간 (초)
     * @return 충전 후 사용자 포인트 정보
     * @throws RuntimeException 락 획득 실패 시
     */
    public UserPoint chargeWithTimeout(long userId, long amount, int timeoutSeconds) {
        ReentrantLock lock = getUserLock(userId);
        try {
            if (!lock.tryLock(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new RuntimeException("락 획득 타임아웃: " + timeoutSeconds + "초");
            }
            
            try {
                return charge(userId, amount);
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("락 대기 중 인터럽트 발생", e);
        }
    }
      // ==================== 모니터링 및 관리 메서드 (임시 구현) ====================

    /**
     * 메모리 사용량 정보 반환 (모니터링용)
     * 
     * @return 현재 메모리 상태 정보
     */
    public String getMemoryInfo() {
        return String.format("Active Locks: %d, Map Size: %d bytes (estimated)", 
                           userLocks.size(), 
                           userLocks.size() * 64); // 대략적인 메모리 사용량
    }
}
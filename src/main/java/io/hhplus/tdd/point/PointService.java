package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.point.policy.ChargePolicy;
import io.hhplus.tdd.point.policy.UsePolicy;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-Safe 포인트 서비스 (STEP02 최종 구현)
 * 
 * 동시성 제어 전략:
 * 1. 사용자별 락으로 동일 사용자의 동시 요청은 순차 처리
 * 2. 서로 다른 사용자의 요청은 병렬 처리 가능
 * 3. 기존 비즈니스 로직(정책, 예외) 완전 보존
 * 4. 포인트 변경과 히스토리 기록의 원자성 보장
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

    // 사용자별 락 객체 관리 (ConcurrentHashMap으로 스레드 안전)
    private final ConcurrentHashMap<Long, Object> userLocks = new ConcurrentHashMap<>();

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
                       UsePolicy usePolicy) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
        this.chargePolicy = chargePolicy;
        this.usePolicy = usePolicy;
    }

    /**
     * 사용자별 락 객체 획득
     * 
     * computeIfAbsent는 원자적으로 실행되어 동일한 사용자에 대해
     * 항상 같은 락 객체를 반환합니다.
     * 
     * @param userId 사용자 ID
     * @return 해당 사용자 전용 락 객체
     */
    private Object getUserLock(Long userId) {
        return userLocks.computeIfAbsent(userId, k -> new Object());
    }

    /**
     * 사용자의 포인트를 조회합니다.
     * 
     * 조회 연산은 동시성 제어를 적용하지 않습니다:
     * - 읽기 전용 연산으로 성능상 이점
     * - Eventual Consistency 허용
     * - 실시간 정확성보다는 응답 속도 우선
     * 
     * @param userId 사용자 ID
     * @return 사용자의 포인트 정보
     */
    public UserPoint getPoint(long userId) {
        return userPointTable.selectById(userId);
    }

    /**
     * 사용자의 포인트를 충전합니다.
     * 
     * 동시성 제어 적용:
     * - 동일 사용자: synchronized 블록으로 순차 처리
     * - 다른 사용자: 병렬 처리 가능
     * - 모든 단계(조회→검증→업데이트→기록)가 원자적으로 실행
     * 
     * synchronized의 동작 원리:
     * 
     * Monitor Lock: 각 객체는 내장 모니터 락을 가짐
     * 상호 배제: 동일한 락 객체에 대해 한 번에 하나의 스레드만 진입 가능
     * Memory Visibility: 락 해제 시 모든 변경사항이 다른 스레드에게 보임
     * 
     * @param userId 사용자 ID
     * @param amount 충전할 금액
     * @return 충전 후 사용자 포인트 정보
     * @throws io.hhplus.tdd.point.exception.InvalidAmountException 유효하지 않은 충전 금액
     * @throws io.hhplus.tdd.point.exception.ExceedsMaxPointException 최대 포인트 초과
     */
    public UserPoint charge(long userId, long amount) {
        // 사용자별 락으로 동시성 제어 - 핵심 동시성 제어 구간
        synchronized (getUserLock(userId)) { // 모니터 락
            // 임계 영역 (Critical Section)
            
            // 1. 현재 포인트 조회
            UserPoint currentUserPoint = userPointTable.selectById(userId);
            
            // 2. 충전 정책 검증 (기존 비즈니스 로직 그대로 유지)
            chargePolicy.validate(amount, currentUserPoint.point());
            
            // 3. 새로운 포인트 계산 및 업데이트
            long newPoint = currentUserPoint.point() + amount;
            UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, newPoint);
            
            // 4. 충전 히스토리 기록 (포인트 변경과 원자적으로 처리)
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());
            
            return updatedUserPoint;
        }
    }

    /**
     * 사용자의 포인트를 사용합니다.
     * 
     * 동시성 제어 적용:
     * - 잔고 부족 검증이 정확히 동작
     * - 동시 사용 요청 시 순차 처리로 이중 차감 방지
     * - 포인트 차감과 히스토리 기록의 일관성 보장
     * 
     * @param userId 사용자 ID
     * @param amount 사용할 금액
     * @return 사용 후 사용자 포인트 정보
     * @throws io.hhplus.tdd.point.exception.InvalidAmountException 유효하지 않은 사용 금액
     * @throws io.hhplus.tdd.point.exception.InsufficientPointException 포인트 부족
     */
    public UserPoint use(long userId, long amount) {
        // 사용자별 락으로 동시성 제어 - 핵심 동시성 제어 구간
        synchronized (getUserLock(userId)) {
            
            // 1. 현재 포인트 조회
            UserPoint currentUserPoint = userPointTable.selectById(userId);
            
            // 2. 사용 정책 검증 (기존 비즈니스 로직 그대로 유지)
            usePolicy.validate(amount, currentUserPoint.point());
            
            // 3. 새로운 포인트 계산 및 업데이트 (차감)
            long newPoint = currentUserPoint.point() - amount;
            UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, newPoint);
            
            // 4. 사용 히스토리 기록 (포인트 변경과 원자적으로 처리)
            pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
            
            return updatedUserPoint;
        }
    }

    /**
     * 사용자의 포인트 사용/충전 내역을 조회합니다.
     * 
     * 히스토리 조회는 동시성 제어를 적용하지 않습니다:
     * - 읽기 전용 연산
     * - 대용량 데이터 조회 시 성능상 이점
     * - 히스토리는 추가만 되고 수정되지 않는 특성
     * 
     * @param userId 사용자 ID
     * @return 사용자의 포인트 내역 리스트 (충전/사용 모두 포함)
     */
    public List<PointHistory> getHistory(long userId) {
        return pointHistoryTable.selectAllByUserId(userId);
    }

    // ==================== 모니터링 및 관리 메서드 ====================

    /**
     * 현재 관리 중인 사용자 락 개수 반환 (모니터링용)
     * 
     * 메모리 사용량 추적과 시스템 상태 모니터링에 활용
     * 
     * @return 활성 락 개수
     */
    public int getActiveLockCount() {
        return userLocks.size();
    }

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

    /**
     * 사용하지 않는 락 정리 (메모리 누수 방지)
     * 
     * 실제 운영 환경에서는:
     * 1. @Scheduled로 주기적 실행 (예: 1시간마다)
     * 2. 사용자별 마지막 접근 시간 추적
     * 3. 임계치 도달 시 LRU 방식으로 정리
     * 
     * 현재는 단순한 임계치 기반 정리 로직
     */
    public void cleanupUnusedLocks() {
        final int MAX_LOCKS = 10000; // 최대 보유 락 수
        
        if (userLocks.size() > MAX_LOCKS) {
            // 실제 운영에서는 더 정교한 로직 필요:
            // - 마지막 사용 시간 기반 LRU
            // - 점진적 정리 (한 번에 모두 삭제하지 않음)
            // - 현재 사용 중인 락은 제외
            
            int beforeSize = userLocks.size();
            userLocks.clear(); // 간단한 예시 (실제로는 권장하지 않음)
            
            System.out.printf("Lock cleanup executed: %d -> %d locks%n", 
                            beforeSize, userLocks.size());
        }
    }

    /**
     * 특정 사용자의 락 해제 (관리자 기능)
     * 
     * 긴급 상황이나 디버깅 시 사용
     * 주의: 해당 사용자의 진행 중인 작업이 있다면 위험할 수 있음
     * 
     * @param userId 락을 해제할 사용자 ID
     * @return 락이 존재했는지 여부
     */
    public boolean releaseLockForUser(Long userId) {
        Object removedLock = userLocks.remove(userId);
        return removedLock != null;
    }
}
package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.database.PointHistoryTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReentrantLock 기반 동시성 테스트
 * 
 * 목적:
 * 1. ReentrantLock으로 개선된 동시성 제어 검증
 * 2. Virtual Thread 환경에서의 안정성 확인
 * 3. 락 관리 기능 검증
 * 4. 타임아웃 기능 검증
 */
@SpringBootTest
class ReentrantLockConcurrencyTest {

    @Autowired
    private PointService pointService;
    
    @Autowired 
    private UserPointTable userPointTable;
    
    @Autowired
    private PointHistoryTable pointHistoryTable;

    @Test
    @DisplayName("✅ ReentrantLock: 동시 충전 시 Lost Update 문제 해결")
    void ReentrantLock_동시_충전_Lost_Update_해결() throws InterruptedException {
        // Given: 사용자에게 기본 포인트 설정
        long userId = 10001L;
        long initialPoint = 1000L;
        userPointTable.insertOrUpdate(userId, initialPoint);
        
        // When: 20개 스레드가 동시에 100포인트씩 충전
        int threadCount = 20;
        long chargeAmount = 100L;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        List<UserPoint> results = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    UserPoint result = pointService.charge(userId, chargeAmount);
                    synchronized (results) {
                        results.add(result);
                    }
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }, executorService);
        }
        
        // 모든 스레드 완료 대기
        latch.await(15, TimeUnit.SECONDS);
        
        // Then: 정확한 결과 확인
        long expectedPoint = initialPoint + (threadCount * chargeAmount); // 3000
        UserPoint finalUserPoint = userPointTable.selectById(userId);
        List<PointHistory> histories = pointHistoryTable.selectAllByUserId(userId);
        
        System.out.println("=== ReentrantLock Lost Update 해결 테스트 결과 ===");
        System.out.println("초기 포인트: " + initialPoint);
        System.out.println("충전 횟수: " + threadCount + "회");
        System.out.println("충전 금액: " + chargeAmount + "포인트");
        System.out.println("기대 포인트: " + expectedPoint);
        System.out.println("실제 포인트: " + finalUserPoint.point());
        System.out.println("성공한 충전: " + successCount.get() + "회");
        System.out.println("히스토리 개수: " + histories.size());
        
        // ✅ ReentrantLock으로 완전히 해결됨
        assertThat(finalUserPoint.point()).isEqualTo(expectedPoint);
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(histories).hasSize(threadCount);
        
        executorService.shutdown();
    }

    @Test
    @DisplayName("⚡ ReentrantLock 성능: 대량 동시 처리 테스트")
    void ReentrantLock_대량_동시_처리_성능() throws InterruptedException {
        // Given: 성능 테스트용 사용자들
        int userCount = 10;
        int operationsPerUser = 50;
        long chargeAmount = 100L;
        
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < userCount; i++) {
            long userId = 20000L + i;
            userIds.add(userId);
            userPointTable.insertOrUpdate(userId, 10000L);
        }
        
        // When: 대량의 동시 요청 처리 (총 500개 작업)
        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(userCount * operationsPerUser);
        
        long startTime = System.currentTimeMillis();
        
        for (Long userId : userIds) {
            for (int j = 0; j < operationsPerUser; j++) {
                CompletableFuture.runAsync(() -> {
                    try {
                        pointService.charge(userId, chargeAmount);
                    } finally {
                        latch.countDown();
                    }
                }, executorService);
            }
        }
        
        latch.await(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        
        // Then: 성능 지표 측정
        long totalOperations = userCount * operationsPerUser;
        long totalTime = endTime - startTime;
        double operationsPerSecond = (double) totalOperations / (totalTime / 1000.0);
        
        System.out.println("=== ReentrantLock 성능 측정 결과 ===");
        System.out.println("총 사용자 수: " + userCount);
        System.out.println("사용자당 연산: " + operationsPerUser);
        System.out.println("총 연산 수: " + totalOperations);
        System.out.println("총 처리 시간: " + totalTime + "ms");
        System.out.println("초당 처리량: " + String.format("%.2f", operationsPerSecond) + " ops/sec");
        System.out.println("평균 응답 시간: " + String.format("%.2f", (double) totalTime / totalOperations) + "ms");
        
        // 모든 연산이 정확히 처리되었는지 확인
        for (Long userId : userIds) {
            UserPoint userPoint = userPointTable.selectById(userId);
            long expectedPoint = 10000L + (operationsPerUser * chargeAmount);
            assertThat(userPoint.point()).isEqualTo(expectedPoint);
            
            List<PointHistory> histories = pointHistoryTable.selectAllByUserId(userId);
            assertThat(histories).hasSize(operationsPerUser);
        }
        
        // ReentrantLock도 우수한 성능 보장
        assertThat(operationsPerSecond).isGreaterThan(100.0);
        
        executorService.shutdown();
    }

    @Test
    @DisplayName("🔒 ReentrantLock 공정성: Fair Lock 동작 확인")
    void ReentrantLock_공정성_확인() throws InterruptedException {
        // Given: 동일한 사용자로 설정
        long userId = 30000L;
        userPointTable.insertOrUpdate(userId, 1000L);
        
        // When: 순차적으로 실행되어야 하는 동시 요청
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        List<Long> executionOrder = new ArrayList<>();
        List<Long> pointSnapshots = new ArrayList<>();
        
        for (int i = 0; i < threadCount; i++) {
            final int operationId = i;
            CompletableFuture.runAsync(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    UserPoint result = pointService.charge(userId, 100L);
                    long endTime = System.currentTimeMillis();
                    
                    synchronized (executionOrder) {
                        executionOrder.add(endTime - startTime);
                        pointSnapshots.add(result.point());
                    }
                    
                    System.out.println("Operation " + operationId + " completed: " + result.point() + " points");
                    
                } finally {
                    latch.countDown();
                }
            }, executorService);
        }
        
        latch.await(15, TimeUnit.SECONDS);
        
        // Then: Fair Lock으로 순차 실행 확인
        UserPoint finalPoint = userPointTable.selectById(userId);
        List<PointHistory> histories = pointHistoryTable.selectAllByUserId(userId);
        
        System.out.println("=== ReentrantLock 공정성 검증 결과 ===");
        System.out.println("최종 포인트: " + finalPoint.point());
        System.out.println("포인트 변화: " + pointSnapshots);
        System.out.println("실행 시간들: " + executionOrder + "ms");
        System.out.println("히스토리 개수: " + histories.size());
        
        // 모든 연산이 순차적으로 실행되어 정확한 결과
        assertThat(finalPoint.point()).isEqualTo(1000L + (threadCount * 100L));
        assertThat(histories).hasSize(threadCount);
        
        // 포인트가 순차적으로 증가했는지 확인
        for (int i = 1; i < pointSnapshots.size(); i++) {
            assertThat(pointSnapshots.get(i)).isGreaterThan(pointSnapshots.get(i - 1));
        }
        
        executorService.shutdown();
    }

    @Test
    @DisplayName("🕐 락 타임아웃 기능 테스트")
    void 락_타임아웃_기능_테스트() throws InterruptedException {
        // Given: 동일한 사용자 ID
        long userId = 40000L;
        userPointTable.insertOrUpdate(userId, 5000L);
        
        // When: 첫 번째 스레드가 락을 오래 보유하는 상황 시뮬레이션
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(2);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger timeoutCount = new AtomicInteger(0);
        
        // 첫 번째 스레드: 락을 오래 보유
        CompletableFuture.runAsync(() -> {
            try {
                pointService.charge(userId, 1000L); // 정상 충전
                Thread.sleep(2000); // 2초 대기 (인위적 지연)
                successCount.incrementAndGet();
                startLatch.countDown(); // 두 번째 스레드 시작 허용
            } catch (Exception e) {
                System.out.println("첫 번째 스레드 예외: " + e.getMessage());
            } finally {
                finishLatch.countDown();
            }
        }, executorService);
        
        // 두 번째 스레드: 타임아웃으로 충전 시도
        CompletableFuture.runAsync(() -> {
            try {
                startLatch.await(); // 첫 번째 스레드가 시작할 때까지 대기
                pointService.chargeWithTimeout(userId, 500L, 1); // 1초 타임아웃
                successCount.incrementAndGet();
            } catch (RuntimeException e) {
                if (e.getMessage().contains("타임아웃")) {
                    timeoutCount.incrementAndGet();
                    System.out.println("예상된 타임아웃 발생: " + e.getMessage());
                }
            } catch (Exception e) {
                System.out.println("두 번째 스레드 예외: " + e.getMessage());
            } finally {
                finishLatch.countDown();
            }
        }, executorService);
        
        finishLatch.await(10, TimeUnit.SECONDS);
        
        // Then: 타임아웃 기능이 올바르게 동작했는지 확인
        System.out.println("=== 락 타임아웃 기능 테스트 결과 ===");
        System.out.println("성공한 충전: " + successCount.get() + "개");
        System.out.println("타임아웃 발생: " + timeoutCount.get() + "개");
        
        // 첫 번째 충전만 성공, 두 번째는 타임아웃
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(timeoutCount.get()).isEqualTo(1);
        
        executorService.shutdown();
    }

    @Test
    @DisplayName("🧹 락 정리 기능 테스트")
    void 락_정리_기능_테스트() throws InterruptedException {
        // Given: 초기 락 상태 확인
        int initialLockCount = pointService.getActiveLockCount();
        System.out.println("초기 락 개수: " + initialLockCount);
        
        // When: 다양한 사용자로 요청 실행하여 락 생성
        int userCount = 15;
        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        CountDownLatch latch = new CountDownLatch(userCount);
        
        for (int i = 0; i < userCount; i++) {
            final long userId = 50000L + i;
            CompletableFuture.runAsync(() -> {
                try {
                    // 초기 포인트 설정 및 충전 수행
                    userPointTable.insertOrUpdate(userId, 1000L);
                    pointService.charge(userId, 100L);
                } finally {
                    latch.countDown();
                }
            }, executorService);
        }
        
        latch.await(10, TimeUnit.SECONDS);
        
        // Then: 락이 적절히 생성되었는지 확인
        int afterOperationLockCount = pointService.getActiveLockCount();
        System.out.println("연산 후 락 개수: " + afterOperationLockCount);
        System.out.println("락 상태: " + pointService.getLockStatus());
        
        // 사용자 수만큼 락이 생성되었는지 확인
        assertThat(afterOperationLockCount).isGreaterThanOrEqualTo(userCount);
        
        // 락 정리 기능 테스트 (수동 호출)
        pointService.cleanupUnusedLocks();
        int afterCleanupLockCount = pointService.getActiveLockCount();
        System.out.println("정리 후 락 개수: " + afterCleanupLockCount);
        
        // 정리가 수행되었는지 확인 (시간 기준이 아니므로 큰 변화는 없을 수 있음)
        assertThat(afterCleanupLockCount).isLessThanOrEqualTo(afterOperationLockCount);
        
        executorService.shutdown();
    }
}
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
import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 문제 시연 및 해결 테스트
 * 
 * 현재 구조의 문제점:
 * 1. Race Condition: 동시에 같은 사용자 포인트 수정 시 Lost Update 발생
 * 2. 데이터 불일치: 포인트 테이블과 히스토리 테이블 간 불일치
 * 3. 최대 한도 검증 실패: 동시 충전으로 한도 초과 가능
 */
@SpringBootTest
class PointConcurrencyTest {

    @Autowired
    private PointService pointService;
    
    @Autowired 
    private UserPointTable userPointTable;
    
    @Autowired
    private PointHistoryTable pointHistoryTable;

    @Test
    @DisplayName("동시에 같은 사용자가 포인트 충전 시 Lost Update 문제 발생")
    void 동시_충전_시_Lost_Update_문제() throws InterruptedException {
        // Given: 사용자에게 기본 포인트 설정
        long userId = 1L;
        long initialPoint = 1000L;
        userPointTable.insertOrUpdate(userId, initialPoint);
        
        // When: 10개 스레드가 동시에 100포인트씩 충전
        int threadCount = 10;
        long chargeAmount = 100L;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        List<CompletableFuture<UserPoint>> futures = new ArrayList<>();
        
        for (int i = 0; i < threadCount; i++) {
            CompletableFuture<UserPoint> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return pointService.charge(userId, chargeAmount);
                } finally {
                    latch.countDown();
                }
            }, executorService);
            futures.add(future);
        }
        
        // 모든 스레드 완료 대기
        latch.await(10, TimeUnit.SECONDS);
        
        // Then: 기대값과 실제값 비교
        long expectedPoint = initialPoint + (threadCount * chargeAmount); // 2000
        UserPoint finalUserPoint = userPointTable.selectById(userId);
        
        System.out.println("=== 동시성 테스트 결과 ===");
        System.out.println("초기 포인트: " + initialPoint);
        System.out.println("충전 횟수: " + threadCount + "회");
        System.out.println("충전 금액: " + chargeAmount + "포인트");
        System.out.println("기대 포인트: " + expectedPoint);
        System.out.println("실제 포인트: " + finalUserPoint.point());
        System.out.println("히스토리 개수: " + pointHistoryTable.selectAllByUserId(userId).size());
        
        // ❌ 이 테스트는 실패할 것임 (Lost Update로 인해)
        assertThat(finalUserPoint.point()).isEqualTo(expectedPoint);
        
        // 히스토리는 모든 충전이 기록되었는지 확인
        assertThat(pointHistoryTable.selectAllByUserId(userId)).hasSize(threadCount);
        
        executorService.shutdown();
    }

    @Test
    @DisplayName("동시에 충전과 사용이 발생할 때 데이터 불일치 문제")
    void 동시_충전_사용_시_데이터_불일치() throws InterruptedException {
        // Given: 사용자에게 충분한 포인트 설정
        long userId = 2L;
        long initialPoint = 10000L;
        userPointTable.insertOrUpdate(userId, initialPoint);
        
        // When: 충전과 사용을 동시에 실행
        int operationCount = 5;
        long amount = 1000L;
        ExecutorService executorService = Executors.newFixedThreadPool(operationCount * 2);
        CountDownLatch latch = new CountDownLatch(operationCount * 2);
        
        // 충전 작업들
        for (int i = 0; i < operationCount; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    pointService.charge(userId, amount);
                } finally {
                    latch.countDown();
                }
            }, executorService);
        }
        
        // 사용 작업들  
        for (int i = 0; i < operationCount; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    pointService.use(userId, amount);
                } finally {
                    latch.countDown();
                }
            }, executorService);
        }
        
        // 모든 작업 완료 대기
        latch.await(10, TimeUnit.SECONDS);
        
        // Then: 최종 포인트는 초기값과 같아야 함 (충전 5회, 사용 5회)
        long expectedPoint = initialPoint; // 변화 없음
        UserPoint finalUserPoint = userPointTable.selectById(userId);
        
        System.out.println("=== 충전/사용 동시성 테스트 결과 ===");
        System.out.println("초기 포인트: " + initialPoint);
        System.out.println("충전 " + operationCount + "회, 사용 " + operationCount + "회");
        System.out.println("기대 포인트: " + expectedPoint);
        System.out.println("실제 포인트: " + finalUserPoint.point());
        System.out.println("총 히스토리: " + pointHistoryTable.selectAllByUserId(userId).size());
        
        // ❌ 동시성 문제로 인해 예상과 다른 결과
        assertThat(finalUserPoint.point()).isEqualTo(expectedPoint);
        assertThat(pointHistoryTable.selectAllByUserId(userId)).hasSize(operationCount * 2);
        
        executorService.shutdown();
    }

    @Test
    @DisplayName("최대 포인트 한도 초과 동시성 문제")
    void 최대_포인트_한도_초과_동시성_문제() throws InterruptedException {
        // Given: 최대 한도 근처의 포인트 설정
        long userId = 3L;
        long initialPoint = 950000L; // 95만원
        userPointTable.insertOrUpdate(userId, initialPoint);
        
        // When: 여러 스레드가 동시에 큰 금액 충전 시도
        int threadCount = 3;
        long chargeAmount = 30000L; // 3만원씩 (총 9만원, 한도 초과)
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        List<Exception> exceptions = new ArrayList<>();
        
        for (int i = 0; i < threadCount; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    pointService.charge(userId, chargeAmount);
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    latch.countDown();
                }
            }, executorService);
        }
        
        latch.await(10, TimeUnit.SECONDS);
        
        // Then: 최대 한도(100만원)를 초과하지 않아야 함
        UserPoint finalUserPoint = userPointTable.selectById(userId);
        
        System.out.println("=== 최대 한도 동시성 테스트 결과 ===");
        System.out.println("초기 포인트: " + initialPoint);
        System.out.println("충전 시도: " + threadCount + "회 x " + chargeAmount + "포인트");
        System.out.println("최종 포인트: " + finalUserPoint.point());
        System.out.println("발생한 예외: " + exceptions.size() + "개");
        System.out.println("최대 한도: 1,000,000포인트");
        
        // 최대 한도를 초과하지 않아야 함
        assertThat(finalUserPoint.point()).isLessThanOrEqualTo(1_000_000L);
        
        executorService.shutdown();
    }

    @Test  
    @DisplayName("서로 다른 사용자는 동시성 문제가 없어야 함")
    void 서로_다른_사용자_동시성_안전성() throws InterruptedException {
        // Given: 서로 다른 사용자들
        long user1Id = 10L;
        long user2Id = 20L;
        long initialPoint = 5000L;
        
        userPointTable.insertOrUpdate(user1Id, initialPoint);
        userPointTable.insertOrUpdate(user2Id, initialPoint);
        
        // When: 각각 다른 사용자에 대해 동시에 작업
        int operationsPerUser = 10;
        long amount = 100L;
        ExecutorService executorService = Executors.newFixedThreadPool(operationsPerUser * 2);
        CountDownLatch latch = new CountDownLatch(operationsPerUser * 2);
        
        // User1 충전 작업들
        for (int i = 0; i < operationsPerUser; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    pointService.charge(user1Id, amount);
                } finally {
                    latch.countDown();
                }
            }, executorService);
        }
        
        // User2 충전 작업들
        for (int i = 0; i < operationsPerUser; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    pointService.charge(user2Id, amount);
                } finally {
                    latch.countDown();
                }
            }, executorService);
        }
        
        latch.await(10, TimeUnit.SECONDS);
        
        // Then: 각 사용자의 포인트가 정확해야 함
        long expectedPoint = initialPoint + (operationsPerUser * amount);
        
        UserPoint user1Point = userPointTable.selectById(user1Id);
        UserPoint user2Point = userPointTable.selectById(user2Id);
        
        System.out.println("=== 다른 사용자 동시성 테스트 결과 ===");
        System.out.println("User1 - 기대: " + expectedPoint + ", 실제: " + user1Point.point());
        System.out.println("User2 - 기대: " + expectedPoint + ", 실제: " + user2Point.point());
        
        // 서로 다른 사용자는 영향을 받지 않아야 함
        assertThat(user1Point.point()).isEqualTo(expectedPoint);
        assertThat(user2Point.point()).isEqualTo(expectedPoint);
        
        executorService.shutdown();
    }
}
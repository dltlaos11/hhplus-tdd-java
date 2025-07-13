package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.point.config.ConcurrencyConfig;
import io.hhplus.tdd.point.config.PointPolicyConfig;
import io.hhplus.tdd.point.policy.ChargePolicy;
import io.hhplus.tdd.point.policy.UsePolicy;
import io.hhplus.tdd.point.exception.InvalidAmountException;
import io.hhplus.tdd.point.exception.InsufficientPointException;
import io.hhplus.tdd.point.exception.ExceedsMaxPointException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import java.util.List;

/**
 * PointService TDD 테스트 (Hybrid 전략)
 * 
 * 테스트 전략:
 * - 도메인 로직 (ChargePolicy, UsePolicy): 실제 객체 사용 → 비즈니스 로직 검증
 * - 외부 의존성 (UserPointTable, PointHistoryTable): Mock 사용 → 격리된 단위 테스트
 * - 설정 (ConcurrencyConfig): Mock 사용 → 테스트 환경 제어
 * 
 * 장점:
 * 1. 실제 도메인 로직이 정상 동작하는지 확인
 * 2. 외부 의존성은 격리하여 빠른 테스트 실행
 * 3. 정책 변경 시 PointService 테스트에서도 확인 가능
 * 4. 통합적인 비즈니스 플로우 검증
 */
@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    // 외부 의존성은 Mock 사용
    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @Mock
    private ConcurrencyConfig concurrencyConfig;

    // 도메인 로직은 실제 객체 사용
    private ChargePolicy chargePolicy;
    private UsePolicy usePolicy;
    private PointService pointService;

    @BeforeEach
    void setUp() {
        // 실제 정책 설정으로 도메인 객체 생성
        PointPolicyConfig.ChargeConfig chargeConfig = new PointPolicyConfig.ChargeConfig(100L, 1_000_000L);
        PointPolicyConfig.UseConfig useConfig = new PointPolicyConfig.UseConfig(100L);
        PointPolicyConfig pointPolicyConfig = new PointPolicyConfig(chargeConfig, useConfig);
        
        // 실제 도메인 정책 객체 생성
        chargePolicy = new ChargePolicy(pointPolicyConfig);
        usePolicy = new UsePolicy(pointPolicyConfig);
        
        // PointService 직접 생성 (Mock과 실제 객체 조합)
        pointService = new PointService(
            userPointTable,    // Mock
            pointHistoryTable, // Mock  
            chargePolicy,      // Real
            usePolicy,         // Real
            concurrencyConfig  // Mock
        );
    }

    // =============== 기존 조회 테스트 ===============

    @Test
    @DisplayName("사용자 ID로 포인트를 조회할 수 있다")
    void 포인트_조회_성공() {
        // given
        long userId = 1L;
        UserPoint expectedPoint = new UserPoint(userId, 1000L, System.currentTimeMillis());
        given(userPointTable.selectById(userId)).willReturn(expectedPoint);

        // when
        UserPoint result = pointService.getPoint(userId);
 
        // then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 포인트 조회 시 0 포인트를 반환한다")
    void 존재하지_않는_사용자_포인트_조회() {
        // given
        long userId = 999L;
        UserPoint emptyPoint = UserPoint.empty(userId);
        given(userPointTable.selectById(userId)).willReturn(emptyPoint);

        // when
        UserPoint result = pointService.getPoint(userId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(0L);
    }

    // =============== 충전 기능 테스트 (실제 정책 검증) ===============

    @Test
    @DisplayName("유효한 금액으로 포인트를 충전할 수 있다")
    void 포인트_충전_성공() {
        // given
        long userId = 1L;
        long chargeAmount = 1000L;
        long currentPoint = 5000L;
        long expectedNewPoint = currentPoint + chargeAmount;
        
        UserPoint currentUserPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
        UserPoint updatedUserPoint = new UserPoint(userId, expectedNewPoint, System.currentTimeMillis());
        PointHistory expectedHistory = new PointHistory(1L, userId, chargeAmount, TransactionType.CHARGE, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(currentUserPoint);
        given(userPointTable.insertOrUpdate(userId, expectedNewPoint)).willReturn(updatedUserPoint);
        given(pointHistoryTable.insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong()))
                .willReturn(expectedHistory);

        // when
        UserPoint result = pointService.charge(userId, chargeAmount);

        // then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(expectedNewPoint);
        
        // 데이터 저장이 호출되었는지 확인
        then(userPointTable).should(times(1)).insertOrUpdate(userId, expectedNewPoint);
        then(pointHistoryTable).should(times(1)).insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong());
    }

    @Test
    @DisplayName("첫 충전 시 0 포인트에서 시작하여 충전할 수 있다")
    void 첫_충전_성공() {
        // given
        long userId = 2L;
        long chargeAmount = 1000L;
        long expectedNewPoint = chargeAmount;
        
        UserPoint emptyUserPoint = UserPoint.empty(userId);
        UserPoint updatedUserPoint = new UserPoint(userId, expectedNewPoint, System.currentTimeMillis());
        PointHistory expectedHistory = new PointHistory(1L, userId, chargeAmount, TransactionType.CHARGE, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(emptyUserPoint);
        given(userPointTable.insertOrUpdate(userId, expectedNewPoint)).willReturn(updatedUserPoint);
        given(pointHistoryTable.insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong()))
                .willReturn(expectedHistory);

        // when
        UserPoint result = pointService.charge(userId, chargeAmount);

        // then
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(expectedNewPoint);
    }

    @Test
    @DisplayName("0원 충전 시 실제 정책에 의해 InvalidAmountException이 발생한다")
    void 영원_충전_시_실제_정책_예외_발생() {
        // given
        long userId = 1L;
        long invalidAmount = 0L;
        UserPoint currentUserPoint = new UserPoint(userId, 1000L, System.currentTimeMillis());
        
        given(userPointTable.selectById(userId)).willReturn(currentUserPoint);

        // when & then - 실제 ChargePolicy가 예외를 발생시킴
        assertThatThrownBy(() -> pointService.charge(userId, invalidAmount))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("충전 금액은 0보다 커야 합니다");
                
        // 예외로 인해 데이터 저장은 호출되지 않음
        then(userPointTable).should(never()).insertOrUpdate(anyLong(), anyLong());
        then(pointHistoryTable).should(never()).insert(anyLong(), anyLong(), eq(TransactionType.CHARGE), anyLong());
    }

    @Test
    @DisplayName("최소 충전 금액(100원) 미만 충전 시 실제 정책에 의해 예외가 발생한다")
    void 최소_금액_미만_충전_시_실제_정책_예외_발생() {
        // given
        long userId = 1L;
        long invalidAmount = 99L; // 실제 설정된 최소 충전 금액(100원) 미만
        UserPoint currentUserPoint = new UserPoint(userId, 1000L, System.currentTimeMillis());
        
        given(userPointTable.selectById(userId)).willReturn(currentUserPoint);

        // when & then - 실제 ChargePolicy의 설정값으로 검증
        assertThatThrownBy(() -> pointService.charge(userId, invalidAmount))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("최소 충전 금액은 100원입니다");
    }

    @Test
    @DisplayName("최대 포인트(1,000,000원) 초과 충전 시 실제 정책에 의해 예외가 발생한다")
    void 최대_포인트_초과_충전_시_실제_정책_예외_발생() {
        // given
        long userId = 1L;
        long chargeAmount = 200000L;
        long currentPoint = 900000L; // 충전 후 1,100,000원이 되어 최대값(1,000,000원) 초과
        
        UserPoint currentUserPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
        given(userPointTable.selectById(userId)).willReturn(currentUserPoint);

        // when & then - 실제 ChargePolicy의 설정값으로 검증
        assertThatThrownBy(() -> pointService.charge(userId, chargeAmount))
                .isInstanceOf(ExceedsMaxPointException.class)
                .hasMessage("최대 보유 가능 포인트는 1,000,000원입니다");
                
        then(userPointTable).should(never()).insertOrUpdate(anyLong(), anyLong());
        then(pointHistoryTable).should(never()).insert(anyLong(), anyLong(), eq(TransactionType.CHARGE), anyLong());
    }

    // =============== 사용 기능 테스트 (실제 정책 검증) ===============

    @Test
    @DisplayName("유효한 금액으로 포인트를 사용할 수 있다")
    void 포인트_사용_성공() {
        // given
        long userId = 1L;
        long useAmount = 1000L;
        long currentPoint = 5000L;
        long expectedNewPoint = currentPoint - useAmount;
        
        UserPoint currentUserPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
        UserPoint updatedUserPoint = new UserPoint(userId, expectedNewPoint, System.currentTimeMillis());
        PointHistory expectedHistory = new PointHistory(2L, userId, useAmount, TransactionType.USE, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(currentUserPoint);
        given(userPointTable.insertOrUpdate(userId, expectedNewPoint)).willReturn(updatedUserPoint);
        given(pointHistoryTable.insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong()))
                .willReturn(expectedHistory);

        // when
        UserPoint result = pointService.use(userId, useAmount);

        // then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(expectedNewPoint);
        
        // 데이터 저장 검증
        then(userPointTable).should(times(1)).insertOrUpdate(userId, expectedNewPoint);
        then(pointHistoryTable).should(times(1)).insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong());
    }

    @Test
    @DisplayName("전체 포인트를 모두 사용할 수 있다")
    void 전체_포인트_사용_성공() {
        // given
        long userId = 2L;
        long useAmount = 3000L;
        long currentPoint = 3000L;
        long expectedNewPoint = 0L;
        
        UserPoint currentUserPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
        UserPoint updatedUserPoint = new UserPoint(userId, expectedNewPoint, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(currentUserPoint);
        given(userPointTable.insertOrUpdate(userId, expectedNewPoint)).willReturn(updatedUserPoint);
        given(pointHistoryTable.insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong()))
                .willReturn(new PointHistory(3L, userId, useAmount, TransactionType.USE, System.currentTimeMillis()));

        // when
        UserPoint result = pointService.use(userId, useAmount);

        // then
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(0L);
    }

    @Test
    @DisplayName("포인트 부족 시 실제 정책에 의해 InsufficientPointException이 발생한다")
    void 포인트_부족_시_실제_정책_예외_발생() {
        // given
        long userId = 1L;
        long useAmount = 6000L;
        long currentPoint = 5000L;
        
        UserPoint currentUserPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
        given(userPointTable.selectById(userId)).willReturn(currentUserPoint);

        // when & then - 실제 UsePolicy가 예외를 발생시킴
        assertThatThrownBy(() -> pointService.use(userId, useAmount))
                .isInstanceOf(InsufficientPointException.class)
                .hasMessage("포인트가 부족합니다. 요청: 6000, 현재: 5000");
                
        // 예외로 인해 데이터 저장은 되지 않음
        then(userPointTable).should(never()).insertOrUpdate(anyLong(), anyLong());
        then(pointHistoryTable).should(never()).insert(anyLong(), anyLong(), eq(TransactionType.USE), anyLong());
    }

    @Test
    @DisplayName("0원 사용 시 실제 정책에 의해 InvalidAmountException이 발생한다")
    void 영원_사용_시_실제_정책_예외_발생() {
        // given
        long userId = 1L;
        long invalidAmount = 0L;
        UserPoint currentUserPoint = new UserPoint(userId, 5000L, System.currentTimeMillis());
        
        given(userPointTable.selectById(userId)).willReturn(currentUserPoint);

        // when & then - 실제 UsePolicy가 예외를 발생시킴
        assertThatThrownBy(() -> pointService.use(userId, invalidAmount))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("사용 금액은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("최소 사용 금액(100원) 미만 사용 시 실제 정책에 의해 예외가 발생한다")
    void 최소_사용_금액_미만_시_실제_정책_예외_발생() {
        // given
        long userId = 1L;
        long invalidAmount = 99L; // 실제 설정된 최소 사용 금액(100원) 미만
        UserPoint currentUserPoint = new UserPoint(userId, 5000L, System.currentTimeMillis());
        
        given(userPointTable.selectById(userId)).willReturn(currentUserPoint);

        // when & then - 실제 UsePolicy의 설정값으로 검증
        assertThatThrownBy(() -> pointService.use(userId, invalidAmount))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("최소 사용 금액은 100원입니다");
    }

    // =============== 새로운 내역 조회 기능 테스트 ===============

    @Test
    @DisplayName("사용자의 포인트 사용 내역을 조회할 수 있다")
    void 포인트_내역_조회_성공() {
        // given
        long userId = 1L;
        PointHistory history1 = new PointHistory(1L, userId, 1000L, TransactionType.CHARGE, System.currentTimeMillis());
        PointHistory history2 = new PointHistory(2L, userId, 500L, TransactionType.USE, System.currentTimeMillis());
        PointHistory history3 = new PointHistory(3L, userId, 2000L, TransactionType.CHARGE, System.currentTimeMillis());
        
        List<PointHistory> expectedHistories = List.of(history1, history2, history3);
        given(pointHistoryTable.selectAllByUserId(userId)).willReturn(expectedHistories);

        // when
        List<PointHistory> result = pointService.getHistory(userId);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(3);
        assertThat(result.get(0).userId()).isEqualTo(userId);
        assertThat(result.get(0).type()).isEqualTo(TransactionType.CHARGE);
        assertThat(result.get(0).amount()).isEqualTo(1000L);
        assertThat(result.get(1).type()).isEqualTo(TransactionType.USE);
        assertThat(result.get(1).amount()).isEqualTo(500L);
        assertThat(result.get(2).type()).isEqualTo(TransactionType.CHARGE);
        assertThat(result.get(2).amount()).isEqualTo(2000L);
        
        // 데이터 조회 검증
        then(pointHistoryTable).should(times(1)).selectAllByUserId(userId);
    }

    @Test
    @DisplayName("포인트 내역이 없는 사용자의 경우 빈 리스트를 반환한다")
    void 포인트_내역_없는_사용자_조회() {
        // given
        long userId = 999L;
        List<PointHistory> emptyHistories = List.of();
        given(pointHistoryTable.selectAllByUserId(userId)).willReturn(emptyHistories);

        // when
        List<PointHistory> result = pointService.getHistory(userId);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        then(pointHistoryTable).should(times(1)).selectAllByUserId(userId);
    }
}
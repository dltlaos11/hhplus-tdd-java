package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.point.policy.ChargePolicy;
import io.hhplus.tdd.point.policy.UsePolicy;
import io.hhplus.tdd.point.exception.InvalidAmountException;
import io.hhplus.tdd.point.exception.InsufficientPointException;
import io.hhplus.tdd.point.exception.ExceedsMaxPointException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;

/**
 * PointService TDD 테스트
 * 
 * Mock 전략:
 * - Database 계층 (UserPointTable, PointHistoryTable): @Mock 사용 (런던파)
 * - 도메인 로직 (ChargePolicy, UsePolicy): @Spy 사용 (실제 동작 + 호출 검증)
 * 
 * @Spy 사용 이유:
 * - ChargePolicy, UsePolicy의 실제 비즈니스 로직 검증 필요
 * - 동시에 메서드 호출 여부도 검증하고 싶음
 * - 실제 정책 동작을 통한 통합적 테스트 가능
 */
@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @Spy
    private ChargePolicy chargePolicy;

    @Spy
    private UsePolicy usePolicy;

    @InjectMocks
    private PointService pointService;

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

    // =============== 새로운 충전 기능 테스트 ===============

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
        
        // 정책 검증이 호출되었는지 확인
        then(chargePolicy).should(times(1)).validate(chargeAmount, currentPoint);
        
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
        long currentPoint = 0L;
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
        then(chargePolicy).should(times(1)).validate(chargeAmount, currentPoint);
    }

    @Test
    @DisplayName("유효하지 않은 충전 금액(0원)으로 충전 시 InvalidAmountException이 발생한다")
    void 영원_충전_시_예외_발생() {
        // given
        long userId = 1L;
        long invalidAmount = 0L;
        UserPoint currentUserPoint = new UserPoint(userId, 1000L, System.currentTimeMillis());
        
        given(userPointTable.selectById(userId)).willReturn(currentUserPoint);

        // when & then
        assertThatThrownBy(() -> pointService.charge(userId, invalidAmount))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("충전 금액은 0보다 커야 합니다");
                
        // 정책 검증은 호출되었지만 예외로 인해 데이터 저장은 호출되지 않음
        then(chargePolicy).should(times(1)).validate(invalidAmount, 1000L);
        then(userPointTable).should(never()).insertOrUpdate(anyLong(), anyLong());
        then(pointHistoryTable).should(never()).insert(anyLong(), anyLong(), eq(TransactionType.CHARGE), anyLong());
    }

    @Test
    @DisplayName("최소 충전 금액(100원) 미만으로 충전 시 InvalidAmountException이 발생한다")
    void 최소_금액_미만_충전_시_예외_발생() {
        // given
        long userId = 1L;
        long invalidAmount = 99L;
        UserPoint currentUserPoint = new UserPoint(userId, 1000L, System.currentTimeMillis());
        
        given(userPointTable.selectById(userId)).willReturn(currentUserPoint);

        // when & then
        assertThatThrownBy(() -> pointService.charge(userId, invalidAmount))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("최소 충전 금액은 100원입니다");
                
        then(chargePolicy).should(times(1)).validate(invalidAmount, 1000L);
    }

    @Test
    @DisplayName("최대 포인트 초과 충전 시 ExceedsMaxPointException이 발생한다")
    void 최대_포인트_초과_충전_시_예외_발생() {
        // given
        long userId = 1L;
        long chargeAmount = 200000L;
        long currentPoint = 900000L; // 충전 후 110만원이 되어 100만원 초과
        
        UserPoint currentUserPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
        given(userPointTable.selectById(userId)).willReturn(currentUserPoint);

        // when & then
        assertThatThrownBy(() -> pointService.charge(userId, chargeAmount))
                .isInstanceOf(ExceedsMaxPointException.class)
                .hasMessage("최대 보유 가능 포인트는 1,000,000원입니다");
                
        // 정책 검증은 호출되었지만 예외로 인해 데이터 저장은 호출되지 않음
        then(chargePolicy).should(times(1)).validate(chargeAmount, currentPoint);
        then(userPointTable).should(never()).insertOrUpdate(anyLong(), anyLong());
        then(pointHistoryTable).should(never()).insert(anyLong(), anyLong(), eq(TransactionType.CHARGE), anyLong());
    }

    // =============== 새로운 사용 기능 테스트 ===============

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
        
        // UsePolicy 호출 검증
        then(usePolicy).should(times(1)).validate(useAmount, currentPoint);
        
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
        then(usePolicy).should(times(1)).validate(useAmount, currentPoint);
    }

    @Test
    @DisplayName("포인트 부족 시 InsufficientPointException이 발생한다")
    void 포인트_부족_시_사용_예외_발생() {
        // given
        long userId = 1L;
        long useAmount = 6000L;
        long currentPoint = 5000L;
        
        UserPoint currentUserPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
        given(userPointTable.selectById(userId)).willReturn(currentUserPoint);

        // when & then
        assertThatThrownBy(() -> pointService.use(userId, useAmount))
                .isInstanceOf(InsufficientPointException.class)
                .hasMessage("포인트가 부족합니다. 요청: 6000, 현재: 5000");
                
        // UsePolicy 호출은 되었지만 예외로 인해 데이터 저장은 되지 않음
        then(usePolicy).should(times(1)).validate(useAmount, currentPoint);
        then(userPointTable).should(never()).insertOrUpdate(anyLong(), anyLong());
        then(pointHistoryTable).should(never()).insert(anyLong(), anyLong(), eq(TransactionType.USE), anyLong());
    }

    @Test
    @DisplayName("유효하지 않은 사용 금액으로 사용 시 InvalidAmountException이 발생한다")
    void 유효하지_않은_사용_금액_시_예외_발생() {
        // given
        long userId = 1L;
        long invalidAmount = 0L;
        UserPoint currentUserPoint = new UserPoint(userId, 5000L, System.currentTimeMillis());
        
        given(userPointTable.selectById(userId)).willReturn(currentUserPoint);

        // when & then
        assertThatThrownBy(() -> pointService.use(userId, invalidAmount))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("사용 금액은 0보다 커야 합니다");
                
        // UsePolicy 호출 검증
        then(usePolicy).should(times(1)).validate(invalidAmount, 5000L);
    }

    @Test
    @DisplayName("최소 사용 금액 미만으로 사용 시 InvalidAmountException이 발생한다")
    void 최소_사용_금액_미만_시_예외_발생() {
        // given
        long userId = 1L;
        long invalidAmount = 99L;
        UserPoint currentUserPoint = new UserPoint(userId, 5000L, System.currentTimeMillis());
        
        given(userPointTable.selectById(userId)).willReturn(currentUserPoint);

        // when & then
        assertThatThrownBy(() -> pointService.use(userId, invalidAmount))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("최소 사용 금액은 100원입니다");
                
        then(usePolicy).should(times(1)).validate(invalidAmount, 5000L);
    }
}
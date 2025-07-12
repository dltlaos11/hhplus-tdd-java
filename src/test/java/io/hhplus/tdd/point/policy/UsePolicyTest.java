package io.hhplus.tdd.point.policy;

import io.hhplus.tdd.point.exception.InvalidAmountException;
import io.hhplus.tdd.point.exception.InsufficientPointException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 사용 정책 클래스 TDD 테스트
 * 
 * TDD 적용 이유:
 * 1. 사용 정책의 비즈니스 규칙을 테스트로 명세화
 * 2. 잔고 부족 등 핵심 예외 상황을 빠뜨리지 않고 검증
 * 3. ChargePolicy와 일관된 구조로 정책 클래스 설계
 * 4. 포인트 사용의 모든 edge case 사전 정의
 */
class UsePolicyTest {

    private UsePolicy usePolicy;

    @BeforeEach
    void setUp() {
        usePolicy = new UsePolicy();
    }

    // =============== 성공 케이스 테스트 ===============

    @Test
    @DisplayName("충분한 포인트가 있을 때 사용이 가능하다")
    void 충분한_포인트_사용_성공() {
        // given
        long useAmount = 1000L;
        long currentPoint = 5000L;

        // when & then
        assertThatNoException()
                .isThrownBy(() -> usePolicy.validate(useAmount, currentPoint));
    }

    @Test
    @DisplayName("최소 사용 금액(100원)으로 사용 가능하다")
    void 최소_사용_금액_성공() {
        // given
        long minAmount = 100L;
        long currentPoint = 1000L;

        // when & then
        assertThatNoException()
                .isThrownBy(() -> usePolicy.validate(minAmount, currentPoint));
    }

    @Test
    @DisplayName("현재 포인트와 정확히 같은 금액을 사용할 수 있다")
    void 전체_포인트_사용_성공() {
        // given
        long useAmount = 5000L;
        long currentPoint = 5000L;

        // when & then
        assertThatNoException()
                .isThrownBy(() -> usePolicy.validate(useAmount, currentPoint));
    }

    // =============== 실패 케이스 테스트 ===============

    @Test
    @DisplayName("0원 사용 시 InvalidAmountException이 발생한다")
    void 영원_사용_시_예외_발생() {
        // given
        long invalidAmount = 0L;
        long currentPoint = 1000L;

        // when & then
        assertThatThrownBy(() -> usePolicy.validate(invalidAmount, currentPoint))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("사용 금액은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("음수 금액 사용 시 InvalidAmountException이 발생한다")
    void 음수_사용_시_예외_발생() {
        // given
        long negativeAmount = -1000L;
        long currentPoint = 5000L;

        // when & then
        assertThatThrownBy(() -> usePolicy.validate(negativeAmount, currentPoint))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("사용 금액은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("최소 사용 금액(100원) 미만 사용 시 InvalidAmountException이 발생한다")
    void 최소_금액_미만_사용_시_예외_발생() {
        // given
        long tooSmallAmount = 99L;
        long currentPoint = 1000L;

        // when & then
        assertThatThrownBy(() -> usePolicy.validate(tooSmallAmount, currentPoint))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("최소 사용 금액은 100원입니다");
    }

    @Test
    @DisplayName("포인트 부족 시 InsufficientPointException이 발생한다")
    void 포인트_부족_시_예외_발생() {
        // given
        long useAmount = 6000L;
        long currentPoint = 5000L;

        // when & then
        assertThatThrownBy(() -> usePolicy.validate(useAmount, currentPoint))
                .isInstanceOf(InsufficientPointException.class)
                .hasMessage("포인트가 부족합니다. 요청: 6000, 현재: 5000");
    }

    @Test
    @DisplayName("포인트가 0일 때 사용 시도하면 InsufficientPointException이 발생한다")
    void 포인트_없을때_사용_시도_시_예외_발생() {
        // given
        long useAmount = 100L;
        long currentPoint = 0L;

        // when & then
        assertThatThrownBy(() -> usePolicy.validate(useAmount, currentPoint))
                .isInstanceOf(InsufficientPointException.class)
                .hasMessage("포인트가 부족합니다. 요청: 100, 현재: 0");
    }

    // =============== 경계값 테스트 ===============

    @Test
    @DisplayName("최소 사용 금액 경계값 테스트 - 99원은 실패")
    void 최소_사용_금액_경계값_실패() {
        // given
        long boundaryAmount = 99L;
        long currentPoint = 1000L;

        // when & then
        assertThatThrownBy(() -> usePolicy.validate(boundaryAmount, currentPoint))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("최소 사용 금액 경계값 테스트 - 100원은 성공")
    void 최소_사용_금액_경계값_성공() {
        // given
        long boundaryAmount = 100L;
        long currentPoint = 1000L;

        // when & then
        assertThatNoException()
                .isThrownBy(() -> usePolicy.validate(boundaryAmount, currentPoint));
    }

    @Test
    @DisplayName("포인트 부족 경계값 테스트 - 초과 금액은 실패")
    void 포인트_부족_경계값_실패() {
        // given
        long useAmount = 1001L;
        long currentPoint = 1000L;

        // when & then
        assertThatThrownBy(() -> usePolicy.validate(useAmount, currentPoint))
                .isInstanceOf(InsufficientPointException.class);
    }

    @Test
    @DisplayName("포인트 부족 경계값 테스트 - 정확한 금액은 성공")
    void 포인트_부족_경계값_성공() {
        // given
        long useAmount = 1000L;
        long currentPoint = 1000L;

        // when & then
        assertThatNoException()
                .isThrownBy(() -> usePolicy.validate(useAmount, currentPoint));
    }
}
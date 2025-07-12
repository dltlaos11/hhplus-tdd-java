package io.hhplus.tdd.point.policy;

import io.hhplus.tdd.point.config.PointPolicyConfig;
import io.hhplus.tdd.point.exception.InvalidAmountException;
import io.hhplus.tdd.point.exception.InsufficientPointException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 설정 기반 사용 정책 테스트
 * 
 * 개선사항:
 * 1. 테스트에서도 실제 설정값 사용
 * 2. ChargePolicy와 일관된 구조로 설정값 활용
 */
class UsePolicyTest {

    private UsePolicy usePolicy;
    private PointPolicyConfig.UseConfig useConfig;

    @BeforeEach
    void setUp() {
        // 테스트용 설정 (실제 설정값과 동일하게 설정)
        PointPolicyConfig.ChargeConfig chargeConfig = new PointPolicyConfig.ChargeConfig(100L, 1_000_000L);
        useConfig = new PointPolicyConfig.UseConfig(100L);
        PointPolicyConfig config = new PointPolicyConfig(chargeConfig, useConfig);
        usePolicy = new UsePolicy(config);
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
    @DisplayName("설정된 최소 사용 금액으로 사용 가능하다")
    void 설정_기반_최소_사용_금액_성공() {
        // given
        long minAmount = usePolicy.getMinUseAmount(); // 설정값 사용
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
    @DisplayName("설정된 최소 사용 금액 미만 사용 시 InvalidAmountException이 발생한다")
    void 설정_기반_최소_금액_미만_사용_시_예외_발생() {
        // given
        long minAmount = usePolicy.getMinUseAmount();
        long tooSmallAmount = minAmount - 1; // 설정값보다 1원 적게
        long currentPoint = 1000L;

        // when & then
        assertThatThrownBy(() -> usePolicy.validate(tooSmallAmount, currentPoint))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("최소 사용 금액은 " + minAmount + "원입니다");
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
    @DisplayName("설정 기반 최소 사용 금액 경계값 테스트 - 미만은 실패")
    void 설정_기반_최소_사용_금액_경계값_실패() {
        // given
        long minAmount = usePolicy.getMinUseAmount();
        long boundaryAmount = minAmount - 1;
        long currentPoint = 1000L;

        // when & then
        assertThatThrownBy(() -> usePolicy.validate(boundaryAmount, currentPoint))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("설정 기반 최소 사용 금액 경계값 테스트 - 경계값은 성공")
    void 설정_기반_최소_사용_금액_경계값_성공() {
        // given
        long minAmount = usePolicy.getMinUseAmount();
        long currentPoint = 1000L;

        // when & then
        assertThatNoException()
                .isThrownBy(() -> usePolicy.validate(minAmount, currentPoint));
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
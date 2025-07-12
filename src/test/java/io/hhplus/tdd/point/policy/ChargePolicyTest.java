package io.hhplus.tdd.point.policy;

import io.hhplus.tdd.point.config.PointPolicyConfig;
import io.hhplus.tdd.point.exception.InvalidAmountException;
import io.hhplus.tdd.point.exception.ExceedsMaxPointException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 설정 기반 충전 정책 테스트
 * 
 * 개선사항:
 * 1. 테스트에서도 실제 설정값 사용
 * 2. 설정 변경에 따른 테스트 유연성 확보
 * 3. 실제 운영 환경과 동일한 조건으로 테스트
 */
class ChargePolicyTest {

    private ChargePolicy chargePolicy;
    private PointPolicyConfig.ChargeConfig chargeConfig;

    @BeforeEach
    void setUp() {
        // 테스트용 설정 (실제 설정값과 동일하게 설정)
        chargeConfig = new PointPolicyConfig.ChargeConfig(100L, 1_000_000L);
        PointPolicyConfig.UseConfig useConfig = new PointPolicyConfig.UseConfig(100L);
        PointPolicyConfig config = new PointPolicyConfig(chargeConfig, useConfig);
        chargePolicy = new ChargePolicy(config);
    }

    // =============== 성공 케이스 테스트 ===============

    @Test
    @DisplayName("유효한 금액으로 충전하면 예외가 발생하지 않는다")
    void 정상_충전_금액_검증_성공() {
        // given
        long validAmount = 1000L;
        long currentPoint = 5000L;

        // when & then
        assertThatNoException()
                .isThrownBy(() -> chargePolicy.validate(validAmount, currentPoint));
    }

    @Test
    @DisplayName("설정된 최소 충전 금액으로 충전 가능하다")
    void 설정_기반_최소_충전_금액_성공() {
        // given
        long minAmount = chargePolicy.getMinChargeAmount(); // 설정값 사용
        long currentPoint = 0L;

        // when & then
        assertThatNoException()
                .isThrownBy(() -> chargePolicy.validate(minAmount, currentPoint));
    }

    @Test
    @DisplayName("설정된 최대 보유 가능 포인트 한도 내에서 충전 가능하다")
    void 설정_기반_최대_한도_내_충전_성공() {
        // given
        long maxPoint = chargePolicy.getMaxTotalPoint();
        long chargeAmount = 100000L;
        long currentPoint = maxPoint - chargeAmount; // 충전 후 정확히 최대값

        // when & then
        assertThatNoException()
                .isThrownBy(() -> chargePolicy.validate(chargeAmount, currentPoint));
    }

    // =============== 실패 케이스 테스트 ===============

    @Test
    @DisplayName("0원 충전 시 InvalidAmountException이 발생한다")
    void 영원_충전_시_예외_발생() {
        // given
        long invalidAmount = 0L;
        long currentPoint = 1000L;

        // when & then
        assertThatThrownBy(() -> chargePolicy.validate(invalidAmount, currentPoint))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("충전 금액은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("설정된 최소 충전 금액 미만 충전 시 InvalidAmountException이 발생한다")
    void 설정_기반_최소_금액_미만_충전_시_예외_발생() {
        // given
        long minAmount = chargePolicy.getMinChargeAmount();
        long tooSmallAmount = minAmount - 1; // 설정값보다 1원 적게
        long currentPoint = 1000L;

        // when & then
        assertThatThrownBy(() -> chargePolicy.validate(tooSmallAmount, currentPoint))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("최소 충전 금액은 " + minAmount + "원입니다");
    }

    @Test
    @DisplayName("설정된 최대 보유 가능 포인트 초과 충전 시 ExceedsMaxPointException이 발생한다")
    void 설정_기반_최대_한도_초과_충전_시_예외_발생() {
        // given
        long maxPoint = chargePolicy.getMaxTotalPoint();
        long excessiveAmount = 200000L;
        long currentPoint = maxPoint - 100000L; // 충전 후 최대값 초과하도록 설정

        // when & then
        assertThatThrownBy(() -> chargePolicy.validate(excessiveAmount, currentPoint))
                .isInstanceOf(ExceedsMaxPointException.class)
                .hasMessage("최대 보유 가능 포인트는 " + 
                    String.format("%,d", maxPoint) + "원입니다");
    }

    // =============== 설정 기반 경계값 테스트 ===============

    @Test
    @DisplayName("설정 기반 경계값 테스트 - 최소 금액 경계")
    void 설정_기반_최소_충전_금액_경계값_테스트() {
        // given
        long minAmount = chargePolicy.getMinChargeAmount();
        long currentPoint = 0L;

        // when & then - 경계값 미만은 실패
        assertThatThrownBy(() -> chargePolicy.validate(minAmount - 1, currentPoint))
                .isInstanceOf(InvalidAmountException.class);

        // when & then - 경계값은 성공
        assertThatNoException()
                .isThrownBy(() -> chargePolicy.validate(minAmount, currentPoint));
    }

    @Test
    @DisplayName("설정 기반 경계값 테스트 - 최대 포인트 경계")
    void 설정_기반_최대_포인트_경계값_테스트() {
        // given
        long maxPoint = chargePolicy.getMaxTotalPoint();
        long minAmount = chargePolicy.getMinChargeAmount();
        
        // 경계값 테스트를 위한 설정
        long chargeAmount = minAmount;
        long exactPoint = maxPoint - chargeAmount; // 충전 후 정확히 최대값
        long overPoint = maxPoint - chargeAmount + 1; // 충전 후 1원 초과

        // when & then - 정확히 최대값은 성공
        assertThatNoException()
                .isThrownBy(() -> chargePolicy.validate(chargeAmount, exactPoint));

        // when & then - 1원이라도 초과하면 실패
        assertThatThrownBy(() -> chargePolicy.validate(chargeAmount, overPoint))
                .isInstanceOf(ExceedsMaxPointException.class);
    }

    @Test
    @DisplayName("설정값 조회 메서드가 올바르게 동작한다")
    void 설정값_조회_메서드_테스트() {
        // when
        long minAmount = chargePolicy.getMinChargeAmount();
        long maxPoint = chargePolicy.getMaxTotalPoint();

        // then
        assertThat(minAmount).isEqualTo(chargeConfig.minAmount());
        assertThat(maxPoint).isEqualTo(chargeConfig.maxTotalPoint());
    }
}
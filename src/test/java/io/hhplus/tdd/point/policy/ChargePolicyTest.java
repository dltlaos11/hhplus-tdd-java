package io.hhplus.tdd.point.policy;

import io.hhplus.tdd.point.exception.InvalidAmountException;
import io.hhplus.tdd.point.exception.ExceedsMaxPointException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 충전 정책 클래스 TDD 테스트
 * 
 * TDD 적용 이유:
 * 1. 비즈니스 규칙을 테스트로 명세화 - 요구사항이 코드로 문서화됨
 * 2. 정책 변경 시 영향도 파악 용이 - 테스트 실패로 변경점 즉시 확인
 * 3. 예외 상황 처리 검증 - 모든 edge case를 빠뜨리지 않고 검증
 * 4. 리팩토링 안전성 - 정책 로직 개선 시 기존 동작 보장
 */
class ChargePolicyTest {

    private ChargePolicy chargePolicy;

    @BeforeEach
    void setUp() {
        chargePolicy = new ChargePolicy();
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
    @DisplayName("최소 충전 금액(100원)으로 충전 가능하다")
    void 최소_충전_금액_성공() {
        // given
        long minAmount = 100L;
        long currentPoint = 0L;

        // when & then
        assertThatNoException()
                .isThrownBy(() -> chargePolicy.validate(minAmount, currentPoint));
    }

    @Test
    @DisplayName("최대 보유 가능 포인트(100만원) 한도 내에서 충전 가능하다")
    void 최대_한도_내_충전_성공() {
        // given
        long chargeAmount = 100000L;
        long currentPoint = 900000L; // 충전 후 100만원이 됨

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
    @DisplayName("음수 금액 충전 시 InvalidAmountException이 발생한다")
    void 음수_충전_시_예외_발생() {
        // given
        long negativeAmount = -1000L;
        long currentPoint = 5000L;

        // when & then
        assertThatThrownBy(() -> chargePolicy.validate(negativeAmount, currentPoint))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("충전 금액은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("최소 충전 금액(100원) 미만 충전 시 InvalidAmountException이 발생한다")
    void 최소_금액_미만_충전_시_예외_발생() {
        // given
        long tooSmallAmount = 99L;
        long currentPoint = 1000L;

        // when & then
        assertThatThrownBy(() -> chargePolicy.validate(tooSmallAmount, currentPoint))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("최소 충전 금액은 100원입니다");
    }

    @Test
    @DisplayName("최대 보유 가능 포인트(100만원) 초과 충전 시 ExceedsMaxPointException이 발생한다")
    void 최대_한도_초과_충전_시_예외_발생() {
        // given
        long excessiveAmount = 200000L;
        long currentPoint = 900000L; // 충전 후 110만원이 됨

        // when & then
        assertThatThrownBy(() -> chargePolicy.validate(excessiveAmount, currentPoint))
                .isInstanceOf(ExceedsMaxPointException.class)
                .hasMessage("최대 보유 가능 포인트는 1,000,000원입니다");
    }

    @Test
    @DisplayName("이미 최대 포인트 보유 시 추가 충전이 불가능하다")
    void 최대_포인트_보유시_충전_불가() {
        // given
        long anyAmount = 100L; // 최소 금액 조건 만족
        long maxPoint = 1000000L; // 이미 최대 포인트

        // when & then
        assertThatThrownBy(() -> chargePolicy.validate(anyAmount, maxPoint))
                .isInstanceOf(ExceedsMaxPointException.class)
                .hasMessage("최대 보유 가능 포인트는 1,000,000원입니다");
    }

    // =============== 경계값 테스트 ===============

    @Test
    @DisplayName("최소 충전 금액 경계값 테스트 - 99원은 실패")
    void 최소_충전_금액_경계값_실패() {
        // given
        long boundaryAmount = 99L;
        long currentPoint = 0L;

        // when & then
        assertThatThrownBy(() -> chargePolicy.validate(boundaryAmount, currentPoint))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("최소 충전 금액 경계값 테스트 - 100원은 성공")
    void 최소_충전_금액_경계값_성공() {
        // given
        long boundaryAmount = 100L;
        long currentPoint = 0L;

        // when & then
        assertThatNoException()
                .isThrownBy(() -> chargePolicy.validate(boundaryAmount, currentPoint));
    }

    @Test
    @DisplayName("최대 포인트 경계값 테스트 - 한도 초과는 실패")
    void 최대_포인트_경계값_실패() {
        // given - 최소 금액 조건도 만족시키면서 최대 포인트 초과하도록 수정
        long chargeAmount = 100L; // 최소 충전 금액 이상
        long currentPoint = 999999L; // 충전 후 1,000,099원이 되어 100만원 초과

        // when & then
        assertThatThrownBy(() -> chargePolicy.validate(chargeAmount, currentPoint))
                .isInstanceOf(ExceedsMaxPointException.class);
    }

    @Test
    @DisplayName("최대 포인트 경계값 테스트 - 한도 정확히 맞추는 것은 성공")
    void 최대_포인트_경계값_성공() {
        // given
        long chargeAmount = 1000000L;
        long currentPoint = 0L; // 충전 후 정확히 100만원

        // when & then
        assertThatNoException()
                .isThrownBy(() -> chargePolicy.validate(chargeAmount, currentPoint));
    }

    // =============== 추가 경계값 테스트 ===============

    @Test
    @DisplayName("정확히 최대 한도에 도달하는 충전은 성공한다")
    void 정확히_최대_한도_도달_성공() {
        // given
        long chargeAmount = 500L; // 최소 금액 이상
        long currentPoint = 999500L; // 충전 후 정확히 1,000,000원

        // when & then
        assertThatNoException()
                .isThrownBy(() -> chargePolicy.validate(chargeAmount, currentPoint));
    }

    @Test
    @DisplayName("최대 한도를 1원이라도 초과하면 실패한다")
    void 최대_한도_1원_초과_실패() {
        // given
        long chargeAmount = 501L; // 최소 금액 이상
        long currentPoint = 999500L; // 충전 후 1,000,001원 (1원 초과)

        // when & then
        assertThatThrownBy(() -> chargePolicy.validate(chargeAmount, currentPoint))
                .isInstanceOf(ExceedsMaxPointException.class)
                .hasMessage("최대 보유 가능 포인트는 1,000,000원입니다");
    }
}
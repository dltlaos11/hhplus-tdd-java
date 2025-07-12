package io.hhplus.tdd.point.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 포인트 관련 예외 클래스들의 TDD 테스트
 * 
 * TDD 적용 이유:
 * 1. 예외 메시지와 타입이 명확히 정의되어야 함
 * 2. 예외 발생 조건을 테스트로 문서화
 * 3. 예외 계층 구조의 올바른 상속 관계 검증
 */
class PointExceptionTest {

    @Test
    @DisplayName("InvalidAmountException은 PointException을 상속받는다")
    void InvalidAmountException_상속_관계_테스트() {
        // given
        String message = "유효하지 않은 금액입니다";
        
        // when
        InvalidAmountException exception = new InvalidAmountException(message);
        
        // then
        assertThat(exception).isInstanceOf(PointException.class);
        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("InsufficientPointException은 현재 포인트와 요청 금액을 포함한 메시지를 생성한다")
    void InsufficientPointException_메시지_생성_테스트() {
        // given
        long currentPoint = 500L;
        long requestAmount = 1000L;
        
        // when
        InsufficientPointException exception = new InsufficientPointException(requestAmount, currentPoint);
        
        // then
        assertThat(exception).isInstanceOf(PointException.class);
        assertThat(exception.getMessage())
            .contains("포인트가 부족합니다")
            .contains("요청: 1000")
            .contains("현재: 500");
    }

    @Test
    @DisplayName("ExceedsMaxPointException은 PointException을 상속받는다")
    void ExceedsMaxPointException_상속_관계_테스트() {
        // given
        String message = "최대 포인트를 초과합니다";
        
        // when
        ExceedsMaxPointException exception = new ExceedsMaxPointException(message);
        
        // then
        assertThat(exception).isInstanceOf(PointException.class);
        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("PointException은 RuntimeException을 상속받는다")
    void PointException_상속_관계_테스트() {
        // given
        String message = "포인트 관련 오류";
        
        // when
        PointException exception = new TestPointException(message);
        
        // then
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo(message);
    }

    /**
     * 테스트용 구체 클래스 (추상 클래스 테스트를 위함)
     */
    private static class TestPointException extends PointException {
        public TestPointException(String message) {
            super(message);
        }
    }
}
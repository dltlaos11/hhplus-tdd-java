package io.hhplus.tdd;

import io.hhplus.tdd.point.exception.PointException;
import io.hhplus.tdd.point.exception.InvalidAmountException;
import io.hhplus.tdd.point.exception.InsufficientPointException;
import io.hhplus.tdd.point.exception.ExceedsMaxPointException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 전역 예외 처리기
 * 
 * 설계 이유:
 * 1. 일관된 에러 응답 형식 제공
 * 2. 도메인별 예외를 적절한 HTTP 상태 코드로 매핑
 * 3. 예외 처리 로직을 Controller에서 분리하여 관심사 분리
 * 4. 클라이언트에게 사용자 친화적인 에러 메시지 제공
 */
@RestControllerAdvice
class ApiControllerAdvice extends ResponseEntityExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ApiControllerAdvice.class);

    /**
     * 포인트 관련 예외 통합 처리
     * 
     * PointException을 상속받는 모든 예외를 400 Bad Request로 처리
     * 이유: 클라이언트의 잘못된 요청으로 인한 비즈니스 규칙 위반
     * 
     * @param e 포인트 관련 예외
     * @return 400 상태 코드와 에러 메시지
     */
    @ExceptionHandler(PointException.class)
    public ResponseEntity<ErrorResponse> handlePointException(PointException e) {
        log.warn("포인트 관련 예외 발생: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("POINT_ERROR", e.getMessage()));
    }

    /**
     * 유효하지 않은 금액 예외 처리
     * 
     * 더 구체적인 에러 코드 제공
     * 
     * @param e 유효하지 않은 금액 예외
     * @return 400 상태 코드와 구체적인 에러 정보
     */
    @ExceptionHandler(InvalidAmountException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAmountException(InvalidAmountException e) {
        log.warn("유효하지 않은 금액: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_AMOUNT", e.getMessage()));
    }

    /**
     * 포인트 부족 예외 처리
     * 
     * @param e 포인트 부족 예외
     * @return 400 상태 코드와 포인트 부족 에러 정보
     */
    @ExceptionHandler(InsufficientPointException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientPointException(InsufficientPointException e) {
        log.warn("포인트 부족: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INSUFFICIENT_POINT", e.getMessage()));
    }

    /**
     * 최대 포인트 초과 예외 처리
     * 
     * @param e 최대 포인트 초과 예외
     * @return 400 상태 코드와 최대 포인트 초과 에러 정보
     */
    @ExceptionHandler(ExceedsMaxPointException.class)
    public ResponseEntity<ErrorResponse> handleExceedsMaxPointException(ExceedsMaxPointException e) {
        log.warn("최대 포인트 초과: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("EXCEEDS_MAX_POINT", e.getMessage()));
    }

    /**
     * 기타 모든 예외 처리 (기존 로직 유지)
     * 
     * @param e 처리되지 않은 예외
     * @return 500 상태 코드와 일반적인 에러 메시지
     */
    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("예상치 못한 에러 발생", e);
        return ResponseEntity.status(500)
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "에러가 발생했습니다."));
    }
}
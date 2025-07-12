package io.hhplus.tdd.point.exception;

/**
 * 포인트 관련 예외의 최상위 추상 클래스
 * 
 * 설계 이유:
 * 1. 포인트 도메인의 모든 예외를 그룹화하여 일관된 예외 처리
 * 2. @RestControllerAdvice에서 포인트 관련 예외를 한 번에 처리 가능
 * 3. instanceof 체크로 포인트 관련 예외임을 쉽게 판별
 * 4. 추후 공통 로깅이나 모니터링 로직 추가 시 확장점 제공
 */
public abstract class PointException extends RuntimeException {
    
    /**
     * 포인트 예외 생성자
     * @param message 예외 메시지 (사용자에게 보여질 수 있는 친화적 메시지)
     */
    protected PointException(String message) {
        super(message);
    }
    
    /**
     * 원인 예외가 있는 포인트 예외 생성자
     * @param message 예외 메시지
     * @param cause 원인 예외 (주로 외부 시스템 연동 시 발생하는 예외)
     */
    protected PointException(String message, Throwable cause) {
        super(message, cause);
    }
}

package io.hhplus.tdd.point.exception;

/**
 * 최대 포인트 초과 예외
 * 
 * 발생 상황:
 * - 충전 후 총 포인트가 최대 한도를 초과하는 경우
 */
public class ExceedsMaxPointException extends PointException {
    
    public ExceedsMaxPointException(String message) {
        super(message);
    }
}
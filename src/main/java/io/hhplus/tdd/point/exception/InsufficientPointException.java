package io.hhplus.tdd.point.exception;

/**
 * 포인트 부족 예외
 * 
 * 발생 상황:
 * - 현재 보유 포인트보다 많은 포인트 사용 시도
 */
public class InsufficientPointException extends PointException {
    
    public InsufficientPointException(long requestAmount, long currentPoint) {
        super(String.format("포인트가 부족합니다. 요청: %d, 현재: %d", requestAmount, currentPoint));
    }
}
package io.hhplus.tdd.point.exception;

/**
 * 유효하지 않은 금액에 대한 예외
 * 
 * 발생 상황:
 * - 음수 또는 0 금액으로 충전/사용 시도
 * - 최소 금액 미만으로 충전/사용 시도
 */
public class InvalidAmountException extends PointException {
    
    public InvalidAmountException(String message) {
        super(message);
    }
}
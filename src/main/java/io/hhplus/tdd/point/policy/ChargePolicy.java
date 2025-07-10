package io.hhplus.tdd.point.policy;

import io.hhplus.tdd.point.exception.InvalidAmountException;
import io.hhplus.tdd.point.exception.ExceedsMaxPointException;
import org.springframework.stereotype.Component;

/**
 * 포인트 충전 정책 클래스
 * 
 * 설계 이유:
 * 1. 단일 책임 원칙 - 충전 관련 비즈니스 규칙만 담당
 * 2. 정책 변경 시 이 클래스만 수정하면 됨 (OCP 원칙)
 * 3. 테스트 가능성 - 정책 로직을 독립적으로 테스트 가능
 * 4. 재사용성 - 다른 컨텍스트에서도 동일한 정책 적용 가능
 * 
 * Spring 관리 이유:
 * - @Component로 Spring 컨테이너가 관리하여 의존성 주입 가능
 * - Singleton으로 관리되어 메모리 효율적
 * - 향후 설정값 주입이나 다른 Bean과의 연동 시 확장 용이
 */
@Component
public class ChargePolicy {
    
    /**
     * 비즈니스 상수들을 클래스 내부에 정의
     * 이유: 정책과 관련된 값들을 한 곳에서 관리하여 응집도 증가
     */
    private static final long MIN_CHARGE_AMOUNT = 100L;        // 최소 충전 금액
    private static final long MAX_TOTAL_POINT = 1_000_000L;    // 최대 보유 가능 포인트

    /**
     * 충전 금액과 현재 포인트를 검증합니다.
     * 
     * @param amount 충전하려는 금액
     * @param currentPoint 현재 보유 포인트
     * @throws InvalidAmountException 유효하지 않은 충전 금액인 경우
     * @throws ExceedsMaxPointException 최대 보유 포인트를 초과하는 경우
     */
    public void validate(long amount, long currentPoint) {
        validateAmount(amount);
        validateMaxPointLimit(amount, currentPoint);
    }

    /**
     * 충전 금액 자체의 유효성을 검증합니다.
     * 
     * 검증 규칙:
     * 1. 양수여야 함 (0보다 커야 함)
     * 2. 최소 충전 금액 이상이어야 함
     * 
     * @param amount 충전 금액
     * @throws InvalidAmountException 유효하지 않은 금액인 경우
     */
    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new InvalidAmountException("충전 금액은 0보다 커야 합니다");
        }
        
        if (amount < MIN_CHARGE_AMOUNT) {
            throw new InvalidAmountException("최소 충전 금액은 " + MIN_CHARGE_AMOUNT + "원입니다");
        }
    }

    /**
     * 충전 후 총 포인트가 최대 한도를 초과하지 않는지 검증합니다.
     * 
     * 검증 규칙:
     * - 충전 후 총 포인트 <= 최대 포인트
     * 
     * @param amount 충전 금액
     * @param currentPoint 현재 포인트
     * @throws ExceedsMaxPointException 최대 포인트를 초과하는 경우
     */
    private void validateMaxPointLimit(long amount, long currentPoint) {
        // 더 직관적인 로직: 충전 후 총액이 한도를 초과하는지 직접 확인
        long totalAfterCharge = currentPoint + amount;
        
        if (totalAfterCharge > MAX_TOTAL_POINT) {
            throw new ExceedsMaxPointException("최대 보유 가능 포인트는 " + 
                String.format("%,d", MAX_TOTAL_POINT) + "원입니다");
        }
    }
}
package io.hhplus.tdd.point.policy;

import io.hhplus.tdd.point.config.PointPolicyConfig;
import io.hhplus.tdd.point.exception.InvalidAmountException;
import io.hhplus.tdd.point.exception.ExceedsMaxPointException;
import org.springframework.stereotype.Component;

/**
 * 포인트 충전 정책 클래스 (설정 외부화 적용)
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
 * 
 * 개선사항:
 * 1. 하드코딩된 상수를 Spring Property로 분리
 * 2. 정책 변경 시 코드 수정 없이 설정만으로 대응 가능
 * 3. 환경별 다른 정책 적용 가능 (dev, prod 등)
 */
@Component
public class ChargePolicy {
    
    /**
     * 비즈니스 상수들을 클래스 내부에 정의
     * 이유: 정책과 관련된 값들을 한 곳에서 관리하여 응집도 증가
     */
    private final PointPolicyConfig.ChargeConfig chargeConfig;

    public ChargePolicy(PointPolicyConfig pointPolicyConfig) {
        this.chargeConfig = pointPolicyConfig.charge();
    }

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
     * 2. 최소 충전 금액 이상이어야 함 (설정값 참조)
     * 
     * @param amount 충전 금액
     * @throws InvalidAmountException 유효하지 않은 금액인 경우
     */
    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new InvalidAmountException("충전 금액은 0보다 커야 합니다");
        }
        
        if (amount < chargeConfig.minAmount()) {
            throw new InvalidAmountException("최소 충전 금액은 " + chargeConfig.minAmount() + "원입니다");
        }
    }

    /**
     * 충전 후 총 포인트가 최대 한도를 초과하지 않는지 검증합니다.
     * 
     * 검증 규칙:
     * - 충전 후 총 포인트 <= 최대 포인트 (설정값 참조)
     * 
     * @param amount 충전 금액
     * @param currentPoint 현재 포인트
     * @throws ExceedsMaxPointException 최대 포인트를 초과하는 경우
     */
    private void validateMaxPointLimit(long amount, long currentPoint) {
        long totalAfterCharge = currentPoint + amount;
        
        if (totalAfterCharge > chargeConfig.maxTotalPoint()) {
            throw new ExceedsMaxPointException("최대 보유 가능 포인트는 " + 
                String.format("%,d", chargeConfig.maxTotalPoint()) + "원입니다");
        }
    }
    
    // 테스트를 위한 설정값 조회 메서드
    public long getMinChargeAmount() {
        return chargeConfig.minAmount();
    }
    
    public long getMaxTotalPoint() {
        return chargeConfig.maxTotalPoint();
    }
}
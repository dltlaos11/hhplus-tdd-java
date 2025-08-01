package io.hhplus.tdd.point.policy;

import io.hhplus.tdd.point.config.PointPolicyConfig;
import io.hhplus.tdd.point.exception.InvalidAmountException;
import io.hhplus.tdd.point.exception.InsufficientPointException;
import org.springframework.stereotype.Component;

/**
 * 포인트 사용 정책 클래스 (설정 외부화 적용)
 * 
 * 설계 이유:
 * 1. 단일 책임 원칙 - 사용 관련 비즈니스 규칙만 담당
 * 2. ChargePolicy와 일관된 구조 - 정책 클래스들의 패턴 통일
 * 3. 테스트 가능성 - 사용 정책 로직을 독립적으로 테스트 가능
 * 4. 확장성 - 새로운 사용 규칙 추가 시 이 클래스만 수정
 * 
 * Spring 관리 이유:
 * - @Component로 Spring 컨테이너가 관리하여 의존성 주입 가능
 * - ChargePolicy와 동일한 생명주기 관리
 * - 향후 다른 정책들과의 조합이나 설정값 주입 시 확장 용이
 * 
 *  개선사항:
 * 1. 하드코딩된 상수를 Spring Property로 분리
 * 2. ChargePolicy와 일관된 구조로 설정값 활용
 */
@Component
public class UsePolicy {
    
    /**
     * 비즈니스 상수들을 클래스 내부에 정의
     * 이유: 사용 정책과 관련된 값들을 한 곳에서 관리하여 응집도 증가
     */
    private final PointPolicyConfig.UseConfig useConfig;

    public UsePolicy(PointPolicyConfig pointPolicyConfig) {
        this.useConfig = pointPolicyConfig.use();
    }

    /**
     * 사용 금액과 현재 포인트를 검증합니다.
     * 
     * @param amount 사용하려는 금액
     * @param currentPoint 현재 보유 포인트
     * @throws InvalidAmountException 유효하지 않은 사용 금액인 경우
     * @throws InsufficientPointException 포인트가 부족한 경우
     */
    public void validate(long amount, long currentPoint) {
        validateAmount(amount);
        validateSufficientPoint(amount, currentPoint);
    }

    /**
     * 사용 금액 자체의 유효성을 검증합니다.
     * 
     * 검증 규칙:
     * 1. 양수여야 함 (0보다 커야 함)
     * 2. 최소 사용 금액 이상이어야 함 (설정값 참조)
     * 
     * @param amount 사용 금액
     * @throws InvalidAmountException 유효하지 않은 금액인 경우
     */
    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new InvalidAmountException("사용 금액은 0보다 커야 합니다");
        }
        
        if (amount < useConfig.minAmount()) {
            throw new InvalidAmountException("최소 사용 금액은 " + useConfig.minAmount() + "원입니다");
        }
    }

    /**
     * 현재 포인트가 사용하려는 금액보다 충분한지 검증합니다.
     * 
     * 검증 규칙:
     * - 현재 포인트 >= 사용 금액
     * 
     * @param amount 사용 금액
     * @param currentPoint 현재 포인트
     * @throws InsufficientPointException 포인트가 부족한 경우
     */
    private void validateSufficientPoint(long amount, long currentPoint) {
        if (currentPoint < amount) {
            throw new InsufficientPointException(amount, currentPoint);
        }
    }
    
    // 테스트를 위한 설정값 조회 메서드
    public long getMinUseAmount() {
        return useConfig.minAmount();
    }
}
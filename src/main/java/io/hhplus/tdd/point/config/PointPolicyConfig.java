package io.hhplus.tdd.point.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 포인트 정책 설정 클래스
 * 
 * application.yml의 point.policy 설정을 바인딩
 * 정책 변경 시 코드 수정 없이 설정만으로 대응 가능
 */
@ConfigurationProperties(prefix = "point.policy")
public record PointPolicyConfig(
    ChargeConfig charge,
    UseConfig use
) {
    
    public record ChargeConfig(
        long minAmount,
        long maxTotalPoint
    ) {}
    
    public record UseConfig(
        long minAmount
    ) {}
}
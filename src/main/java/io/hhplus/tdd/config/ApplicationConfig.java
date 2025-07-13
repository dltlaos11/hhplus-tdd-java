package io.hhplus.tdd.config;

import io.hhplus.tdd.point.config.ConcurrencyConfig;
import io.hhplus.tdd.point.config.PointPolicyConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 애플리케이션 설정 클래스
 * 
 * 기능:
 * 1. ConfigurationProperties 활성화
 * 2. 스케줄링 기능 활성화 (락 정리용)
 * 3. 설정 클래스들을 Spring Context에 등록
 */
@Configuration
@EnableConfigurationProperties({
    PointPolicyConfig.class,
    ConcurrencyConfig.class
})
@EnableScheduling // 락 정리 스케줄러를 위해 필요
public class ApplicationConfig {
}
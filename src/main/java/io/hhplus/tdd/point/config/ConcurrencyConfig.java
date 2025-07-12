package io.hhplus.tdd.point.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 동시성 제어 설정 클래스
 * 
 * 락 관리 정책을 외부 설정으로 제어
 */
@ConfigurationProperties(prefix = "point.concurrency")
public record ConcurrencyConfig(
    int maxLocks,
    long cleanupInterval
) {}
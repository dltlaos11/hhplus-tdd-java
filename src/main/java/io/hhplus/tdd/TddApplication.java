package io.hhplus.tdd;

import io.hhplus.tdd.point.config.ConcurrencyConfig;
import io.hhplus.tdd.point.config.PointPolicyConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({
    PointPolicyConfig.class,
    ConcurrencyConfig.class
})
@EnableScheduling
public class TddApplication {

    public static void main(String[] args) {
        SpringApplication.run(TddApplication.class, args);
    }
}
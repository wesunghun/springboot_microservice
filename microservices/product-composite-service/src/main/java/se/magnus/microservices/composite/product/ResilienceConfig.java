package se.magnus.microservices.composite.product;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

@Configuration 
public class ResilienceConfig {
    private static final Logger LOG = LoggerFactory.getLogger(ResilienceConfig.class);
    
    public ResilienceConfig(
        @Value("${app.resilience.enabled:true}") String resilienceEnabled,
        CircuitBreakerRegistry circuitBreakerRegistry,
        TimeLimiterRegistry timeLimiterRegistry,
        RetryRegistry retryRegistry){

        if (!"false".equalsIgnoreCase(resilienceEnabled)){
            LOG.debug("Resilience4j is enabled ({})", resilienceEnabled);
            return;
        }

        LOG.warn("Resilience4j is disabled (app.resilience.enabled = {})", resilienceEnabled);
        circuitBreakerRegistry.remove("product");
        retryRegistry.remove("product");
        timeLimiterRegistry.replace("product", TimeLimiter.of(Duration.ofHours(24)));
    }
}

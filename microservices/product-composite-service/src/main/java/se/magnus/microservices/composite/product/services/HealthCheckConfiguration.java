package se.magnus.microservices.composite.product.services;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.CompositeReactiveHealthContributor;
import org.springframework.boot.health.contributor.ReactiveHealthContributor;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HealthCheckConfiguration {

  private final ProductCompositeIntegration integration;

  public HealthCheckConfiguration(ProductCompositeIntegration integration) {
    this.integration = integration;
  }

  @Bean
  ReactiveHealthContributor coreServices() {

    final Map<String, ReactiveHealthIndicator> registry = new LinkedHashMap<>();

    registry.put("product", () -> integration.getProductHealth());
    registry.put("recommendation", () -> integration.getRecommendationHealth());
    registry.put("review", () -> integration.getReviewHealth());

    return CompositeReactiveHealthContributor.fromMap(registry);
  }
}
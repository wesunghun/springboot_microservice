package se.magnus.springcloud.gateway;

import static java.util.logging.Level.FINE;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.CompositeReactiveHealthContributor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthContributor;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@Configuration
public class HealthCheckConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(HealthCheckConfiguration.class);

  private final WebClient webClient;

  public HealthCheckConfiguration(WebClient.Builder webClientBuilder) {
    this.webClient = webClientBuilder.build();      // @LoadBalanced 빌더 (③)
  }

  @Bean
  ReactiveHealthContributor healthcheckMicroservices() {

    final Map<String, ReactiveHealthIndicator> registry = new LinkedHashMap<>();

    registry.put("product",           () -> getHealth("http://product"));
    registry.put("recommendation",    () -> getHealth("http://recommendation"));
    registry.put("review",            () -> getHealth("http://review"));
    registry.put("product-composite", () -> getHealth("http://product-composite"));

    return CompositeReactiveHealthContributor.fromMap(registry);
  }

  private Mono<Health> getHealth(String baseUrl) {
    String url = baseUrl + "/actuator/health";
    LOG.debug("Setting up a call to the Health API on URL: {}", url);
    return webClient.get().uri(url).retrieve().bodyToMono(String.class)
      .map(s -> Health.up().build())
      .onErrorResume(ex -> Mono.just(Health.down(ex).build()))
      .log(LOG.getName(), FINE);
  }
}
package se.magnus.springcloud.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Hooks;

@SpringBootApplication
public class GatewayApplication {

  @Bean
  public WebClient.Builder webClientBuilder() {
    return WebClient.builder();
  }

  public static void main(String[] args) {
    Hooks.enableAutomaticContextPropagation();
    SpringApplication.run(GatewayApplication.class, args);
  }
}
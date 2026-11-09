package se.magnus.api.core.recommendation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RecommendationService {

  Mono<Recommendation> createRecommendation(Recommendation body);   // 이벤트로 호출 (@PostMapping 제거)

  /**
   * Sample usage: "curl $HOST:$PORT/recommendation?productId=1".
   */
  @GetMapping(value = "/recommendation", produces = "application/json")
  Flux<Recommendation> getRecommendations(@RequestParam(value = "productId", required = true) int productId);

  Mono<Void> deleteRecommendations(int productId);                  // 이벤트로 호출 (@DeleteMapping 제거)
}
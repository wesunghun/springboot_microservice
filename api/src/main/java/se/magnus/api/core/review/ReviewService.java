package se.magnus.api.core.review;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReviewService {

  Mono<Review> createReview(Review body);   // 이벤트로 호출 (@PostMapping 제거)

  /**
   * Sample usage: "curl $HOST:$PORT/review?productId=1".
   */
  @GetMapping(value = "/review", produces = "application/json")
  Flux<Review> getReviews(@RequestParam(value = "productId", required = true) int productId);

  Mono<Void> deleteReviews(int productId);  // 이벤트로 호출 (@DeleteMapping 제거)
}
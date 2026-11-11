package se.magnus.microservices.composite.product.services;

import static java.util.logging.Level.FINE;
import static se.magnus.api.event.Event.Type.CREATE;
import static se.magnus.api.event.Event.Type.DELETE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import se.magnus.api.core.product.Product;
import se.magnus.api.core.product.ProductService;
import se.magnus.api.core.recommendation.Recommendation;
import se.magnus.api.core.recommendation.RecommendationService;
import se.magnus.api.core.review.Review;
import se.magnus.api.core.review.ReviewService;
import se.magnus.api.event.Event;
import se.magnus.api.exceptions.InvalidInputException;
import se.magnus.api.exceptions.NotFoundException;
import se.magnus.util.http.HttpErrorInfo;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProductCompositeIntegration implements ProductService, RecommendationService, ReviewService {

  private static final Logger LOG = LoggerFactory.getLogger(ProductCompositeIntegration.class);

  private final WebClient webClient;
  private final ObjectMapper mapper;
  private final StreamBridge streamBridge;
  private final Scheduler publishEventScheduler;

    // 9장: 호스트·포트 대신 유레카에 등록된 서비스 이름 사용
  private static final String PRODUCT_SERVICE_BASE_URL = "http://product";
  private static final String RECOMMENDATION_SERVICE_BASE_URL = "http://recommendation";
  private static final String REVIEW_SERVICE_BASE_URL = "http://review";

  private final String productServiceUrl = PRODUCT_SERVICE_BASE_URL + "/product";
  private final String recommendationServiceUrl = RECOMMENDATION_SERVICE_BASE_URL + "/recommendation";
  private final String reviewServiceUrl = REVIEW_SERVICE_BASE_URL + "/review";

  public ProductCompositeIntegration(
      @Qualifier("publishEventScheduler") Scheduler publishEventScheduler,
      WebClient.Builder webClientBuilder,
      ObjectMapper mapper,
      StreamBridge streamBridge) {

    this.publishEventScheduler = publishEventScheduler;
    this.webClient = webClientBuilder.build();   // 로드 밸런서가 붙은 빌더 (② 참고)
    this.mapper = mapper;
    this.streamBridge = streamBridge;
  }

  // ───────────── Product ─────────────

  @Override
  public Mono<Product> createProduct(Product body) {
    return Mono.fromCallable(() -> {
      sendMessage("products-out-0", new Event<>(CREATE, body.getProductId(), body));
      return body;
    }).subscribeOn(publishEventScheduler);
  }

  @Override
  public Mono<Product> getProduct(int productId) {
    String url = productServiceUrl + "/" + productId;
    LOG.debug("Will call the getProduct API on URL: {}", url);

    return webClient.get().uri(url).retrieve()
        .bodyToMono(Product.class)
        .log(LOG.getName(), FINE)
        .onErrorMap(WebClientResponseException.class, ex -> handleException(ex));
  }

  @Override
  public Mono<Void> deleteProduct(int productId) {
    return Mono.fromRunnable(() -> sendMessage("products-out-0", new Event<>(DELETE, productId, null)))
        .subscribeOn(publishEventScheduler).then();
  }

  // ───────────── Recommendation ─────────────

  @Override
  public Mono<Recommendation> createRecommendation(Recommendation body) {
    return Mono.fromCallable(() -> {
      sendMessage("recommendations-out-0", new Event<>(CREATE, body.getProductId(), body));
      return body;
    }).subscribeOn(publishEventScheduler);
  }

  @Override
  public Flux<Recommendation> getRecommendations(int productId) {
    String url = recommendationServiceUrl + "?productId=" + productId;

    return webClient.get().uri(url).retrieve()
        .bodyToFlux(Recommendation.class)
        .log(LOG.getName(), FINE)
        .onErrorResume(error -> Flux.empty());
  }

  @Override
  public Mono<Void> deleteRecommendations(int productId) {
    return Mono.fromRunnable(() -> sendMessage("recommendations-out-0", new Event<>(DELETE, productId, null)))
        .subscribeOn(publishEventScheduler).then();
  }

  // ───────────── Review ─────────────

  @Override
  public Mono<Review> createReview(Review body) {
    return Mono.fromCallable(() -> {
      sendMessage("reviews-out-0", new Event<>(CREATE, body.getProductId(), body));
      return body;
    }).subscribeOn(publishEventScheduler);
  }

  @Override
  public Flux<Review> getReviews(int productId) {
    String url = reviewServiceUrl + "?productId=" + productId;
    LOG.debug("Will call the getReviews API on URL: {}", url);

    return webClient.get().uri(url).retrieve()
        .bodyToFlux(Review.class)
        .log(LOG.getName(), FINE)
        .onErrorResume(error -> Flux.empty());
  }

  @Override
  public Mono<Void> deleteReviews(int productId) {
    return Mono.fromRunnable(() -> sendMessage("reviews-out-0", new Event<>(DELETE, productId, null)))
        .subscribeOn(publishEventScheduler).then();
  }

  // ───────────── 메시지 전송 ─────────────

  private void sendMessage(String bindingName, Event<?, ?> event) {
    LOG.debug("Sending a {} message to {}", event.getEventType(), bindingName);
    Message<?> message = MessageBuilder.withPayload(event)
        .setHeader("partitionKey", event.getKey())
        .build();
    streamBridge.send(bindingName, message);
  }

  // ───────────── 오류 처리 (WebClient 조회용) ─────────────

  private Throwable handleException(WebClientResponseException ex) {
    switch (HttpStatus.resolve(ex.getStatusCode().value())) {
      case NOT_FOUND:
        return new NotFoundException(getErrorMessage(ex));
      case UNPROCESSABLE_CONTENT: // Boot 4 (교재: UNPROCESSABLE_ENTITY)
        return new InvalidInputException(getErrorMessage(ex));
      default:
        LOG.warn("Got an unexpected HTTP error: {}, will rethrow it", ex.getStatusCode());
        LOG.warn("Error body: {}", ex.getResponseBodyAsString());
        return ex;
    }
  }

  private String getErrorMessage(WebClientResponseException ex) {
    try {
      return mapper.readValue(ex.getResponseBodyAsString(), HttpErrorInfo.class).getMessage();
    } catch (JacksonException jex) { // 교재: IOException
      return ex.getMessage();
    }
  }

    // ───────────── 상태 확인 ─────────────

    public Mono<Health> getProductHealth() {
    return getHealth(PRODUCT_SERVICE_BASE_URL);
  }

  public Mono<Health> getRecommendationHealth() {
    return getHealth(RECOMMENDATION_SERVICE_BASE_URL);
  }

  public Mono<Health> getReviewHealth() {
    return getHealth(REVIEW_SERVICE_BASE_URL);
  }

  private Mono<Health> getHealth(String baseUrl) {
    String url = baseUrl + "/actuator/health";
    LOG.debug("Will call the Health API on URL: {}", url);
    return webClient.get().uri(url).retrieve().bodyToMono(String.class)
        .map(s -> Health.up().build())
        .onErrorResume(ex -> Mono.just(Health.down(ex).build()))
        .log(LOG.getName(), FINE);
  }
}
package se.magnus.microservices.composite.product;

import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static reactor.core.publisher.Mono.just;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import se.magnus.api.composite.product.ProductAggregate;
import se.magnus.api.composite.product.RecommendationSummary;
import se.magnus.api.composite.product.ReviewSummary;
import se.magnus.api.core.product.Product;
import se.magnus.api.core.recommendation.Recommendation;
import se.magnus.api.core.review.Review;
import se.magnus.api.exceptions.InvalidInputException;
import se.magnus.api.exceptions.NotFoundException;
import se.magnus.microservices.composite.product.services.ProductCompositeIntegration;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
class ProductCompositeServiceApplicationTests {

  private static final int PRODUCT_ID_OK = 1;
  private static final int PRODUCT_ID_NOT_FOUND = 2;
  private static final int PRODUCT_ID_INVALID = 3;

  @Autowired private WebTestClient client;

  @MockitoBean private ProductCompositeIntegration compositeIntegration;

  @BeforeEach
  void setUp() {

    // 조회: Mono / Flux 반환
    when(compositeIntegration.getProduct(PRODUCT_ID_OK))
      .thenReturn(Mono.just(new Product(PRODUCT_ID_OK, "name", 1, "mock-address")));
    when(compositeIntegration.getRecommendations(PRODUCT_ID_OK))
      .thenReturn(Flux.fromIterable(singletonList(
        new Recommendation(PRODUCT_ID_OK, 1, "author", 1, "content", "mock address"))));
    when(compositeIntegration.getReviews(PRODUCT_ID_OK))
      .thenReturn(Flux.fromIterable(singletonList(
        new Review(PRODUCT_ID_OK, 1, "author", "subject", "content", "mock address"))));

    when(compositeIntegration.getProduct(PRODUCT_ID_NOT_FOUND))
      .thenThrow(new NotFoundException("NOT FOUND: " + PRODUCT_ID_NOT_FOUND));

    when(compositeIntegration.getProduct(PRODUCT_ID_INVALID))
      .thenThrow(new InvalidInputException("INVALID: " + PRODUCT_ID_INVALID));

    // 생성·삭제: Mock 기본값(null) 대신 빈 Mono 반환
    // (교재 이벤트 절에서는 이 테스트들이 MessagingTests로 옮겨짐)
    when(compositeIntegration.createProduct(any())).thenReturn(Mono.empty());
    when(compositeIntegration.createRecommendation(any())).thenReturn(Mono.empty());
    when(compositeIntegration.createReview(any())).thenReturn(Mono.empty());
    when(compositeIntegration.deleteProduct(anyInt())).thenReturn(Mono.empty());
    when(compositeIntegration.deleteRecommendations(anyInt())).thenReturn(Mono.empty());
    when(compositeIntegration.deleteReviews(anyInt())).thenReturn(Mono.empty());
  }

  @Test
  void contextLoads() {}

  @Test
  void createCompositeProduct1() {
    ProductAggregate compositeProduct = new ProductAggregate(1, "name", 1, null, null, null);
    postAndVerifyProduct(compositeProduct, ACCEPTED);
  }

  @Test
  void createCompositeProduct2() {
    ProductAggregate compositeProduct = new ProductAggregate(1, "name", 1,
      singletonList(new RecommendationSummary(1, "a", 1, "c")),
      singletonList(new ReviewSummary(1, "a", "s", "c")), null);
    postAndVerifyProduct(compositeProduct, ACCEPTED);
  }

  @Test
  void deleteCompositeProduct() {
    ProductAggregate compositeProduct = new ProductAggregate(1, "name", 1,
      singletonList(new RecommendationSummary(1, "a", 1, "c")),
      singletonList(new ReviewSummary(1, "a", "s", "c")), null);

    postAndVerifyProduct(compositeProduct, ACCEPTED);

    deleteAndVerifyProduct(compositeProduct.getProductId(), ACCEPTED);
    deleteAndVerifyProduct(compositeProduct.getProductId(), ACCEPTED);
  }

  @Test
  void getProductById() {
    getAndVerifyProduct(PRODUCT_ID_OK, OK)
      .jsonPath("$.productId").isEqualTo(PRODUCT_ID_OK)
      .jsonPath("$.recommendations.length()").isEqualTo(1)
      .jsonPath("$.reviews.length()").isEqualTo(1);
  }

  @Test
  void getProductNotFound() {
    getAndVerifyProduct(PRODUCT_ID_NOT_FOUND, NOT_FOUND)
      .jsonPath("$.path").isEqualTo("/product-composite/" + PRODUCT_ID_NOT_FOUND)
      .jsonPath("$.message").isEqualTo("NOT FOUND: " + PRODUCT_ID_NOT_FOUND);
  }

  @Test
  void getProductInvalidInput() {
    getAndVerifyProduct(PRODUCT_ID_INVALID, UNPROCESSABLE_CONTENT)
      .jsonPath("$.path").isEqualTo("/product-composite/" + PRODUCT_ID_INVALID)
      .jsonPath("$.message").isEqualTo("INVALID: " + PRODUCT_ID_INVALID);
  }

  private WebTestClient.BodyContentSpec getAndVerifyProduct(int productId, HttpStatus expectedStatus) {
    return client.get()
      .uri("/product-composite/" + productId)
      .accept(APPLICATION_JSON)
      .exchange()
      .expectStatus().isEqualTo(expectedStatus)
      .expectHeader().contentType(APPLICATION_JSON)
      .expectBody();
  }

  private void postAndVerifyProduct(ProductAggregate compositeProduct, HttpStatus expectedStatus) {
    client.post()
      .uri("/product-composite")
      .body(just(compositeProduct), ProductAggregate.class)
      .exchange()
      .expectStatus().isEqualTo(expectedStatus);
  }

  private void deleteAndVerifyProduct(int productId, HttpStatus expectedStatus) {
    client.delete()
      .uri("/product-composite/" + productId)
      .exchange()
      .expectStatus().isEqualTo(expectedStatus);
  }
}
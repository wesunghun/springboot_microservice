package se.magnus.springcloud.authorizationserver.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

@Configuration
public class AuthorizationServerConfig {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
    http
      .oauth2AuthorizationServer(authorizationServer -> {
        http.securityMatcher(authorizationServer.getEndpointsMatcher());
        authorizationServer.oidc(Customizer.withDefaults());          // OpenID Connect 1.0
      })
      .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
      .exceptionHandling(exceptions -> exceptions
        .defaultAuthenticationEntryPointFor(                          // 브라우저면 로그인 화면으로
          new LoginUrlAuthenticationEntryPoint("/login"),
          new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
    return http.build();
  }

  @Bean
  public RegisteredClientRepository registeredClientRepository() {

    RegisteredClient writerClient = RegisteredClient.withId(UUID.randomUUID().toString())
      .clientId("writer")
      .clientSecret("{noop}secret-writer")                          // {noop} = 실습용 평문
      .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
      .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
      .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
      .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
      .redirectUri("https://my.redirect.uri")
      .redirectUri("https://localhost:8443/openapi/swagger-ui/oauth2-redirect.html")
      .scope(OidcScopes.OPENID)
      .scope("product:read")
      .scope("product:write")
      .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).requireProofKey(false) .build())
      .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofHours(1)).build())
      .build();

    RegisteredClient readerClient = RegisteredClient.withId(UUID.randomUUID().toString())
      .clientId("reader")
      .clientSecret("{noop}secret-reader")
      .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
      .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
      .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
      .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
      .redirectUri("https://my.redirect.uri")
      .redirectUri("https://localhost:8443/openapi/swagger-ui/oauth2-redirect.html")
      .scope(OidcScopes.OPENID)
      .scope("product:read")                                       // 읽기만
      .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).requireProofKey(false) .build())
      .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofHours(1)).build())
      .build();

    return new InMemoryRegisteredClientRepository(writerClient, readerClient);
  }

  @Bean
  public JWKSource<SecurityContext> jwkSource() {
    KeyPair keyPair = generateRsaKey();
    RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
      .privateKey((RSAPrivateKey) keyPair.getPrivate())
      .keyID(UUID.randomUUID().toString())
      .build();
    return new ImmutableJWKSet<>(new JWKSet(rsaKey));
  }

  private static KeyPair generateRsaKey() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Bean
  public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
    return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
  }

  @Bean
  public AuthorizationServerSettings authorizationServerSettings() {
    return AuthorizationServerSettings.builder().issuer("http://auth-server").build();
  }
}
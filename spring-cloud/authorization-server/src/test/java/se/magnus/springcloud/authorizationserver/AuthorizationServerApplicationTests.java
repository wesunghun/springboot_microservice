package se.magnus.springcloud.authorizationserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.cloud.config.enabled=false"})
class AuthorizationServerApplicationTests {

	@Test
	void contextLoads() {
	}

}

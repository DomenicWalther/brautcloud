
package com.domenicwalther.brautcloud;

import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UserControllerTest {

	@LocalServerPort
	private Integer port;

	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:latest");

	@BeforeAll
	static void beforeAll() {
		postgres.start();
	}

	@AfterAll
	static void afterAll() {
		postgres.stop();
	}

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	UserRepository userRepository;

	@BeforeEach
	void setUp() {
		RestAssured.baseURI = "http://localhost:" + port;
		userRepository.deleteAll();
	}

	@Test
	void shouldGetAllUsers() {
		List<User> users = List.of(
				User.builder()
					.lastName("LastName")
					.firstNameCoupleOne("FirstNameOne")
					.firstNameCoupleTwo("FirstNameTwo")
					.email("info@test.com")
					.emailVerified(false)
					.password("password")
					.build(),
				User.builder()
					.lastName("LastName3")
					.firstNameCoupleOne("FirstNameOne2")
					.firstNameCoupleTwo("FirstNameTwo2")
					.email("domenic@test.com")
					.emailVerified(false)
					.password("password2")
					.build());
		userRepository.saveAll(users);

		given().contentType(ContentType.JSON).when().get("/users").then().statusCode(200).body(".", hasSize(2));
	}

}

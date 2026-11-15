package io.cradesk.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ApiApplication {

	// Arranca la aplicación Spring Boot.
	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

}

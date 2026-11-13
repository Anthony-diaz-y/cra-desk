package io.cradesk.api;

import org.springframework.boot.SpringApplication;

import io.cradesk.api.support.TestcontainersConfiguration;

public class TestApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(ApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

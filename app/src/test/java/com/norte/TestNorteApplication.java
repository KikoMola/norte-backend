package com.norte;

import org.springframework.boot.SpringApplication;

public class TestNorteApplication {

	public static void main(String[] args) {
		SpringApplication.from(NorteApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

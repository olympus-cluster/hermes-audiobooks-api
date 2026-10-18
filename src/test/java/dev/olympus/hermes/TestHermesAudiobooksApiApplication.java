package dev.olympus.hermes;

import org.springframework.boot.SpringApplication;

public class TestHermesAudiobooksApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(HermesAudiobooksApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

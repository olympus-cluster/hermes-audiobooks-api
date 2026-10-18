package dev.olympus.hermes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HermesAudiobooksApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(HermesAudiobooksApiApplication.class, args);
	}

}

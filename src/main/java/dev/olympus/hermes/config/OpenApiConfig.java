package dev.olympus.hermes.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	@Bean
	OpenAPI hermesOpenApi() {
		return new OpenAPI().info(new Info()
				.title("Hermes Audiobooks API")
				.version("v1")
				.description("Manage and stream audiobooks. Errors use RFC 9457 problem details."));
	}
}

package dev.olympus.hermes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** Probes, OpenAPI and Swagger UI as the cluster and developers see them. */
class PlatformEndpointsTests extends IntegrationTest {

	@Test
	void probesAreUp() {
		ResponseEntity<JsonNode> liveness = http.get().uri("/actuator/health/liveness").retrieve()
				.toEntity(JsonNode.class);
		assertThat(liveness.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(liveness.getBody().get("status").asString()).isEqualTo("UP");

		ResponseEntity<JsonNode> readiness = http.get().uri("/actuator/health/readiness").retrieve()
				.toEntity(JsonNode.class);
		assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(readiness.getBody().get("status").asString()).isEqualTo("UP");
	}

	@Test
	void readinessGroupContainsDbAndS3() {
		JsonNode readiness = http.get().uri("/actuator/health/readiness").retrieve().body(JsonNode.class);
		JsonNode components = readiness.get("components");
		assertThat(components.has("db")).isTrue();
		assertThat(components.has("s3")).isTrue();
		assertThat(components.has("readinessState")).isTrue();
		assertThat(components.get("s3").get("status").asString()).isEqualTo("UP");
		assertThat(components.get("db").get("status").asString()).isEqualTo("UP");
	}

	@Test
	void onlyHealthAndInfoAreExposed() {
		assertThat(http.get().uri("/actuator/info").retrieve().toBodilessEntity().getStatusCode())
				.isEqualTo(HttpStatus.OK);
		assertThat(http.get().uri("/actuator/env").retrieve().toBodilessEntity().getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void openApiAndSwaggerUiAreServed() {
		ResponseEntity<JsonNode> spec = http.get().uri("/v3/api-docs").retrieve().toEntity(JsonNode.class);
		assertThat(spec.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(spec.getBody().get("openapi").asString()).startsWith("3.");
		assertThat(spec.getBody().get("paths").has("/api/v1/audiobooks/{audiobookId}/chapters/{chapterId}/audio"))
				.isTrue();

		ResponseEntity<String> ui = http.get().uri("/swagger-ui/index.html").retrieve().toEntity(String.class);
		assertThat(ui.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(ui.getBody()).containsIgnoringCase("swagger");

		ResponseEntity<Void> redirect = http.get().uri("/swagger-ui.html").retrieve().toBodilessEntity();
		assertThat(redirect.getStatusCode().is2xxSuccessful() || redirect.getStatusCode().is3xxRedirection())
				.isTrue();
	}
}

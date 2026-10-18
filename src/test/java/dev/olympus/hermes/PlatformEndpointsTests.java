package dev.olympus.hermes;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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

	/** What a generated (Retrofit/Kotlin) client relies on: per-operation status codes, ids and error schemas. */
	@Test
	void openApiDocumentsRealResponsesPerOperation() throws Exception {
		String json = http.get().uri("/v3/api-docs").retrieve().body(String.class);
		Path dump = Path.of("target", "openapi.json");
		Files.createDirectories(dump.getParent());
		Files.writeString(dump, json);
		JsonNode spec = JsonMapper.builder().build().readTree(json);
		JsonNode paths = spec.get("paths");
		String book = "/api/v1/audiobooks/{id}";
		String chapter = "/api/v1/audiobooks/{audiobookId}/chapters/{chapterId}";

		assertThat(responseCodes(paths, "/api/v1/audiobooks", "get")).containsExactlyInAnyOrder("200", "400");
		assertThat(responseCodes(paths, "/api/v1/audiobooks", "post")).containsExactlyInAnyOrder("201", "400", "415");
		JsonNode created = paths.get("/api/v1/audiobooks").get("post").get("responses").get("201");
		assertThat(created.get("headers").has("Location")).isTrue();
		assertThat(created.get("content").get("application/json").get("schema").get("$ref").asString())
				.endsWith("/AudiobookDetail");
		assertThat(responseCodes(paths, book, "get")).containsExactlyInAnyOrder("200", "404");
		assertThat(json).doesNotContain("\"*/*\"");
		assertThat(responseCodes(paths, book, "put")).containsExactlyInAnyOrder("200", "400", "404", "415");
		assertThat(responseCodes(paths, book, "patch")).containsExactlyInAnyOrder("200", "400", "404", "415");
		assertThat(responseCodes(paths, book, "delete")).containsExactlyInAnyOrder("204", "404");
		assertThat(responseCodes(paths, book + "/cover", "put"))
				.containsExactlyInAnyOrder("204", "400", "404", "413", "415", "502");
		assertThat(responseCodes(paths, book + "/cover", "get")).containsExactlyInAnyOrder("200", "304", "404", "502");
		assertThat(responseCodes(paths, "/api/v1/audiobooks/{audiobookId}/chapters", "post"))
				.containsExactlyInAnyOrder("201", "400", "404", "409", "413", "415", "502");
		assertThat(responseCodes(paths, chapter, "put")).containsExactlyInAnyOrder("200", "400", "404", "409", "415");
		assertThat(responseCodes(paths, chapter, "patch")).containsExactlyInAnyOrder("200", "400", "404", "409", "415");
		assertThat(responseCodes(paths, chapter, "delete")).containsExactlyInAnyOrder("204", "404");
		assertThat(responseCodes(paths, chapter + "/audio", "get"))
				.containsExactlyInAnyOrder("200", "206", "304", "404", "416", "502");
		assertThat(responseCodes(paths, chapter + "/audio", "head"))
				.containsExactlyInAnyOrder("200", "206", "304", "404", "416");
		assertThat(responseCodes(paths, "/api/v1/audiobooks/{audiobookId}/progress", "get"))
				.containsExactlyInAnyOrder("200", "204", "404");
		assertThat(responseCodes(paths, "/api/v1/audiobooks/{audiobookId}/progress", "put"))
				.containsExactlyInAnyOrder("200", "400", "404", "415");

		// every operation has a stable, unique id (no springdoc "_1" suffixes)
		List<String> ids = new ArrayList<>();
		paths.properties().forEach(path -> path.getValue().properties()
				.forEach(op -> ids.add(op.getValue().get("operationId").asString())));
		assertThat(ids).doesNotHaveDuplicates().noneMatch(id -> id.matches(".*_\\d+$"))
				.contains("streamChapterAudio", "headChapterAudio", "patchAudiobook", "getProgress");

		// errors are RFC 9457 problem details
		JsonNode notFound = spec.get("components").get("responses").get("NotFound");
		assertThat(notFound.get("content").get("application/problem+json").get("schema").get("$ref").asString())
				.isEqualTo("#/components/schemas/ProblemDetail");
		JsonNode problem = spec.get("components").get("schemas").get("ProblemDetail");
		assertThat(problem.get("properties").has("status")).isTrue();
		assertThat(problem.get("properties").has("errors")).isTrue();

		// non-null response fields are required, so generated Kotlin properties are non-nullable
		List<String> required = new ArrayList<>();
		spec.get("components").get("schemas").get("ChapterResponse").get("required")
				.forEach(n -> required.add(n.asString()));
		assertThat(required).contains("id", "audioUrl", "sizeBytes").doesNotContain("durationSeconds");
	}

	private static List<String> responseCodes(JsonNode paths, String path, String method) {
		JsonNode operation = paths.get(path).get(method);
		assertThat(operation).as(method + " " + path).isNotNull();
		List<String> codes = new ArrayList<>();
		operation.get("responses").propertyNames().forEach(codes::add);
		return codes;
	}
}

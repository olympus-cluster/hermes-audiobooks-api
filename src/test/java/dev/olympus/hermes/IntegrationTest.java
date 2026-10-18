package dev.olympus.hermes;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;

/**
 * Base for full-stack tests: real Tomcat on a random port, PostgreSQL and RustFS in containers (started once per JVM
 * and shared by every test class, so the Spring context is cached too).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "HERMES_MAX_UPLOAD=64KB",
				// test-only: lets tests assert which contributors the readiness group contains
				"management.endpoint.health.group.readiness.show-components=always" })
public abstract class IntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(TestContainers.POSTGRES_IMAGE)
			// same locale as the CloudNativePG cluster (C), so locale-dependent SQL behaves like production
			.withEnv("POSTGRES_INITDB_ARGS", "--locale=C --encoding=UTF8");

	static final GenericContainer<?> rustfs = TestContainers.rustfs();

	static {
		postgres.start();
		rustfs.start();
	}

	@DynamicPropertySource
	static void s3Properties(DynamicPropertyRegistry registry) {
		TestContainers.registerS3(registry, rustfs);
	}

	@Value("${local.server.port}")
	int port;

	protected RestClient http;

	@BeforeEach
	void setUpClient() {
		http = RestClient.builder()
				.baseUrl("http://localhost:" + port)
				.requestFactory(new JdkClientHttpRequestFactory(
						HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
				.defaultStatusHandler(status -> true, (request, response) -> {
				})
				.build();
	}

	protected UUID createAudiobook(String title, String author) {
		ResponseEntity<JsonNode> res = http.post().uri("/api/v1/audiobooks")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("title", title, "author", author))
				.retrieve().toEntity(JsonNode.class);
		if (res.getStatusCode().value() != 201) {
			throw new IllegalStateException("create failed: " + res);
		}
		return UUID.fromString(res.getBody().get("id").asString());
	}

	protected ResponseEntity<JsonNode> uploadChapter(UUID audiobookId, byte[] audio, String contentType,
			Map<String, String> fields) {
		MultipartBodyBuilder body = new MultipartBodyBuilder();
		body.part("file", new ByteArrayResource(audio) {
			@Override
			public String getFilename() {
				return "chapter.bin";
			}
		}).contentType(MediaType.parseMediaType(contentType));
		fields.forEach(body::part);
		return http.post().uri("/api/v1/audiobooks/{id}/chapters", audiobookId)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(body.build())
				.retrieve().toEntity(JsonNode.class);
	}

	protected static byte[] bytes(int size) {
		byte[] data = new byte[size];
		for (int i = 0; i < size; i++) {
			data[i] = (byte) (i * 31 + 7);
		}
		return data;
	}
}

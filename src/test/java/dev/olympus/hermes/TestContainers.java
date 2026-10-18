package dev.olympus.hermes;

import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Shared container definitions for tests. */
public final class TestContainers {

	public static final String POSTGRES_IMAGE = "postgres:18";
	public static final String RUSTFS_IMAGE = "rustfs/rustfs:1.0.3";
	public static final String S3_ACCESS_KEY = "hermes-test";
	public static final String S3_SECRET_KEY = "hermes-test-secret";
	public static final String S3_BUCKET = "hermes-audio-test";

	private TestContainers() {
	}

	@SuppressWarnings("resource")
	public static GenericContainer<?> rustfs() {
		return new GenericContainer<>(DockerImageName.parse(RUSTFS_IMAGE))
				.withExposedPorts(9000)
				.withEnv("RUSTFS_ACCESS_KEY", S3_ACCESS_KEY)
				.withEnv("RUSTFS_SECRET_KEY", S3_SECRET_KEY)
				.waitingFor(Wait.forHttp("/health/ready").forPort(9000).forStatusCode(200)
						.withStartupTimeout(Duration.ofMinutes(2)));
	}

	public static void registerS3(DynamicPropertyRegistry registry, GenericContainer<?> rustfs) {
		registry.add("hermes.s3.endpoint", () -> "http://" + rustfs.getHost() + ":" + rustfs.getMappedPort(9000));
		registry.add("hermes.s3.region", () -> "us-east-1");
		registry.add("hermes.s3.bucket", () -> S3_BUCKET);
		registry.add("hermes.s3.access-key", () -> S3_ACCESS_KEY);
		registry.add("hermes.s3.secret-key", () -> S3_SECRET_KEY);
	}
}

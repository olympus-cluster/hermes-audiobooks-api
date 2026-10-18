package dev.olympus.hermes;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Used by {@link TestHermesAudiobooksApiApplication} to run the app locally against throw-away containers. */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse(TestContainers.POSTGRES_IMAGE));
	}

	@Bean
	GenericContainer<?> rustfsContainer() {
		return TestContainers.rustfs();
	}

	@Bean
	DynamicPropertyRegistrar s3Properties(GenericContainer<?> rustfsContainer) {
		return registry -> TestContainers.registerS3(registry, rustfsContainer);
	}

}

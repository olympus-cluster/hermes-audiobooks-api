package dev.olympus.hermes.storage;

import dev.olympus.hermes.config.S3Properties;
import java.net.URI;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration(proxyBeanMethods = false)
class S3Config {

	@Bean(destroyMethod = "close")
	S3Client s3Client(S3Properties props) {
		S3ClientBuilder builder = S3Client.builder()
				.region(Region.of(props.region()))
				.forcePathStyle(true)
				// S3-compatible servers do not all support the SDK's default flexible checksums / aws-chunked trailers
				.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
				.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
				.httpClientBuilder(ApacheHttpClient.builder()
						.maxConnections(200)
						.connectionTimeout(Duration.ofSeconds(5))
						.socketTimeout(Duration.ofSeconds(60)));
		if (props.hasEndpoint()) {
			builder.endpointOverride(URI.create(props.endpoint()));
		}
		builder.credentialsProvider(props.hasStaticCredentials()
				? StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()))
				: DefaultCredentialsProvider.builder().build());
		return builder.build();
	}
}

package dev.olympus.hermes.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * S3-compatible object storage settings (RustFS in the cluster).
 *
 * @param endpoint  endpoint override, e.g. {@code http://rustfs.data.svc:9000}; blank means AWS default endpoints
 * @param region    signing region
 * @param bucket    bucket holding covers and chapter audio (created on startup if missing)
 * @param accessKey static access key; blank falls back to the default AWS credentials chain
 * @param secretKey static secret key
 */
@Validated
@ConfigurationProperties("hermes.s3")
public record S3Properties(
		String endpoint,
		@NotBlank String region,
		@NotBlank String bucket,
		String accessKey,
		String secretKey) {

	public boolean hasEndpoint() {
		return endpoint != null && !endpoint.isBlank();
	}

	public boolean hasStaticCredentials() {
		return accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank();
	}
}

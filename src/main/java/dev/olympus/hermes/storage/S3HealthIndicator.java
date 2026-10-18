package dev.olympus.hermes.storage;

import dev.olympus.hermes.config.S3Properties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

/** Health contributor {@code s3} (part of the readiness group): the bucket must answer HeadBucket. */
@Component("s3HealthIndicator")
class S3HealthIndicator implements HealthIndicator {

	private final S3Client s3;
	private final String bucket;

	S3HealthIndicator(S3Client s3, S3Properties props) {
		this.s3 = s3;
		this.bucket = props.bucket();
	}

	@Override
	public Health health() {
		try {
			s3.headBucket(b -> b.bucket(bucket));
			return Health.up().withDetail("bucket", bucket).build();
		}
		catch (RuntimeException ex) {
			return Health.down(ex).withDetail("bucket", bucket).build();
		}
	}
}

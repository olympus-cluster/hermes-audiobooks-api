package dev.olympus.hermes.storage;

import dev.olympus.hermes.config.S3Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Creates the bucket on startup if it does not exist. Any other storage error fails startup, so the pod restarts
 * until storage is reachable.
 */
@Component
class BucketInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(BucketInitializer.class);

	private final S3Client s3;
	private final String bucket;

	BucketInitializer(S3Client s3, S3Properties props) {
		this.s3 = s3;
		this.bucket = props.bucket();
	}

	@Override
	public void run(ApplicationArguments args) {
		try {
			s3.headBucket(b -> b.bucket(bucket));
			log.info("Using existing bucket '{}'", bucket);
		}
		catch (S3Exception ex) {
			if (ex.statusCode() != 404) {
				throw ex;
			}
			try {
				s3.createBucket(b -> b.bucket(bucket));
				log.info("Created bucket '{}'", bucket);
			}
			catch (BucketAlreadyOwnedByYouException race) {
				log.info("Bucket '{}' was created concurrently", bucket);
			}
		}
	}
}

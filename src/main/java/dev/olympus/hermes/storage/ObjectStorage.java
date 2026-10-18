package dev.olympus.hermes.storage;

import dev.olympus.hermes.config.S3Properties;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

/** Thin wrapper over the S3 client; all object access goes through here. */
@Component
public class ObjectStorage {

	private static final Logger log = LoggerFactory.getLogger(ObjectStorage.class);
	private static final int DELETE_BATCH = 1000;

	private final S3Client s3;
	private final String bucket;

	ObjectStorage(S3Client s3, S3Properties props) {
		this.s3 = s3;
		this.bucket = props.bucket();
	}

	/** Streams {@code size} bytes from {@code content} to S3; the content is never buffered in full. */
	public void put(String key, InputStream content, long size, String contentType) {
		s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType).contentLength(size),
				RequestBody.fromInputStream(content, size));
	}

	/** Opens the object, or a range of it. The caller must close (or abort) the returned stream. */
	public ResponseInputStream<GetObjectResponse> get(String key, ByteRange range) {
		GetObjectRequest.Builder req = GetObjectRequest.builder().bucket(bucket).key(key);
		if (range != null) {
			req.range(range.toS3Header());
		}
		return s3.getObject(req.build());
	}

	/** Best-effort bulk delete; failures are logged (orphaned objects are harmless and can be swept later). */
	public void deleteQuietly(Collection<String> keys) {
		List<ObjectIdentifier> ids = keys.stream().map(k -> ObjectIdentifier.builder().key(k).build()).toList();
		for (int i = 0; i < ids.size(); i += DELETE_BATCH) {
			List<ObjectIdentifier> batch = ids.subList(i, Math.min(ids.size(), i + DELETE_BATCH));
			try {
				DeleteObjectsResponse resp = s3.deleteObjects(b -> b.bucket(bucket)
						.delete(Delete.builder().objects(batch).quiet(true).build()));
				resp.errors().forEach(e -> log.warn("Failed to delete s3://{}/{}: {} {}", bucket, e.key(), e.code(),
						e.message()));
			}
			catch (RuntimeException ex) {
				log.warn("Failed to delete {} objects from bucket {}", batch.size(), bucket, ex);
			}
		}
	}

	public void deleteQuietly(String key) {
		try {
			s3.deleteObject(b -> b.bucket(bucket).key(key));
		}
		catch (RuntimeException ex) {
			log.warn("Failed to delete s3://{}/{}", bucket, key, ex);
		}
	}
}

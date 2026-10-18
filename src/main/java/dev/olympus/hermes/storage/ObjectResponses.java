package dev.olympus.hermes.storage;

import dev.olympus.hermes.error.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** Builds HTTP responses that proxy an S3 object to the client without buffering it. */
@Component
public class ObjectResponses {

	private static final int BUFFER = 64 * 1024;

	private final ObjectStorage storage;

	ObjectResponses(ObjectStorage storage) {
		this.storage = storage;
	}

	/**
	 * Range-aware response for an object of known size: 304 (client copy is current), 200 (full), 206 (single range,
	 * unless If-Range names another version) or 416. For HEAD and 304 the headers are computed without touching S3.
	 */
	public ResponseEntity<StreamingResponseBody> ranged(String key, String contentType, long size, ObjectVersion version,
			CacheControl cacheControl, HttpHeaders request, boolean headOnly) {
		if (version.notModified(request)) {
			return notModified(version, cacheControl);
		}
		Optional<ByteRange> range = version.rangeApplies(request)
				? RangeParser.parse(request.getFirst(HttpHeaders.RANGE), size)
				: Optional.empty();
		ResponseEntity.BodyBuilder builder = range.isPresent()
				? ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
						.header(HttpHeaders.CONTENT_RANGE, range.get().toContentRange())
						.contentLength(range.get().length())
				: ResponseEntity.ok().contentLength(size);
		builder.header(HttpHeaders.ACCEPT_RANGES, "bytes").contentType(MediaType.parseMediaType(contentType));
		validators(builder, version, cacheControl);
		if (headOnly) {
			return builder.build();
		}
		return builder.body(stream(open(key, range.orElse(null))));
	}

	/** Full-object response (or 304 without touching S3); length comes from S3. */
	public ResponseEntity<StreamingResponseBody> full(String key, String contentType, ObjectVersion version,
			CacheControl cacheControl, HttpHeaders request) {
		if (version.notModified(request)) {
			return notModified(version, cacheControl);
		}
		ResponseInputStream<GetObjectResponse> in = open(key, null);
		ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(contentType));
		validators(builder, version, cacheControl);
		Long length = in.response().contentLength();
		if (length != null) {
			builder.contentLength(length);
		}
		return builder.body(stream(in));
	}

	private static ResponseEntity<StreamingResponseBody> notModified(ObjectVersion version, CacheControl cacheControl) {
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.NOT_MODIFIED);
		validators(builder, version, cacheControl);
		return builder.build();
	}

	private static void validators(ResponseEntity.BodyBuilder builder, ObjectVersion version,
			CacheControl cacheControl) {
		builder.eTag(version.etag().formattedTag()).lastModified(version.lastModified()).cacheControl(cacheControl);
	}

	private ResponseInputStream<GetObjectResponse> open(String key, ByteRange range) {
		try {
			return storage.get(key, range);
		}
		catch (NoSuchKeyException ex) {
			throw new NotFoundException("Stored object for this resource is missing");
		}
	}

	private static StreamingResponseBody stream(ResponseInputStream<GetObjectResponse> in) {
		return out -> copy(in, out);
	}

	private static void copy(ResponseInputStream<GetObjectResponse> in, OutputStream out) throws IOException {
		boolean complete = false;
		try {
			transfer(in, out);
			complete = true;
		}
		finally {
			if (!complete) {
				// do not drain the rest of a (possibly huge) object just to reuse the connection
				in.abort();
			}
			in.close();
		}
	}

	private static void transfer(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[BUFFER];
		int n;
		while ((n = in.read(buffer)) != -1) {
			out.write(buffer, 0, n);
		}
		out.flush();
	}
}

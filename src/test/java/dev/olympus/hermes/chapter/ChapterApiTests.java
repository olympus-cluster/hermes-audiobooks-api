package dev.olympus.hermes.chapter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.olympus.hermes.IntegrationTest;
import dev.olympus.hermes.TestContainers;
import dev.olympus.hermes.storage.StorageKeys;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tools.jackson.databind.JsonNode;

class ChapterApiTests extends IntegrationTest {

	private static final int SIZE = 1000;

	@Autowired
	S3Client s3;

	UUID bookId;
	UUID chapterId;
	byte[] audio;

	@BeforeEach
	void uploadChapter() {
		bookId = createAudiobook("Streaming book", "Author");
		audio = bytes(SIZE);
		ResponseEntity<JsonNode> res = uploadChapter(bookId, audio, "audio/mpeg",
				Map.of("title", "Chapter one", "durationSeconds", "125"));
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		JsonNode body = res.getBody();
		chapterId = UUID.fromString(body.get("id").asString());
		assertThat(body.get("position").asInt()).isEqualTo(1);
		assertThat(body.get("contentType").asString()).isEqualTo("audio/mpeg");
		assertThat(body.get("sizeBytes").asLong()).isEqualTo(SIZE);
		assertThat(body.get("audioUrl").asString()).endsWith("/chapters/" + chapterId + "/audio");
	}

	@Test
	void chaptersAreOrderedAndTotalDurationIsSummed() {
		uploadChapter(bookId, bytes(10), "audio/ogg", Map.of("title", "Chapter three", "position", "3",
				"durationSeconds", "60"));
		ResponseEntity<JsonNode> second = uploadChapter(bookId, bytes(10), "audio/mp4",
				Map.of("title", "Chapter two", "position", "2"));
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		ResponseEntity<JsonNode> next = uploadChapter(bookId, bytes(10), "audio/flac", Map.of("title", "Four"));
		assertThat(next.getBody().get("position").asInt()).isEqualTo(4);

		JsonNode detail = http.get().uri("/api/v1/audiobooks/{id}", bookId).retrieve().body(JsonNode.class);
		assertThat(detail.get("totalDurationSeconds").asLong()).isEqualTo(185);
		assertThat(detail.get("chapters").size()).isEqualTo(4);
		assertThat(detail.get("chapters").get(0).get("title").asString()).isEqualTo("Chapter one");
		assertThat(detail.get("chapters").get(1).get("title").asString()).isEqualTo("Chapter two");
		assertThat(detail.get("chapters").get(2).get("title").asString()).isEqualTo("Chapter three");

		ResponseEntity<JsonNode> clash = uploadChapter(bookId, bytes(10), "audio/mpeg",
				Map.of("title", "Clash", "position", "2"));
		assertThat(clash.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void streamsFullBody() {
		ResponseEntity<byte[]> res = audio(null);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(res.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
		assertThat(res.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("audio/mpeg"));
		assertThat(res.getHeaders().getContentLength()).isEqualTo(SIZE);
		assertThat(res.getBody()).isEqualTo(audio);
	}

	@Test
	void streamsBoundedRange() {
		ResponseEntity<byte[]> res = audio("bytes=0-9");
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
		assertThat(res.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 0-9/" + SIZE);
		assertThat(res.getHeaders().getContentLength()).isEqualTo(10);
		assertThat(res.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
		assertThat(res.getBody()).isEqualTo(Arrays.copyOfRange(audio, 0, 10));
	}

	@Test
	void streamsOpenEndedAndSuffixRanges() {
		ResponseEntity<byte[]> open = audio("bytes=990-");
		assertThat(open.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
		assertThat(open.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 990-999/" + SIZE);
		assertThat(open.getBody()).isEqualTo(Arrays.copyOfRange(audio, 990, SIZE));

		ResponseEntity<byte[]> suffix = audio("bytes=-100");
		assertThat(suffix.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
		assertThat(suffix.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 900-999/" + SIZE);
		assertThat(suffix.getHeaders().getContentLength()).isEqualTo(100);
		assertThat(suffix.getBody()).isEqualTo(Arrays.copyOfRange(audio, 900, SIZE));

		ResponseEntity<byte[]> clamped = audio("bytes=500-5000");
		assertThat(clamped.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 500-999/" + SIZE);
		assertThat(clamped.getBody()).isEqualTo(Arrays.copyOfRange(audio, 500, SIZE));
	}

	@Test
	void audioCarriesValidatorsAndCacheControl() {
		ResponseEntity<byte[]> full = audio(null);
		assertThat(full.getHeaders().getETag()).isEqualTo("\"" + chapterId + "\"");
		assertThat(full.getHeaders().getLastModified()).isPositive();
		assertThat(full.getHeaders().getCacheControl()).isEqualTo("max-age=31536000, immutable");

		ResponseEntity<byte[]> partial = audio("bytes=0-9");
		assertThat(partial.getHeaders().getETag()).isEqualTo(full.getHeaders().getETag());
		assertThat(partial.getHeaders().getLastModified()).isEqualTo(full.getHeaders().getLastModified());
		assertThat(partial.getHeaders().getCacheControl()).isEqualTo("max-age=31536000, immutable");
	}

	@Test
	void revalidationWithIfNoneMatchOrIfModifiedSinceIs304() {
		ResponseEntity<byte[]> full = audio(null);
		String etag = full.getHeaders().getETag();
		long lastModified = full.getHeaders().getLastModified();

		ResponseEntity<byte[]> byEtag = audioWith(h -> h.setIfNoneMatch(etag));
		assertThat(byEtag.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
		assertThat(byEtag.getBody()).isNull();
		assertThat(byEtag.getHeaders().getETag()).isEqualTo(etag);
		assertThat(byEtag.getHeaders().getCacheControl()).isEqualTo("max-age=31536000, immutable");

		// weak comparison, lists and * all match; a Range does not matter when the copy is current
		assertThat(audioWith(h -> h.set(HttpHeaders.IF_NONE_MATCH, "\"other\", W/" + etag)).getStatusCode())
				.isEqualTo(HttpStatus.NOT_MODIFIED);
		assertThat(audioWith(h -> h.set(HttpHeaders.IF_NONE_MATCH, "*")).getStatusCode())
				.isEqualTo(HttpStatus.NOT_MODIFIED);
		assertThat(audioWith(h -> {
			h.setIfNoneMatch(etag);
			h.set(HttpHeaders.RANGE, "bytes=0-9");
		}).getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);

		ResponseEntity<byte[]> otherEtag = audioWith(h -> h.setIfNoneMatch("\"stale\""));
		assertThat(otherEtag.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(otherEtag.getBody()).isEqualTo(audio);

		assertThat(audioWith(h -> h.setIfModifiedSince(lastModified)).getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
		assertThat(audioWith(h -> h.setIfModifiedSince(lastModified - 1000)).getStatusCode()).isEqualTo(HttpStatus.OK);
		// If-None-Match wins over If-Modified-Since
		assertThat(audioWith(h -> {
			h.setIfNoneMatch("\"stale\"");
			h.setIfModifiedSince(lastModified);
		}).getStatusCode()).isEqualTo(HttpStatus.OK);

		ResponseEntity<Void> head = http.head()
				.uri("/api/v1/audiobooks/{id}/chapters/{c}/audio", bookId, chapterId)
				.header(HttpHeaders.IF_NONE_MATCH, etag)
				.retrieve().toBodilessEntity();
		assertThat(head.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
	}

	@Test
	void ifRangeServesTheRangeOnlyForTheCurrentVersion() {
		ResponseEntity<byte[]> full = audio(null);
		String etag = full.getHeaders().getETag();
		String lastModified = full.getHeaders().getFirst(HttpHeaders.LAST_MODIFIED);

		ResponseEntity<byte[]> sameEtag = audioWith(h -> {
			h.set(HttpHeaders.RANGE, "bytes=10-19");
			h.set(HttpHeaders.IF_RANGE, etag);
		});
		assertThat(sameEtag.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
		assertThat(sameEtag.getBody()).isEqualTo(Arrays.copyOfRange(audio, 10, 20));

		ResponseEntity<byte[]> sameDate = audioWith(h -> {
			h.set(HttpHeaders.RANGE, "bytes=10-19");
			h.set(HttpHeaders.IF_RANGE, lastModified);
		});
		assertThat(sameDate.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);

		for (String stale : new String[] { "\"stale\"", "W/" + etag, "Thu, 01 Jan 1970 00:00:00 GMT", "garbage" }) {
			ResponseEntity<byte[]> res = audioWith(h -> {
				h.set(HttpHeaders.RANGE, "bytes=10-19");
				h.set(HttpHeaders.IF_RANGE, stale);
			});
			assertThat(res.getStatusCode()).as(stale).isEqualTo(HttpStatus.OK);
			assertThat(res.getBody()).as(stale).isEqualTo(audio);
		}

		// a stale If-Range also means an unsatisfiable Range is ignored rather than answered with 416
		ResponseEntity<byte[]> ignored = audioWith(h -> {
			h.set(HttpHeaders.RANGE, "bytes=5000-");
			h.set(HttpHeaders.IF_RANGE, "\"stale\"");
		});
		assertThat(ignored.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void unsatisfiableRangeIs416() {
		ResponseEntity<byte[]> res = audio("bytes=5000-");
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
		assertThat(res.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */" + SIZE);

		assertThat(audio("bytes=20-10").getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
		assertThat(audio("bytes=abc").getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
	}

	@Test
	void headReturnsHeadersOnly() {
		ResponseEntity<Void> head = http.head()
				.uri("/api/v1/audiobooks/{id}/chapters/{c}/audio", bookId, chapterId)
				.header(HttpHeaders.RANGE, "bytes=0-99")
				.retrieve().toBodilessEntity();
		assertThat(head.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
		assertThat(head.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 0-99/" + SIZE);
		assertThat(head.getHeaders().getContentLength()).isEqualTo(100);
		assertThat(head.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
	}

	@Test
	void putWithoutDurationKeepsTheStoredDuration() {
		ResponseEntity<JsonNode> updated = http.put()
				.uri("/api/v1/audiobooks/{id}/chapters/{c}", bookId, chapterId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("title", "Renamed", "position", 1))
				.retrieve().toEntity(JsonNode.class);
		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(updated.getBody().get("title").asString()).isEqualTo("Renamed");
		assertThat(updated.getBody().get("durationSeconds").asInt()).isEqualTo(125);

		JsonNode detail = http.get().uri("/api/v1/audiobooks/{id}", bookId).retrieve().body(JsonNode.class);
		assertThat(detail.get("totalDurationSeconds").asLong()).isEqualTo(125);
	}

	@Test
	void patchChangesOnlyTheGivenFields() {
		ResponseEntity<JsonNode> renamed = patch(Map.of("title", "  Prologue "));
		assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(renamed.getBody().get("title").asString()).isEqualTo("Prologue");
		assertThat(renamed.getBody().get("position").asInt()).isEqualTo(1);
		assertThat(renamed.getBody().get("durationSeconds").asInt()).isEqualTo(125);

		ResponseEntity<JsonNode> moved = patch(Map.of("position", 5, "durationSeconds", 130));
		assertThat(moved.getBody().get("title").asString()).isEqualTo("Prologue");
		assertThat(moved.getBody().get("position").asInt()).isEqualTo(5);
		assertThat(moved.getBody().get("durationSeconds").asInt()).isEqualTo(130);

		uploadChapter(bookId, bytes(10), "audio/mpeg", Map.of("title", "Other", "position", "2"));
		assertThat(patch(Map.of("position", 2)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		ResponseEntity<JsonNode> blank = patch(Map.of("title", " "));
		assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(blank.getBody().get("errors").get(0).get("field").asString()).isEqualTo("title");
		assertThat(patch(Map.of("position", 0)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		assertThat(http.patch().uri("/api/v1/audiobooks/{id}/chapters/{c}", bookId, UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("title", "x"))
				.retrieve().toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void updateAndDeleteChapter() {
		ResponseEntity<JsonNode> updated = http.put()
				.uri("/api/v1/audiobooks/{id}/chapters/{c}", bookId, chapterId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("title", "Renamed", "position", 7, "durationSeconds", 99))
				.retrieve().toEntity(JsonNode.class);
		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(updated.getBody().get("title").asString()).isEqualTo("Renamed");
		assertThat(updated.getBody().get("position").asInt()).isEqualTo(7);

		ResponseEntity<JsonNode> invalid = http.put()
				.uri("/api/v1/audiobooks/{id}/chapters/{c}", bookId, chapterId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("title", "", "position", 0))
				.retrieve().toEntity(JsonNode.class);
		assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		String key = StorageKeys.chapter(bookId, chapterId);
		assertThat(objectExists(key)).isTrue();
		assertThat(http.delete().uri("/api/v1/audiobooks/{id}/chapters/{c}", bookId, chapterId)
				.retrieve().toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(objectExists(key)).isFalse();
		assertThat(audio(null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void rejectsWrongMediaTypeMissingFieldsAndOversizedUploads() {
		ResponseEntity<JsonNode> wrongType = uploadChapter(bookId, bytes(10), "video/mp4", Map.of("title", "x"));
		assertThat(wrongType.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		assertThat(wrongType.getBody().get("status").asInt()).isEqualTo(415);

		ResponseEntity<JsonNode> notMultipart = http.post().uri("/api/v1/audiobooks/{id}/chapters", bookId)
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("title", "x"))
				.retrieve().toEntity(JsonNode.class);
		assertThat(notMultipart.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

		ResponseEntity<JsonNode> noTitle = uploadChapter(bookId, bytes(10), "audio/mpeg", Map.of());
		assertThat(noTitle.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(noTitle.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

		// tests run with HERMES_MAX_UPLOAD=64KB
		ResponseEntity<JsonNode> tooBig = uploadChapter(bookId, bytes(100 * 1024), "audio/mpeg",
				Map.of("title", "big"));
		assertThat(tooBig.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
		assertThat(tooBig.getBody().get("status").asInt()).isEqualTo(413);

		ResponseEntity<JsonNode> unknownBook = uploadChapter(UUID.randomUUID(), bytes(10), "audio/mpeg",
				Map.of("title", "x"));
		assertThat(unknownBook.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		ResponseEntity<byte[]> unknownChapter = http.get()
				.uri("/api/v1/audiobooks/{id}/chapters/{c}/audio", bookId, UUID.randomUUID())
				.retrieve().toEntity(byte[].class);
		assertThat(unknownChapter.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private ResponseEntity<byte[]> audio(String range) {
		return audioWith(h -> {
			if (range != null) {
				h.set(HttpHeaders.RANGE, range);
			}
		});
	}

	private ResponseEntity<byte[]> audioWith(Consumer<HttpHeaders> headers) {
		return http.get().uri("/api/v1/audiobooks/{id}/chapters/{c}/audio", bookId, chapterId)
				.headers(headers)
				.retrieve().toEntity(byte[].class);
	}

	private ResponseEntity<JsonNode> patch(Map<String, Object> body) {
		return http.patch().uri("/api/v1/audiobooks/{id}/chapters/{c}", bookId, chapterId)
				.contentType(MediaType.APPLICATION_JSON).body(body)
				.retrieve().toEntity(JsonNode.class);
	}

	private boolean objectExists(String key) {
		try {
			s3.headObject(b -> b.bucket(TestContainers.S3_BUCKET).key(key));
			return true;
		}
		catch (S3Exception ex) {
			if (ex.statusCode() == 404) {
				return false;
			}
			throw ex;
		}
	}
}

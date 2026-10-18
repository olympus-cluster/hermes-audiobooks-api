package dev.olympus.hermes.audiobook;

import static org.assertj.core.api.Assertions.assertThat;

import dev.olympus.hermes.IntegrationTest;
import dev.olympus.hermes.TestContainers;
import dev.olympus.hermes.storage.StorageKeys;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tools.jackson.databind.JsonNode;

class AudiobookApiTests extends IntegrationTest {

	@Autowired
	S3Client s3;

	@Test
	void createGetUpdateAndList() {
		String marker = UUID.randomUUID().toString().substring(0, 8);
		ResponseEntity<JsonNode> created = http.post().uri("/api/v1/audiobooks")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("title", "Zeta " + marker, "author", "Homer", "narrator", "Ian McKellen",
						"language", "en", "publishedYear", 1996, "description", "An epic"))
				.retrieve().toEntity(JsonNode.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(created.getHeaders().getLocation()).isNotNull();
		JsonNode body = created.getBody();
		UUID id = UUID.fromString(body.get("id").asString());
		assertThat(body.get("title").asString()).isEqualTo("Zeta " + marker);
		assertThat(body.get("coverUrl").isNull()).isTrue();
		assertThat(body.get("chapters").size()).isZero();
		assertThat(body.get("totalDurationSeconds").asLong()).isZero();

		createAudiobook("alpha " + marker, "Someone Else");
		createAudiobook("Unrelated " + marker, "HOMER the Poet");

		JsonNode fetched = http.get().uri("/api/v1/audiobooks/{id}", id).retrieve().body(JsonNode.class);
		assertThat(fetched.get("narrator").asString()).isEqualTo("Ian McKellen");
		assertThat(fetched.get("publishedYear").asInt()).isEqualTo(1996);

		ResponseEntity<JsonNode> updated = http.put().uri("/api/v1/audiobooks/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("title", "Beta " + marker, "author", "Homer"))
				.retrieve().toEntity(JsonNode.class);
		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(updated.getBody().get("title").asString()).isEqualTo("Beta " + marker);
		assertThat(updated.getBody().get("narrator").isNull()).isTrue();

		// search on title (case-insensitive), sorted by title
		JsonNode page = http.get().uri("/api/v1/audiobooks?q={q}&size=10", marker.toUpperCase())
				.retrieve().body(JsonNode.class);
		assertThat(page.get("totalElements").asLong()).isEqualTo(3);
		assertThat(titles(page)).containsExactly("alpha " + marker, "Beta " + marker, "Unrelated " + marker);

		// search on author
		JsonNode byAuthor = http.get().uri("/api/v1/audiobooks?q={q}", "homer").retrieve().body(JsonNode.class);
		assertThat(titles(byAuthor)).contains("Beta " + marker, "Unrelated " + marker)
				.doesNotContain("alpha " + marker);

		// paging
		JsonNode paged = http.get().uri("/api/v1/audiobooks?q={q}&page=1&size=2", marker)
				.retrieve().body(JsonNode.class);
		assertThat(paged.get("page").asInt()).isEqualTo(1);
		assertThat(paged.get("totalPages").asInt()).isEqualTo(2);
		assertThat(titles(paged)).containsExactly("Unrelated " + marker);

		// non-ASCII text is matched case-insensitively too (Greek, accented letters)
		createAudiobook("Οδύσσεια " + marker, "Όμηρος");
		createAudiobook("Ünïcødé " + marker, "Author");
		JsonNode greek = http.get().uri("/api/v1/audiobooks?q={q}", "ΟΔΎΣΣ").retrieve().body(JsonNode.class);
		assertThat(titles(greek)).containsExactly("Οδύσσεια " + marker);
		JsonNode greekAuthor = http.get().uri("/api/v1/audiobooks?q={q}", "όμηρ").retrieve().body(JsonNode.class);
		assertThat(titles(greekAuthor)).contains("Οδύσσεια " + marker);
		JsonNode accented = http.get().uri("/api/v1/audiobooks?q={q}", "ÜNÏ").retrieve().body(JsonNode.class);
		assertThat(titles(accented)).containsExactly("Ünïcødé " + marker);

		// LIKE wildcards are matched literally
		JsonNode wildcard = http.get().uri("/api/v1/audiobooks?q={q}", "%" + marker.substring(0, 3) + "_")
				.retrieve().body(JsonNode.class);
		assertThat(wildcard.get("totalElements").asLong()).isZero();
	}

	@Test
	void coverUploadDownloadAndDeleteRemovesObjects() {
		UUID id = createAudiobook("With cover", "Author");
		byte[] png = bytes(2048);

		ResponseEntity<Void> put = putCover(id, png, "image/png");
		assertThat(put.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		ResponseEntity<byte[]> cover = http.get().uri("/api/v1/audiobooks/{id}/cover", id)
				.retrieve().toEntity(byte[].class);
		assertThat(cover.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(cover.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
		assertThat(cover.getBody()).isEqualTo(png);

		JsonNode detail = http.get().uri("/api/v1/audiobooks/{id}", id).retrieve().body(JsonNode.class);
		assertThat(detail.get("coverUrl").asString()).contains("/api/v1/audiobooks/" + id + "/cover?v=");

		ResponseEntity<JsonNode> chapter = uploadChapter(id, bytes(500), "audio/mpeg", Map.of("title", "One"));
		UUID chapterId = UUID.fromString(chapter.getBody().get("id").asString());
		assertThat(objectExists(StorageKeys.cover(id))).isTrue();
		assertThat(objectExists(StorageKeys.chapter(id, chapterId))).isTrue();

		ResponseEntity<Void> deleted = http.delete().uri("/api/v1/audiobooks/{id}", id).retrieve().toBodilessEntity();
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		assertThat(http.get().uri("/api/v1/audiobooks/{id}", id).retrieve().toBodilessEntity().getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(objectExists(StorageKeys.cover(id))).isFalse();
		assertThat(objectExists(StorageKeys.chapter(id, chapterId))).isFalse();
	}

	@Test
	void coverIsVersionedAndRevalidates() {
		UUID id = createAudiobook("Cached cover", "Author");
		putCover(id, bytes(300), "image/jpeg");
		String coverUrl = coverUrl(id);
		assertThat(coverUrl).matches(".*/api/v1/audiobooks/" + id + "/cover\\?v=\\d+");
		String version = coverUrl.substring(coverUrl.indexOf("?v=") + 3);

		ResponseEntity<byte[]> versioned = getCover(id, version, null);
		assertThat(versioned.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(versioned.getHeaders().getCacheControl()).isEqualTo("max-age=31536000, immutable");
		String etag = versioned.getHeaders().getETag();
		assertThat(etag).isEqualTo("\"" + version + "\"");
		assertThat(versioned.getHeaders().getLastModified()).isPositive();

		ResponseEntity<byte[]> unversioned = getCover(id, null, null);
		assertThat(unversioned.getHeaders().getCacheControl()).isEqualTo("no-cache");
		assertThat(unversioned.getHeaders().getETag()).isEqualTo(etag);

		ResponseEntity<byte[]> revalidated = getCover(id, null, h -> h.setIfNoneMatch(etag));
		assertThat(revalidated.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
		assertThat(revalidated.getBody()).isNull();
		assertThat(revalidated.getHeaders().getETag()).isEqualTo(etag);
		long lastModified = versioned.getHeaders().getLastModified();
		assertThat(getCover(id, null, h -> h.setIfModifiedSince(lastModified)).getStatusCode())
				.isEqualTo(HttpStatus.NOT_MODIFIED);

		// metadata changes do not touch the cover version
		http.patch().uri("/api/v1/audiobooks/{id}", id).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("narrator", "Someone")).retrieve().toBodilessEntity();
		assertThat(coverUrl(id)).isEqualTo(coverUrl);

		// replacing the cover changes the URL and the ETag
		byte[] png = bytes(400);
		putCover(id, png, "image/png");
		String newUrl = coverUrl(id);
		assertThat(newUrl).isNotEqualTo(coverUrl);
		JsonNode summary = http.get().uri("/api/v1/audiobooks?q={q}", "Cached cover").retrieve().body(JsonNode.class)
				.get("content").get(0);
		assertThat(summary.get("coverUrl").asString()).isEqualTo(newUrl);

		ResponseEntity<byte[]> changed = getCover(id, null, h -> h.setIfNoneMatch(etag));
		assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(changed.getBody()).isEqualTo(png);
		assertThat(changed.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
		assertThat(changed.getHeaders().getETag()).isNotEqualTo(etag);
		// the old version parameter still works but is no longer immutable
		assertThat(getCover(id, version, null).getHeaders().getCacheControl()).isEqualTo("no-cache");
	}

	@Test
	void patchChangesOnlyTheGivenFields() {
		ResponseEntity<JsonNode> created = http.post().uri("/api/v1/audiobooks")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("title", "Patch me", "author", "Author", "narrator", "Reader", "language", "en",
						"publishedYear", 2001, "description", "Text"))
				.retrieve().toEntity(JsonNode.class);
		UUID id = UUID.fromString(created.getBody().get("id").asString());

		ResponseEntity<JsonNode> patched = http.patch().uri("/api/v1/audiobooks/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"narrator\": \" New Reader \", \"publishedYear\": null}")
				.retrieve().toEntity(JsonNode.class);
		assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode body = patched.getBody();
		assertThat(body.get("narrator").asString()).isEqualTo("New Reader");
		assertThat(body.get("title").asString()).isEqualTo("Patch me");
		assertThat(body.get("author").asString()).isEqualTo("Author");
		assertThat(body.get("language").asString()).isEqualTo("en");
		assertThat(body.get("publishedYear").asInt()).isEqualTo(2001);
		assertThat(body.get("description").asString()).isEqualTo("Text");
		assertThat(Instant.parse(body.get("updatedAt").asString()))
				.isAfterOrEqualTo(Instant.parse(created.getBody().get("updatedAt").asString()));

		ResponseEntity<JsonNode> invalid = http.patch().uri("/api/v1/audiobooks/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("title", "", "language", "not a tag!"))
				.retrieve().toEntity(JsonNode.class);
		assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		List<String> fields = new ArrayList<>();
		invalid.getBody().get("errors").forEach(e -> fields.add(e.get("field").asString()));
		assertThat(fields).containsExactlyInAnyOrder("title", "language");

		assertThat(http.patch().uri("/api/v1/audiobooks/{id}", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("title", "x"))
				.retrieve().toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void coverWithWrongMediaTypeIs415() {
		UUID id = createAudiobook("Bad cover", "Author");
		ResponseEntity<Void> res = putCover(id, bytes(10), "application/pdf");
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		assertThat(http.get().uri("/api/v1/audiobooks/{id}/cover", id).retrieve().toBodilessEntity()
				.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void validationErrorIs400ProblemDetail() {
		ResponseEntity<JsonNode> res = http.post().uri("/api/v1/audiobooks")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("title", " ", "author", "x".repeat(201), "language", "not a tag!"))
				.retrieve().toEntity(JsonNode.class);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(res.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		JsonNode pd = res.getBody();
		assertThat(pd.get("status").asInt()).isEqualTo(400);
		assertThat(pd.get("title").asString()).isEqualTo("Bad Request");
		List<String> fields = new ArrayList<>();
		pd.get("errors").forEach(e -> fields.add(e.get("field").asString()));
		assertThat(fields).contains("title", "author", "language");

		ResponseEntity<JsonNode> badPage = http.get().uri("/api/v1/audiobooks?size=1000")
				.retrieve().toEntity(JsonNode.class);
		assertThat(badPage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(badPage.getBody().get("status").asInt()).isEqualTo(400);

		ResponseEntity<JsonNode> malformed = http.post().uri("/api/v1/audiobooks")
				.contentType(MediaType.APPLICATION_JSON).body("{not json")
				.retrieve().toEntity(JsonNode.class);
		assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void unknownAudiobookIs404ProblemDetail() {
		UUID missing = UUID.randomUUID();
		ResponseEntity<JsonNode> res = http.get().uri("/api/v1/audiobooks/{id}", missing)
				.retrieve().toEntity(JsonNode.class);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(res.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(res.getBody().get("status").asInt()).isEqualTo(404);
		assertThat(res.getBody().get("detail").asString()).contains(missing.toString());

		assertThat(http.delete().uri("/api/v1/audiobooks/{id}", missing).retrieve().toBodilessEntity()
				.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(http.put().uri("/api/v1/audiobooks/{id}", missing).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("title", "t", "author", "a")).retrieve().toBodilessEntity().getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void bucketWasCreatedOnStartup() {
		s3.headBucket(b -> b.bucket(TestContainers.S3_BUCKET));
	}

	private ResponseEntity<Void> putCover(UUID id, byte[] data, String contentType) {
		MultipartBodyBuilder body = new MultipartBodyBuilder();
		body.part("file", new ByteArrayResource(data) {
			@Override
			public String getFilename() {
				return "cover";
			}
		}).contentType(MediaType.parseMediaType(contentType));
		return http.put().uri("/api/v1/audiobooks/{id}/cover", id)
				.contentType(MediaType.MULTIPART_FORM_DATA).body(body.build())
				.retrieve().toBodilessEntity();
	}

	private String coverUrl(UUID id) {
		return http.get().uri("/api/v1/audiobooks/{id}", id).retrieve().body(JsonNode.class).get("coverUrl").asString();
	}

	private ResponseEntity<byte[]> getCover(UUID id, String version, Consumer<HttpHeaders> headers) {
		return http.get().uri(b -> {
			b.path("/api/v1/audiobooks/{id}/cover");
			if (version != null) {
				b.queryParam("v", version);
			}
			return b.build(id);
		}).headers(h -> {
			if (headers != null) {
				headers.accept(h);
			}
		}).retrieve().toEntity(byte[].class);
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

	private static List<String> titles(JsonNode page) {
		List<String> titles = new ArrayList<>();
		page.get("content").forEach(n -> titles.add(n.get("title").asString()));
		return titles;
	}
}

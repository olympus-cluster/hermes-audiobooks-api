package dev.olympus.hermes.progress;

import static org.assertj.core.api.Assertions.assertThat;

import dev.olympus.hermes.IntegrationTest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

class ProgressApiTests extends IntegrationTest {

	@Test
	void upsertAndGetProgress() {
		UUID bookId = createAudiobook("Progress book", "Author");
		UUID ch1 = UUID.fromString(uploadChapter(bookId, bytes(10), "audio/mpeg", Map.of("title", "1"))
				.getBody().get("id").asString());
		UUID ch2 = UUID.fromString(uploadChapter(bookId, bytes(10), "audio/mpeg", Map.of("title", "2"))
				.getBody().get("id").asString());

		ResponseEntity<byte[]> none = http.get().uri("/api/v1/audiobooks/{id}/progress", bookId)
				.retrieve().toEntity(byte[].class);
		assertThat(none.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(none.getBody()).isNull();

		JsonNode first = put(bookId, ch1, 42).getBody();
		assertThat(first.get("chapterId").asString()).isEqualTo(ch1.toString());
		assertThat(first.get("positionSeconds").asInt()).isEqualTo(42);
		Instant firstUpdate = Instant.parse(first.get("updatedAt").asString());

		ResponseEntity<JsonNode> second = put(bookId, ch2, 7);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(Instant.parse(second.getBody().get("updatedAt").asString())).isAfterOrEqualTo(firstUpdate);

		JsonNode stored = http.get().uri("/api/v1/audiobooks/{id}/progress", bookId).retrieve().body(JsonNode.class);
		assertThat(stored.get("audiobookId").asString()).isEqualTo(bookId.toString());
		assertThat(stored.get("chapterId").asString()).isEqualTo(ch2.toString());
		assertThat(stored.get("positionSeconds").asInt()).isEqualTo(7);
		assertThat(stored.get("updatedAt").asString()).isEqualTo(second.getBody().get("updatedAt").asString());

		// deleting the referenced chapter cascades to the progress row
		http.delete().uri("/api/v1/audiobooks/{id}/chapters/{c}", bookId, ch2).retrieve().toBodilessEntity();
		assertThat(http.get().uri("/api/v1/audiobooks/{id}/progress", bookId).retrieve().toBodilessEntity()
				.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void unknownAudiobookIs404() {
		ResponseEntity<JsonNode> res = http.get().uri("/api/v1/audiobooks/{id}/progress", UUID.randomUUID())
				.retrieve().toEntity(JsonNode.class);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(res.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void positionMustNotExceedAKnownChapterDuration() {
		UUID bookId = createAudiobook("Progress bounds", "Author");
		UUID timed = UUID.fromString(uploadChapter(bookId, bytes(10), "audio/mpeg",
				Map.of("title", "1", "durationSeconds", "90")).getBody().get("id").asString());
		UUID untimed = UUID.fromString(uploadChapter(bookId, bytes(10), "audio/mpeg", Map.of("title", "2"))
				.getBody().get("id").asString());

		assertThat(put(bookId, timed, 90).getStatusCode()).isEqualTo(HttpStatus.OK);
		ResponseEntity<JsonNode> tooFar = put(bookId, timed, 91);
		assertThat(tooFar.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(tooFar.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(tooFar.getBody().get("detail").asString()).contains("91").contains("90");

		// unknown duration: any non-negative position is accepted
		assertThat(put(bookId, untimed, 100_000).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void rejectsInvalidProgress() {
		UUID bookId = createAudiobook("Progress validation", "Author");
		UUID chapter = UUID.fromString(uploadChapter(bookId, bytes(10), "audio/mpeg", Map.of("title", "1"))
				.getBody().get("id").asString());

		ResponseEntity<JsonNode> negative = put(bookId, chapter, -1);
		assertThat(negative.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(negative.getBody().get("errors").get(0).get("field").asString()).isEqualTo("positionSeconds");

		UUID otherBook = createAudiobook("Other", "Author");
		assertThat(put(otherBook, chapter, 5).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(put(UUID.randomUUID(), chapter, 5).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private ResponseEntity<JsonNode> put(UUID bookId, UUID chapterId, int position) {
		return http.put().uri("/api/v1/audiobooks/{id}/progress", bookId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("chapterId", chapterId, "positionSeconds", position))
				.retrieve().toEntity(JsonNode.class);
	}
}

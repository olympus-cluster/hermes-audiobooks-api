package dev.olympus.hermes.chapter;

import dev.olympus.hermes.config.ApiLinks;
import dev.olympus.hermes.storage.ObjectResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Chapters")
@RestController
@RequestMapping(ApiLinks.BASE + "/audiobooks/{audiobookId}/chapters")
class ChapterController {

	private final ChapterService chapters;
	private final ObjectResponses responses;

	ChapterController(ChapterService chapters, ObjectResponses responses) {
		this.chapters = chapters;
		this.responses = responses;
	}

	@Operation(summary = "Upload a chapter",
			description = "Multipart upload; the audio part is streamed to object storage. Position defaults to the next free one.")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<ChapterResponse> upload(@PathVariable UUID audiobookId,
			@RequestPart("file") MultipartFile file,
			@RequestParam @NotBlank @Size(max = 300) String title,
			@RequestParam(required = false) @Min(1) Integer position,
			@RequestParam(required = false) @Min(0) Integer durationSeconds) {
		ChapterResponse created = chapters.upload(audiobookId, title, position, durationSeconds, file);
		return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(created.id()).toUri()).body(created);
	}

	@Operation(summary = "Update chapter metadata")
	@PutMapping("/{chapterId}")
	ChapterResponse update(@PathVariable UUID audiobookId, @PathVariable UUID chapterId,
			@Valid @RequestBody ChapterUpdateRequest request) {
		return chapters.update(audiobookId, chapterId, request);
	}

	@Operation(summary = "Delete a chapter and its audio object")
	@DeleteMapping("/{chapterId}")
	ResponseEntity<Void> delete(@PathVariable UUID audiobookId, @PathVariable UUID chapterId) {
		chapters.delete(audiobookId, chapterId);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Stream chapter audio",
			description = "Supports single byte ranges (bytes=a-b, bytes=a-, bytes=-n): 206 with Content-Range, 416 if unsatisfiable.")
	@RequestMapping(path = "/{chapterId}/audio", method = { RequestMethod.GET, RequestMethod.HEAD })
	ResponseEntity<StreamingResponseBody> audio(@PathVariable UUID audiobookId, @PathVariable UUID chapterId,
			@RequestHeader(name = HttpHeaders.RANGE, required = false) String range, HttpMethod method) {
		ChapterService.AudioObject audio = chapters.audio(audiobookId, chapterId);
		return responses.ranged(audio.key(), audio.contentType(), audio.sizeBytes(), range,
				HttpMethod.HEAD.equals(method));
	}
}

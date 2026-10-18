package dev.olympus.hermes.chapter;

import dev.olympus.hermes.config.ApiErrors;
import dev.olympus.hermes.config.ApiLinks;
import dev.olympus.hermes.storage.ObjectResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

	/** A chapter's audio never changes under its URL (uploading again creates a new chapter id). */
	static final CacheControl AUDIO_CACHE = CacheControl.maxAge(Duration.ofDays(365)).immutable();

	private static final String AUDIO_DESCRIPTION = """
			Supports single byte ranges (`bytes=a-b`, `bytes=a-`, `bytes=-n`): 206 with Content-Range, 416 if \
			unsatisfiable. Conditional requests: If-None-Match / If-Modified-Since answer 304 when the cached copy \
			is current; with If-Range (entity tag or date) a range request for a changed object gets the full body \
			(200). Responses carry ETag, Last-Modified and `Cache-Control: max-age=31536000, immutable`.""";

	private final ChapterService chapters;
	private final ObjectResponses responses;

	ChapterController(ChapterService chapters, ObjectResponses responses) {
		this.chapters = chapters;
		this.responses = responses;
	}

	@Operation(operationId = "uploadChapter", summary = "Upload a chapter",
			description = "Multipart upload; the audio part is streamed to object storage. Position defaults to the next free one.")
	@ApiResponse(responseCode = "201", description = "Created; Location points to the new chapter",
			headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new chapter",
					schema = @Schema(type = "string", format = "uri")),
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ChapterResponse.class)))
	@ApiResponse(responseCode = "400", ref = ApiErrors.BAD_REQUEST_REF)
	@ApiResponse(responseCode = "404", ref = ApiErrors.NOT_FOUND_REF)
	@ApiResponse(responseCode = "409", ref = ApiErrors.CONFLICT_REF)
	@ApiResponse(responseCode = "413", ref = ApiErrors.CONTENT_TOO_LARGE_REF)
	@ApiResponse(responseCode = "415", ref = ApiErrors.UNSUPPORTED_MEDIA_TYPE_REF)
	@ApiResponse(responseCode = "502", ref = ApiErrors.BAD_GATEWAY_REF)
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

	@Operation(operationId = "replaceChapter", summary = "Replace chapter metadata",
			description = "Replaces title and position. `durationSeconds` describes the audio, so omitting it keeps "
					+ "the stored duration. Use PATCH to change single fields.")
	@ApiResponse(responseCode = "200", description = "Updated chapter")
	@ApiResponse(responseCode = "400", ref = ApiErrors.BAD_REQUEST_REF)
	@ApiResponse(responseCode = "404", ref = ApiErrors.NOT_FOUND_REF)
	@ApiResponse(responseCode = "409", ref = ApiErrors.CONFLICT_REF)
	@ApiResponse(responseCode = "415", ref = ApiErrors.UNSUPPORTED_MEDIA_TYPE_REF)
	@PutMapping(path = "/{chapterId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	ChapterResponse update(@PathVariable UUID audiobookId, @PathVariable UUID chapterId,
			@Valid @RequestBody ChapterUpdateRequest request) {
		return chapters.update(audiobookId, chapterId, request);
	}

	@Operation(operationId = "patchChapter", summary = "Partially update chapter metadata",
			description = "Only fields present and non-null in the body change; the others keep their stored value.")
	@ApiResponse(responseCode = "200", description = "Updated chapter")
	@ApiResponse(responseCode = "400", ref = ApiErrors.BAD_REQUEST_REF)
	@ApiResponse(responseCode = "404", ref = ApiErrors.NOT_FOUND_REF)
	@ApiResponse(responseCode = "409", ref = ApiErrors.CONFLICT_REF)
	@ApiResponse(responseCode = "415", ref = ApiErrors.UNSUPPORTED_MEDIA_TYPE_REF)
	@PatchMapping(path = "/{chapterId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	ChapterResponse patch(@PathVariable UUID audiobookId, @PathVariable UUID chapterId,
			@Valid @RequestBody ChapterPatchRequest request) {
		return chapters.patch(audiobookId, chapterId, request);
	}

	@Operation(operationId = "deleteChapter", summary = "Delete a chapter and its audio object")
	@ApiResponse(responseCode = "204", description = "Deleted")
	@ApiResponse(responseCode = "404", ref = ApiErrors.NOT_FOUND_REF)
	@DeleteMapping("/{chapterId}")
	ResponseEntity<Void> delete(@PathVariable UUID audiobookId, @PathVariable UUID chapterId) {
		chapters.delete(audiobookId, chapterId);
		return ResponseEntity.noContent().build();
	}

	@Operation(operationId = "streamChapterAudio", summary = "Stream chapter audio", description = AUDIO_DESCRIPTION)
	@Parameters({
			@Parameter(in = ParameterIn.HEADER, name = HttpHeaders.RANGE, schema = @Schema(type = "string"),
					description = "Single byte range, e.g. `bytes=0-`"),
			@Parameter(in = ParameterIn.HEADER, name = HttpHeaders.IF_RANGE, schema = @Schema(type = "string"),
					description = "ETag or Last-Modified of the partially cached copy"),
			@Parameter(in = ParameterIn.HEADER, name = HttpHeaders.IF_NONE_MATCH, schema = @Schema(type = "string")),
			@Parameter(in = ParameterIn.HEADER, name = HttpHeaders.IF_MODIFIED_SINCE, schema = @Schema(type = "string")) })
	@ApiResponse(responseCode = "200", description = "Full audio",
			headers = { @Header(name = HttpHeaders.ETAG, schema = @Schema(type = "string")),
					@Header(name = HttpHeaders.LAST_MODIFIED, schema = @Schema(type = "string")),
					@Header(name = HttpHeaders.CACHE_CONTROL, schema = @Schema(type = "string")),
					@Header(name = HttpHeaders.ACCEPT_RANGES, schema = @Schema(type = "string", example = "bytes")) },
			content = @Content(mediaType = "audio/*", schema = @Schema(type = "string", format = "binary")))
	@ApiResponse(responseCode = "206", description = "The requested byte range",
			headers = { @Header(name = HttpHeaders.CONTENT_RANGE,
					schema = @Schema(type = "string", example = "bytes 0-1023/52428800")),
					@Header(name = HttpHeaders.ETAG, schema = @Schema(type = "string")),
					@Header(name = HttpHeaders.LAST_MODIFIED, schema = @Schema(type = "string")),
					@Header(name = HttpHeaders.CACHE_CONTROL, schema = @Schema(type = "string")) },
			content = @Content(mediaType = "audio/*", schema = @Schema(type = "string", format = "binary")))
	@ApiResponse(responseCode = "304", description = "The cached copy is current (no body)",
			headers = { @Header(name = HttpHeaders.ETAG, schema = @Schema(type = "string")),
					@Header(name = HttpHeaders.CACHE_CONTROL, schema = @Schema(type = "string")) })
	@ApiResponse(responseCode = "404", ref = ApiErrors.NOT_FOUND_REF)
	@ApiResponse(responseCode = "416", ref = ApiErrors.RANGE_NOT_SATISFIABLE_REF)
	@ApiResponse(responseCode = "502", ref = ApiErrors.BAD_GATEWAY_REF)
	@GetMapping("/{chapterId}/audio")
	ResponseEntity<StreamingResponseBody> audio(@PathVariable UUID audiobookId, @PathVariable UUID chapterId,
			@Parameter(hidden = true) @RequestHeader HttpHeaders headers) {
		return stream(audiobookId, chapterId, headers, false);
	}

	@Operation(operationId = "headChapterAudio", summary = "Chapter audio headers only",
			description = "Same status and headers as GET without a body; storage is not read.")
	@Parameters({
			@Parameter(in = ParameterIn.HEADER, name = HttpHeaders.RANGE, schema = @Schema(type = "string")),
			@Parameter(in = ParameterIn.HEADER, name = HttpHeaders.IF_RANGE, schema = @Schema(type = "string")),
			@Parameter(in = ParameterIn.HEADER, name = HttpHeaders.IF_NONE_MATCH, schema = @Schema(type = "string")),
			@Parameter(in = ParameterIn.HEADER, name = HttpHeaders.IF_MODIFIED_SINCE, schema = @Schema(type = "string")) })
	@ApiResponse(responseCode = "200", description = "Headers of the full audio")
	@ApiResponse(responseCode = "206", description = "Headers of the requested byte range")
	@ApiResponse(responseCode = "304", description = "The cached copy is current")
	@ApiResponse(responseCode = "404", description = "The audiobook or chapter does not exist")
	@ApiResponse(responseCode = "416", description = "The byte range cannot be satisfied")
	@RequestMapping(path = "/{chapterId}/audio", method = RequestMethod.HEAD)
	ResponseEntity<StreamingResponseBody> audioHead(@PathVariable UUID audiobookId, @PathVariable UUID chapterId,
			@Parameter(hidden = true) @RequestHeader HttpHeaders headers) {
		return stream(audiobookId, chapterId, headers, true);
	}

	private ResponseEntity<StreamingResponseBody> stream(UUID audiobookId, UUID chapterId, HttpHeaders headers,
			boolean headOnly) {
		ChapterService.AudioObject audio = chapters.audio(audiobookId, chapterId);
		return responses.ranged(audio.key(), audio.contentType(), audio.sizeBytes(), audio.version(), AUDIO_CACHE,
				headers, headOnly);
	}
}

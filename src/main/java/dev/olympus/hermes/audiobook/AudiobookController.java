package dev.olympus.hermes.audiobook;

import dev.olympus.hermes.audiobook.AudiobookDtos.AudiobookDetail;
import dev.olympus.hermes.audiobook.AudiobookDtos.AudiobookPatch;
import dev.olympus.hermes.audiobook.AudiobookDtos.AudiobookRequest;
import dev.olympus.hermes.audiobook.AudiobookDtos.AudiobookSummary;
import dev.olympus.hermes.audiobook.AudiobookDtos.PageResponse;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Audiobooks")
@RestController
@RequestMapping(ApiLinks.BASE + "/audiobooks")
class AudiobookController {

	private static final Sort SORT = Sort.by(Sort.Order.asc("title").ignoreCase(), Sort.Order.asc("id"));

	/** The versioned cover URL ({@code ?v=} = current version) never changes content. */
	static final CacheControl VERSIONED_COVER_CACHE = CacheControl.maxAge(Duration.ofDays(365)).immutable();

	/** Unversioned or outdated cover URLs: cacheable, but revalidated (ETag/Last-Modified) before each use. */
	static final CacheControl COVER_CACHE = CacheControl.noCache();

	private final AudiobookService audiobooks;
	private final ObjectResponses responses;

	AudiobookController(AudiobookService audiobooks, ObjectResponses responses) {
		this.audiobooks = audiobooks;
		this.responses = responses;
	}

	@Operation(operationId = "listAudiobooks", summary = "List audiobooks",
			description = "Case-insensitive search on title and author, sorted by title.")
	@ApiResponse(responseCode = "200", description = "One page of audiobooks")
	@ApiResponse(responseCode = "400", ref = ApiErrors.BAD_REQUEST_REF)
	@GetMapping
	PageResponse<AudiobookSummary> list(
			@RequestParam(required = false) @Size(max = 200) String q,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return audiobooks.list(q, PageRequest.of(page, size, SORT));
	}

	@Operation(operationId = "createAudiobook", summary = "Create an audiobook")
	@ApiResponse(responseCode = "201", description = "Created; Location points to the new audiobook",
			headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new audiobook",
					schema = @Schema(type = "string", format = "uri")),
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = AudiobookDetail.class)))
	@ApiResponse(responseCode = "400", ref = ApiErrors.BAD_REQUEST_REF)
	@ApiResponse(responseCode = "415", ref = ApiErrors.UNSUPPORTED_MEDIA_TYPE_REF)
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<AudiobookDetail> create(@Valid @RequestBody AudiobookRequest request) {
		AudiobookDetail created = audiobooks.create(request);
		return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(created.id()).toUri()).body(created);
	}

	@Operation(operationId = "getAudiobook", summary = "Get an audiobook with its chapters")
	@ApiResponse(responseCode = "200", description = "The audiobook")
	@ApiResponse(responseCode = "404", ref = ApiErrors.NOT_FOUND_REF)
	@GetMapping("/{id}")
	AudiobookDetail get(@PathVariable UUID id) {
		return audiobooks.get(id);
	}

	@Operation(operationId = "replaceAudiobook", summary = "Replace audiobook metadata",
			description = "Full replacement: optional fields that are omitted or null are cleared. "
					+ "Use PATCH to change single fields.")
	@ApiResponse(responseCode = "200", description = "Updated audiobook")
	@ApiResponse(responseCode = "400", ref = ApiErrors.BAD_REQUEST_REF)
	@ApiResponse(responseCode = "404", ref = ApiErrors.NOT_FOUND_REF)
	@ApiResponse(responseCode = "415", ref = ApiErrors.UNSUPPORTED_MEDIA_TYPE_REF)
	@PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	AudiobookDetail update(@PathVariable UUID id, @Valid @RequestBody AudiobookRequest request) {
		return audiobooks.update(id, request);
	}

	@Operation(operationId = "patchAudiobook", summary = "Partially update audiobook metadata",
			description = "Only fields present and non-null in the body change; the others keep their stored value. "
					+ "To clear an optional field, use PUT.")
	@ApiResponse(responseCode = "200", description = "Updated audiobook")
	@ApiResponse(responseCode = "400", ref = ApiErrors.BAD_REQUEST_REF)
	@ApiResponse(responseCode = "404", ref = ApiErrors.NOT_FOUND_REF)
	@ApiResponse(responseCode = "415", ref = ApiErrors.UNSUPPORTED_MEDIA_TYPE_REF)
	@PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	AudiobookDetail patch(@PathVariable UUID id, @Valid @RequestBody AudiobookPatch request) {
		return audiobooks.patch(id, request);
	}

	@Operation(operationId = "deleteAudiobook", summary = "Delete an audiobook, its chapters, progress and stored objects")
	@ApiResponse(responseCode = "204", description = "Deleted")
	@ApiResponse(responseCode = "404", ref = ApiErrors.NOT_FOUND_REF)
	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		audiobooks.delete(id);
		return ResponseEntity.noContent().build();
	}

	@Operation(operationId = "putCover", summary = "Upload or replace the cover image (image/jpeg, image/png, image/webp)",
			description = "Changes the `v` parameter of the audiobook's coverUrl.")
	@ApiResponse(responseCode = "204", description = "Stored")
	@ApiResponse(responseCode = "400", ref = ApiErrors.BAD_REQUEST_REF)
	@ApiResponse(responseCode = "404", ref = ApiErrors.NOT_FOUND_REF)
	@ApiResponse(responseCode = "413", ref = ApiErrors.CONTENT_TOO_LARGE_REF)
	@ApiResponse(responseCode = "415", ref = ApiErrors.UNSUPPORTED_MEDIA_TYPE_REF)
	@ApiResponse(responseCode = "502", ref = ApiErrors.BAD_GATEWAY_REF)
	@PutMapping(path = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<Void> putCover(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
		audiobooks.putCover(id, file);
		return ResponseEntity.noContent().build();
	}

	@Operation(operationId = "getCover", summary = "Get the cover image",
			description = "Use the audiobook's coverUrl: with the current `v` the response is cacheable for a year "
					+ "(`max-age=31536000, immutable`); otherwise `no-cache`. ETag/Last-Modified allow revalidation "
					+ "(If-None-Match / If-Modified-Since answer 304).")
	@Parameters({
			@Parameter(in = ParameterIn.HEADER, name = HttpHeaders.IF_NONE_MATCH, schema = @Schema(type = "string")),
			@Parameter(in = ParameterIn.HEADER, name = HttpHeaders.IF_MODIFIED_SINCE, schema = @Schema(type = "string")) })
	@ApiResponse(responseCode = "200", description = "The image",
			headers = { @Header(name = HttpHeaders.ETAG, schema = @Schema(type = "string")),
					@Header(name = HttpHeaders.LAST_MODIFIED, schema = @Schema(type = "string")),
					@Header(name = HttpHeaders.CACHE_CONTROL, schema = @Schema(type = "string")) },
			content = @Content(mediaType = "image/*", schema = @Schema(type = "string", format = "binary")))
	@ApiResponse(responseCode = "304", description = "The cached copy is current (no body)",
			headers = { @Header(name = HttpHeaders.ETAG, schema = @Schema(type = "string")),
					@Header(name = HttpHeaders.CACHE_CONTROL, schema = @Schema(type = "string")) })
	@ApiResponse(responseCode = "404", ref = ApiErrors.NOT_FOUND_REF)
	@ApiResponse(responseCode = "502", ref = ApiErrors.BAD_GATEWAY_REF)
	@GetMapping("/{id}/cover")
	ResponseEntity<StreamingResponseBody> getCover(@PathVariable UUID id,
			@Parameter(description = "Cover version from coverUrl (cache key only)")
			@RequestParam(name = "v", required = false) String version,
			@Parameter(hidden = true) @RequestHeader HttpHeaders headers) {
		AudiobookService.CoverObject cover = audiobooks.cover(id);
		CacheControl cache = cover.version().tag().equals(version) ? VERSIONED_COVER_CACHE : COVER_CACHE;
		return responses.full(cover.key(), cover.contentType(), cover.version(), cache, headers);
	}
}

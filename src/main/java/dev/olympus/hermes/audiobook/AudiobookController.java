package dev.olympus.hermes.audiobook;

import dev.olympus.hermes.audiobook.AudiobookDtos.AudiobookDetail;
import dev.olympus.hermes.audiobook.AudiobookDtos.AudiobookRequest;
import dev.olympus.hermes.audiobook.AudiobookDtos.AudiobookSummary;
import dev.olympus.hermes.audiobook.AudiobookDtos.PageResponse;
import dev.olympus.hermes.config.ApiLinks;
import dev.olympus.hermes.storage.ObjectResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

	private final AudiobookService audiobooks;
	private final ObjectResponses responses;

	AudiobookController(AudiobookService audiobooks, ObjectResponses responses) {
		this.audiobooks = audiobooks;
		this.responses = responses;
	}

	@Operation(summary = "List audiobooks", description = "Case-insensitive search on title and author, sorted by title.")
	@GetMapping
	PageResponse<AudiobookSummary> list(
			@RequestParam(required = false) @Size(max = 200) String q,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return audiobooks.list(q, PageRequest.of(page, size, SORT));
	}

	@Operation(summary = "Create an audiobook")
	@PostMapping
	ResponseEntity<AudiobookDetail> create(@Valid @RequestBody AudiobookRequest request) {
		AudiobookDetail created = audiobooks.create(request);
		return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(created.id()).toUri()).body(created);
	}

	@Operation(summary = "Get an audiobook with its chapters")
	@GetMapping("/{id}")
	AudiobookDetail get(@PathVariable UUID id) {
		return audiobooks.get(id);
	}

	@Operation(summary = "Replace audiobook metadata")
	@PutMapping("/{id}")
	AudiobookDetail update(@PathVariable UUID id, @Valid @RequestBody AudiobookRequest request) {
		return audiobooks.update(id, request);
	}

	@Operation(summary = "Delete an audiobook, its chapters, progress and stored objects")
	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		audiobooks.delete(id);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Upload or replace the cover image (image/jpeg, image/png, image/webp)")
	@PutMapping(path = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<Void> putCover(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
		audiobooks.putCover(id, file);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Get the cover image")
	@GetMapping("/{id}/cover")
	ResponseEntity<StreamingResponseBody> getCover(@PathVariable UUID id) {
		AudiobookService.CoverObject cover = audiobooks.cover(id);
		return responses.full(cover.key(), cover.contentType());
	}
}

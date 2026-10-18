package dev.olympus.hermes.progress;

import dev.olympus.hermes.config.ApiErrors;
import dev.olympus.hermes.config.ApiLinks;
import dev.olympus.hermes.progress.ProgressDtos.ProgressRequest;
import dev.olympus.hermes.progress.ProgressDtos.ProgressResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Listening progress")
@RestController
@RequestMapping(ApiLinks.BASE + "/audiobooks/{audiobookId}/progress")
class ProgressController {

	private final ProgressService progress;

	ProgressController(ProgressService progress) {
		this.progress = progress;
	}

	@Operation(operationId = "getProgress", summary = "Get the saved listening position")
	@ApiResponse(responseCode = "200", description = "The saved position")
	@ApiResponse(responseCode = "204", description = "Nothing saved yet for this audiobook")
	@ApiResponse(responseCode = "404", ref = ApiErrors.NOT_FOUND_REF)
	@GetMapping
	ResponseEntity<ProgressResponse> get(@PathVariable UUID audiobookId) {
		return progress.get(audiobookId)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().build());
	}

	@Operation(operationId = "saveProgress", summary = "Save the listening position (upsert)",
			description = "positionSeconds must not exceed the chapter's durationSeconds when that is known.")
	@ApiResponse(responseCode = "200", description = "The saved position")
	@ApiResponse(responseCode = "400", ref = ApiErrors.BAD_REQUEST_REF)
	@ApiResponse(responseCode = "404", ref = ApiErrors.NOT_FOUND_REF)
	@ApiResponse(responseCode = "415", ref = ApiErrors.UNSUPPORTED_MEDIA_TYPE_REF)
	@PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	ProgressResponse put(@PathVariable UUID audiobookId, @Valid @RequestBody ProgressRequest request) {
		return progress.upsert(audiobookId, request);
	}
}

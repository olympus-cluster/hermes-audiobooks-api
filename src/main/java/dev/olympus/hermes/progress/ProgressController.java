package dev.olympus.hermes.progress;

import dev.olympus.hermes.config.ApiLinks;
import dev.olympus.hermes.progress.ProgressDtos.ProgressRequest;
import dev.olympus.hermes.progress.ProgressDtos.ProgressResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
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

	@Operation(summary = "Get the saved listening position (404 if none)")
	@GetMapping
	ProgressResponse get(@PathVariable UUID audiobookId) {
		return progress.get(audiobookId);
	}

	@Operation(summary = "Save the listening position (upsert)")
	@PutMapping
	ProgressResponse put(@PathVariable UUID audiobookId, @Valid @RequestBody ProgressRequest request) {
		return progress.upsert(audiobookId, request);
	}
}

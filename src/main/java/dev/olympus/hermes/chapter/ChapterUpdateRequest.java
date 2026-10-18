package dev.olympus.hermes.chapter;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /audiobooks/{id}/chapters/{chapterId}} (full replacement of the chapter metadata). */
public record ChapterUpdateRequest(
		@NotBlank @Size(max = 300) String title,
		@NotNull @Min(1) Integer position,
		@Min(0) Integer durationSeconds) {
}

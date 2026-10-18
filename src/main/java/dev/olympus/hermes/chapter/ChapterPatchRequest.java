package dev.olympus.hermes.chapter;

import dev.olympus.hermes.config.Validation;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /audiobooks/{id}/chapters/{chapterId}}: partial update. Absent or null fields keep their
 * stored value; present fields are validated like in {@link ChapterUpdateRequest}.
 */
public record ChapterPatchRequest(
		@Size(max = 300) @Pattern(regexp = Validation.NOT_BLANK, message = Validation.NOT_BLANK_MESSAGE) String title,
		@Min(1) Integer position,
		@Min(0) Integer durationSeconds) {
}

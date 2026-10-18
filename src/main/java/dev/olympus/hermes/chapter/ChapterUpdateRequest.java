package dev.olympus.hermes.chapter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /audiobooks/{id}/chapters/{chapterId}}: replaces title and position. {@code durationSeconds}
 * describes the (immutable) audio rather than editable metadata, so omitting it keeps the stored value.
 */
public record ChapterUpdateRequest(
		@NotBlank @Size(max = 300) String title,
		@NotNull @Min(1) Integer position,
		@Schema(description = "Duration of the audio in seconds; omit (or send null) to keep the stored value")
		@Min(0) Integer durationSeconds) {
}

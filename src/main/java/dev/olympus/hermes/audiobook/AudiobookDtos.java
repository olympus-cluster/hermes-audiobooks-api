package dev.olympus.hermes.audiobook;

import dev.olympus.hermes.chapter.ChapterResponse;
import dev.olympus.hermes.config.Validation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response contracts for the audiobook resource. */
public final class AudiobookDtos {

	private static final String LANGUAGE = "^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$";
	private static final String LANGUAGE_MESSAGE = "must be a BCP 47 language tag such as 'en' or 'el-GR'";

	private AudiobookDtos() {
	}

	/**
	 * Body of {@code POST /audiobooks} and {@code PUT /audiobooks/{id}}. PUT is a full replacement: optional fields
	 * that are omitted (or null) are cleared. Use {@link AudiobookPatch} to change single fields.
	 */
	public record AudiobookRequest(
			@NotBlank @Size(max = 300) String title,
			@NotBlank @Size(max = 200) String author,
			@Size(max = 200) String narrator,
			@Size(max = 20000) String description,
			@Size(max = 10) @Pattern(regexp = LANGUAGE, message = LANGUAGE_MESSAGE) String language,
			@Min(0) @Max(9999) Integer publishedYear) {
	}

	/** Body of {@code PATCH /audiobooks/{id}}: absent or null fields keep their stored value. */
	public record AudiobookPatch(
			@Size(max = 300) @Pattern(regexp = Validation.NOT_BLANK, message = Validation.NOT_BLANK_MESSAGE) String title,
			@Size(max = 200) @Pattern(regexp = Validation.NOT_BLANK, message = Validation.NOT_BLANK_MESSAGE) String author,
			@Size(max = 200) String narrator,
			@Size(max = 20000) String description,
			@Size(max = 10) @Pattern(regexp = LANGUAGE, message = LANGUAGE_MESSAGE) String language,
			@Min(0) @Max(9999) Integer publishedYear) {
	}

	public record AudiobookSummary(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String author,
			String narrator,
			String language,
			Integer publishedYear,
			String coverUrl,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt) {
	}

	public record AudiobookDetail(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String author,
			String narrator,
			String description,
			String language,
			Integer publishedYear,
			String coverUrl,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalDurationSeconds,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ChapterResponse> chapters,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt) {
	}

	public record PageResponse<T>(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<T> content,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages) {
	}
}

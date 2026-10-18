package dev.olympus.hermes.audiobook;

import dev.olympus.hermes.chapter.ChapterResponse;
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

	private AudiobookDtos() {
	}

	/** Body of {@code POST /audiobooks} and {@code PUT /audiobooks/{id}} (full replacement of the metadata). */
	public record AudiobookRequest(
			@NotBlank @Size(max = 300) String title,
			@NotBlank @Size(max = 200) String author,
			@Size(max = 200) String narrator,
			@Size(max = 20000) String description,
			@Size(max = 10) @Pattern(regexp = "^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$",
					message = "must be a BCP 47 language tag such as 'en' or 'el-GR'") String language,
			@Min(0) @Max(9999) Integer publishedYear) {
	}

	public record AudiobookSummary(
			UUID id,
			String title,
			String author,
			String narrator,
			String language,
			Integer publishedYear,
			String coverUrl,
			Instant createdAt,
			Instant updatedAt) {
	}

	public record AudiobookDetail(
			UUID id,
			String title,
			String author,
			String narrator,
			String description,
			String language,
			Integer publishedYear,
			String coverUrl,
			long totalDurationSeconds,
			List<ChapterResponse> chapters,
			Instant createdAt,
			Instant updatedAt) {
	}

	public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
	}
}

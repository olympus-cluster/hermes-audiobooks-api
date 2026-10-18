package dev.olympus.hermes.progress;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class ProgressDtos {

	private ProgressDtos() {
	}

	public record ProgressRequest(@NotNull UUID chapterId, @NotNull @Min(0) Integer positionSeconds) {
	}

	public record ProgressResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID audiobookId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID chapterId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int positionSeconds,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt) {

		static ProgressResponse of(ListeningProgress p) {
			return new ProgressResponse(p.getAudiobookId(), p.getChapterId(), p.getPositionSeconds(),
					p.getUpdatedAt());
		}
	}
}

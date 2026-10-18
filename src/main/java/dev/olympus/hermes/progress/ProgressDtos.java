package dev.olympus.hermes.progress;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class ProgressDtos {

	private ProgressDtos() {
	}

	public record ProgressRequest(@NotNull UUID chapterId, @NotNull @Min(0) Integer positionSeconds) {
	}

	public record ProgressResponse(UUID audiobookId, UUID chapterId, int positionSeconds, Instant updatedAt) {

		static ProgressResponse of(ListeningProgress p) {
			return new ProgressResponse(p.getAudiobookId(), p.getChapterId(), p.getPositionSeconds(),
					p.getUpdatedAt());
		}
	}
}

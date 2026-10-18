package dev.olympus.hermes.chapter;

import dev.olympus.hermes.config.ApiLinks;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ChapterResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int position,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
		Integer durationSeconds,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String contentType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sizeBytes,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String audioUrl,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {

	public static ChapterResponse of(Chapter c) {
		return new ChapterResponse(c.getId(), c.getPosition(), c.getTitle(), c.getDurationSeconds(),
				c.getContentType(), c.getSizeBytes(), ApiLinks.chapterAudio(c.getAudiobookId(), c.getId()),
				c.getCreatedAt());
	}
}

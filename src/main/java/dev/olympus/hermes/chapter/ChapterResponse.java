package dev.olympus.hermes.chapter;

import dev.olympus.hermes.config.ApiLinks;
import java.time.Instant;
import java.util.UUID;

public record ChapterResponse(
		UUID id,
		int position,
		String title,
		Integer durationSeconds,
		String contentType,
		long sizeBytes,
		String audioUrl,
		Instant createdAt) {

	public static ChapterResponse of(Chapter c) {
		return new ChapterResponse(c.getId(), c.getPosition(), c.getTitle(), c.getDurationSeconds(),
				c.getContentType(), c.getSizeBytes(), ApiLinks.chapterAudio(c.getAudiobookId(), c.getId()),
				c.getCreatedAt());
	}
}

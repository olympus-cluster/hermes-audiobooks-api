package dev.olympus.hermes.progress;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Single-user listening position per audiobook (keyed by audiobook until auth adds a user dimension). */
@Entity
@Table(name = "listening_progress")
public class ListeningProgress {

	@Id
	@Column(name = "audiobook_id")
	private UUID audiobookId;

	@Column(name = "chapter_id", nullable = false)
	private UUID chapterId;

	@Column(name = "position_seconds", nullable = false)
	private int positionSeconds;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ListeningProgress() {
	}

	ListeningProgress(UUID audiobookId) {
		this.audiobookId = audiobookId;
	}

	void record(UUID chapterId, int positionSeconds) {
		this.chapterId = chapterId;
		this.positionSeconds = positionSeconds;
		this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	public UUID getAudiobookId() {
		return audiobookId;
	}

	public UUID getChapterId() {
		return chapterId;
	}

	public int getPositionSeconds() {
		return positionSeconds;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}

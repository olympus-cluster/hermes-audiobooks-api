package dev.olympus.hermes.chapter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "chapter")
public class Chapter implements Persistable<UUID> {

	@Id
	private UUID id;

	@Column(name = "audiobook_id", nullable = false, updatable = false)
	private UUID audiobookId;

	@Column(nullable = false)
	private int position;

	@Column(nullable = false, length = 300)
	private String title;

	@Column(name = "duration_seconds")
	private Integer durationSeconds;

	@Column(name = "audio_key", nullable = false, length = 500)
	private String audioKey;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Transient
	private boolean isNew = true;

	protected Chapter() {
	}

	Chapter(UUID id, UUID audiobookId, int position, String title, Integer durationSeconds, String audioKey,
			String contentType, long sizeBytes) {
		this.id = id;
		this.audiobookId = audiobookId;
		this.position = position;
		this.title = title;
		this.durationSeconds = durationSeconds;
		this.audioKey = audioKey;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	void update(String title, int position, Integer durationSeconds) {
		this.title = title;
		this.position = position;
		this.durationSeconds = durationSeconds;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		this.isNew = false;
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	public UUID getAudiobookId() {
		return audiobookId;
	}

	public int getPosition() {
		return position;
	}

	public String getTitle() {
		return title;
	}

	public Integer getDurationSeconds() {
		return durationSeconds;
	}

	public String getAudioKey() {
		return audioKey;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}

package dev.olympus.hermes.audiobook;

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
@Table(name = "audiobook")
public class Audiobook implements Persistable<UUID> {

	@Id
	private UUID id;

	@Column(nullable = false, length = 300)
	private String title;

	@Column(nullable = false, length = 200)
	private String author;

	@Column(length = 200)
	private String narrator;

	@Column(columnDefinition = "text")
	private String description;

	@Column(length = 10)
	private String language;

	@Column(name = "published_year")
	private Integer publishedYear;

	@Column(name = "cover_key", length = 500)
	private String coverKey;

	@Column(name = "cover_content_type", length = 100)
	private String coverContentType;

	@Column(name = "cover_updated_at")
	private Instant coverUpdatedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Transient
	private boolean isNew = true;

	protected Audiobook() {
	}

	public Audiobook(String title, String author, String narrator, String description, String language,
			Integer publishedYear) {
		this.id = UUID.randomUUID();
		this.createdAt = now();
		this.updatedAt = this.createdAt;
		update(title, author, narrator, description, language, publishedYear);
	}

	public void update(String title, String author, String narrator, String description, String language,
			Integer publishedYear) {
		this.title = title;
		this.author = author;
		this.narrator = narrator;
		this.description = description;
		this.language = language;
		this.publishedYear = publishedYear;
		touch();
	}

	public void setCover(String key, String contentType) {
		this.coverKey = key;
		this.coverContentType = contentType;
		touch();
		this.coverUpdatedAt = this.updatedAt;
	}

	public void touch() {
		this.updatedAt = now();
	}

	private static Instant now() {
		// PostgreSQL stores microseconds; truncate so the returned value equals the stored one
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
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

	public String getTitle() {
		return title;
	}

	public String getAuthor() {
		return author;
	}

	public String getNarrator() {
		return narrator;
	}

	public String getDescription() {
		return description;
	}

	public String getLanguage() {
		return language;
	}

	public Integer getPublishedYear() {
		return publishedYear;
	}

	public String getCoverKey() {
		return coverKey;
	}

	public String getCoverContentType() {
		return coverContentType;
	}

	/** When the cover bytes last changed ({@code null} without a cover). */
	public Instant getCoverUpdatedAt() {
		return coverUpdatedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}

package dev.olympus.hermes.chapter;

import dev.olympus.hermes.audiobook.AudiobookRepository;
import dev.olympus.hermes.error.BadRequestException;
import dev.olympus.hermes.error.ConflictException;
import dev.olympus.hermes.error.NotFoundException;
import dev.olympus.hermes.storage.MediaTypes;
import dev.olympus.hermes.storage.ObjectStorage;
import dev.olympus.hermes.storage.StorageKeys;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ChapterService {

	private final ChapterRepository chapters;
	private final AudiobookRepository audiobooks;
	private final ObjectStorage storage;
	private final TransactionTemplate tx;

	ChapterService(ChapterRepository chapters, AudiobookRepository audiobooks, ObjectStorage storage,
			TransactionTemplate tx) {
		this.chapters = chapters;
		this.audiobooks = audiobooks;
		this.storage = storage;
		this.tx = tx;
	}

	/**
	 * Streams the part to S3 first (no DB transaction held during the upload), then inserts the row. If the insert
	 * fails, the uploaded object is removed again.
	 */
	public ChapterResponse upload(UUID audiobookId, String title, Integer position, Integer durationSeconds,
			MultipartFile file) {
		requireAudiobook(audiobookId);
		String contentType = MediaTypes.require(file, MediaTypes.AUDIO);
		if (file.isEmpty()) {
			throw new BadRequestException("file must not be empty");
		}
		if (position != null && chapters.existsByAudiobookIdAndPosition(audiobookId, position)) {
			throw positionTaken(position);
		}
		UUID chapterId = UUID.randomUUID();
		String key = StorageKeys.chapter(audiobookId, chapterId);
		long size = file.getSize();
		try (InputStream in = file.getInputStream()) {
			storage.put(key, in, size, contentType);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read uploaded part", ex);
		}
		try {
			Chapter saved = tx.execute(status -> {
				requireAudiobook(audiobookId);
				int pos = position != null ? position : chapters.maxPosition(audiobookId) + 1;
				return chapters.saveAndFlush(
						new Chapter(chapterId, audiobookId, pos, title.strip(), durationSeconds, key, contentType, size));
			});
			return ChapterResponse.of(saved);
		}
		catch (RuntimeException ex) {
			storage.deleteQuietly(key);
			throw ex;
		}
	}

	@Transactional
	public ChapterResponse update(UUID audiobookId, UUID chapterId, ChapterUpdateRequest req) {
		Chapter chapter = require(audiobookId, chapterId);
		if (chapter.getPosition() != req.position()
				&& chapters.existsByAudiobookIdAndPositionAndIdNot(audiobookId, req.position(), chapterId)) {
			throw positionTaken(req.position());
		}
		chapter.update(req.title().strip(), req.position(), req.durationSeconds());
		return ChapterResponse.of(chapters.saveAndFlush(chapter));
	}

	public void delete(UUID audiobookId, UUID chapterId) {
		String key = tx.execute(status -> {
			Chapter chapter = require(audiobookId, chapterId);
			chapters.delete(chapter);
			return chapter.getAudioKey();
		});
		storage.deleteQuietly(key);
	}

	/** What the streaming endpoint needs to proxy the audio object. */
	public record AudioObject(String key, String contentType, long sizeBytes) {
	}

	@Transactional(readOnly = true)
	public AudioObject audio(UUID audiobookId, UUID chapterId) {
		Chapter c = require(audiobookId, chapterId);
		return new AudioObject(c.getAudioKey(), c.getContentType(), c.getSizeBytes());
	}

	private Chapter require(UUID audiobookId, UUID chapterId) {
		return chapters.findByIdAndAudiobookId(chapterId, audiobookId)
				.orElseThrow(() -> NotFoundException.chapter(audiobookId, chapterId));
	}

	private void requireAudiobook(UUID audiobookId) {
		if (!audiobooks.existsById(audiobookId)) {
			throw NotFoundException.audiobook(audiobookId);
		}
	}

	private static ConflictException positionTaken(int position) {
		return new ConflictException("Position " + position + " is already used by another chapter");
	}
}

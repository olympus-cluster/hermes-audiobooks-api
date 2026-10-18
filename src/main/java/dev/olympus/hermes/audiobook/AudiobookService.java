package dev.olympus.hermes.audiobook;

import dev.olympus.hermes.audiobook.AudiobookDtos.AudiobookDetail;
import dev.olympus.hermes.audiobook.AudiobookDtos.AudiobookPatch;
import dev.olympus.hermes.audiobook.AudiobookDtos.AudiobookRequest;
import dev.olympus.hermes.audiobook.AudiobookDtos.AudiobookSummary;
import dev.olympus.hermes.audiobook.AudiobookDtos.PageResponse;
import dev.olympus.hermes.chapter.ChapterRepository;
import dev.olympus.hermes.chapter.ChapterResponse;
import dev.olympus.hermes.config.ApiLinks;
import dev.olympus.hermes.error.BadRequestException;
import dev.olympus.hermes.error.NotFoundException;
import dev.olympus.hermes.storage.MediaTypes;
import dev.olympus.hermes.storage.ObjectStorage;
import dev.olympus.hermes.storage.ObjectVersion;
import dev.olympus.hermes.storage.StorageKeys;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AudiobookService {

	private final AudiobookRepository audiobooks;
	private final ChapterRepository chapters;
	private final ObjectStorage storage;
	private final TransactionTemplate tx;

	AudiobookService(AudiobookRepository audiobooks, ChapterRepository chapters, ObjectStorage storage,
			TransactionTemplate tx) {
		this.audiobooks = audiobooks;
		this.chapters = chapters;
		this.storage = storage;
		this.tx = tx;
	}

	@Transactional(readOnly = true)
	public PageResponse<AudiobookSummary> list(String query, Pageable pageable) {
		Page<Audiobook> page = (query == null || query.isBlank())
				? audiobooks.findAll(pageable)
				: audiobooks.search(likePattern(query), pageable);
		return new PageResponse<>(page.getContent().stream().map(AudiobookService::summary).toList(),
				page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
	}

	@Transactional
	public AudiobookDetail create(AudiobookRequest req) {
		Audiobook book = new Audiobook(req.title().strip(), req.author().strip(), trim(req.narrator()),
				req.description(), trim(req.language()), req.publishedYear());
		return detail(audiobooks.save(book), List.of());
	}

	@Transactional(readOnly = true)
	public AudiobookDetail get(UUID id) {
		Audiobook book = require(id);
		return detail(book, chapters.findByAudiobookIdOrderByPositionAsc(id).stream().map(ChapterResponse::of).toList());
	}

	/** PUT: full replacement; optional fields missing from the request are cleared. */
	@Transactional
	public AudiobookDetail update(UUID id, AudiobookRequest req) {
		Audiobook book = require(id);
		book.update(req.title().strip(), req.author().strip(), trim(req.narrator()), req.description(),
				trim(req.language()), req.publishedYear());
		return detail(book, chapters.findByAudiobookIdOrderByPositionAsc(id).stream().map(ChapterResponse::of).toList());
	}

	/** PATCH: only non-null fields are applied; the others keep their stored value. */
	@Transactional
	public AudiobookDetail patch(UUID id, AudiobookPatch req) {
		Audiobook book = require(id);
		book.update(
				req.title() != null ? req.title().strip() : book.getTitle(),
				req.author() != null ? req.author().strip() : book.getAuthor(),
				req.narrator() != null ? trim(req.narrator()) : book.getNarrator(),
				req.description() != null ? req.description() : book.getDescription(),
				req.language() != null ? trim(req.language()) : book.getLanguage(),
				req.publishedYear() != null ? req.publishedYear() : book.getPublishedYear());
		return detail(book, chapters.findByAudiobookIdOrderByPositionAsc(id).stream().map(ChapterResponse::of).toList());
	}

	/** Deletes the rows (chapters and progress cascade), then the S3 objects once the transaction has committed. */
	public void delete(UUID id) {
		List<String> keys = tx.execute(status -> {
			Audiobook book = require(id);
			List<String> objectKeys = new ArrayList<>(chapters.findAudioKeys(id));
			if (book.getCoverKey() != null) {
				objectKeys.add(book.getCoverKey());
			}
			audiobooks.delete(book);
			return objectKeys;
		});
		storage.deleteQuietly(Objects.requireNonNull(keys));
	}

	/** Streams the image to S3 (overwriting any previous cover), then records it. */
	public void putCover(UUID id, MultipartFile file) {
		require(id);
		String contentType = MediaTypes.require(file, MediaTypes.COVER);
		if (file.isEmpty()) {
			throw new BadRequestException("file must not be empty");
		}
		String key = StorageKeys.cover(id);
		try (InputStream in = file.getInputStream()) {
			storage.put(key, in, file.getSize(), contentType);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read uploaded part", ex);
		}
		tx.executeWithoutResult(status -> require(id).setCover(key, contentType));
	}

	/** {@code version.tag()} is also the {@code v} query parameter of the cover URL. */
	public record CoverObject(String key, String contentType, ObjectVersion version) {
	}

	@Transactional(readOnly = true)
	public CoverObject cover(UUID id) {
		Audiobook book = require(id);
		if (book.getCoverKey() == null) {
			throw new NotFoundException("Audiobook " + id + " has no cover");
		}
		return new CoverObject(book.getCoverKey(), book.getCoverContentType(), coverVersion(book));
	}

	private Audiobook require(UUID id) {
		return audiobooks.findById(id).orElseThrow(() -> NotFoundException.audiobook(id));
	}

	private static String likePattern(String query) {
		// case folding happens in SQL (see AudiobookRepository.search)
		String escaped = query.strip()
				.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		return "%" + escaped + "%";
	}

	private static String trim(String value) {
		if (value == null) {
			return null;
		}
		String stripped = value.strip();
		return stripped.isEmpty() ? null : stripped;
	}

	private static ObjectVersion coverVersion(Audiobook b) {
		Instant changed = b.getCoverUpdatedAt() != null ? b.getCoverUpdatedAt() : b.getUpdatedAt();
		return new ObjectVersion(Long.toString(changed.toEpochMilli()), changed);
	}

	private static String coverUrl(Audiobook b) {
		return b.getCoverKey() == null ? null : ApiLinks.cover(b.getId(), coverVersion(b).tag());
	}

	private static AudiobookSummary summary(Audiobook b) {
		return new AudiobookSummary(b.getId(), b.getTitle(), b.getAuthor(), b.getNarrator(), b.getLanguage(),
				b.getPublishedYear(), coverUrl(b), b.getCreatedAt(), b.getUpdatedAt());
	}

	private static AudiobookDetail detail(Audiobook b, List<ChapterResponse> chapterList) {
		long total = chapterList.stream()
				.map(ChapterResponse::durationSeconds)
				.filter(Objects::nonNull)
				.mapToLong(Integer::longValue)
				.sum();
		return new AudiobookDetail(b.getId(), b.getTitle(), b.getAuthor(), b.getNarrator(), b.getDescription(),
				b.getLanguage(), b.getPublishedYear(), coverUrl(b), total, chapterList, b.getCreatedAt(),
				b.getUpdatedAt());
	}
}

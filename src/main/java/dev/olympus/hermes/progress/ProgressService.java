package dev.olympus.hermes.progress;

import dev.olympus.hermes.audiobook.AudiobookRepository;
import dev.olympus.hermes.chapter.Chapter;
import dev.olympus.hermes.chapter.ChapterRepository;
import dev.olympus.hermes.error.BadRequestException;
import dev.olympus.hermes.error.NotFoundException;
import dev.olympus.hermes.progress.ProgressDtos.ProgressRequest;
import dev.olympus.hermes.progress.ProgressDtos.ProgressResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProgressService {

	private final ListeningProgressRepository progress;
	private final AudiobookRepository audiobooks;
	private final ChapterRepository chapters;

	ProgressService(ListeningProgressRepository progress, AudiobookRepository audiobooks, ChapterRepository chapters) {
		this.progress = progress;
		this.audiobooks = audiobooks;
		this.chapters = chapters;
	}

	/** Empty when nothing has been saved yet; 404 if the audiobook does not exist. */
	@Transactional(readOnly = true)
	Optional<ProgressResponse> get(UUID audiobookId) {
		requireAudiobook(audiobookId);
		return progress.findById(audiobookId).map(ProgressResponse::of);
	}

	@Transactional
	ProgressResponse upsert(UUID audiobookId, ProgressRequest req) {
		requireAudiobook(audiobookId);
		Chapter chapter = chapters.findByIdAndAudiobookId(req.chapterId(), audiobookId)
				.orElseThrow(() -> new BadRequestException(
						"Chapter " + req.chapterId() + " does not belong to audiobook " + audiobookId));
		Integer duration = chapter.getDurationSeconds();
		if (duration != null && req.positionSeconds() > duration) {
			throw new BadRequestException("positionSeconds " + req.positionSeconds()
					+ " exceeds the chapter duration of " + duration + " seconds");
		}
		ListeningProgress p = progress.findById(audiobookId).orElseGet(() -> new ListeningProgress(audiobookId));
		p.record(req.chapterId(), req.positionSeconds());
		return ProgressResponse.of(progress.saveAndFlush(p));
	}

	private void requireAudiobook(UUID audiobookId) {
		if (!audiobooks.existsById(audiobookId)) {
			throw NotFoundException.audiobook(audiobookId);
		}
	}
}

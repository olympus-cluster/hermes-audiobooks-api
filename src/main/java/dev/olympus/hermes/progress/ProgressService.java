package dev.olympus.hermes.progress;

import dev.olympus.hermes.audiobook.AudiobookRepository;
import dev.olympus.hermes.chapter.ChapterRepository;
import dev.olympus.hermes.error.BadRequestException;
import dev.olympus.hermes.error.NotFoundException;
import dev.olympus.hermes.progress.ProgressDtos.ProgressRequest;
import dev.olympus.hermes.progress.ProgressDtos.ProgressResponse;
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

	@Transactional(readOnly = true)
	ProgressResponse get(UUID audiobookId) {
		return progress.findById(audiobookId)
				.map(ProgressResponse::of)
				.orElseThrow(() -> new NotFoundException("No listening progress for audiobook " + audiobookId));
	}

	@Transactional
	ProgressResponse upsert(UUID audiobookId, ProgressRequest req) {
		if (!audiobooks.existsById(audiobookId)) {
			throw NotFoundException.audiobook(audiobookId);
		}
		if (chapters.findByIdAndAudiobookId(req.chapterId(), audiobookId).isEmpty()) {
			throw new BadRequestException("Chapter " + req.chapterId() + " does not belong to audiobook " + audiobookId);
		}
		ListeningProgress p = progress.findById(audiobookId).orElseGet(() -> new ListeningProgress(audiobookId));
		p.record(req.chapterId(), req.positionSeconds());
		return ProgressResponse.of(progress.saveAndFlush(p));
	}
}

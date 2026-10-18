package dev.olympus.hermes.error;

import java.util.UUID;

public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}

	public static NotFoundException audiobook(UUID id) {
		return new NotFoundException("Audiobook " + id + " not found");
	}

	public static NotFoundException chapter(UUID audiobookId, UUID chapterId) {
		return new NotFoundException("Chapter " + chapterId + " not found in audiobook " + audiobookId);
	}
}

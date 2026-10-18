package dev.olympus.hermes.storage;

import java.util.UUID;

public final class StorageKeys {

	private StorageKeys() {
	}

	public static String cover(UUID audiobookId) {
		return "audiobooks/" + audiobookId + "/cover";
	}

	public static String chapter(UUID audiobookId, UUID chapterId) {
		return "audiobooks/" + audiobookId + "/chapters/" + chapterId;
	}
}

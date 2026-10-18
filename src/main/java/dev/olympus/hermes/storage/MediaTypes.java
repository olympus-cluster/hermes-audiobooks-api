package dev.olympus.hermes.storage;

import dev.olympus.hermes.error.UnsupportedFileTypeException;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/** Accepted upload content types. */
public final class MediaTypes {

	public static final Set<String> COVER = Set.of("image/jpeg", "image/png", "image/webp");

	public static final Set<String> AUDIO = Set.of("audio/mpeg", "audio/mp4", "audio/x-m4a", "audio/aac",
			"audio/ogg", "audio/flac", "audio/wav");

	private MediaTypes() {
	}

	/** Returns the part's normalized content type (lower-case, parameters stripped) or throws 415. */
	public static String require(MultipartFile file, Set<String> allowed) {
		String raw = file.getContentType();
		String normalized = raw == null ? "" : raw.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
		if (!allowed.contains(normalized)) {
			throw new UnsupportedFileTypeException(raw == null ? "(none)" : raw, allowed.stream().sorted().toList());
		}
		return normalized;
	}
}

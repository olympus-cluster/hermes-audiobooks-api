package dev.olympus.hermes.config;

import java.util.UUID;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Absolute links handed to clients. Built from the current request, so they honour {@code X-Forwarded-*} headers
 * set by the ingress ({@code server.forward-headers-strategy=native}).
 */
public final class ApiLinks {

	public static final String BASE = "/api/v1";

	private ApiLinks() {
	}

	/**
	 * Cover link carrying the cover version ({@code ?v=}), so clients that cache images by URL (Coil, Glide) fetch
	 * the new image after the cover is replaced.
	 */
	public static String cover(UUID audiobookId, String version) {
		return ServletUriComponentsBuilder.fromCurrentContextPath()
				.path(BASE + "/audiobooks/{id}/cover").queryParam("v", version)
				.buildAndExpand(audiobookId).toUriString();
	}

	public static String chapterAudio(UUID audiobookId, UUID chapterId) {
		return ServletUriComponentsBuilder.fromCurrentContextPath()
				.path(BASE + "/audiobooks/{id}/chapters/{chapterId}/audio")
				.buildAndExpand(audiobookId, chapterId).toUriString();
	}
}

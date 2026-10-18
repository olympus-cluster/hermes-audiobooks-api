package dev.olympus.hermes.storage;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.ETag;
import org.springframework.http.HttpHeaders;

/**
 * Validators of a stored object as sent to clients: a strong entity tag and the modification time. Evaluates the
 * conditional request headers of RFC 9110 section 13 that matter for GET/HEAD of media.
 *
 * @param tag          unquoted entity tag value; must change whenever the bytes change
 * @param lastModified when the bytes last changed
 */
public record ObjectVersion(String tag, Instant lastModified) {

	public ObjectVersion {
		// HTTP dates have second precision; truncating keeps Last-Modified and the comparisons consistent
		lastModified = lastModified.truncatedTo(ChronoUnit.SECONDS);
	}

	public ETag etag() {
		return new ETag(tag, false);
	}

	/**
	 * {@code true} if the client's cached copy is current (respond 304): If-None-Match matches (weak comparison),
	 * or, only when If-None-Match is absent, the object has not changed since If-Modified-Since.
	 */
	public boolean notModified(HttpHeaders request) {
		List<String> ifNoneMatch = request.get(HttpHeaders.IF_NONE_MATCH);
		if (ifNoneMatch != null && !ifNoneMatch.isEmpty()) {
			return ifNoneMatch.stream()
					.flatMap(value -> ETag.parse(value).stream())
					.anyMatch(candidate -> candidate.isWildcard() || candidate.compare(etag(), false));
		}
		long ifModifiedSince = request.getIfModifiedSince();
		return ifModifiedSince >= 0 && lastModified.toEpochMilli() <= ifModifiedSince;
	}

	/**
	 * {@code true} if a Range header may be honoured: there is no If-Range, or it names this exact version (strong
	 * entity-tag comparison, or an HTTP date equal to Last-Modified). Otherwise the full body must be sent.
	 */
	public boolean rangeApplies(HttpHeaders request) {
		String ifRange = request.getFirst(HttpHeaders.IF_RANGE);
		if (ifRange == null || ifRange.isBlank()) {
			return true;
		}
		String value = ifRange.trim();
		if (value.startsWith("\"") || value.startsWith("W/")) {
			return ETag.create(value).compare(etag(), true);
		}
		try {
			return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().equals(lastModified);
		}
		catch (DateTimeParseException ex) {
			return false;
		}
	}
}

package dev.olympus.hermes.storage;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a single HTTP {@code Range} header (RFC 9110 section 14) against a known object size.
 * <ul>
 * <li>{@code null}/blank, a non-{@code bytes} unit or a multi-range request: empty (serve the full body; RFC 9110
 * allows a server to ignore Range)</li>
 * <li>{@code bytes=a-b}, {@code bytes=a-}, {@code bytes=-n}: resolved range, clamped to the object size</li>
 * <li>malformed or unsatisfiable byte ranges: {@link RangeNotSatisfiableException} (416)</li>
 * </ul>
 */
public final class RangeParser {

	private static final String PREFIX = "bytes=";
	private static final Pattern SINGLE = Pattern.compile("^\\s*(\\d*)\\s*-\\s*(\\d*)\\s*$");

	private RangeParser() {
	}

	public static Optional<ByteRange> parse(String header, long size) {
		if (header == null || header.isBlank()) {
			return Optional.empty();
		}
		String value = header.trim();
		if (!value.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
			return Optional.empty();
		}
		String spec = value.substring(PREFIX.length());
		if (spec.indexOf(',') >= 0) {
			return Optional.empty();
		}
		Matcher m = SINGLE.matcher(spec);
		if (!m.matches() || size <= 0) {
			throw new RangeNotSatisfiableException(header, size);
		}
		String first = m.group(1);
		String last = m.group(2);
		try {
			if (first.isEmpty()) {
				if (last.isEmpty()) {
					throw new RangeNotSatisfiableException(header, size);
				}
				long suffix = Long.parseLong(last);
				if (suffix == 0) {
					throw new RangeNotSatisfiableException(header, size);
				}
				return Optional.of(new ByteRange(Math.max(0, size - suffix), size - 1, size));
			}
			long start = Long.parseLong(first);
			if (start >= size) {
				throw new RangeNotSatisfiableException(header, size);
			}
			if (last.isEmpty()) {
				return Optional.of(new ByteRange(start, size - 1, size));
			}
			long end = Long.parseLong(last);
			if (end < start) {
				throw new RangeNotSatisfiableException(header, size);
			}
			return Optional.of(new ByteRange(start, Math.min(end, size - 1), size));
		}
		catch (NumberFormatException ex) {
			throw new RangeNotSatisfiableException(header, size);
		}
	}
}

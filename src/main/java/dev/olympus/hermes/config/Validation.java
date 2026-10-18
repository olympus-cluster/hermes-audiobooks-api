package dev.olympus.hermes.config;

/** Shared bean-validation constants. */
public final class Validation {

	/** {@code @Pattern} for optional (PATCH) strings: when present they must contain a non-whitespace character. */
	public static final String NOT_BLANK = "(?s).*\\S.*";

	public static final String NOT_BLANK_MESSAGE = "must not be blank";

	private Validation() {
	}
}

package dev.olympus.hermes.error;

import java.util.Collection;

/** The uploaded multipart file has a content type this endpoint does not accept (mapped to 415). */
public class UnsupportedFileTypeException extends RuntimeException {

	private final Collection<String> supported;

	public UnsupportedFileTypeException(String actual, Collection<String> supported) {
		super("Unsupported file content type '" + actual + "'; supported: " + String.join(", ", supported));
		this.supported = supported;
	}

	public Collection<String> supported() {
		return supported;
	}
}

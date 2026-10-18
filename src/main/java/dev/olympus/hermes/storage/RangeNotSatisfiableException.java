package dev.olympus.hermes.storage;

public class RangeNotSatisfiableException extends RuntimeException {

	private final long totalSize;

	public RangeNotSatisfiableException(String range, long totalSize) {
		super("Range '" + range + "' cannot be satisfied for a resource of " + totalSize + " bytes");
		this.totalSize = totalSize;
	}

	public long totalSize() {
		return totalSize;
	}
}

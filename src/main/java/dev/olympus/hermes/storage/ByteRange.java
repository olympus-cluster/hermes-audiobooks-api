package dev.olympus.hermes.storage;

/** An inclusive, already-resolved byte range {@code [start, end]} within an object of {@code totalSize} bytes. */
public record ByteRange(long start, long end, long totalSize) {

	public long length() {
		return end - start + 1;
	}

	public String toS3Header() {
		return "bytes=" + start + "-" + end;
	}

	public String toContentRange() {
		return "bytes " + start + "-" + end + "/" + totalSize;
	}
}

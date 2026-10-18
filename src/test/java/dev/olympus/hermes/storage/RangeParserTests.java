package dev.olympus.hermes.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RangeParserTests {

	@ParameterizedTest
	@CsvSource({
			"bytes=0-9,       0,  9",
			"bytes=10-,       10, 99",
			"bytes=-10,       90, 99",
			"bytes=-1000,     0,  99",
			"bytes=50-1000,   50, 99",
			"BYTES=1-1,       1,  1",
			"bytes= 5 - 6 ,   5,  6" })
	void resolvesSatisfiableRanges(String header, long start, long end) {
		ByteRange range = RangeParser.parse(header, 100).orElseThrow();
		assertThat(range.start()).isEqualTo(start);
		assertThat(range.end()).isEqualTo(end);
		assertThat(range.length()).isEqualTo(end - start + 1);
		assertThat(range.toContentRange()).isEqualTo("bytes " + start + "-" + end + "/100");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "items=0-1", "bytes=0-1,5-6" })
	void ignoresUnsupportedRanges(String header) {
		assertThat(RangeParser.parse(header, 100)).isEmpty();
		assertThat(RangeParser.parse(null, 100)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "bytes=100-", "bytes=5-4", "bytes=-0", "bytes=-", "bytes=x-1", "bytes=99999999999999999999-" })
	void rejectsUnsatisfiableRanges(String header) {
		assertThatThrownBy(() -> RangeParser.parse(header, 100))
				.isInstanceOf(RangeNotSatisfiableException.class)
				.satisfies(ex -> assertThat(((RangeNotSatisfiableException) ex).totalSize()).isEqualTo(100));
	}
}

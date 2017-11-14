package se.jbee.lex;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class TestLexPerf {

	static byte[] bytes(String s) {
		return s.getBytes(UTF_8);
	}
	
	private final byte[] TEXT = readBytes();
	private static byte[] readBytes() {
		try {
			return Files.readAllBytes(new File("libraries.htm").toPath());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
			
	private final byte[] SEARCH_TEXT_LEX = bytes("`~(<p>)~(</p>)`");
	@Benchmark
	public void lexSearch50K(Blackhole hole) {
		byte[] data = TEXT;
		int d0 = 0;
		int[] res;
		int c = 0;
		while (d0 >= 0 && d0 < data.length) {
			res = match(SEARCH_TEXT_LEX, data, d0);
			d0 = res[1];
			c++;
		}
		hole.consume(c);
	}
	
	private final Pattern SEARCH_TEXT_REGEX = Pattern.compile("<p>.*?</p>");
	@Benchmark
	public void regexSearch50k(Blackhole hole) {
		byte[] data = TEXT;
		int c = 0;
		Matcher m = SEARCH_TEXT_REGEX.matcher(new ByteArrayCharSeq(data));
		while (m.find()) {
			c++;
		}
		hole.consume(c);
	}
	
	private final byte[] WORDS = bytes("EH10 2QQ");
	private final byte[] MATCH_LEX = bytes("`@[@]#[{0-9A-Za-z}][ ]#@@`");
	
	@Benchmark
	public void lexMatchWords(Blackhole hole) {
		hole.consume(Lex.match(MATCH_LEX, 0, WORDS, 0));
	}
	private final Pattern MATCH_REGEX = Pattern.compile("[a-zA-Z]{1,2}[0-9][0-9A-Za-z]{0,1} {0,1}[0-9][A-Za-z]{2}");
	@Benchmark
	public void regexMatchWords(Blackhole hole) {
		hole.consume(MATCH_REGEX.matcher(new ByteArrayCharSeq(WORDS)));
	}
	
	private final byte[] LINE = bytes("The author is Mark Twain. The book is titled Huckleberry Finn.");
	private final byte[] SEARCH_WORDS_LEX = bytes("`~(Twain)~(Huck)`");
	@Benchmark
	public void lexSearchLine(Blackhole hole) {
		hole.consume(Lex.match(SEARCH_WORDS_LEX, 0, LINE, 0));
	}
	
	private final Pattern SEARCH_WORDS_REGEX = Pattern.compile(".*?Twain.*?Huck");
	@Benchmark
	public void regexSearchLine(Blackhole hole) {
		hole.consume(SEARCH_WORDS_REGEX.matcher(new ByteArrayCharSeq(LINE)).find());
	}
	
	private final byte[][] NUMBERS = new byte[][] {
			bytes("12"), bytes("13L"), bytes("14l"), bytes("12.0"), bytes("0.0"), bytes(".42f"), bytes("42d"),
			bytes("0xCAFE_BABE"), bytes("0b0000_1101")
	};
	private final byte[] MATCH_NUMBER_LEX = bytes("`{.0-9}[{.xb0-9}[{0-9A-Fa-f_}+][.#+]][{dDfFlL}]`");
	@Benchmark
	public void lexMatchNumbers(Blackhole hole) {
		for (int i = 0; i < NUMBERS.length; i++) {
			hole.consume(Lex.match(MATCH_NUMBER_LEX, 0, NUMBERS[i], 0));
		}
	}	
	
	private final Pattern MATCH_NUMBER_REGEX = Pattern.compile("[.0-9]([.xb0-9]([0-9A-Fa-f_]+)?(\\.\\d+)?)?([dDfFlL])?");
	@Benchmark
	public void regexMatchNumbers(Blackhole hole) {
		for (int i = 0; i < NUMBERS.length; i++) {
			hole.consume(MATCH_NUMBER_REGEX.matcher(new ByteArrayCharSeq(NUMBERS[i])).matches());
		}
	}
	
	private static int[] match(byte[] pattern, byte[] input, int d0) {
		long res = Lex.match(pattern, 0, input, d0);
		return new int[] { (int)(res >> 32), (int)res };
	}
	
	/**
	 * Make an byte[] look like a {@link CharSequence}.
	 */
	static final class ByteArrayCharSeq implements CharSequence {
		
		private final byte[] input;
		
		ByteArrayCharSeq(byte[] input) {
			super();
			this.input = input;
		}

		@Override
		public CharSequence subSequence(int start, int end) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int length() {
			return input.length;
		}

		@Override
		public char charAt(int index) {
			return (char) input[index];
		}
	}
}


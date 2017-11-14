package se.jbee.lex;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import org.openjdk.jmh.runner.RunnerException;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
public class TestLexPerf {

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

	private final byte[] LEX_PATTERN = bytes("`{.0-9}[{.xb0-9}[{0-9A-Fa-f_}+][.#+]][{dDfFlL}]`");
	private final Pattern REGEX_PATTERN = Pattern.compile("[.0-9]([.xb0-9]([0-9A-Fa-f_]+)?(\\.\\d+)?)?([dDfFlL])?");
	
	private final byte[][] DATA = new byte[][] {
		bytes("12"), bytes("13L"), bytes("14l"), bytes("12.0"), bytes("0.0"), bytes(".42f"), bytes("42d"),
		bytes("0xCAFE_BABE"), bytes("0b0000_1101")
	};
	
	static byte[] bytes(String s) {
		return s.getBytes(UTF_8);
	}
	
	private final byte[] FILE = readBytes();
	private final byte[] SEARCH_Lex = bytes("`~(<p>)~(</p>)`");
	
	private static byte[] readBytes() {
		try {
			return Files.readAllBytes(new File("libraries.htm").toPath());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
			
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	@Benchmark
	public void searchTextInFileHx(Blackhole hole) throws IOException {
		byte[] data = FILE;
		int d0 = 0;
		int[] res;
		int c = 0;
		while (d0 >= 0 && d0 < data.length) {
			res = match(SEARCH_Lex, data, d0);
			d0 = res[1];
			c++;
		}
		hole.consume(c);
	}
	
	private final Pattern SEARCH_RX = Pattern.compile("<p>.*?</p>");
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	@Benchmark
	public void searchTextInFileRx(Blackhole hole) {
		byte[] data = FILE;
		int c = 0;
		Matcher m = SEARCH_RX.matcher(new ByteArrayCharSeq(data));
		while (m.find()) {
			c++;
		}
		hole.consume(c);
	}
	
	private static int[] match(byte[] pattern, byte[] input, int d0) {
		long res = Lex.match(pattern, 0, input, d0);
		return new int[] { (int)(res >> 32), (int)res };
	}
	
	@Benchmark
	public void hiperX(Blackhole hole) {
		for (int i = 0; i < DATA.length; i++) {
			hole.consume(matchLex(DATA[i]));
		}
	}
	
	// [a-zA-Z]{1,2}[0-9][0-9A-Za-z]{0,1} {0,1}[0-9][A-Za-z]{2}
	private final byte[] SM_1D = bytes("EH10 2QQ");
	private final byte[] SM_1P = bytes("`@[@]#[{0-9A-Za-z}][ ]#@@`");
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	@Benchmark
	public void sm1_hx(Blackhole hole) {
		hole.consume(Lex.match(SM_1P, 0, SM_1D, 0));
	}
	private final Pattern SM_1PRX = Pattern.compile("[a-zA-Z]{1,2}[0-9][0-9A-Za-z]{0,1} {0,1}[0-9][A-Za-z]{2}");
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	@Benchmark
	public void sm1_rx(Blackhole hole) {
		hole.consume(SM_1PRX.matcher(new ByteArrayCharSeq(SM_1D)));
	}
	
	private final byte[] SM_2D = bytes("The author is Mark Twain. The book is titled Huckleberry Finn.");
	private final byte[] SM_2P = bytes("`~(Twain)~(Huck)`");
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	@Benchmark
	public void sm2_hx(Blackhole hole) {
		hole.consume(Lex.match(SM_2P, 0, SM_2D, 0));
	}
	
	private final Pattern SM_2PRX = Pattern.compile(".*?Twain.*?Huck");
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	@Benchmark
	public void sm2_rx(Blackhole hole) {
		hole.consume(SM_2PRX.matcher(new ByteArrayCharSeq(SM_2D)).find());
	}
	
	@Benchmark
	public void regEx(Blackhole hole) {
		for (int i = 0; i < DATA.length; i++) {
			hole.consume(matchRegEx(DATA[i]));
		}
	}
	
	private boolean matchRegEx(byte[] input) {
		return REGEX_PATTERN.matcher(new ByteArrayCharSeq(input)).matches();
	}
	
	private long matchLex(byte[] input) {
		return Lex.match(LEX_PATTERN, 0, input, 0);
	}
	
}


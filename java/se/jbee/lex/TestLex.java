package se.jbee.lex;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.fill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static se.jbee.lex.Lex.isMaskable;
import static se.jbee.lex.Lex.mismatchAt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Test;

public class TestLex {

	@Test
	public void literalMasking() {
		for (byte b : bytes("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ<> ")) {
			assertTrue(isMaskable(b));
		}
		for (byte b : bytes(Lex.ops)) {
			assertFalse(isMaskable(b));
		}
		assertFalse(isMaskable((byte)0));
		assertFalse(isMaskable((byte)31));
		assertFalse(isMaskable((byte)-1));
		assertFalse(isMaskable((byte)-128));
	}

	@Test
	public void escapeOps() {
		int c = 0;
		for (byte i = -128; i < 127; i++) {
			boolean op = Lex.isOp(i);
			if (Lex.ops.contains(String.valueOf((char)i)) && !op)
				fail((char)i+" ("+(i)+") is an op and must be escaped.");
			if (op) {
				c++;
			}
		}
		assertEquals(c, Lex.ops.length());
	}

	@Test
	public void searchText() {
		Match match = match("`~(Twain)~(Huck)`", "The author is Mark Twain. The book is titled Huckleberry Finn.");
		assertEquals(49, match.dn);
	}

	@Test
	public void searchTextCombination() {
		Match match = match("`~(Twain~(Huck))`", "The author is Mark Twain. The book is titled Huckleberry Finn.");
		assertEquals(49, match.dn);
		match = match("`~(Twain~(Huck))`", "The author is Mark Twain. The book is titled Hu.");
		assertEquals(-1, match.dn);
	}

	/**
	 * requires "ant jar" to have the file downloaded
	 **/
	@Test
	public void searchTextInFile() throws IOException {
		File f = new File("libraries.htm");
		if (!f.exists())
			return;
		byte[] data = Files.readAllBytes(f.toPath());
		int d0 = 0;
		Match match;
		int c = 0;
		byte[] pattern = bytes("`~(<p>)~(</p>)`");
		while (d0 >= 0 && d0 < data.length) {
			match = match(pattern, data, d0);
			d0 = match.dn;
			c++;
		}
		assertEquals(7, c);
	}

	@Test
	public void searchTextInLargeFile() throws IOException {
		File f = new File("mtent12.txt");
		if (!f.exists())
			return;
		byte[] data = Files.readAllBytes(f.toPath());
		int d0 = 0;
		Match match;
		int c = 0;
		byte[] pattern = bytes("`~(Huck@+)`");
		while (d0 >= 0 && d0 < data.length) {
			match = match(pattern, data, d0);
			d0 = match.dn;
			c++;
		}
		assertEquals(84, c);
	}

	@Test
	public void matchReadmeExamples() {
		assertFullMatch("####/##/##", "1950/05/12");
		assertFullMatch("##:##[:##]", "20:45");
		assertFullMatch("##:##[:##]", "20:45:11");
		assertFullMatch("#+[.#+]", "12");
		assertFullMatch("#+[.#+]", "12.95");
		assertFullMatch("\"{^\"}+\"", "\"hello world\"");
		assertFullMatch("\"~\"", "\"hello world\"");
		assertFullMatch("\\$@{?a-zA-Z0-9_}+", "$püü");
		assertFullMatch("[\\+]#+[{- }#+]+x", "+45 1234 56789x");
		assertFullMatch("~(Foo)", "Hello Foo");
		assertFullMatch("\"~({^\\\\}\")", "\"hello \\\"world\\\"\"");
		assertFullMatch("~(<h#>)", "Some text <h1>");
		assertFullMatch("~(<h{1-6}>)", "Some text <h1>");
		assertFullMatch("~(Foo~(Bar))", "Only a Foo followed by a Bar");
	}

	@Test
	public void matchNumberExamples() {
		// dates
		assertFullMatch("####/##/##", "2017/10/24");
		assertFullMatch("####{-/.}##{-/.}##", "2017/10/24");
		assertFullMatch("####{-/.}##{-/.}##", "2017-10-24");
		assertFullMatch("####{-/.}##{-/.}##", "2017.10.24");

		// time
		assertFullMatch("##:##:##", "12:35:45");
		assertFullMatch("##{.:}##{.:}##", "12:35:45");
		assertFullMatch("##{.:}##{.:}##", "12.35.45");

		// integers
		assertFullMatch("#+", "1");
		assertFullMatch("#+", "12");
		assertFullMatch("#+", "12345678900543");
		// with dividers
		assertFullMatch("#+[,###]+", "12");
		assertFullMatch("#+[,###]+", "12000");
		assertFullMatch("#+[,###]+", "12,000");
		assertFullMatch("#+[,###]+", "12,345,456");

		// floating point
		assertFullMatch("#+[.#+]", "1");
		assertFullMatch("#+[.#+]", "1.0");
		assertFullMatch("#+[.#+]", "13.45");
		// java style
		assertFullMatch("[#+[{_,}###]+].#+", "13.45");
		assertFullMatch("[#+[{_,}###]+].#+", ".01");
		assertFullMatch("[#+[{_,}###]+].#+", "0.0");
		assertFullMatch("[#+[{_,}###]+].#+", "12_345.9");

		String anyNumber = "{.0-9}[{.xb0-9}[{0-9A-Fa-f_}+][.#+]][{dDfFlL}]";
		assertFullMatch(anyNumber, "12");
		assertFullMatch(anyNumber, "13L");
		assertFullMatch(anyNumber, "14l");
		assertFullMatch(anyNumber, "12.0");
		assertFullMatch(anyNumber, "0.0");
		assertFullMatch(anyNumber, ".42f");
		assertFullMatch(anyNumber, "42d");
		assertFullMatch(anyNumber, "0xCAFE_BABE");
		assertFullMatch(anyNumber, "0b0000_1101");
		// but also:
		assertFullMatch(anyNumber, ".");
		assertFullMatch(anyNumber, "..");
		assertFullMatch(anyNumber, "..0.0");
	}

	/**
	 * single quoted for better readability of the example,
	 * works likewise for double quotes
	 **/
	@Test
	public void matchQuotedStrings() {
		assertFullMatch("'~'", "'abcd'");
		assertFullMatch("'''~(''')", "'''ab'c'd'''");
		assertFullMatch("'{^'}+'", "'abcd and d'");  // by using exclusive sets
		assertFullMatch("'~({^\\\\}')", "'ab'");       // with escaping
		assertFullMatch("'~({^\\\\}')", "'ab\\'c'");
		assertFullMatch("'~({^\\\\}')", "'ab\\'\\'c'");
	}

	@Test
	public void matchIdentifiers() {
		assertFullMatch("{a-z}+[{-_}{a-zA-Z0-9}+]+", "a");
		assertFullMatch("{a-z}+[{-_}{a-zA-Z0-9}+]+", "a-b");
		assertFullMatch("{a-z}+[{-_}{a-zA-Z0-9}+]+", "aa_b0");
		assertFullMatch("{a-z}+[[{-_}]{a-zA-Z0-9}+]+", "a");
		assertFullMatch("{a-z}+[[{-_}]{a-zA-Z0-9}+]+", "aCamalCase");

		// only camel case - java style
		assertFullMatch("{a-z}[{a-z0-9_}+][{A-Za-z0-9_}+]+", "aVariable");
		assertFullMatch("{a-z}[{a-z0-9_}+][{A-Za-z0-9_}+]+", "a");
		assertFullMatch("{a-z}[{a-z0-9_}+][{A-Za-z0-9_}+]+", "a9");
		assertFullMatch("{a-z}[{a-z0-9_}+][{A-Za-z0-9_}+]+", "usingCamelCase");
	}

	@Test
	public void matchLiteral() {
		assertFullMatch("abcdefäöå", "abcdefäöå");
	}

	@Test
	public void matchLiteralPlus() {
		assertFullMatch("a+", "a");
		assertFullMatch("a+", "aa");
		assertFullMatch("a+", "aaa");
		assertFullMatch("a+b", "ab");
		assertFullMatch("a+b", "aab");
	}

	@Test
	public void matchLiteralPlusPlus() {
		assertFullMatch("a++", "a");
		assertFullMatch("a++", "aa");
		assertFullMatch("a++", "aaa");
		assertFullMatch("a++b", "ab");
		assertFullMatch("a++b", "aab");
		assertFullMatch("a+++b", "aab");
	}

	@Test
	public void mismatchLiteralPlus() {
		assertNoMatchAt("a+x", "ay", 1);
		assertNoMatchAt("a+x", "aay", 2);
		assertNoMatchAt("a+x", "aaay", 3);
	}

	@Test
	public void matchLiteralAndSet() {
		assertFullMatch("ab{cd}", "abc");
		assertFullMatch("ab{cd}", "abd");
	}

	@Test
	public void matchLiteralAndSetPlus() {
		assertFullMatch("ab{cd}+", "abcd");
		assertFullMatch("ab{cde}+", "abedc");
		assertFullMatch("ab{cd}+e", "abde");
	}

	@Test
	public void mismatchSetOddCases() {
		assertNoMatchAt("{-}", "x", 0);
		assertNoMatchAt("{\\-}", "x", 0);
		assertNoMatchAt("{\\^}", "x", 0);
		assertNoMatchAt("{?-c}", "a", 0); // = {?\-c}
		assertNoMatchAt("{?-c}", "b", 0); // = {?\-c}
		assertNoMatchAt("{0-?}", "/", 0); // = {0-\?}
		assertNoMatchAt("{0-?}", "@", 0); // = {0-\?}
	}

	@Test
	public void matchSetOddCases() {
		assertFullMatch("{^}", "^");
		assertMatchUpTo("{^}+", "any byte", 8);
		assertFullMatch("{-}", "-");
		assertFullMatch("{\\-}", "-");
		assertFullMatch("{\\^}", "^");
		assertFullMatch("{?-c}", "-"); // = {?\-c}
		assertFullMatch("{?-c}", "c"); // = {?\-c}
		assertFullMatch("{0-?}", "1"); // = {0-\?}
		assertFullMatch("{0-?}", "?"); // = {0-\?}
		assertFullMatch("{@-c}", "m"); // not a range
		assertFullMatch("{@-c}", "c"); // not a range
	}

	@Test
	public void matchSetRangeEscape() {
		assertFullMatch("{\\a-\\z}", "a");
		assertFullMatch("{\\a-\\z}", "g");
		assertFullMatch("{\\a-\\z}", "z");
		assertFullMatch("{\\?-\\@}", "?");
		assertFullMatch("{\\?-\\@}", "@");
		assertFullMatch("{+-?}", "+");
		assertFullMatch("{+-?}", "?");
	}

	@Test
	public void mismatchSetRangeEscape() {
		assertNoMatchAt("{\\a-\\z}", "`", 0);
		assertNoMatchAt("{\\a-\\z}", "{", 0);
		assertNoMatchAt("{\\?-\\@}", ">", 0);
		assertNoMatchAt("{\\?-\\@}", "A", 0);
		assertNoMatchAt("{+-?}", "*", 0);
		assertNoMatchAt("{+-?}", "@", 0);
	}

	@Test
	public void matchSetRangeAt() {
		assertFullMatch("{@@-@ }", ""+(char)0);
		assertFullMatch("{@@-@ }", "`");
	}

	@Test
	public void mismatchSetRangeAt() {
		assertNoMatchAt("{@@-@ }", "a", 0);
		assertNoMatchAt("{@@-@ }", "~", 0);
	}

	@Test
	public void matchSetAtFold() {
		assertFullMatch("{@I}", "\t");
		assertFullMatch("{@J}", "\n");
		assertFullMatch("{@M}", "\r");
		assertFullMatch("{@?}", ""+(char)127);
		assertFullMatch("{@@}", ""+(char)0);
		assertFullMatch("{@+}", "k");
	}

	@Test
	public void matchExclusiveSet() {
		assertFullMatch("{^-}", "x");
		assertFullMatch("{^cd}", "a");
		assertFullMatch("{^cd}", "b");
		assertFullMatch("{^cd}", "e");
		assertFullMatch("{^c}", "e");
		assertFullMatch("{^^}", "x");
		assertFullMatch("{^^}", "#");
	}

	@Test
	public void matchExclusiveSetRange() {
		assertFullMatch("{^b-fgx-y}", "-");
		assertFullMatch("{^b-fgx-y}", "a");
		assertFullMatch("{^b-fgx-y}", "h");
		assertFullMatch("{^b-fgx-y}", "i");
		assertFullMatch("{^b-fgx-y}", "z");
		assertFullMatch("{^b-fgx-y}", "w");
		assertFullMatch("{^b-fgx-y}", "*");
		assertFullMatch("{^b-fgx-y}", "^");
		assertFullMatch("{^b-fgx-y}", "'");
	}

	@Test
	public void matchExclusiveSetRangePlus() {
		assertFullMatch("{^b-fgx-y }+", "ahijklmnopqrstuvwz");
		assertFullMatch("{^b-fgx-y }+", "1234567890");
		assertFullMatch("{^b-fgx-y }+", "+-*/~^'&|@#$");
		assertFullMatch("{^b-fgx-y }+", "<>[]{}");
	}

	@Test
	public void matchSetRangeSquareBrackets() {
		assertFullMatch("\\[{^]}+\\]", "[foo]");
		assertFullMatch("\\[{^]}+\\]", "[1]");
		assertFullMatch("\\[{^]}+\\]", "[x++]");
	}

	@Test
	public void mismatchExclusiveSetRange() {
		assertNoMatchAt("{^b-fgx-y}", "b", 0);
		assertNoMatchAt("{^b-fgx-y}", "c", 0);
		assertNoMatchAt("{^b-fgx-y}", "f", 0);
		assertNoMatchAt("{^b-fgx-y}", "g", 0);
		assertNoMatchAt("{^b-fgx-y}", "x", 0);
		assertNoMatchAt("{^b-fgx-y}", "y", 0);
	}

	@Test
	public void mismatchExclusiveSet() {
		assertNoMatchAt("{^cde}", "c", 0);
		assertNoMatchAt("{^cde}", "d", 0);
		assertNoMatchAt("{^cde}", "e", 0);
		assertNoMatchAt("{^c}", "c", 0);
		assertNoMatchAt("{^-}", "-", 0);
		assertNoMatchAt("{^^}", "^", 0);
	}

	@Test
	public void matchSetWithEscape() {
		assertFullMatch("{^\\\\}", "}");
		assertFullMatch("{\\}}", "}");
	}

	@Test
	public void matchSetNotClosed() {
		assertFullMatch("a[{b", "a");
	}

	@Test
	public void matchOption() {
		assertFullMatch("[abc]", "abc");
		assertMatchUpTo("[abc]", "xbc", 0);
		assertMatchUpTo("[abc]", "abx", 0);
		assertFullMatch("[abc]x", "x");
		assertFullMatch("[abc]x", "abcx");
	}

	@Test
	public void matchOptionSet() {
		assertFullMatch("a[b{x[]}]", "a");
		assertFullMatch("a[b{x[]}]", "abx");
		assertFullMatch("a[b{x[]}]", "ab[");
		assertFullMatch("a[b{x[]}]", "ab]");
	}

	@Test
	public void matchOptionPlus() {
		assertFullMatch("[ab]+c", "abc");
		assertFullMatch("[ab]+c", "ababc");
		assertFullMatch("[ab]+c", "c");
	}

	@Test
	public void mismatchOptionPlus() {
		assertNoMatchAt("[ab]+c", "abe", 2);
		assertNoMatchAt("[ab]+c", "e", 0);
	}

	@Test
	public void matchGroup() {
		assertFullMatch("(ab)", "ab");
		assertFullMatch("(abc)", "abc");
		assertFullMatch("(#.#)", "1.1");
	}

	@Test
	public void mismatchGroup() {
		assertNoMatchAt("(ab)", "ax", 1);
		assertNoMatchAt("(abc)", "abx", 2);
		assertNoMatchAt("(abc)", "xbc", 0);
	}

	@Test
	public void matchGroupPlus() {
		assertFullMatch("(a)+", "a");
		assertFullMatch("(a)+", "aa");
		assertFullMatch("(a)+", "aaa");
		assertFullMatch("(a)+", "aaaa");
		assertFullMatch("(a)+", "aaaaa");
		assertFullMatch("(bb)+", "bb");
		assertFullMatch("(bb)+", "bbbb");
		assertFullMatch("(bb)+", "bbbbbb");
		assertFullMatch("(bb)+", "bbbbbbbb");
		assertFullMatch("(bb)+", "bbbbbbbbbb");
	}

	@Test
	public void matchOptionalGroupPlus() {
		assertFullMatch("[a]+", "");
		assertFullMatch("[a]+", "a");
		assertFullMatch("[a]+", "aa");
		assertFullMatch("[a]+", "aaa");
		assertFullMatch("[a]+", "aaaa");
		assertFullMatch("[a]+", "aaaaa");
		assertFullMatch("[bb]+", "");
		assertFullMatch("[bb]+", "bb");
		assertFullMatch("[bb]+", "bbbb");
		assertFullMatch("[bb]+", "bbbbbb");
		assertFullMatch("[bb]+", "bbbbbbbb");
		assertFullMatch("[bb]+", "bbbbbbbbbb");
	}

	@Test
	public void matchSetPlus() {
		assertFullMatch("{a}+", "aa");
		assertFullMatch("{a}+", "a");
		assertFullMatch("{a}+", "aaa");
		assertFullMatch("{a}+", "aaaa");
		assertFullMatch("{a}+", "aaaaa");
		assertFullMatch("{ab}+", "ab");
		assertFullMatch("{ab}+", "bb");
		assertFullMatch("{ab}+", "aa");
		assertFullMatch("{ab}+", "aaa");
		assertFullMatch("{ab}+", "bbb");
		assertFullMatch("{ab}+", "abba");
		assertFullMatch("{ab}+", "baab");
	}

	@Test
	public void matchGroupWithSetPlus() {
		assertMatchUpTo("(1{a-z}2)+", "1a21ab2", 3);
		assertFullMatch("(1{a-z}2)+", "1a21a2");
		assertFullMatch("(1{a-z}2)+[13]", "1a2");
		assertFullMatch("(1{a-z}2)+[13]", "1a213");
		assertFullMatch("(1{a-z}2)+[13]", "1a21b2");
		assertFullMatch("(1{a-z}2)+[13]", "1a21b213");
		assertFullMatch("({a-z}12)+", "a12");
		assertFullMatch("({a-z}12)+", "a12b12");
		assertFullMatch("({a-z}12)+", "z12r12m12");
		assertFullMatch("(12{a-z}+)+", "12ab");
		assertFullMatch("(12{a-z}+)+", "12ab12c");
		assertFullMatch("(12{a-z}+)+", "12abc12cd");
		assertFullMatch("(12{a-z}+)+", "12abc12cd12fff");
	}

	@Test
	public void matchGroupNested() {
		assertFullMatch("(a(b(c)))", "abc");
		assertFullMatch("(ax(bx(cx)))", "axbxcx");
	}

	@Test
	public void matchGroupNestedPlus() {
		assertFullMatch("(a(b(c)+)+)+", "abc");
		assertFullMatch("(a(b(c)+)+)+", "abcabc");
		assertFullMatch("(a(b(c)+)+)+", "abccbc");
		assertFullMatch("(a(b+(cd)+)+)+", "abbcdcdabcd");
		assertFullMatch("(a(b+(cd)+)+)+", "abbbbbbcdcdcdcdcdabcd");
	}

	@Test
	public void matchSetRange() {
		assertFullMatch("{a-z}", "g");
		assertFullMatch("{a-z}", "a");
		assertFullMatch("{a-z}", "z");
	}

	@Test
	public void matchSetIncludingNonASCII() {
		assertFullMatch("{?}+b", "öb");
		assertFullMatch("{?a}+b", "aab");
		assertFullMatch("{?a}+b", "aäöaü²€b");
		assertFullMatch("{?a-zA-Z0-9_}+", "püü");
	}

	@Test
	public void matchSetRangePlus() {
		assertFullMatch("{a-z}+", "abc");
		assertFullMatch("{A-Za-z}+", "zA");
		assertFullMatch("{A-Za-z0-9}+", "bD7aZ");
		assertFullMatch("{A-Za-z<0-9>}+", "<bD7aZ>");
		assertFullMatch("{a-zA-Z }+.", "The quick brown fox jumps over the lazy dog.");
	}

	@Test
	public void mismatchSetRange() {
		assertNoMatchAt("{a-z}", "G", 0);
		assertNoMatchAt("{a-z}", "`", 0);
		assertNoMatchAt("{a-z}", "{", 0);
		assertNoMatchAt("{a-df-z}", "e", 0);
		assertNoMatchAt("{a-df-z}+X", "aeX", 1);
	}
	@Test
	public void mismatchLiteralAndSetPlus() {
		assertNoMatchAt("ab{cd}+", "abb", 2);
		assertNoMatchAt("ab{cd}+e", "abcb", 3);
		assertNoMatchAt("ab{cd}+e", "abdcb", 4);
	}

	@Test
	public void matchDigitPattern() {
		assertFullMatch("#", "0");
		assertFullMatch("#", "9");
		assertFullMatch("#", "5");
		assertFullMatch("##:##:##", "12:59:45");
		assertFullMatch("####/##/##", "2010/12/31");
	}

	@Test
	public void matchDigitPlus() {
		assertFullMatch("#+", "1");
		assertFullMatch("#+", "12");
		assertFullMatch("#+", "123");
		assertFullMatch("#+", "1234567890");
		assertFullMatch("x#+y", "x1234567890y");
	}

	@Test
	public void mismatchDigitPlus() {
		assertNoMatchAt("#+", "a", 0);
		assertNoMatchAt("#+x", "0y", 1);
		assertNoMatchAt("#+x", "01y", 2);
	}

	@Test
	public void mismatchDigitPattern() {
		assertNoMatchAt("#", "a", 0);
		assertNoMatchAt("#", "/", 0);
		assertNoMatchAt("#", ":", 0);
		assertNoMatchAt("#", "T", 0);
		assertNoMatchAt("#", "t", 0);
		assertNoMatchAt("x#", "xa", 1);
		assertNoMatchAt("#y", "0t", 1);
	}

	@Test
	public void matchLetterPattern() {
		assertFullMatch("@", "a");
		assertFullMatch("@", "A");
		assertFullMatch("@", "z");
		assertFullMatch("@", "Z");
		assertFullMatch("@", "F");
		assertFullMatch("@", "g");
		assertFullMatch("@@@", "Ace");
	}

	@Test
	public void misatchLetterPattern() {
		assertNoMatchAt("@", "@", 0);
		assertNoMatchAt("@", "[", 0);
		assertNoMatchAt("@", "`", 0);
		assertNoMatchAt("@", "{", 0);
		assertNoMatchAt("@", "0", 0);
		assertNoMatchAt("@", "+", 0);
	}

	@Test
	public void matchFillLiteral() {
		assertFullMatch("a~b", "ab");
		assertFullMatch("a~b", "axb");
		assertFullMatch("a~b", "axxb");
		assertFullMatch("a~b", "axyzb");
	}

	@Test
	public void matchScanDigit() {
		assertFullMatch("a~#", "a0");
		assertFullMatch("a~#", "axx1");
		assertFullMatch("a~#", "ayyy9");
		assertFullMatch("a~#.#", "ayyy9.1");
	}

	@Test
	public void mismatchScanDigit() {
		assertNoMatchAt("a~#", "aa", 1);
	}

	@Test
	public void matchScanSet() {
		assertFullMatch("a~{b-z}", "a0b");
		assertFullMatch("a~{b-z}", "a11z");
	}

	@Test
	public void mismatchScan() {
		assertNoMatchAt("a~b", "ax", 1);
		assertNoMatchAt("a~b", "axy", 1);
	}

	@Test
	public void matchScanGroup() {
		assertFullMatch("a~(#.#+)", "axxxx1.3");
		assertFullMatch("a~(bc)", "abc");
		assertFullMatch("a~(bc)", "abdbc");
		assertFullMatch("a~(bc)", "acxbc");
		assertFullMatch("a~(#bc)", "acxx1bc");
		assertFullMatch("a~(#@bc)", "acxx1wbc");
		assertFullMatch("a~(#@_bc)", "acxx1w bc");
		assertFullMatch("a~(#@_^bc)", "acxx1w +bc");
		assertFullMatch("a~({^+}bc)", "acxbc");
		assertFullMatch("a~({^+}bc)", "axbc");
		assertFullMatch("a~(\\#bc)", "a#bc");
		assertFullMatch("a~(\\#bc)", "axxx#bc");
		assertFullMatch("a~([v]bc)", "abc");
		assertFullMatch("a~([v]bc)", "avbc");
		assertFullMatch("a~([v]bc)", "axxvbc");
	}

	@Test
	public void matchScanGroupPlus() {
		assertFullMatch("a~(bc)+", "abc");
		assertFullMatch("a~(bc)+", "abcbc");
		assertFullMatch("a~(bc)+", "abdbc");
		assertFullMatch("a~(bc)+", "acxbc");
		assertFullMatch("a~(bc)+", "abdbcbc");
		assertFullMatch("a~(bc)+", "acxbcbc");
	}

	@Test
	public void matchScanPlus() {
		assertFullMatch("a~b+c", "abc");
		assertFullMatch("a~b+c", "axbc");
		assertFullMatch("a~b+c", "axxbc");
		assertFullMatch("a~b+c", "abbc");
		assertFullMatch("a~b+c", "abbbc");
		assertFullMatch("a~b+c", "axxbbc");
		assertFullMatch("a~b+c", "axxxbbbc");
	}

	@Test
	public void matchScanDirectPlus() {
		assertFullMatch("a~\\+", "ax+");
		assertNoMatchAt("a~+c", "axxxbbbc", 1);
	}

	@Test
	public void matchScanScan() {
		assertFullMatch("a~~bc", "abc");
		assertFullMatch("a~~bc", "axbc");
		assertFullMatch("a~~bc", "axxbc");
		assertFullMatch("a~~bc", "axxxbc");
		assertFullMatch("a~~~bc", "axxxbc");
	}

	@Test
	public void matchScanOption() {
		assertFullMatch("a~[b]c", "ac");
		assertFullMatch("a~[b]c", "abc");
	}

	@Test
	public void mismatchScanOption() {
		assertNoMatchAt("a~[b]c", "axc", 1);
		assertNoMatchAt("a~[b]c", "axbc", 1);
	}

	@Test
	public void matchNestedOption() {
		assertFullMatch("a[b[c]]d", "ad");
		assertFullMatch("a[b[c]]d", "abd");
		assertFullMatch("a[b[c]]d", "abcd");
	}

	@Test
	public void matchNestedOptionPlus() {
		assertFullMatch("a[b+[c]+]d", "ad");
		assertFullMatch("a[b+[c]+]d", "abd");
		assertFullMatch("a[b+[c]+]d", "abcd");
		assertFullMatch("a[b+[c]+]d", "abbd");
		assertFullMatch("a[b+[c]+]d", "abbcd");
		assertFullMatch("a[b+[c]+]d", "abccd");
		assertFullMatch("a[b+[c]+]d", "abbccd");
	}

	@Test
	public void matchNonWhitespaceSetPlus() {
		assertFullMatch("^+", "azAZ:-,;*()[]{}?!<>|&%@");
	}

	@Test
	public void matchWhitespaceSetPlus() {
		assertFullMatch("_+x", " \t\n\rx");
	}

	@Test
	public void matchNL() {
		assertFullMatch("$$", "\n\r");
	}

	@Test
	public void mismatchNL() {
		assertNoMatchAt("$", " ", 0);
		assertNoMatchAt("$", "\t", 0);
		assertNoMatchAt("$", "a", 0);
	}

	@Test
	public void mismatchNonWhitespaceSet() {
		assertNoMatchAt("^", " ", 0);
		assertNoMatchAt("^", "\n", 0);
		assertNoMatchAt("^", "\t", 0);
		assertNoMatchAt("^", "\r", 0);
	}

	@Test
	public void matchAnyByteSet() {
		assertFullMatch("??????", "aAzZ09");
		assertFullMatch("??????", "<>[]{}");
		assertFullMatch("??????", "!?-:.,");
		assertFullMatch("??????", "+-*/^=");
		assertFullMatch("??????", "&|%#@~");
		assertFullMatch("????",   " \t\n\r");
	}

	@Test
	public void matchEscape() {
		assertFullMatch("~\\a", "xxxa");
		assertFullMatch("\\\\", "\\");
		assertFullMatch("\\(", "(");
		assertFullMatch("\\++", "++");
		assertFullMatch("\\++", "+++");
		assertFullMatch("(\\))", ")");
		assertFullMatch("(\\)+##)@@", "))11xx");
		assertFullMatch("(\\)+)", ")");
		assertFullMatch("[\\]+]", "]]");
		assertFullMatch("[\\]+##]#+", "]]1234");
		assertFullMatch("\\~", "~");
		assertFullMatch("\\$", "$");
	}

	@Test
	public void matchIncompleteSet() {
		assertExactMatch("{a", "a");
		assertExactMatch("{^a", "b");
		assertExactMatch("{a-", "a");
		assertExactMatch("{a-c", "a");
		assertExactMatch("{a-c", "b");
		assertExactMatch("{a-c", "c");
		assertExactMatch("{a-@", "a");
		assertMatchUpTo("{?", "ä", 1);
	}

	@Test
	public void mismatchIncompleteSet() {
		assertNoMatchAt("{@", "a", 0);
		assertNoMatchAt("{\\", "a", 0);
		assertNoMatchAt("{a", "b", 0);
		assertNoMatchAt("{^a", "a", 0);
		assertNoMatchAt("{a-", "b", 0);
		assertNoMatchAt("{a-c", "d", 0);
		assertNoMatchAt("{a-c", "@", 0);
		assertNoMatchAt("{a-c", "-", 0);
		assertNoMatchAt("{a-@", "b", 0);
		assertNoMatchAt("{?", "a", 0);
	}

	@Test
	public void matchIncompleteOption() {
		assertExactMatch("[a", "a");
		assertExactMatch("[a#", "a1");
		// option cannot mismatch but match nothing
		assertMatchUpTo("[a", "b", 0);
		assertMatchUpTo("[a#", "aa1", 0);
	}

	@Test
	public void matchIncompleteGroup() {
		assertExactMatch("(a", "a");
		assertExactMatch("(a#", "a1");
	}

	@Test
	public void mismatchIncompleteGroup() {
		assertNoMatchAt("(a", "b", 0);
		assertNoMatchAt("(a#", "aa1", 1);
	}

	@Test
	public void matchNonAsciiSet() {
		assertEquals(3, match("`{?}+`".getBytes(US_ASCII), new byte[] {-1, -42, -127}, 0).dn);
	}

	private static void assertNoMatchAt(String pattern, String data, int pos) {
		Match match = match("`"+pattern+"`", data);
		assertEquals(mismatchAt(pos), match.dn);
	}

	private static void assertMatchUpTo(String pattern, String data, int pos) {
		assertEquals(pos, match("`"+pattern+"`", data).dn);
	}

	private static void assertExactMatch(String pattern, String data) {
		Match match = match(pattern, data);
		assertEquals(bytes(data).length, match.dn);
	}

	/**
	 * If a pattern end with groups or repetitions their positions might not be
	 * passed when data is fully processed.
	 *
	 * How do we know the full pattern matched?
	 * 1) We add a literal at the end to both pattern and data (see mark below)
	 * 2) We add extra byte at the end of data to make sure in pattern causes ` exit
	 */
	private static void assertFullMatch(String pattern, String data) {
		assertMatchInSequence(pattern, data);
		String mark = " ";
		assertMatchInSequence("`"+pattern+mark+"`", data+mark);
	}

	private static void assertMatchInSequence(String pattern, String data) {
		Match match = match(pattern, data+" "); // we add one more "random" byte to data so that there would be more to process so that ` (or end of pattern) exits matching not having reached end of data
		assertEquals(bytes(data).length, match.dn);
		assertEquals(bytes(pattern).length, match.pn);
		assertExactMatch(pattern, data);
	}

	static final class Match {
		byte[] pattern; int pn;
		byte[] data;    int dn;


		Match(byte[] pattern, byte[] input, long pndn) {
			this(pattern, (int)(pndn >> 32), input, (int)pndn);
		}

		Match(byte[] pattern, int pn, byte[] input, int dn) {
			this.pattern = pattern;
			this.pn = pn;
			this.data = input;
			this.dn = dn;
		}

		@Override
		public String toString() {
			String res = "";
			res += new String(pattern, US_ASCII)+"\n";
			char[] indent = new char[pn]; fill(indent, ' ');
			res += new String(indent)+"^"+pn+"\n";
			int pos = dn < 0 ? mismatchAt(dn) : dn;
			if (pos < 20) {
				res += new String(data, UTF_8)+"\n";
				indent = new char[pos]; fill(indent, ' ');
			} else {
				int pre = 0;
				while (pre < 25 && data[pos-pre] != '\n') pre++;
				int post = 0;
				while (post < 18 && data[pos+post] != '\n') post++;
				pre--;
				post--;
				int pre2 = pre;
				while (pre2 > 0 && data[pos-pre2] != ' ') pre2--;
				if (pre2 > 0) pre=pre2;
				res += "..."+new String(data, pos-pre, pre+post-1, UTF_8)+"...\n";
				indent = new char[pre+3]; fill(indent, ' ');
			}
			res += new String(indent)+"^"+dn+"\n";
			return res;
		}
	}

	private static Match match(String pattern, String data) {
		return match(bytes(pattern), bytes(data), 0);
	}

	private static Match match(byte[] pattern, byte[] input, int d0) {
		return new Match(pattern, input, Lex.match(pattern, 0, input, d0));
	}

	private static byte[] bytes(String s) {
		return s.getBytes(UTF_8);
	}
}


package se.jbee.lex;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Lex is short for Linear expressions.
 *
 * These are strictly sequential expressions (or patterns) or a
 * "Sequence Matching Machine". A form of a simpler "regex" that does not need to
 * be compiled and still is very efficient. Think of regex stripped of all the
 * features not needed so that it is both useful but also very simple and
 * compact to implement the matching algorithm.
 *
 * The matching algorithm is a single function (utilizing recursion).
 */
public final class Lex {

	public static long match(byte[] pattern, int p0, byte[] data, int d0) {
		return match(pattern, p0, data, d0, -1, -1);
	}

	/**
	 * Matches the pattern against the data and returns the end position of
	 * pattern and data at the end of a match or mismatch.
	 *
	 * @param pattern
	 *            the "match program"
	 * @param p0
	 *            starting position in the program
	 * @param data
	 *            the content to match
	 * @param d0
	 *            starting position in the content
	 * @param pPlus
	 *            pattern position for the + repeated currently, or -1 if no repeat
	 * @param maxOps
	 *            maximal number of operations evaluated before returning
	 * @return end positions (pn,dn) implemented as long to make the algorithm
	 *         allocation free. pn is next position in pattern, dn next position
	 *         in data after the match. On mismatch dn is (-position -1).
	 */
	public static long match(byte[] pattern, int p0, byte[] data, int d0, int pPlus, int maxOps) {
		int pn = p0;
		int dn = d0;
		int dr = d0;
		int pPlus0 = -1; // position from where to repeat (last op in loop on this level)
		boolean plussed = pPlus >= 0;
		while (pn < pattern.length && dn < data.length && maxOps-- != 0) {
			if (!plussed)
				dr = mismatch(dn);
			int pOp = pn;
			byte op  = pattern[pn++];
			switch (op) {
			// literals:
			case '\\': if (pattern[pn++] != data[dn++]) return pos(pOp, dr); break;
			default :  if (op != data[dn++])   return pos(pOp, dr); break; //TODO shouldn't it be pOp here and everywhere else?
			// special sets...
			case '*': dn++; break;
			case '^': if (isWS(data[dn++]))  return pos(pOp, dr); break;
			case '_': if (!isWS(data[dn++])) return pos(pOp, dr); break;
			case '$': if (!isNL(data[dn++])) return pos(pOp, dr); break;
			          // range test use: (unsigned)(number-lower) <= (upper-lower)
			case '@': if ((0xFFFF & (data[dn++] & 0xDF) - 'A') >= 26) return pos(pOp, dr); break;
			case '#': if ((0xFFFF & (data[dn++]) - '0') >= 10) return pos(pOp, dr); break;
			// groups:
			case ')':
			case ']': if (pn != pPlus) return pos(pn, dn); break; // NOOP before the + right after
			case '`': if (pOp > p0) return pos(pn, dn); break; // NOOP on first in block
			case '(': // group must occur
			case '[': // group can occur
				if (!plussed || p0 != pOp) {
					long pndn = match(pattern, pn, data, dn, -1, -1);
					if ((int)pndn < 0) {
						if (op == '(') // when must occur its a mismatch
							return plussed ? pos(pOp, dr) : pndn ;
						pn = skipBeyondOption(pattern, pn);
					} else {
						pn = (int)(pndn >> 32);
						dn = (int)pndn;
					}
				}
				break;
			case '~': // scan
				dn = scan(pattern, pn, data, dn);
				if (dn >= data.length)
					return pos(pn, dr);
				break;
			case '+': // retry:
				if (pOp == pPlus) { // reached same + again
					pn = p0;        // go back to loop start
					dr = dn;        // remember successful match position
				} else {
					dn = (int)match(pattern, pPlus0, data, dn, pOp, maxOps);
					if (dn < 0)
						dn = mismatch(dn); // reverses a mismatch by applying function again (blocks return positive)
				}
				break;
			// set:
			case '{': // set excluding non ASCII bytes
			case '}': // set including non ASCII bytes
				if (!setContains(pattern, pOp, data[dn++]))
					return pos(pOp, dr); // mismatch
				pn = plussed && p0 == pOp ? pPlus : skipBeyondSet(pattern, pOp);
				break;
			}
			pPlus0 = pOp; // remember as loop start
		}
		return pos(pn, dn);
	}

	private static boolean setContains(byte[] pattern, int pn, int chr) {
		final int eos = pattern[pn++] == '{' ? '}' : '{';
		if (chr < 0) // a non ASCII
			return eos == '{';
		boolean exclusive = pattern[pn] == '^';
		if (exclusive) pn++;
		byte op = pattern[pn++];
		if (op == chr) // first char special handling for {-}
			return !exclusive;
		if (op == eos)
			return exclusive; // for {^}
		if (op == '\\') pn--; // rewind in case of escaped first member
		do {
			op = pattern[pn++];
		} while (!(op == '-' && chr <= pattern[pn++] && chr > pattern[pn-3] // range match
				|| op == '\\' && chr == pattern[pn++]                       // escaped literal match
				|| op == eos                                                // end of set
				|| op == chr && op != '-'));                                // literal match
		return ((op == eos) == exclusive);
	}

	private static int scan(byte[] pattern, int pn, byte[] data, int dn) {
		int m0 = maskPosition(pattern, pn); // find literal position (-1 if no such exists)
		int mn = m0;
		long mask = 0L;
		if (m0 >= 0) {
			if (pattern[pn] == '(') {
				while (mn < pattern.length && isLiteral(pattern[mn])) mn++;
			} else {
				mn++;
			}
			mask = mask(pattern, m0, mn); // make literal mask
		}
		do {
			if (m0 > 0)
				dn = hop(pattern, m0, mask, mn, data, dn);
		} while ((int)match(pattern, pn, data, dn, -1, 1) < 0 && ++dn < data.length);
		return dn;
	}

	private static int skipBeyondSet(byte[] pattern, int pn) {
		int eos = pattern[pn++] == '{' ? '}' : '{';
		while (pn < pattern.length) {
			byte op = pattern[pn++];
			if (op == '\\') {
				pn++;
			} else if (op == eos)
				return pn;
		}
		return pn;
	}

	private static int skipBeyondOption(byte[] pattern, int pn) {
		int level = 1;
		while (level > 0 && pn < pattern.length) {
			byte op = pattern[pn];
			if (op == '\\') {
				pn+=2;
			} else if (op == '{' || op == '}') {
				pn = skipBeyondSet(pattern, pn);
			} else {
				if (op == '[') level++;
				if (op == ']') level--;
				pn++;
			}
		}
		return pn;
	}

	private static long pos(int pn, int dn) {
		return (long)pn << 32 | dn & 0xFFFFFFFFL;
	}

	static int mismatch(int dn) {
		return -dn-1;
	}

	private static boolean isWS(byte chr) {
		return chr == ' ' || chr == '\t' || isNL(chr);
	}

	private static boolean isNL(byte chr) {
		return chr == '\n' || chr == '\r';
	}

	/*
	 * Everything below is purely a performance optimization for scanning to
	 * find a literal sequence.
	 *
	 * The idea is this: if ~ is followed by a literal or literal sequence or a
	 * group (...) starting with a literal we can hop forward checking every
	 * n-th byte to see if it is one of the bytes in the literal sequence
	 * identified. This check is done by building a bitmask. For the bitmask we
	 * use a long and map 32-95 to 0-63. ASCIIs from 96 to 127 are mapped to
	 * their lower case variant 64-95.
	 */

	private static int hop(byte[] pattern, int m0, long mask, int mn, byte[] data, int d0) {
		int dn = d0;
		final int len = mn - m0;
		final byte start = pattern[m0];
		if (len == 1) {
			while (dn < data.length && data[dn] != start) dn++;
			return dn;
		}
		do {
			while (dn < data.length && ((1L << shift(data[dn])) & mask) == 0)
				dn+= len;
			if (dn < data.length) {
				int c = len;
				int dx = dn;
				while (c-- > 0 && dx > d0 && data[dx] != start) dx--;
				if (data[dx] == start) {
					c = 0;
					while (c < len && dx < data.length && data[dx++] == pattern[m0+c]) c++;
					if (c >= len)
						return dx-len;
				}
				dn++;
			}
		} while (dn < data.length);
		return dn;
	}

	private static int maskPosition(byte[] pattern, int pn) {
		while (pattern[pn] == '(') pn++;
		return isLiteral(pattern[pn]) ? pn : -1;
	}

	private static final long OPS_MASK = opsMask();
	public static boolean isLiteral(byte b) {
		return b > 0 && ((1L << shift(b)) & OPS_MASK) == 0L;
	}

	private static long opsMask() {
		String ops = "#$()+@[]^_\\{}*`~";
		return mask(ops.getBytes(US_ASCII), 0, ops.length());
	}

	private static long mask(byte[] pattern, int s, int e) {
		long mask = 0L;
		for (int i = s; i < e; i++)
			mask |= 1L << shift(pattern[i]);
		return mask;
	}

	private static int shift(byte b) {
		return b >= '`' ? (b & 0xDF)-32 : b-32;
	}

}


package se.jbee.lex;

import java.nio.charset.StandardCharsets;

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
		return match(pattern, p0+1, data, d0, pattern[p0], false, -1);
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
	 * @param end
	 *            the bytes that marks the end of matching
	 * @param rep
	 *            true, if the match was called from a repetition '+' (so it is
	 *            "optional")
	 * @param maxOps
	 *            maximal number of operations evaluated before returning
	 * @return end positions (pn,dn) implemented as long to make the algorithm
	 *         allocation free. pn is next position in pattern, dn next position
	 *         in data after the match. On mismatch dn is (-position -1).
	 */
	public static long match(byte[] pattern, int p0, byte[] data, int d0, byte end, boolean rep, int maxOps) {
		int pn = p0;
		int dn = d0;
		int p1 = p0; // position from where to repeat
		byte rend = '+';
		byte c;
		while (pn < pattern.length && dn < data.length && maxOps-- != 0) {
			byte op  = pattern[pn++];
			switch (op) {
			case '*': dn++; break;
			case '!': if (data[dn++] >= 0) return pos(pn, mismatch(dn-1)); break;
			case '^': if (isWS(data[dn++])) return pos(pn, mismatch(dn-1)); break;
			case '_': if (!isWS(data[dn++])) return pos(pn, mismatch(dn-1)); break;
			case '$': if (!isNL(data[dn++])) return pos(pn, mismatch(dn-1)); break;
			// range test use: (unsigned)(number-lower) <= (upper-lower)
			case '@': if ((0xFFFF & (data[dn++] & 0xDF) - 'A') >= 26) return pos(pn, mismatch(dn-1)); break;
			case '#': if ((0xFFFF & (data[dn++]) - '0') >= 10) return pos(pn, mismatch(dn-1)); break;
			case '~': // scan
				int dnf = -1;
				int dnf0 = dn;
				/* perf optimization start */
				int pm0 = maskPosition(pattern, pn); // find literal position (-1 if no such exists)
				int pm = pm0;
				long mask = 0L;
				if (pm0 >= 0) {
					if (pattern[pn] == '(') {
						while (pm < pattern.length && isLiteral(pattern[pm])) pm++;
					} else {
						pm++;
					}
					mask = mask(pattern, pm0, pm); // make literal mask
				}
				do {
					if (pm0 > 0) {
						dn = skip(pattern, pm0, mask, pm, data, dn);
					}
					/* perf optimization end */
					if (dn < data.length) {
						dnf = (int)match(pattern, pn, data, dn, end, false, 1);
					}
				} while (dnf < 0 && ++dn < data.length);
				if (dnf < 0)
					return pos(pn, mismatch(dnf0));
				break;
			case '(': // group:
			case '[': // optional group:
				p1 = pn;
				rend = (byte)(op == '(' ? ')' : ']');
				long res = match(pattern, pn, data, dn, rend, false, maxOps == 0 ? -1 : maxOps);
				if ((int)res < 0) {
					if (op == '(') {
						return res; // mismatch for (...)
					}
					// skip rest of an optional block
					int level = 1; boolean set = false;
					while (level > 0 && pn < pattern.length) {
						if (!set) {
							if (pattern[pn] == '[') level++; if (pattern[pn++] ==']') level--;
						} else {
							if (pattern[pn] == '{' || pattern[pn++] == '}') set = !set;
						}
					}
				} else {
					pn = (int)(res >> 32);
					dn = (int)res;
				}
				break;
			case '+': // repetition:
				if (rep) {
					pn = p0;
				} else {
					c = pattern[pn-2];
					boolean block = c == '}' || c == '{' || c == ')' || c == ']';
					dn = (int)match(pattern, block ? p1 : pn-2, data, dn, rend, true, maxOps);
					if (dn < 0) {
						dn = mismatch(dn); // reverses a mismatch by applying function again
					} else if (block)
						pn--;
				}
				break;
			case '{': // set (of symbols, excluding non ASCII bytes):
			case '}': // set (including non ASCII bytes)
				final int eos = op == '{' ? '}' : '{';
				p1 = pn-1;
				boolean exclusive = pattern[pn] == '^';
				if (exclusive) pn++;
				int pa = pn;
				do {
					c = data[dn++];
					boolean done = false;
					while (!done) {
						final byte m = pattern[pn++];
						// order of tests is important so that pn advances after end of a set
						done =     m == '-' && pn-1 > pa && c <= pattern[pn++] && c > pattern[pn-3] // range
								|| m == eos // end of set
								|| eos == '{' && (c < 0)
								|| m == c && (c != '-' || pn-1 == pa);  // match
					}
					boolean match = pattern[pn-1] != eos; // match (since we did not reach the end of the set)
					if (match == exclusive)
						return pos(p1, mismatch(dn-1));
					if (match && !rep) { // only jump to end on match and no + invocation (no match is at the end)
						while (pattern[pn++] != eos);
					}
					if (rep) pn = pa; // keep matching the set on +
				} while (rep && dn < data.length);
				break;
			default: // literals:
				if (op == end)
					return pos(pn, dn);
				if (op != data[dn])
					return pos(pn, mismatch(dn));
				dn++;
			}
		}
		return pos(pn, dn);
	}

	private static long pos(int pn, int dn) {
		return (long)pn << 32 | dn & 0xFFFFFFFFL;
	}

	static int mismatch(int pos) {
		return -pos-1;
	}

	private static boolean isWS(byte c) {
		return c == ' ' || c == '\t' || isNL(c);
	}

	private static boolean isNL(byte c) {
		return c == '\n' || c == '\r';
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

	private static int skip(byte[] pattern, int pm0, long mask, int pm,	byte[] data, int d0) {
		int dn = d0;
		final int len = pm - pm0;
		final byte start = pattern[pm0];
		if (len == 1) {
			while (dn < data.length && data[dn] != start) dn++;
			return dn;
		}
		do {
			while (dn < data.length && ((1L << shift(data[dn])) & mask) == 0) {
				dn+= len;
			}
			if (dn < data.length) {
				int c = len;
				int dx = dn;
				while (c-- > 0 && dx > d0 && data[dx] != start) dx--;
				if (data[dx] == start) {
					c = 0;
					while (c < len && dx < data.length && data[dx++] == pattern[pm0+c]) c++;
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
		String ops = "#$()+@[]^_";
		return mask(ops.getBytes(StandardCharsets.US_ASCII), 0, ops.length());
	}

	private static long mask(byte[] pattern, int s, int e) {
		long mask = 0L;
		for (int i = s; i < e; i++) {
			mask |= 1L << shift(pattern[i]);
		}
		return mask;
	}

	private static int shift(byte b) {
		return b >= '`' ? (b & 0xDF)-32 : b-32;
	}

}


package se.jbee.lex;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.nio.ByteBuffer;

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
	 * Matches the pattern against the data and returns the end position of pattern
	 * and data at the end of a match or mismatch.
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
	 *            pattern position for the + retried currently, or -1 if no repeat
	 * @param maxOps
	 *            maximal number of operations evaluated before returning, negative
	 *            for unlimited
	 * @return end positions (pn,dn) implemented as long to make the algorithm
	 *         allocation free. pn is next position in pattern, dn next position in
	 *         data after the match. On mismatch dn is (-position -1), pn points to
	 *         the instruction that did not match.
	 */
	public static long match(byte[] pattern, int p0, byte[] data, int d0, int pPlus, int maxOps) {
		int pn = p0;
		int dn = d0;
		int dr = d0;
		int pPlus0 = -1; // position from where to retry (last op in loop on this level)
		boolean plussed = pPlus >= 0;
		while (pn < pattern.length && dn < data.length && maxOps-- != 0) {
			if (!plussed)
				dr = mismatchAt(dn);
			int pOp = pn;
			byte op  = pattern[pn++];
			switch (op) {
			// literals:
			case '\\':if (pattern[pn++] != data[dn++]) return pos(pOp, dr); break;
			default : if (op != data[dn++]) return pos(pOp, dr); break;
			// special sets...
			case '?': dn++; break;
			case '^': if (isWS(data[dn++]))  return pos(pOp, dr); break;
			case '_': if (!isWS(data[dn++])) return pos(pOp, dr); break;
			case '$': if (!isNL(data[dn++])) return pos(pOp, dr); break;
			          // range test use: (unsigned)(number-lower) <= (upper-lower)
			case '@': if ((0xFFFF & (data[dn++] & 0xDF) - 'A') >= 26) return pos(pOp, dr); break;
			case '#': if ((0xFFFF & (data[dn++]) - '0') >= 10) return pos(pOp, dr); break;
			// groups:
			case '}':
			case ')':
			case ']': if (pn != pPlus) return pos(pn, dn); break; // SKIP before the + right after
			case '`': if (pOp > p0)    return pos(pn, dn); break; // NOOP on first in block
			case '(': // group must occur
			case '[': // group can occur
				if (!plussed || p0 != pOp) {
					long pndn = match(pattern, pn, data, dn, -1, -1);
					if ((int)pndn < 0) {
						if (op == '(') // when must occur its a mismatch
							return plussed ? pos(pOp, dr) : pndn ;
						pn = skipBeyondBlock(pattern, pn);
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
				} else if (pOp > p0 && pattern[pPlus0] != '+') {
					dn = (int)match(pattern, pPlus0, data, dn, pOp, maxOps);
					if (dn < 0)
						dn = mismatchAt(dn); // reverses a mismatch by applying function again (blocks return positive)
				}
				break;
			// set:
			case '{':
				if (!inSet(pattern, pn, data[dn++]))
					return pos(pOp, dr); // mismatch
				pn = plussed && p0 == pOp ? pPlus : skipBeyondSet(pattern, pOp);
				break;
			}
			pPlus0 = pOp; // remember as loop start
		}
		return pos(pn, dn);
	}

	private static boolean inSet(byte[] pattern, int p0, byte chr) {
		if (pattern[p0] == '^' && pattern[p0-1] == '{')
			return !inSet(pattern, p0+1, chr);
		final int pEnd = pattern.length;
		int pn = p0;
		int c = 0;
		while (pn < pEnd) {
			byte op = pattern[pn++];
			switch(op) {
			default  : if (op == chr)  return true; break;
			case '?' : if (chr < 0)    return true; c = -1; break;
			case '\\': if (pn >= pEnd) return false; if (pattern[pn++] == chr)         return true; break;
			case '@' : if (pn >= pEnd) return false; if ((pattern[pn++] ^ '@') == chr) return true; break;
			case '}' : return false;
			case '-' :
				if (c == 0) { // after ? or as first member
					if (op == chr) return true; // => match - literally
				} else {      // range (at this point == lower has been tested already)
					if (pn >= pEnd) return false;
					byte lower = pattern[pn-2];
					if (pn >= 3 && pattern[pn-3] == '@') lower ^= '@';
					byte upper = pattern[pn++];
					if (upper == '}' || pn + 1 >= pEnd && (upper == '\\' || upper == '@')) return false;
					if (upper == '\\') { upper = pattern[pn++]; }
					else if (upper == '@') upper = (byte) (pattern[pn++] ^ '@');
					if (chr <= upper && chr >= lower) return true;
				}
				break;
			}
			c++;
		}
		return false;
	}

	private static int scan(byte[] pattern, int p0, byte[] data, int dn) {
		if (pattern[p0] == '+')
			return data.length; // mismatch
		if (pattern[p0] != '(') // basic scan (if no group is used there is no point)
			return scanLinear(pattern, p0, data, dn);
		return scanHop(pattern, p0, data, dn);
	}

	private static int scanHop(byte[] pattern, int p0, byte[] data, int dn) {
		int pm = p0;
		int offset = 0; //
		boolean done = false;
		// skip instructions of known length in hope to find literal afterwards
		while (pm < pattern.length && !done) {
			byte op = pattern[pm++];
			offset++;
			switch (op) {
			case '#' :
			case '?' :
			case '_' :
			case '^' :
			case '@' : break;
			case '{' : pm = skipBeyondSet(pattern, pm); break;
			case '\\': pm++; break;
			default  : pm--; done = true; //$FALL-THROUGH$
			case '(' : offset--; // does not consume input
			}
		}
		int pmEnd = pm;
		while (pmEnd < pattern.length && isMaskable(pattern[pmEnd])) pmEnd++;
		int len = pmEnd-pm;
		if (len == 0) // bad luck: no maskable sequence at group start
			return scanLinear(pattern, p0, data, dn);
		long mask = len == 1 ? 0L : mask(pattern, pm, pmEnd); // make literal mask
		do {
			dn = hop(pattern, pm, data, dn, mask, len);
		} while ((int)match(pattern, p0, data, dn-offset, -1, 1) < 0 && ++dn < data.length);
		return dn-offset;
	}

	private static int scanLinear(byte[] pattern, int p0, byte[] data, int dn) {
		byte chr = pattern[p0];
		if (isOp(chr)) { // slow: pattern
			while ((int)match(pattern, p0, data, dn, -1, 1) < 0 && ++dn < data.length);
		} else
			dn = skipToNext(chr, data, dn);
		return dn;
	}

	private static int skipToNext(byte chr, byte[] data, int dn) {
		while (dn < data.length && data[dn] != chr) dn++;
		return dn;
	}

	private static int skipBeyondSet(byte[] pattern, int pn) {
		final int pEnd = pattern.length;
		while (pn < pEnd) {
			byte op = pattern[pn++];
			if (op == '\\' || op == '@') {
				pn++;
			} else if (op == '}')
				return pn;
		}
		return pn;
	}

	private static int skipBeyondBlock(byte[] pattern, int pn) {
		final int pEnd = pattern.length;
		int level = 1;
		while (level > 0 && pn < pEnd) {
			byte op = pattern[pn];
			if (op == '\\') {
				pn+=2;
			} else if (op == '{') {
				pn = skipBeyondSet(pattern, pn);
			} else {
				if (op == '[' || op == '(') {
					level++;
				} else if (op == ')' || op == ']' || op == '}')
					level--;
				pn++;
			}
		}
		return pn;
	}

	private static long pos(int pn, int dn) {
		return (long)pn << 32 | dn & 0xFFFFFFFFL;
	}

	static int mismatchAt(int dn) {
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

	private static int hop(byte[] pattern, int p0, byte[] data, int d0, long mask, int len) {
		int dn = d0;
		final byte first = pattern[p0];
		if (len == 1)
			return skipToNext(first, data, dn);
		do {
			while (dn < data.length && ((1L << shift(data[dn])) & mask) == 0)
				dn+= len;
			if (dn < data.length) {
				int c = len;
				int dx = dn;
				while (c-- > 0 && dx > d0 && data[dx] != first) dx--;
				if (data[dx] == first) {
					c = 0;
					while (c < len && dx < data.length && data[dx++] == pattern[p0+c]) c++;
					if (c >= len)
						return dx-len;
				}
				dn++;
			}
		} while (dn < data.length);
		return dn;
	}

	static final String ops = "()[]{}#$+@^_\\?`~";
	private static final long OPS_MASK = mask(ops.getBytes(US_ASCII), 0, ops.length());

	public static boolean isMaskable(byte b) {
		return b >= 32 && ((1L << shift(b)) & OPS_MASK) == 0L;
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

	/*
	 * Escaping
	 */

	public static boolean isOp(byte b) {
		return b > 32 && b != '|' && b != 127 && ((1L << shift(b)) & OPS_MASK) != 0L;
	}

	public static byte[] escaped(byte[] literal) {
		return escaped(literal, 0, literal.length);
	}

	public static byte[] escaped(byte[] literal, int start, int end) {
		ByteBuffer escaped = ByteBuffer.allocate(literal.length+literal.length/8);
		for (int i = start; i < end; i++) {
			byte chr = literal[i];
			if (isOp(chr))
				escaped.put((byte)'\\');
			escaped.put(chr);
		}
		return escaped.array();
	}
}


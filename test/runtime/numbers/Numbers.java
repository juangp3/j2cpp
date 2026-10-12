// Boxed number types and Character: constants, parsing, bit twiddling,
// unsigned arithmetic, floating-point bits and Latin-1 classification
public class Numbers {
	static void out(String s) {
		System.out.println(s);
	}

	static String q(String s) {
		if (s == null) {
			return "null";
		}
		// Escape non-ASCII text, whose console encoding varies
		StringBuilder sb = new StringBuilder("\"");
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c < 0x7f) {
				sb.append(c);
			} else {
				sb.append("\\u").append(Integer.toHexString(c));
			}
		}
		return sb.append('"').toString();
	}

	static String failure(RuntimeException e) {
		return "threw " + e.getClass().getName() + ": " + e.getMessage();
	}

	// Parsing, each failure caught and printed

	static void parseInt(String s, int radix) {
		String call = "Integer.parseInt(" + q(s) + ", " + radix + ")";
		try {
			out(call + " = " + Integer.parseInt(s, radix));
		} catch (NumberFormatException e) {
			out(call + " " + failure(e));
		}
	}

	static void parseLong(String s, int radix) {
		String call = "Long.parseLong(" + q(s) + ", " + radix + ")";
		try {
			out(call + " = " + Long.parseLong(s, radix));
		} catch (NumberFormatException e) {
			out(call + " " + failure(e));
		}
	}

	static void parseUnsignedInt(String s, int radix) {
		String call = "Integer.parseUnsignedInt(" + q(s) + ", " + radix + ")";
		try {
			out(call + " = " + Integer.toUnsignedString(Integer.parseUnsignedInt(s, radix)));
		} catch (NumberFormatException e) {
			out(call + " " + failure(e));
		}
	}

	static void parseUnsignedLong(String s, int radix) {
		String call = "Long.parseUnsignedLong(" + q(s) + ", " + radix + ")";
		try {
			out(call + " = " + Long.toUnsignedString(Long.parseUnsignedLong(s, radix)));
		} catch (NumberFormatException e) {
			out(call + " " + failure(e));
		}
	}

	static void parseByte(String s, int radix) {
		String call = "Byte.parseByte(" + q(s) + ", " + radix + ")";
		try {
			out(call + " = " + Byte.parseByte(s, radix));
		} catch (NumberFormatException e) {
			out(call + " " + failure(e));
		}
	}

	static void parseShort(String s, int radix) {
		String call = "Short.parseShort(" + q(s) + ", " + radix + ")";
		try {
			out(call + " = " + Short.parseShort(s, radix));
		} catch (NumberFormatException e) {
			out(call + " " + failure(e));
		}
	}

	static void decode(String s) {
		String call = "decode(" + q(s) + ")";
		String result;
		try {
			result = "Integer " + Integer.decode(s);
		} catch (NumberFormatException e) {
			result = "Integer " + failure(e);
		}
		try {
			result += ", Long " + Long.decode(s);
		} catch (NumberFormatException e) {
			result += ", Long " + failure(e);
		}
		try {
			result += ", Short " + Short.decode(s);
		} catch (NumberFormatException e) {
			result += ", Short " + failure(e);
		}
		try {
			result += ", Byte " + Byte.decode(s);
		} catch (NumberFormatException e) {
			result += ", Byte " + failure(e);
		}
		out(call + ": " + result);
	}

	static void parseDouble(String s) {
		String call = "parseDouble(" + q(s) + ")";
		try {
			double d = Double.parseDouble(s);
			out(call + " = " + Double.toHexString(d) + " bits " + Long.toHexString(Double.doubleToLongBits(d)));
		} catch (NumberFormatException e) {
			out(call + " " + failure(e));
		}
		call = "parseFloat(" + q(s) + ")";
		try {
			float f = Float.parseFloat(s);
			out(call + " = " + Float.toHexString(f) + " bits " + Integer.toHexString(Float.floatToIntBits(f)));
		} catch (NumberFormatException e) {
			out(call + " " + failure(e));
		}
	}

	static void toChars(int codePoint) {
		String call = "toChars(" + codePoint + ")";
		try {
			char[] chars = Character.toChars(codePoint);
			String s = Character.toString(codePoint);
			String units = "";
			for (int i = 0; i < chars.length; i++) {
				units += " " + Integer.toHexString(chars[i]);
			}
			out(call + " =" + units + ", count " + Character.charCount(codePoint) + ", string length "
					+ s.length() + ", back " + Character.codePointAt(s, 0) + ", valid "
					+ Character.isValidCodePoint(codePoint) + ", bmp " + Character.isBmpCodePoint(codePoint)
					+ ", supplementary " + Character.isSupplementaryCodePoint(codePoint));
		} catch (IllegalArgumentException e) {
			out(call + " " + failure(e));
		}
		try {
			out("toString(" + codePoint + ") length " + Character.toString(codePoint).length());
		} catch (IllegalArgumentException e) {
			out("toString(" + codePoint + ") " + failure(e));
		}
	}

	static void divide(int a, int b) {
		String call = "unsigned " + Integer.toUnsignedString(a) + " / " + Integer.toUnsignedString(b);
		try {
			out(call + " = " + Integer.toUnsignedString(Integer.divideUnsigned(a, b)) + " rem "
					+ Integer.toUnsignedString(Integer.remainderUnsigned(a, b)));
		} catch (ArithmeticException e) {
			out(call + " " + failure(e));
		}
	}

	static void divide(long a, long b) {
		String call = "unsigned " + Long.toUnsignedString(a) + " / " + Long.toUnsignedString(b);
		try {
			out(call + " = " + Long.toUnsignedString(Long.divideUnsigned(a, b)) + " rem "
					+ Long.toUnsignedString(Long.remainderUnsigned(a, b)));
		} catch (ArithmeticException e) {
			out(call + " " + failure(e));
		}
	}

	// Sections

	static void integers() {
		out("Integer: " + Integer.MIN_VALUE + " " + Integer.MAX_VALUE + " " + Integer.SIZE + " " + Integer.BYTES);
		out("Long: " + Long.MIN_VALUE + " " + Long.MAX_VALUE + " " + Long.SIZE + " " + Long.BYTES);
		out("Short: " + Short.MIN_VALUE + " " + Short.MAX_VALUE + " " + Short.SIZE + " " + Short.BYTES);
		out("Byte: " + Byte.MIN_VALUE + " " + Byte.MAX_VALUE + " " + Byte.SIZE + " " + Byte.BYTES);

		int[] ints = { 0, 1, -1, 42, -42, 255, 0x12345678, 0x80000000, 0x7fffffff, 0xcafebabe, 1 << 20 };
		for (int i : ints) {
			out("int " + i + ": bitCount " + Integer.bitCount(i) + " highest " + Integer.highestOneBit(i)
					+ " lowest " + Integer.lowestOneBit(i) + " nlz " + Integer.numberOfLeadingZeros(i)
					+ " ntz " + Integer.numberOfTrailingZeros(i) + " reverse " + Integer.reverse(i)
					+ " reverseBytes " + Integer.reverseBytes(i) + " rotl3 " + Integer.rotateLeft(i, 3)
					+ " rotr3 " + Integer.rotateRight(i, 3) + " rotl-5 " + Integer.rotateLeft(i, -5)
					+ " rotr33 " + Integer.rotateRight(i, 33) + " signum " + Integer.signum(i)
					+ " unsigned " + Integer.toUnsignedString(i) + " " + Integer.toUnsignedLong(i)
					+ " hex " + Integer.toHexString(i) + " oct " + Integer.toOctalString(i)
					+ " bin " + Integer.toBinaryString(i) + " hash " + Integer.hashCode(i));
			out("  radix: " + Integer.toString(i, 2) + " " + Integer.toString(i, 16) + " "
					+ Integer.toString(i, 36) + " " + Integer.toString(i, 1) + " " + Integer.toString(i, 37)
					+ " unsigned " + Integer.toUnsignedString(i, 16) + " " + Integer.toUnsignedString(i, 36)
					+ " " + Integer.toUnsignedString(i, 99));
		}
		long[] longs = { 0, 1, -1, 42, -42, 0x123456789abcdef0L, Long.MIN_VALUE, Long.MAX_VALUE,
				0xcafebabedeadbeefL, 1L << 40, 0xffffffffL };
		for (long l : longs) {
			out("long " + l + ": bitCount " + Long.bitCount(l) + " highest " + Long.highestOneBit(l)
					+ " lowest " + Long.lowestOneBit(l) + " nlz " + Long.numberOfLeadingZeros(l)
					+ " ntz " + Long.numberOfTrailingZeros(l) + " reverse " + Long.reverse(l)
					+ " reverseBytes " + Long.reverseBytes(l) + " rotl3 " + Long.rotateLeft(l, 3)
					+ " rotr3 " + Long.rotateRight(l, 3) + " rotl-5 " + Long.rotateLeft(l, -5)
					+ " rotr65 " + Long.rotateRight(l, 65) + " signum " + Long.signum(l)
					+ " unsigned " + Long.toUnsignedString(l) + " hex " + Long.toHexString(l)
					+ " oct " + Long.toOctalString(l) + " hash " + Long.hashCode(l));
			out("  radix: " + Long.toString(l, 2) + " " + Long.toString(l, 36) + " " + Long.toString(l, 0)
					+ " unsigned " + Long.toUnsignedString(l, 2) + " " + Long.toUnsignedString(l, 7)
					+ " " + Long.toUnsignedString(l, 36) + " " + Long.toUnsignedString(l, -3));
		}

		out("compare: " + Integer.compare(1, 2) + " " + Integer.compare(2, 2) + " " + Integer.compare(3, 2)
				+ " unsigned " + Integer.compareUnsigned(-1, 1) + " " + Integer.compareUnsigned(1, -1)
				+ " " + Integer.compareUnsigned(Integer.MIN_VALUE, Integer.MAX_VALUE)
				+ " long " + Long.compare(Long.MIN_VALUE, 0) + " " + Long.compareUnsigned(Long.MIN_VALUE, 0)
				+ " " + Long.compareUnsigned(-1L, -2L) + " " + Long.compareUnsigned(5L, 5L));
		out("short/byte compare: " + Short.compare((short) -5, (short) 7) + " " + Byte.compare((byte) 100, (byte) -100)
				+ " unsigned " + Short.compareUnsigned((short) -1, (short) 1) + " "
				+ Byte.compareUnsigned((byte) -1, (byte) 1) + " " + Byte.toUnsignedInt((byte) -1) + " "
				+ Byte.toUnsignedLong((byte) -128) + " " + Short.toUnsignedInt((short) -1) + " "
				+ Short.toUnsignedLong((short) -32768) + " reverseBytes " + Short.reverseBytes((short) 0x1234)
				+ " " + Short.reverseBytes((short) -256) + " " + (int) Character.reverseBytes('\u12ab'));

		divide(7, 2);
		divide(-1, 2);
		divide(-1, -2);
		divide(-2, -1);
		divide(0x80000000, 3);
		divide(5, 0);
		divide(-5, 0);
		divide(7L, 2L);
		divide(-1L, 2L);
		divide(-1L, 10L);
		divide(-1L, -2L);
		divide(-2L, -1L);
		divide(Long.MIN_VALUE, 3L);
		divide(0xfedcba9876543210L, 0x123456789L);
		divide(12345L, Long.MIN_VALUE);
		divide(5L, 0L);
		divide(-5L, 0L);
		out("min/max/sum: " + Integer.min(3, -4) + " " + Integer.max(3, -4) + " " + Integer.sum(Integer.MAX_VALUE, 1)
				+ " " + Long.min(3, -4) + " " + Long.max(3, -4) + " " + Long.sum(Long.MAX_VALUE, 1));
	}

	static void parsing() {
		parseInt("123", 10);
		parseInt("-2147483648", 10);
		parseInt("2147483647", 10);
		parseInt("2147483648", 10);
		parseInt("-2147483649", 10);
		parseInt("+7", 10);
		parseInt("-", 10);
		parseInt("+", 10);
		parseInt("", 10);
		parseInt("", 16);
		parseInt(null, 10);
		parseInt("12a", 10);
		parseInt(" 1", 10);
		parseInt("ff", 16);
		parseInt("-FF", 16);
		parseInt("zz", 36);
		parseInt("1010", 2);
		parseInt("2", 2);
		parseInt("7fffffff", 16);
		parseInt("80000000", 16);
		parseInt("-80000000", 16);
		parseInt("1", 1);
		parseInt("1", 37);
		parseInt("\uff11\uff12", 10);
		parseLong("9223372036854775807", 10);
		parseLong("-9223372036854775808", 10);
		parseLong("9223372036854775808", 10);
		parseLong("-7fffffffffffffff", 16);
		parseLong("1L", 10);
		parseLong(null, 10);
		parseLong("12", 99);
		parseUnsignedInt("4294967295", 10);
		parseUnsignedInt("4294967296", 10);
		parseUnsignedInt("2147483648", 10);
		parseUnsignedInt("ffffffff", 16);
		parseUnsignedInt("+123", 10);
		parseUnsignedInt("-1", 10);
		parseUnsignedInt("", 10);
		parseUnsignedInt("", 99);
		parseUnsignedInt(null, 10);
		parseUnsignedInt("12345678x0", 10);
		parseUnsignedInt("1", 99);
		parseUnsignedInt("100000000", 16);
		parseUnsignedLong("18446744073709551615", 10);
		parseUnsignedLong("18446744073709551616", 10);
		parseUnsignedLong("99999999999999999999", 10);
		parseUnsignedLong("12345678901234567890123", 10);
		parseUnsignedLong("9223372036854775808", 10);
		parseUnsignedLong("+18446744073709551615", 10);
		parseUnsignedLong("ffffffffffffffff", 16);
		parseUnsignedLong("10000000000000000", 16);
		parseUnsignedLong("1111111111111111111111111111111111111111111111111111111111111111", 2);
		parseUnsignedLong("11111111111111111111111111111111111111111111111111111111111111111", 2);
		parseUnsignedLong("3w5e11264sgsf", 36);
		parseUnsignedLong("3w5e11264sgsg", 36);
		parseUnsignedLong("zzzzzzzzzzzzzz", 36);
		parseUnsignedLong("1234567890123x5", 10);
		parseUnsignedLong("1234567890123456789x", 10);
		parseUnsignedLong("12345678901234", 99);
		parseUnsignedLong("-0", 10);
		parseUnsignedLong("", 99);
		parseUnsignedLong(null, 10);
		parseByte("127", 10);
		parseByte("-128", 10);
		parseByte("128", 10);
		parseByte("300", 10);
		parseByte("-81", 16);
		parseByte("ff", 16);
		parseByte("x", 10);
		parseByte(null, 10);
		parseByte("1", 40);
		parseShort("32767", 10);
		parseShort("-32768", 10);
		parseShort("70000", 16);
		parseShort("abc", 10);
		decode("0x1F");
		decode("-0X1f");
		decode("#ff");
		decode("-#80");
		decode("010");
		decode("-010");
		decode("0");
		decode("-0");
		decode("+5");
		decode("08");
		decode("0x");
		decode("#");
		decode("-");
		decode("");
		decode("-+1");
		decode("0x-1");
		decode("0x80000000");
		decode("-0x80000000");
		decode("2147483648");
		decode("-9223372036854775808");
		decode("300");
		decode("-0x81");
		decode("40000");
		out("valueOf radix: " + Integer.valueOf("-ff", 16) + " " + Long.valueOf("zz", 36) + " "
				+ Short.valueOf("-7fff", 16) + " " + Byte.valueOf("-1010", 2) + " " + Integer.valueOf("12")
				+ " " + Long.valueOf("-12") + " " + Short.valueOf("123") + " " + Byte.valueOf("-7"));
	}

	static void boxes() {
		Integer i1 = 127;
		Integer i2 = 127;
		Integer i3 = 128;
		Integer i4 = 128;
		Short s1 = (short) -128;
		Short s2 = Short.valueOf((short) -128);
		Short s3 = (short) 1000;
		Short s4 = Short.valueOf((short) 1000);
		Byte b1 = (byte) -100;
		Byte b2 = Byte.valueOf((byte) -100);
		Long l1 = 5L;
		Long l2 = Long.valueOf(5);
		out("cache: " + (i1 == i2) + " " + (i3 == i4) + " " + i3.equals(i4) + " " + (s1 == s2) + " " + (s3 == s4)
				+ " " + s3.equals(s4) + " " + (b1 == b2) + " " + (l1 == l2));
		Byte b = (byte) -7;
		Short s = (short) 3000;
		out("Byte: " + b + " " + b.byteValue() + " " + b.shortValue() + " " + b.intValue() + " " + b.longValue()
				+ " " + b.floatValue() + " " + b.doubleValue() + " " + b.hashCode() + " " + Byte.hashCode((byte) -1)
				+ " " + b.equals(Byte.valueOf((byte) -7)) + " " + b.equals(Short.valueOf((short) -7)) + " "
				+ b.compareTo(Byte.valueOf((byte) 9)) + " " + Byte.toString((byte) -128));
		out("Short: " + s + " " + s.byteValue() + " " + s.shortValue() + " " + s.intValue() + " " + s.longValue()
				+ " " + s.floatValue() + " " + s.doubleValue() + " " + s.hashCode() + " "
				+ s.equals(Short.valueOf((short) 3000)) + " " + s.equals(Integer.valueOf(3000)) + " "
				+ s.compareTo(Short.valueOf((short) -3000)) + " " + Short.toString((short) -32768));
		Integer big = 100000;
		Long lng = 0x123456789L;
		out("narrowing: " + big.byteValue() + " " + big.shortValue() + " " + lng.intValue() + " "
				+ lng.shortValue() + " " + lng.byteValue() + " " + lng.floatValue() + " " + lng.doubleValue());
		Double d = 1e10;
		Float f = -3.75f;
		out("double narrowing: " + d.byteValue() + " " + d.shortValue() + " " + d.intValue() + " " + d.longValue()
				+ " " + f.byteValue() + " " + f.intValue() + " " + f.longValue() + " " + f.doubleValue());
		out("Boolean: " + Boolean.logicalAnd(true, false) + " " + Boolean.logicalAnd(true, true) + " "
				+ Boolean.logicalOr(false, false) + " " + Boolean.logicalOr(false, true) + " "
				+ Boolean.logicalXor(true, true) + " " + Boolean.logicalXor(true, false) + " "
				+ Boolean.parseBoolean("TrUe") + " " + Boolean.parseBoolean("yes") + " " + Boolean.valueOf("TRUE")
				+ " " + Boolean.hashCode(true) + " " + Boolean.hashCode(false) + " " + Boolean.compare(true, false));
	}

	static void floats() {
		out("Double: " + Double.MAX_VALUE + " " + Double.toHexString(Double.MIN_VALUE) + " " + Double.toHexString(Double.MIN_NORMAL) + " "
				+ Double.MAX_EXPONENT + " " + Double.MIN_EXPONENT + " " + Double.SIZE + " " + Double.BYTES);
		out("Float: " + Float.MAX_VALUE + " " + Float.toHexString(Float.MIN_VALUE) + " " + Float.toHexString(Float.MIN_NORMAL) + " "
				+ Float.MAX_EXPONENT + " " + Float.MIN_EXPONENT + " " + Float.SIZE + " " + Float.BYTES);
		double[] doubles = { 0.0, -0.0, 1.0, -1.0, 3.0, 0.1, -2.5, 1024.0, 1e-300, 1e300, Double.MAX_VALUE,
				Double.MIN_VALUE, Double.MIN_NORMAL, Double.MIN_NORMAL / 3, Double.NaN, Double.POSITIVE_INFINITY,
				Double.NEGATIVE_INFINITY, Math.PI };
		for (double d : doubles) {
			out("double " + Double.toHexString(d) + " bits " + Long.toHexString(Double.doubleToLongBits(d))
					+ " raw " + Long.toHexString(Double.doubleToRawLongBits(d)) + " finite " + Double.isFinite(d)
					+ " infinite " + Double.isInfinite(d) + " nan " + Double.isNaN(d) + " hash " + Double.hashCode(d)
					+ " back " + Double.toHexString(Double.longBitsToDouble(Double.doubleToLongBits(d))));
		}
		float[] floats = { 0.0f, -0.0f, 1.0f, -1.0f, 3.0f, 0.1f, -2.5f, 1e-40f, 1e30f, Float.MAX_VALUE,
				Float.MIN_VALUE, Float.MIN_NORMAL, 3e-39f, -5.877472e-39f, Float.NaN, Float.POSITIVE_INFINITY,
				Float.NEGATIVE_INFINITY };
		for (float f : floats) {
			out("float " + Float.toHexString(f) + " bits " + Integer.toHexString(Float.floatToIntBits(f))
					+ " raw " + Integer.toHexString(Float.floatToRawIntBits(f)) + " finite " + Float.isFinite(f)
					+ " infinite " + Float.isInfinite(f) + " nan " + Float.isNaN(f) + " hash " + Float.hashCode(f)
					+ " box hash " + Float.valueOf(f).hashCode() + " back " + Float.toHexString(Float.intBitsToFloat(Float.floatToIntBits(f))));
		}
		out("intBitsToFloat: " + Float.intBitsToFloat(0x3fc00000) + " " + Float.toHexString(Float.intBitsToFloat(0x00000001)) + " "
				+ Float.intBitsToFloat(0xff800000) + " " + Integer.toHexString(Float.floatToIntBits(Float.intBitsToFloat(0x7fc00001)))
				+ " " + Integer.toHexString(Float.floatToRawIntBits(Float.intBitsToFloat(0x7fc00001))));
		Double nan1 = Double.NaN;
		Double nan2 = 0.0 / 0.0;
		Double zero = 0.0;
		Double negZero = -0.0;
		out("Double equals: NaN " + nan1.equals(nan2) + " " + (Double.NaN == Double.NaN) + " zeros " + zero.equals(negZero)
				+ " " + (0.0 == -0.0) + " compare " + Double.compare(0.0, -0.0) + " " + Double.compare(-0.0, 0.0)
				+ " " + Double.compare(Double.NaN, Double.POSITIVE_INFINITY) + " " + Double.compare(Double.NaN, Double.NaN)
				+ " " + Double.compare(1.0, 2.0) + " " + nan1.compareTo(zero) + " hash " + zero.hashCode() + " "
				+ negZero.hashCode() + " " + nan1.hashCode() + " isNaN " + nan1.isNaN() + " " + zero.isInfinite());
		Float fnan1 = Float.NaN;
		Float fnan2 = Float.intBitsToFloat(0x7fc00001);
		Float fzero = 0.0f;
		Float fnegZero = -0.0f;
		out("Float equals: NaN " + fnan1.equals(fnan2) + " zeros " + fzero.equals(fnegZero) + " compare "
				+ Float.compare(0.0f, -0.0f) + " " + Float.compare(-0.0f, 0.0f) + " " + Float.compare(Float.NaN, 1.0f)
				+ " " + Float.compare(Float.NaN, Float.NaN) + " " + fnan1.compareTo(fzero) + " hash " + fzero.hashCode()
				+ " " + fnegZero.hashCode() + " " + fnan1.hashCode() + " " + Float.valueOf(1.5f).hashCode() + " "
				+ Float.valueOf(1.5f).equals(Float.valueOf(1.5f)) + " " + Float.valueOf(1.5f).equals(Double.valueOf(1.5)));
		out("min/max/sum: " + Double.min(-0.0, 0.0) + " " + Double.max(-0.0, 0.0) + " " + Double.max(1.0, Double.NaN)
				+ " " + Double.sum(0.1, 0.2) + " " + Float.min(-0.0f, 0.0f) + " " + Float.max(2.0f, 3.0f) + " "
				+ Float.sum(0.1f, 0.2f));

		parseDouble("1.5");
		parseDouble("  1.5  ");
		parseDouble("\t-2.25\n");
		parseDouble("1.5f");
		parseDouble("1.5F");
		parseDouble("1.5d");
		parseDouble("1.5D");
		parseDouble(".5");
		parseDouble("5.");
		parseDouble("+.5e1");
		parseDouble("1e400");
		parseDouble("-1e400");
		parseDouble("1e-400");
		parseDouble("1e-45");
		parseDouble("3.4028235e38");
		parseDouble("3.4028236e38");
		parseDouble("1E10");
		parseDouble("1e+2");
		parseDouble("1e-2");
		parseDouble("0x1p3");
		parseDouble("0x1.8p1");
		parseDouble("-0X.8P-1");
		parseDouble("0x1p3f");
		parseDouble("0xAbC.dEp-4d");
		parseDouble("0x1.fffffffffffffp1023");
		parseDouble("0x1p-1074");
		parseDouble("NaN");
		parseDouble("-NaN");
		parseDouble("Infinity");
		parseDouble("-Infinity");
		parseDouble("+Infinity");
		parseDouble("  Infinity ");
		parseDouble("-0");
		parseDouble("0.1");
		parseDouble("123456789012345678901234567890");
		parseDouble("2.2250738585072012e-308");
		parseDouble("");
		parseDouble("   ");
		parseDouble("abc");
		parseDouble("  abc ");
		parseDouble("1.5x");
		parseDouble("1e");
		parseDouble("1e+");
		parseDouble("e5");
		parseDouble(".");
		parseDouble("-.");
		parseDouble("1.2.3");
		parseDouble("1..2");
		parseDouble("..");
		parseDouble("1.2e3.4");
		parseDouble("0x1.2.3p1");
		parseDouble("0x1.8");
		parseDouble("0x");
		parseDouble("0xp1");
		parseDouble("1p3");
		parseDouble("1_000");
		parseDouble("Infinityf");
		parseDouble("nan");
		parseDouble("inf");
		parseDouble("1.5ff");
		parseDouble("- 1");
		parseDouble("++1");
		out("valueOf: " + Double.valueOf("2.5") + " " + Float.valueOf("-0.75") + " " + Double.valueOf(1.0 / 3)
				+ " " + Float.valueOf(1.0f / 3));
	}

	static String bits(String name, boolean[] set) {
		StringBuilder sb = new StringBuilder(name);
		sb.append(':');
		int start = -1;
		for (int c = 0; c <= set.length; c++) {
			boolean on = c < set.length && set[c];
			if (on && start < 0) {
				start = c;
			} else if (!on && start >= 0) {
				sb.append(' ').append(Integer.toHexString(start));
				if (c - 1 > start) {
					sb.append('-').append(Integer.toHexString(c - 1));
				}
				start = -1;
			}
		}
		return sb.toString();
	}

	static void characters() {
		out("Character: " + Character.MIN_RADIX + " " + Character.MAX_RADIX + " " + (int) Character.MIN_VALUE + " "
				+ (int) Character.MAX_VALUE + " " + Character.MIN_CODE_POINT + " " + Character.MAX_CODE_POINT + " "
				+ Character.MIN_SUPPLEMENTARY_CODE_POINT + " " + (int) Character.MIN_HIGH_SURROGATE + " "
				+ (int) Character.MAX_HIGH_SURROGATE + " " + (int) Character.MIN_LOW_SURROGATE + " "
				+ (int) Character.MAX_LOW_SURROGATE + " " + (int) Character.MIN_SURROGATE + " "
				+ (int) Character.MAX_SURROGATE + " " + Character.SIZE + " " + Character.BYTES);
		int n = 256;
		boolean[] letter = new boolean[n];
		boolean[] letterInt = new boolean[n];
		boolean[] alphabetic = new boolean[n];
		boolean[] digit = new boolean[n];
		boolean[] letterOrDigit = new boolean[n];
		boolean[] upper = new boolean[n];
		boolean[] lower = new boolean[n];
		boolean[] whitespace = new boolean[n];
		boolean[] spaceChar = new boolean[n];
		boolean[] control = new boolean[n];
		boolean[] identStart = new boolean[n];
		boolean[] identPart = new boolean[n];
		boolean[] ignorable = new boolean[n];
		boolean[] surrogate = new boolean[n];
		StringBuilder upperMap = new StringBuilder("toUpperCase:");
		StringBuilder lowerMap = new StringBuilder("toLowerCase:");
		StringBuilder numeric = new StringBuilder("getNumericValue:");
		for (int c = 0; c < n; c++) {
			char ch = (char) c;
			letter[c] = Character.isLetter(ch);
			letterInt[c] = Character.isLetter(c);
			alphabetic[c] = Character.isAlphabetic(c);
			digit[c] = Character.isDigit(ch);
			letterOrDigit[c] = Character.isLetterOrDigit(ch);
			upper[c] = Character.isUpperCase(ch);
			lower[c] = Character.isLowerCase(ch);
			whitespace[c] = Character.isWhitespace(ch);
			spaceChar[c] = Character.isSpaceChar(ch);
			control[c] = Character.isISOControl(ch);
			identStart[c] = Character.isJavaIdentifierStart(ch);
			identPart[c] = Character.isJavaIdentifierPart(ch);
			ignorable[c] = Character.isIdentifierIgnorable(ch);
			surrogate[c] = Character.isSurrogate(ch);
			if (Character.toUpperCase(c) != c) {
				upperMap.append(' ').append(Integer.toHexString(c)).append('>')
						.append(Integer.toHexString(Character.toUpperCase(c)));
			}
			if (Character.toUpperCase(ch) != Character.toUpperCase(c)) {
				upperMap.append(" char(").append(Integer.toHexString(c)).append(")>")
						.append(Integer.toHexString(Character.toUpperCase(ch)));
			}
			if (Character.toLowerCase(ch) != ch) {
				lowerMap.append(' ').append(Integer.toHexString(c)).append('>')
						.append(Integer.toHexString(Character.toLowerCase(ch)));
			}
			if (Character.getNumericValue(ch) != -1) {
				numeric.append(' ').append(Integer.toHexString(c)).append('=').append(Character.getNumericValue(ch));
			}
		}
		out(bits("isLetter", letter));
		out(bits("isLetter(int)", letterInt));
		out(bits("isAlphabetic", alphabetic));
		out(bits("isDigit", digit));
		out(bits("isLetterOrDigit", letterOrDigit));
		out(bits("isUpperCase", upper));
		out(bits("isLowerCase", lower));
		out(bits("isWhitespace", whitespace));
		out(bits("isSpaceChar", spaceChar));
		out(bits("isISOControl", control));
		out(bits("isJavaIdentifierStart", identStart));
		out(bits("isJavaIdentifierPart", identPart));
		out(bits("isIdentifierIgnorable", ignorable));
		out(bits("isSurrogate", surrogate));
		out(upperMap.toString());
		out(lowerMap.toString());
		out(numeric.toString());

		int[] spaces = { 0x1680, 0x2000, 0x2006, 0x2007, 0x2008, 0x200a, 0x200b, 0x2028, 0x2029, 0x202f, 0x205f,
				0x3000, 0xfeff };
		String spaceText = "spaces:";
		for (int c : spaces) {
			spaceText += " " + Integer.toHexString(c) + "=" + Character.isWhitespace(c) + "/" + Character.isSpaceChar(c)
					+ "/" + Character.isWhitespace((char) c);
		}
		out(spaceText);
		out("full width: " + Character.digit('\uff17', 10) + " " + Character.digit('\uff41', 16) + " "
				+ Character.digit('\uff3a', 36) + " " + Character.getNumericValue('\uff19') + " "
				+ Character.getNumericValue('\uff5a') + " " + Character.isDigit('\uff10') + " "
				+ Character.isDigit(0xff19) + " " + Character.digit(0xff10, 2));
		out("digit: " + Character.digit('7', 8) + " " + Character.digit('8', 8) + " " + Character.digit('z', 36) + " "
				+ Character.digit('Z', 35) + " " + Character.digit('a', 1) + " " + Character.digit('a', 37) + " "
				+ Character.digit(0x10000, 10) + " forDigit " + Character.forDigit(11, 16) + " "
				+ (int) Character.forDigit(16, 16) + " " + (int) Character.forDigit(1, 37) + " "
				+ (int) Character.forDigit(-1, 10) + " " + Character.forDigit(35, 36));

		char high = '\ud83d';
		char low = '\ude00';
		out("surrogates: " + Character.isHighSurrogate(high) + " " + Character.isLowSurrogate(high) + " "
				+ Character.isLowSurrogate(low) + " " + Character.isHighSurrogate(low) + " "
				+ Character.isSurrogate(high) + " " + Character.isSurrogate('a') + " "
				+ Character.isSurrogatePair(high, low) + " " + Character.isSurrogatePair(low, high) + " "
				+ Character.toCodePoint(high, low) + " " + Integer.toHexString(Character.highSurrogate(0x1f600)) + " "
				+ Integer.toHexString(Character.lowSurrogate(0x1f600)) + " " + Character.charCount(0xffff) + " "
				+ Character.charCount(0x10000));
		char[] pair = { 'x', high, low, high };
		out("codePointAt: " + Character.codePointAt(pair, 0) + " " + Character.codePointAt(pair, 1) + " "
				+ Character.codePointAt(pair, 2) + " " + Character.codePointAt(pair, 3) + " "
				+ Character.codePointAt(new String(pair), 1) + " " + Character.codePointAt("a" + high, 1));
		char[] dst = new char[4];
		int written = Character.toChars(0x10ffff, dst, 1);
		out("toChars into array: " + written + " " + Integer.toHexString(dst[1]) + " " + Integer.toHexString(dst[2])
				+ " " + Character.toChars('Q', dst, 0) + " " + dst[0]);
		toChars(65);
		toChars(0xe9);
		toChars(0xd800);
		toChars(0xffff);
		toChars(0x10000);
		toChars(0x1f600);
		toChars(0x10ffff);
		toChars(0x110000);
		toChars(-1);
		toChars(Integer.MIN_VALUE);
		out("toString(int): " + Character.toString(0x41) + Character.toString(0x7a) + " "
				+ Character.toString(0x1f600).equals(new String(new char[] { high, low })));
		Character c1 = 'a';
		Character c2 = 'a';
		Character c3 = '\u1234';
		out("Character boxes: " + (c1 == c2) + " " + c3.equals(Character.valueOf('\u1234')) + " " + c1.compareTo('d')
				+ " " + Character.compare('z', 'a') + " " + c3.hashCode() + " " + Character.hashCode('A') + " "
				+ c1.charValue() + " " + Character.toString('q'));
	}

	public static void main(String[] args) {
		integers();
		parsing();
		boxes();
		floats();
		characters();
	}
}

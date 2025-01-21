package union.utils.encoding;

import org.jetbrains.annotations.NotNull;

public class Base62 {
	public static String encode(long value) {
		if (value < 0) throw new IllegalArgumentException("Negative value provided.");
		if (value == 0) return "0";

		final StringBuilder result = new StringBuilder();

		do {
			int remainder = (int) (value % 62);
			result.append(BASE62.charAt(remainder));
			value /= 62;
		} while (value > 0);

		return result.reverse().toString();
	}

	public static long decode(@NotNull String encoded) {
		if (!isBase62Encoding(encoded.toCharArray())) {
			throw new IllegalArgumentException("Input is not encoded correctly");
		}
		if (encoded.equals("0")) return 0L;

		long result = 0;

		for (char c : encoded.toCharArray()) {
			int value = BASE62.indexOf(c);
			result = result * 62 + value;
		}

		return result;
	}

	/**
	 * Checks whether a sequence of characters is encoded over a Base62 alphabet.
	 *
	 * @param chars a sequence of characters.
	 * @return {@code true} when the bytes are encoded over a Base62 alphabet, {@code false} otherwise.
	 */
	private static boolean isBase62Encoding(final char[] chars) {
		if (chars == null) {
			return false;
		}

		for (final char c : chars) {
			if (BASE62.indexOf(c) == -1)
				return false;
		}

		return true;
	}

	private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
}

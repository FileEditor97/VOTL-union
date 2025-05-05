package union.utils.encoding;

public final class EncodingUtil {
	private EncodingUtil() {}
	/**
	 * @param channelId Channel ID
	 * @return Filename 'transcript-[encoded channelId].html'
	 */
	public static String encodeTranscript(final long channelId) {
		return "transcript-%s.html".formatted(encode(channelId));
	}

	/**
	 * @param guildId Guild ID
	 * @param userId User ID
	 * @return Filename 'profile-[encoded guildId:userId].png'
	 */
	public static String encodeUserBg(final long guildId, final long userId) {
		return "profile-%s.png".formatted(encode(guildId, userId));
	}

	/**
	 * @param guildId Guild ID
	 * @param userId User ID
	 * @param epochSeconds Epoch seconds (now)
	 * @return Filename 'modstats-[encoded guildId:userId:timestamp].png'
	 */
	public static String encodeModstats(final long guildId, final long userId, final long epochSeconds) {
		return "modstats-%s.png".formatted(encode(guildId, userId, epochSeconds));
	}

	/**
	 * @param guildId Guild ID
	 * @param epochSeconds Epoch seconds (now)
	 * @return Filename 'modreport-[encoded guildId:timestamp].png'
	 */
	public static String encodeModreport(final long guildId, final long epochSeconds) {
		return "modreport-%s.png".formatted(encode(guildId, epochSeconds));
	}

	/**
	 * @param id Message or Channel ID
	 * @param epochSeconds Epoch seconds (now)
	 * @return Filename 'msg-[encoded ID:timestamp].txt'
	 */
	public static String encodeMessage(final long id, final long epochSeconds) {
		return "msg-%s.txt".formatted(encode(id, epochSeconds));
	}

	/**
	 * @param epochSeconds Epoch seconds (now)
	 * @return Filename 'bulk-account-[encoded timestamp].txt'
	 */
	public static String encodeBulkAccount(final long epochSeconds) {
		return "bulk-account-%s.txt".formatted(encode(epochSeconds));
	}

	/**
	 * Encodes a sequence of values in Base62 encoding.
	 *
	 * @param values a Long sequence.
	 * @return a string with Base62-encoded values.
	 */
	private static String encode(final long... values) {
		if (values.length == 0) throw new IllegalArgumentException("No values provided.");
		String[] results = new String[values.length];
		for (int i = 0; i < values.length; i++) {
			results[i] = Base62.encode(values[i]);
		}
		return String.join("-", results);
	}

}

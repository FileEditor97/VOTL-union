package union.utils.encoding;

import org.jetbrains.annotations.NotNull;

public class EncodingUtil {
	private final Base62 base62;

	public EncodingUtil() {
		base62 = Base62.createInstance();
	}

	public Base62 getBase62() {
		return base62;
	}

	/**
	 * @param guildId Guild ID
	 * @param channelId Channel ID
	 * @return Filename 'transcript-[encoded guildId:channelId].html'
	 */
	public String encodeTranscript(final long guildId, final long channelId) {
		return "transcript-%s.html".formatted(encode(String.valueOf(guildId), String.valueOf(channelId)));
	}

	/**
	 * @param guildId Guild ID
	 * @param userId User ID
	 * @return Filename 'user_bg-[encoded guildId:userId].png'
	 */
	public String encodeUserBg(final long guildId, final long userId) {
		return "user_bg-%s.png".formatted(encode(String.valueOf(guildId), String.valueOf(userId)));
	}

	/**
	 * @param guildId Guild ID
	 * @param userId User ID
	 * @param epochSeconds Epoch seconds (now)
	 * @return Filename 'modstats-[encoded guildId:userId]-timestamp.png'
	 */
	public String encodeModstats(final long guildId, final long userId, final long epochSeconds) {
		return "modstats-%s-%s.png".formatted(encode(String.valueOf(guildId), String.valueOf(userId)), epochSeconds);
	}

	/**
	 * @param guildId Guild ID
	 * @param epochSeconds Epoch seconds (now)
	 * @return Filename 'modstats-[encoded guildId:userId]-timestamp.png'
	 */
	public String encodeModreport(final long guildId, final long epochSeconds) {
		return "modreport-%s-%s.png".formatted(encode(String.valueOf(guildId)), epochSeconds);
	}

	private String encode(@NotNull String... strings) {
		if (strings.length == 0) throw new IllegalArgumentException("No values provided.");
		return new String(base62.encode(String.join(":", strings).getBytes()));
	}
}

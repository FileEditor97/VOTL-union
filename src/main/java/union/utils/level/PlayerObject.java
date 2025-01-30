package union.utils.level;

import net.dv8tion.jda.api.entities.Member;

public class PlayerObject {
	public final long guildId, userId;

	public PlayerObject(long guildId, long userId) {
		this.guildId = guildId;
		this.userId = userId;
	}

	public PlayerObject(Member member) {
		this.guildId = member.getGuild().getIdLong();
		this.userId = member.getIdLong();
	}

	public String asKey() {
		return guildId + ":" + userId;
	}

	public static String asKey(long guildId, long userId) {
		return guildId + ":" + userId;
	}

	public static PlayerObject fromKey(String key) {
		String[] split = key.split(":");
		if (split.length != 2)
			throw new IllegalArgumentException("Invalid key: " + key);
		return new PlayerObject(Long.parseLong(split[0]), Long.parseLong(split[1]));
	}
}

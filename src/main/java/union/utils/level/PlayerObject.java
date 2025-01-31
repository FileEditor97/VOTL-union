package union.utils.level;

import net.dv8tion.jda.api.entities.Member;

import java.util.Objects;

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

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		PlayerObject that = (PlayerObject) o;
		return guildId == that.guildId && userId == that.userId;
	}

	@Override
	public int hashCode() {
		return Objects.hash(guildId, userId);
	}
}

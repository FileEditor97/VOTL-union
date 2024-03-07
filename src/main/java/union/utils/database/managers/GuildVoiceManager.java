package union.utils.database.managers;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class GuildVoiceManager extends LiteDBBase {

	public GuildVoiceManager(ConnectionUtil cu) {
		super(cu, "guildVoice");
	}

	public void setup(long guildId, long categoryId, long channelId) {
		execute("INSERT INTO %s(guildId, categoryId, channelId) VALUES (%d, %d, %d) ON CONFLICT(guildId) DO UPDATE SET categoryId=%3$d, channelId=%4$d"
			.formatted(table, guildId, categoryId, channelId));
	}

	public void remove(long guildId) {
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public void setName(long guildId, String defaultName) {
		execute("INSERT INTO %s(guildId, defaultName) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET defaultName=%<s"
			.formatted(table, guildId, quote(defaultName)));
	}

	public void setLimit(long guildId, int defaultLimit) {
		execute("INSERT INTO %s(guildId, defaultLimit) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET defaultLimit=%<d"
			.formatted(table, guildId, defaultLimit));
	}

	public Long getCategoryId(long guildId) {
		return selectOne("SELECT categoryId FROM %s WHERE (guildId=%d)".formatted(table, guildId), "categoryId", Long.class);
	}

	public Long getChannelId(long guildId) {
		return selectOne("SELECT channelId FROM %s WHERE (guildId=%d)".formatted(table, guildId), "channelId", Long.class);
	}

	public String getName(long guildId) {
		return selectOne("SELECT defaultName FROM %s WHERE (guildId=%d)".formatted(table, guildId), "defaultName", String.class);
	}

	public Integer getLimit(long guildId) {
		return selectOne("SELECT defaultLimit FROM %s WHERE (guildId=%d)".formatted(table, guildId), "defaultLimit", Integer.class);
	}
}

package union.utils.database.managers;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class GuildVoiceManager extends LiteDBBase {
	
	private final String table = "guildVoice";

	public GuildVoiceManager(ConnectionUtil cu) {
		super(cu);
	}

	public void setup(String guildId, String categoryId, String channelId) {
		execute("INSERT INTO %s(guildId, categoryId, channelId) VALUES (%s, %s, %s) ON CONFLICT(guildId) DO UPDATE SET categoryId=%s, channelId=%s"
			.formatted(table, guildId, categoryId, channelId, categoryId, channelId));
	}

	public void remove(String guildId) {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public void setName(String guildId, String defaultName) {
		execute("INSERT INTO %s(guildId, defaultName) VALUES (%s, %s) ON CONFLICT(guildId) DO UPDATE SET defaultName=%s"
			.formatted(table, guildId, quote(defaultName), quote(defaultName)));
	}

	public void setLimit(String guildId, Integer defaultLimit) {
		execute("INSERT INTO %s(guildId, defaultLimit) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET defaultLimit=%d"
			.formatted(table, guildId, defaultLimit, defaultLimit));
	}

	public String getCategory(String guildId) {
		return selectOne("SELECT categoryId FROM %s WHERE (guildId=%s)".formatted(table, guildId), "categoryId", String.class);
	}

	public String getChannel(String guildId) {
		return selectOne("SELECT channelId FROM %s WHERE (guildId=%s)".formatted(table, guildId), "channelId", String.class);
	}

	public String getName(String guildId) {
		return selectOne("SELECT defaultName FROM %s WHERE (guildId=%s)".formatted(table, guildId), "defaultName", String.class);
	}

	public Integer getLimit(String guildId) {
		return selectOne("SELECT defaultLimit FROM %s WHERE (guildId=%s)".formatted(table, guildId), "defaultLimit", Integer.class);
	}
}

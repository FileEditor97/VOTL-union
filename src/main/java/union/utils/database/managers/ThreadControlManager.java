package union.utils.database.managers;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import java.sql.SQLException;
import java.util.List;

public class ThreadControlManager extends LiteDBBase {
	public ThreadControlManager(ConnectionUtil cu) {
		super(cu, "threadControl");
	}

	public void add(long guildId, long channelId) throws SQLException {
		execute("INSERT INTO %s(guildId, channelId) VALUES (%s, %s)".formatted(table, guildId, channelId));
	}

	public void remove(long channelId) throws SQLException {
		execute("DELETE FROM %s WHERE (channelId=%s)".formatted(table, channelId));
	}

	public void removeAll(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public boolean exist(long channelId) {
		return getGuildId(channelId) != null;
	}

	public Long getGuildId(long channelId) {
		return selectOne("SELECT guildId FROM %s WHERE (channelId=%s)".formatted(table, channelId), "guildId", Long.class);
	}

	public List<Long> getChannelIds(long guildId) {
		return select("SELECT channelId FROM %s WHERE (guildId=%s)".formatted(table, guildId), "channelId", Long.class);
	}
}

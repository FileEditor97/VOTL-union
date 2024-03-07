package union.utils.database.managers;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class VoiceChannelManager extends LiteDBBase {
	
	public VoiceChannelManager(ConnectionUtil cu) {
		super(cu, "voiceChannels");
	}

	public void add(long userId, long channelId) {
		execute("INSERT INTO %s(userId, channelId) VALUES (%d, %d) ON CONFLICT(channelId) DO UPDATE SET channelId=%<d".formatted(table, userId, channelId));
	}

	public void remove(long channelId) {
		execute("DELETE FROM %s WHERE (channelId=%d)".formatted(table, channelId));
	}

	public boolean existsUser(long userId) {
		return getChannel(userId) != null;
	}

	public boolean existsChannel(long channelId) {
		return getUser(channelId) != null;
	}

	public void setUser(long channelId, long userId) {
		execute("UPDATE %s SET userId=%s WHERE (channelId=%d)".formatted(table, userId, channelId));
	}

	public Long getChannel(long userId) {
		return selectOne("SELECT channelId FROM %s WHERE (userId=%d)".formatted(table, userId), "channelId", Long.class);
	}

	public Long getUser(long channelId) {
		return selectOne("SELECT userId FROM %s WHERE (channelId=%d)".formatted(table, channelId), "userId", Long.class);
	}
}

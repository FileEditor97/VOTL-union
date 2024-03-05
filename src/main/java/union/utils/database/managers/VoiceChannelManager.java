package union.utils.database.managers;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class VoiceChannelManager extends LiteDBBase {
	
	public VoiceChannelManager(ConnectionUtil cu) {
		super(cu, "voiceChannel");
	}

	public void add(String userId, String channelId) {
		execute("INSERT INTO %s(userId, channelId) VALUES (%s, %s) ON CONFLICT(channelId) DO UPDATE SET channelId=%s".formatted(table, userId, channelId, channelId));
	}

	public void remove(String channelId) {
		execute("DELETE FROM %s WHERE (channelId=%s)".formatted(table, channelId));
	}

	public boolean existsUser(String userId) {
		return selectOne("SELECT userId FROM %s WHERE (userId=%s)".formatted(table, userId), "userId", String.class) != null;
	}

	public boolean existsChannel(String channelId) {
		return selectOne("SELECT channelId FROM %s WHERE (channelId=%s)".formatted(table, channelId), "channelId", String.class) != null;
	}

	public void setUser(String channelId, String userId) {
		execute("UPDATE %s SET userId=%s WHERE (channelId=%s)".formatted(table, userId, channelId));
	}

	public String getChannel(String userId) {
		return selectOne("SELECT channelId FROM %s WHERE (userId=%s)".formatted(table, userId), "channelId", String.class);
	}

	public String getUser(String channelId) {
		return selectOne("SELECT userId FROM %s WHERE (channelId=%s)".formatted(table, channelId), "userId", String.class);
	}
}

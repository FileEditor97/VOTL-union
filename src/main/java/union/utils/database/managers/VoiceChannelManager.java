package union.utils.database.managers;

import java.util.List;

import union.utils.database.DBUtil;
import union.utils.database.LiteDBBase;

public class VoiceChannelManager extends LiteDBBase {
	
	private final String TABLE = "voiceChannel";
	
	public VoiceChannelManager(DBUtil util) {
		super(util);
	}

	public void add(String userId, String channelId) {
		insert(TABLE, List.of("userId", "channelId"), List.of(userId, channelId));
	}

	public void remove(String channelId) {
		delete(TABLE, "channelId", channelId);
	}

	public boolean existsUser(String userId) {
		if (selectOne(TABLE, "userId", "userId", userId) == null) return false;
		return true;
	}

	public boolean existsChannel(String channelId) {
		if (selectOne(TABLE, "channelId", "channelId", channelId) == null) return false;
		return true;
	}

	public void setUser(String channelId, String userId) {
		update(TABLE, "userId", userId, "channelId", channelId);
	}

	public String getChannel(String userId) {
		Object data = selectOne(TABLE, "channelId", "userId", userId);
		if (data == null) return null;
		return (String) data;
	}

	public String getUser(String channelId) {
		Object data = selectOne(TABLE, "userId", "channelId", channelId);
		if (data == null) return null;
		return (String) data;
	}
}

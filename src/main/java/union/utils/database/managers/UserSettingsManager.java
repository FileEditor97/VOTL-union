package union.utils.database.managers;

import union.utils.database.DBUtil;
import union.utils.database.LiteDBBase;

public class UserSettingsManager extends LiteDBBase {
	
	private final String TABLE = "user";

	public UserSettingsManager(DBUtil util) {
		super(util);
	}

	public void add(String userId) {
		insert(TABLE, "userId", userId);
	}

	public void remove(String userId) {
		delete(TABLE, "userId", userId);
	}

	public boolean exists(String userId) {
		if (selectOne(TABLE, "userId", "userId", userId) == null) return false;
		return true;
	}

	public void setName(String userId, String channelName) {
		update(TABLE, "voiceName", channelName, "userId", userId);
	}

	public void setLimit(String userId, Integer channelLimit) {
		update(TABLE, "voiceLimit", channelLimit, "userId", userId);
	}

	public String getName(String userId) {
		Object data = select(TABLE, "voiceName", "userId", userId);
		if (data == null) return null;
		return (String) data;
	}

	public Integer getLimit(String userId) {
		Object data = select(TABLE, "voiceLimit", "userId", userId);
		if (data == null) return null;
		return (Integer) data;
	}

}

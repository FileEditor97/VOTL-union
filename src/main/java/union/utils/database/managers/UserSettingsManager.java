package union.utils.database.managers;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class UserSettingsManager extends LiteDBBase {

	public UserSettingsManager(ConnectionUtil cu) {
		super(cu, "user");
	}

	public void remove(String userId) {
		execute("DELETE FROM %s WHERE (userId=%s)".formatted(table, userId));
	}

	public void setName(String userId, String channelName) {
		execute("INSERT INTO %s(userId, voiceName) VALUES (%s, %s) ON CONFLICT(userId) DO UPDATE SET voiceName=%s".formatted(table, userId, quote(channelName), quote(channelName)));
	}

	public void setLimit(String userId, Integer channelLimit) {
		execute("INSERT INTO %s(userId, voiceLimit) VALUES (%s, %d) ON CONFLICT(userId) DO UPDATE SET voiceLimit=%d".formatted(table, userId, channelLimit, channelLimit));
	}

	public String getName(String userId) {
		return selectOne("SELECT voiceName FROM %s WHERE (userId=%s)".formatted(table, userId), "voiceName", String.class);
	}

	public Integer getLimit(String userId) {
		return selectOne("SELECT voiceLimit FROM %s WHERE (userId=%s)".formatted(table, userId), "voiceLimit", Integer.class);
	}

}

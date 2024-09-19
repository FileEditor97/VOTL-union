package union.utils.database.managers;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class UserSettingsManager extends LiteDBBase {
	public UserSettingsManager(ConnectionUtil cu) {
		super(cu, "users");
	}

	public void remove(long userId) {
		execute("DELETE FROM %s WHERE (userId=%s)".formatted(table, userId));
	}

	public void setName(long userId, String channelName) {
		execute("INSERT INTO %s(userId, voiceName) VALUES (%d, %s) ON CONFLICT(userId) DO UPDATE SET voiceName=%<s".formatted(table, userId, quote(channelName)));
	}

	public void setLimit(long userId, int channelLimit) {
		execute("INSERT INTO %s(userId, voiceLimit) VALUES (%d, %d) ON CONFLICT(userId) DO UPDATE SET voiceLimit=%<d".formatted(table, userId, channelLimit));
	}

	public String getName(long userId) {
		return selectOne("SELECT voiceName FROM %s WHERE (userId=%d)".formatted(table, userId), "voiceName", String.class);
	}

	public Integer getLimit(long userId) {
		return selectOne("SELECT voiceLimit FROM %s WHERE (userId=%d)".formatted(table, userId), "voiceLimit", Integer.class);
	}
}

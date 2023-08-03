package union.utils.database.managers;

import union.utils.database.LiteDBBase;
import union.objects.constants.Constants;
import union.utils.database.DBUtil;

public class GuildSettingsManager extends LiteDBBase {

	private final String TABLE = "guild";
	
	public GuildSettingsManager(DBUtil util) {
		super(util);
	}

	public boolean add(String guildId) {
		if (!exists(guildId)) {
			insert(TABLE, "guildId", guildId);
			return true;
		}
		return false;
	}

	public void remove(String guildId) {
		delete(TABLE, "guildId", guildId);
	}

	public boolean exists(String guildId) {
		if (selectOne(TABLE, "guildId", "guildId", guildId) == null) return false;
		return true;
	}

	public void setColor(String guildId, Integer color) {
		update(TABLE, "color", color, "guildId", guildId);
	}

	public Integer getColor(String guildId) {
		Object data = selectOne(TABLE, "color", "guildId", guildId);
		if (data == null) return Constants.COLOR_DEFAULT;
		return Integer.decode(String.valueOf(data));
	}

	public void setModLogChannel(String guildId, String channelId) {
		update(TABLE, "modLogId", channelId, "guildId", guildId);
	}

	public void setGroupLogChannel(String guildId, String channelId) {
		update(TABLE, "groupLogId", channelId, "guildId", guildId);
	}

	public void setVerifyLogChannel(String guildId, String channelId) {
		update(TABLE, "verificationLogId", channelId, "guildId", guildId);
	}

	public void setTicketLogChannel(String guildId, String channelId) {
		update(TABLE, "ticketLogId", channelId, "guildId", guildId);
	}

	public String getModLogChannel(String guildId) {
		Object data = selectOne(TABLE, "modLogId", "guildId", guildId);
		if (data == null) return null;
		return String.valueOf(data);
	}

	public String getGroupLogChannel(String guildId) {
		Object data = selectOne(TABLE, "groupLogId", "guildId", guildId);
		if (data == null) return null;
		return String.valueOf(data);
	}

	public String getVerifyLogChannel(String guildId) {
		Object data = selectOne(TABLE, "verificationLogId", "guildId", guildId);
		if (data == null) return null;
		return String.valueOf(data);
	}

	public String getTicketLogChannel(String guildId) {
		Object data = selectOne(TABLE, "ticketLogId", "guildId", guildId);
		if (data == null) return null;
		return String.valueOf(data);
	}

}

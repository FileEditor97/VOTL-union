package union.utils.database.managers;

import union.utils.database.LiteDBBase;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import union.objects.LogChannels;
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
		return Integer.decode((String) data);
	}

	public void setupLogChannels(String guildId, String channelId) {
		update(TABLE, LogChannels.getAllNames(), Collections.nCopies(LogChannels.values().length, channelId), "guildId", guildId);
	}

	public void setLogChannel(LogChannels type, String guildId, String channelId) {
		update(TABLE, type.getDBName(), channelId, "guildId", guildId);
	}

	public String getLogChannel(LogChannels type, String guildId) {
		Object data = selectOne(TABLE, type.getDBName(), "guildId", guildId);
		if (data == null) return null;
		return (String) data;
	}

	public Map<LogChannels, String> getAllLogChannels(String guildId) {
		Map<String, Object> data = selectOne(TABLE, LogChannels.getAllNames(), "guildId", guildId);
		if (data.values().stream().allMatch(Objects::isNull)) return null;
		Map<LogChannels, String> result = new HashMap<>(data.size());
		data.forEach((log, id) -> result.put(LogChannels.of(log), (String) id));
		return result;
	}

	public void setLastWebhookId(String guildId, String webhookId) {
		update(TABLE, "lastWebhook", webhookId, "guildId", guildId);
	}

	public String getLastWebhookId(String guildId) {
		Object data = selectOne(TABLE, "lastWebhook", "guildId", guildId);
		if (data == null) return null;
		return (String) data;
	}

	public void setAppealLink(String guildId, String link) {
		update(TABLE, "appealLink", link, "guildId", guildId);
	}

	public String getAppealLink(String guildId) {
		Object data = selectOne(TABLE, "appealLink", "guildId", guildId);
		if (data == null) return null;
		return (String) data;
	}

	public void setReportChannelId(String guildId, String channelId) {
		update(TABLE, "reportChannelId", channelId, "guildId", guildId);
	}

	public String getReportChannelId(String guildId) {
		Object data = selectOne(TABLE, "reportChannelId", "guildId", guildId);
		if (data == null) return null;
		return (String) data;
	}

}

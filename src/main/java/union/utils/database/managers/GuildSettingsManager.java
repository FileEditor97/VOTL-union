package union.utils.database.managers;

import union.utils.database.LiteDBBase;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import union.objects.LogChannels;
import union.objects.constants.Constants;
import union.utils.database.ConnectionUtil;

public class GuildSettingsManager extends LiteDBBase {

	private final String table = "guild";
	
	public GuildSettingsManager(ConnectionUtil cu) {
		super(cu);
	}

	public void remove(String guildId) {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}
	
	public void setColor(String guildId, Integer color) {
		execute("INSERT INTO %s(guildId, color) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET color=%d".formatted(table, guildId, color, color));
	}

	public Integer getColor(String guildId) {
		String data = selectOne("SELECT color FROM %s WHERE (guildId=%s)".formatted(table, guildId), "color", String.class);
		if (data == null) return Constants.COLOR_DEFAULT;
		return Integer.decode(data);
	}

	public void setupLogChannels(String guildId, String channelId) {
		String updateList = LogChannels.getAllNames().stream().map(k -> k+"="+channelId).collect(Collectors.joining(", "));
		execute("INSERT INTO %s(guildId, %s) VALUES (%s, %s) ON CONFLICT(guildId) DO UPDATE SET %s"
			.formatted(table, String.join(", ", LogChannels.getAllNames()), guildId, String.join(", ", Collections.nCopies(LogChannels.values().length, channelId)), updateList));
	}

	public void setLogChannel(LogChannels type, String guildId, String channelId) {
		execute("INSERT INTO %s(guildId, %s) VALUES (%s, %s) ON CONFLICT(guildId) DO UPDATE SET %s=%s".formatted(table, type.getName(), guildId, channelId, type.getName(), channelId));
	}

	public String getLogChannel(LogChannels type, String guildId) {
		return selectOne("SELECT %s FROM %s WHERE (guildId=%s)".formatted(type.getName(), table, guildId), type.getName(), String.class);
	}

	public Map<LogChannels, String> getAllLogChannels(String guildId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (guildId=%s)".formatted(table, guildId), LogChannels.getAllNames());
		if (data==null || data.values().stream().allMatch(Objects::isNull)) return null;
		Map<LogChannels, String> result = new HashMap<>(data.size());
		data.forEach((log, id) -> result.put(LogChannels.of(log), (String) id));
		return data.entrySet().stream().collect(Collectors.toMap(e -> LogChannels.of(e.getKey()), e -> (String) e.getValue()));
	}

	public void setLastWebhookId(String guildId, String webhookId) {
		execute("INSERT INTO %s(guildId, lastWebhook) VALUES (%s, %s) ON CONFLICT(guildId) DO UPDATE SET lastWebhook=%s".formatted(table, guildId, webhookId, webhookId));
	}

	public String getLastWebhookId(String guildId) {
		return selectOne("SELECT lastWebhook FROM %s WHERE (guildId=%s)".formatted(table, guildId), "lastWebhook", String.class);
	}

	public void setAppealLink(String guildId, String link) {
		execute("INSERT INTO %s(guildId, appealLink) VALUES (%s, %s) ON CONFLICT(guildId) DO UPDATE SET appealLink=%s".formatted(table, guildId, quote(link), quote(link)));
	}

	public String getAppealLink(String guildId) {
		return selectOne("SELECT appealLink FROM %s WHERE (guildId=%s)".formatted(table, guildId), "appealLink", String.class);
	}

	public void setReportChannelId(String guildId, String channelId) {
		execute("INSERT INTO %s(guildId, reportChannelId) VALUES (%s, %s) ON CONFLICT(guildId) DO UPDATE SET reportChannelId=%s".formatted(table, guildId, channelId, channelId));
	}

	public String getReportChannelId(String guildId) {
		return selectOne("SELECT reportChannelId FROM %s WHERE (guildId=%s)".formatted(table, guildId), "reportChannelId", String.class);
	}

	public void setStrikeExpiresAfter(String guildId, Integer expiresAfter) {
		execute("INSERT INTO %s(guildId, strikeExpires) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET strikeExpires=%d".formatted(table, guildId, expiresAfter, expiresAfter));
	}

	public Integer getStrikeExpiresAfter(String guildId) {
		Integer data = selectOne("SELECT strikeExpires FROM %s WHERE (guildId=%s)".formatted(table, guildId), "strikeExpires", Integer.class);
		return data != null ? data : 7;
	}

}

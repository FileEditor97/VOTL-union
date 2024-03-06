package union.utils.database.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import union.objects.LogChannels;
import union.objects.constants.Constants;
import union.utils.FixedCache;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class GuildLogsManager extends LiteDBBase {

	// Cache
	private final FixedCache<Long, LogSettings> cache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE);
	private final LogSettings blankSettings = new LogSettings();
	
	public GuildLogsManager(ConnectionUtil cu) {
		super(cu, "logWebhooks");
	}

	public void setLogWebhook(LogChannels type, long guildId, WebhookData webhookData) {
		invalidateCache(guildId);
		String data = webhookData==null ? "NULL" : webhookData.encodeData();
		execute("INSERT INTO %s(guildId, %s) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET %2$s=%4$s".formatted(table, type.getName(), guildId, data));
	}

	public void removeLogWebhook(LogChannels type, long guildId) {
		invalidateCache(guildId);
		execute("UPDATE %s SET %s=NULL WHERE (guildId=%d)".formatted(table, type.getName(), guildId));
	}

	public void removeGuild(long guildId) {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public WebhookData getLogWebhook(LogChannels type, long guildId) {
		if (cache.contains(guildId))
			return cache.get(guildId).getWebhookData(type);
		LogSettings settings = applyNonNull(getData(guildId), data -> new LogSettings(data));
		if (settings == null)
			return null;
		cache.put(guildId, settings);
		return settings.getWebhookData(type);
	}

	private Map<String, Object> getData(long guildId) {
		return selectOne("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), LogChannels.getAllNames());
	}

	public LogSettings getSettings(long guildId) {
		if (cache.contains(guildId))
			return cache.get(guildId);
		LogSettings settings = applyNonNull(getData(guildId), data -> new LogSettings(data));
		if (settings == null)
			return blankSettings;
		cache.put(guildId, settings);
		return settings;
	}

	private void invalidateCache(long guildId) {
		cache.pull(guildId);
	}

	public class LogSettings {
		private final Map<LogChannels, WebhookData> logs;

		public LogSettings() {
			this.logs = new HashMap<>();
		}

		public LogSettings(Map<String, Object> data) {
			this.logs = new HashMap<>();
			data.forEach((log, str) -> logs.put( LogChannels.of(log), new WebhookData((String) str) ));
		}

		public WebhookData getWebhookData(LogChannels type) {
			return logs.getOrDefault(type, null);
		}

		public Map<LogChannels, Long> getChannels() {
			return logs.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getChannelId()));
		}

		public boolean isEmpty() {
			return logs.isEmpty();
		}
	}

	public static class WebhookData {
		private final long channelId, webhookId;
		private final String token;
		
		public WebhookData(long channelId, long webhookId, String token) {
			this.channelId = channelId;
			this.webhookId = webhookId;
			this.token = token;
		}

		public WebhookData(String data) {
			String[] array = data.split(":");
			this.channelId = Long.parseLong(array[0]);
			this.webhookId = Long.parseLong(array[1]);
			this.token = array[2];
		}

		public long getChannelId() {
			return channelId;
		}

		public long getWebhookId() {
			return webhookId;
		}

		public String getToken() {
			return token;
		}

		public String encodeData() {
			return channelId+":"+webhookId+":"+token;
		}
	}
}

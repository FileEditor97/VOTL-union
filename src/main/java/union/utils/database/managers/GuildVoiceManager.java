package union.utils.database.managers;

import union.objects.constants.Constants;
import union.utils.FixedCache;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import java.util.Map;
import java.util.Set;

import static union.utils.CastUtil.getOrDefault;

public class GuildVoiceManager extends LiteDBBase {

	private final Set<String> columns = Set.of(
		"categoryId", "channelId", "defaultName", "defaultLimit"
	);
	// Cache
	private final FixedCache<Long, VoiceSettings> cache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE);
	private final VoiceSettings blankSettings = new VoiceSettings();

	public GuildVoiceManager(ConnectionUtil cu) {
		super(cu, "guildVoice");
	}

	public boolean setup(long guildId, long categoryId, long channelId) {
		invalidateCache(guildId);
		return execute("INSERT INTO %s(guildId, categoryId, channelId) VALUES (%d, %d, %d) ON CONFLICT(guildId) DO UPDATE SET categoryId=%3$d, channelId=%4$d"
			.formatted(table, guildId, categoryId, channelId));
	}

	public void remove(long guildId) {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public boolean setName(long guildId, String defaultName) {
		invalidateCache(guildId);
		return execute("INSERT INTO %s(guildId, defaultName) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET defaultName=%<s"
			.formatted(table, guildId, quote(defaultName)));
	}

	public boolean setLimit(long guildId, int defaultLimit) {
		invalidateCache(guildId);
		return execute("INSERT INTO %s(guildId, defaultLimit) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET defaultLimit=%<d"
			.formatted(table, guildId, defaultLimit));
	}

	public VoiceSettings getSettings(long guildId) {
		if (cache.contains(guildId))
			return cache.get(guildId);
		VoiceSettings settings = applyNonNull(getData(guildId), VoiceSettings::new);
		if (settings == null)
			settings = blankSettings;
		cache.put(guildId, settings);
		return settings;
	}

	private Map<String, Object> getData(long guildId) {
		return selectOne("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), columns);
	}

	private void invalidateCache(long guildId) {
		cache.pull(guildId);
	}

	public static class VoiceSettings {
		private final Long categoryId, channelId;
		private final String defaultName;
		private final Integer defaultLimit;

		public VoiceSettings() {
			this.categoryId = null;
			this.channelId = null;
			this.defaultName = null;
			this.defaultLimit = null;
		}

		public VoiceSettings(Map<String, Object> data) {
			this.categoryId = getOrDefault(data.get("categoryId"), null);
			this.channelId = getOrDefault(data.get("channelId"), null);
			this.defaultName = getOrDefault(data.get("defaultName"), null);
			this.defaultLimit = getOrDefault(data.get("defaultLimit"), null);
		}

		public Long getChannelId() {
			return channelId;
		}

		public Long getCategoryId() {
			return categoryId;
		}

		public String getDefaultName() {
			return defaultName;
		}

		public Integer getDefaultLimit() {
			return defaultLimit;
		}
	}
}

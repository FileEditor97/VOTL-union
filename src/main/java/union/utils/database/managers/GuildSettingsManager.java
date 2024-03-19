package union.utils.database.managers;

import union.utils.database.LiteDBBase;

import net.dv8tion.jda.api.entities.Guild;

import java.util.Map;
import java.util.Set;

import union.objects.CmdModule;
import union.objects.annotation.Nullable;
import union.objects.constants.Constants;
import union.utils.FixedCache;
import union.utils.database.ConnectionUtil;
import static union.utils.CastUtil.castLong;

public class GuildSettingsManager extends LiteDBBase {

	private final Set<String> columns = Set.of("color", "lastWebhookId", "appealLink", "reportChannelId", "strikeExpires", "modulesOff");

	// Cache
	private final FixedCache<Long, GuildSettings> cache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE);
	private final GuildSettings blankSettings = new GuildSettings();
	
	public GuildSettingsManager(ConnectionUtil cu) {
		super(cu, "guild");
	}

	public GuildSettings getSettings(Guild guild) {
		return getSettings(guild.getIdLong());
	}

	public GuildSettings getSettings(long guildId) {
		if (cache.contains(guildId))
			return cache.get(guildId);
		GuildSettings settings = applyNonNull(getData(guildId), data -> new GuildSettings(data));
		if (settings == null)
			return blankSettings;
		cache.put(guildId, settings);
		return settings;
	}

	private Map<String, Object> getData(long guildId) {
		return selectOne("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), columns);
	}

	public void remove(long guildId) {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}
	
	public void setColor(long guildId, int color) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, color) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET color=%<d".formatted(table, guildId, color));
	}

	public void setLastWebhookId(long guildId, long webhookId) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, lastWebhookId) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET lastWebhookId=%<d".formatted(table, guildId, webhookId));
	}

	public void setAppealLink(long guildId, String link) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, appealLink) VALUES (%s, %s) ON CONFLICT(guildId) DO UPDATE SET appealLink=%<s".formatted(table, guildId, quote(link)));
	}

	public void setReportChannelId(long guildId, @Nullable Long channelId) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, reportChannelId) VALUES (%s, %s) ON CONFLICT(guildId) DO UPDATE SET reportChannelId=%<s".formatted(table, guildId, channelId==null ? "NULL" : channelId));
	}

	public void setStrikeExpiresAfter(long guildId, int expiresAfter) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, strikeExpires) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET strikeExpires=%<d".formatted(table, guildId, expiresAfter));
	}

	public void setModuleDisabled(long guildId, int modulesOff) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, modulesOff) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET modulesOff=%<d".formatted(table, guildId, modulesOff));
	}

	public void setAnticrash(long guildId, boolean enabled) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, anticrash) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET anticrash=%<d".formatted(table, guildId, enabled ? 1 : 0));
	}


	private void invalidateCache(long guildId) {
		cache.pull(guildId);
	}

	public class GuildSettings {
		private final Long lastWebhookId, reportChannelId;
		private final int color, strikeExpires, modulesOff;
		private final String appealLink;
		private final boolean anticrash;

		public GuildSettings() {
			this.color = Constants.COLOR_DEFAULT;
			this.lastWebhookId = null;
			this.appealLink = null;
			this.reportChannelId = null;
			this.strikeExpires = 7;
			this.modulesOff = 0;
			this.anticrash = false;
		}

		public GuildSettings(Map<String, Object> data) {
			this.color = Integer.decode((String) data.getOrDefault("color", Constants.COLOR_DEFAULT.toString()));
			this.lastWebhookId = castLong(data.getOrDefault("lastWebhookId", null));
			this.appealLink = (String) data.getOrDefault("appealLink", null);
			this.reportChannelId = castLong(data.getOrDefault("reportChannelId", null));
			this.strikeExpires = (Integer) data.getOrDefault("strikeExpires", 7);
			this.modulesOff = (Integer) data.getOrDefault("modulesOff", 0);
			this.anticrash = ((Integer) data.getOrDefault("anticrash", 0)) == 1;
		}

		public int getColor() {
			return color;
		}

		public Long getLastWebhookId() {
			return lastWebhookId;
		}

		public String getAppealLink() {
			return appealLink;
		}

		public Long getReportChannelId() {
			return reportChannelId;
		}

		public int getStrikeExpires() {
			return strikeExpires;
		}

		public int getModulesOff() {
			return modulesOff;
		}

		public Set<CmdModule> getDisabledModules() {
			return CmdModule.decodeModules(modulesOff);
		}

		public boolean isDisabled(CmdModule module) {
			return (modulesOff & module.getValue()) == module.getValue();
		}

		public boolean anticrashEnabled() {
			return anticrash;
		}

	}

}

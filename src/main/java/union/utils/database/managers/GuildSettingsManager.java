package union.utils.database.managers;

import union.utils.database.LiteDBBase;
import union.utils.file.lang.LocaleUtil;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import union.objects.CmdModule;
import union.objects.annotation.Nullable;
import union.objects.constants.Constants;
import union.utils.FixedCache;
import union.utils.database.ConnectionUtil;
import static union.utils.CastUtil.getOrDefault;
import static union.utils.CastUtil.resolveOrDefault;

public class GuildSettingsManager extends LiteDBBase {

	private final Set<String> columns = Set.of(
		"color", "lastWebhookId", "appealLink", "reportChannelId", "strikeExpires", "modulesOff",
		"anticrash", "informBan", "informKick", "informMute", "informStrike", "informDelstrike"
	);

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

	public void setInformBanLevel(long guildId, ModerationInformLevel informLevel) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, informBan) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET informBan=%<d".formatted(table, guildId, informLevel.getLevel()));
	}

	public void setInformKickLevel(long guildId, ModerationInformLevel informLevel) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, informKick) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET informKick=%<d".formatted(table, guildId, informLevel.getLevel()));
	}

	public void setInformMuteLevel(long guildId, ModerationInformLevel informLevel) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, informMute) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET informMute=%<d".formatted(table, guildId, informLevel.getLevel()));
	}

	public void setInformStrikeLevel(long guildId, ModerationInformLevel informLevel) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, informStrike) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET informStrike=%<d".formatted(table, guildId, informLevel.getLevel()));
	}

	public void setInformDelstrikeLevel(long guildId, ModerationInformLevel informLevel) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, informDelstrike) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET informDelstrike=%<d".formatted(table, guildId, informLevel.getLevel()));
	}


	private void invalidateCache(long guildId) {
		cache.pull(guildId);
	}

	public class GuildSettings {
		private final Long lastWebhookId, reportChannelId;
		private final int color, strikeExpires, modulesOff;
		private final String appealLink;
		private final boolean anticrash;
		private final ModerationInformLevel informBan, informKick, informMute, informStrike, informDelstrike;

		public GuildSettings() {
			this.color = Constants.COLOR_DEFAULT;
			this.lastWebhookId = null;
			this.appealLink = null;
			this.reportChannelId = null;
			this.strikeExpires = 7;
			this.modulesOff = 0;
			this.anticrash = false;
			this.informBan = ModerationInformLevel.DEFAULT;
			this.informKick = ModerationInformLevel.DEFAULT;
			this.informMute = ModerationInformLevel.DEFAULT;
			this.informStrike = ModerationInformLevel.DEFAULT;
			this.informDelstrike = ModerationInformLevel.DONT;
		}

		public GuildSettings(Map<String, Object> data) {
			this.color = resolveOrDefault(data.get("color"), obj -> Integer.decode(obj.toString()), Constants.COLOR_DEFAULT);
			this.lastWebhookId = getOrDefault(data.get("lastWebhookId"), null);
			this.appealLink = getOrDefault(data.get("appealLink"), null);
			this.reportChannelId = getOrDefault(data.get("reportChannelId"), null);
			this.strikeExpires = getOrDefault(data.get("strikeExpires"), 7);
			this.modulesOff = getOrDefault(data.get("modulesOff"), 0);
			this.anticrash = getOrDefault(data.get("anticrash"), 0) == 1;
			this.informBan = ModerationInformLevel.byLevel(getOrDefault(data.get("informBan"), 1));
			this.informKick = ModerationInformLevel.byLevel(getOrDefault(data.get("informKick"), 1));
			this.informMute = ModerationInformLevel.byLevel(getOrDefault(data.get("informMute"), 1));
			this.informStrike = ModerationInformLevel.byLevel(getOrDefault(data.get("informStrike"), 1));
			this.informDelstrike = ModerationInformLevel.byLevel(getOrDefault(data.get("informDelstrike"), 0));
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

		public ModerationInformLevel getInformBan() {
			return informBan;
		}

		public ModerationInformLevel getInformKick() {
			return informKick;
		}

		public ModerationInformLevel getInformMute() {
			return informMute;
		}

		public ModerationInformLevel getInformStrike() {
			return informStrike;
		}

		public ModerationInformLevel getInformDelstrike() {
			return informDelstrike;
		}
	}

	public enum ModerationInformLevel {
		DONT(0, "logger_embed.inform.0"),
		DEFAULT(1, "logger_embed.inform.1"),
		REASON(2, "logger_embed.inform.2"),
		MOD(3, "logger_embed.inform.3");

		private int level;
		private String path;

		private static final Map<Integer, ModerationInformLevel> BY_LEVEL = new HashMap<Integer, ModerationInformLevel>();

		static {
			for (ModerationInformLevel informLevel : ModerationInformLevel.values()) {
				BY_LEVEL.put(informLevel.getLevel(), informLevel);
			}
		}

		ModerationInformLevel(int level, String path) {
			this.level = level;
			this.path = path;
		}

		public int getLevel() {
			return level;
		}

		public String getPath() {
			return path;
		}

		public static ModerationInformLevel byLevel(int value) {
			return BY_LEVEL.get(value);
		}

		public static List<Choice> asChoices(LocaleUtil lu) {
			return Stream.of(values()).map(informLevel -> new Choice(lu.getText(informLevel.getPath()), informLevel.getLevel())).toList();
		}
	}

}

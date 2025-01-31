package union.utils.database.managers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.Guild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import union.objects.ExpType;
import union.objects.constants.Constants;
import union.utils.CastUtil;
import union.utils.FixedCache;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;
import union.utils.level.LevelUtil;
import union.utils.level.PlayerObject;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static union.utils.CastUtil.*;

@SuppressWarnings("unused")
public class LevelManager extends LiteDBBase {
	private final String TABLE_SETTINGS = "levelSettings";
	private final String TABLE_PLAYERS = "levelPlayers";

	// cache
	private final Cache<String, PlayerData> playersCache = Caffeine.newBuilder()
		.expireAfterAccess(5, TimeUnit.MINUTES)
		.build();
	private final FixedCache<Long, LevelSettings> settingsCache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE);
	private final LevelSettings blankSettings = new LevelSettings();

	public LevelManager(ConnectionUtil cu) {
		super(cu, null);
	}

	// Settings
	public LevelSettings getSettings(Guild guild) {
		return getSettings(guild.getIdLong());
	}

	@NotNull
	public LevelSettings getSettings(long guildId) {
		if (settingsCache.contains(guildId))
			return settingsCache.get(guildId);
		LevelSettings settings = applyNonNull(getSettingsData(guildId), LevelSettings::new);
		if (settings == null)
			settings = blankSettings;
		settingsCache.put(guildId, settings);
		return settings;
	}

	private Map<String, Object> getSettingsData(long guildId) {
		return selectOne("SELECT * FROM %s WHERE (guildId=%d)".formatted(TABLE_SETTINGS, guildId), Set.of("enabled", "exemptChannels", "enabledVoice"));
	}

	public void remove(long guildId) {
		invalidateSettings(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(TABLE_SETTINGS, guildId));
	}

	public boolean setEnabled(long guildId, boolean enabled) {
		invalidateSettings(guildId);
		return execute("INSERT INTO %s(guildId, enabled) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET enabled=%<d".formatted(TABLE_SETTINGS, guildId, enabled?1:0));
	}

	public boolean setExemptChannels(long guildId, @Nullable String channelIds) {
		invalidateSettings(guildId);
		return execute("INSERT INTO %s(guildId, exemptChannels) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET exemptChannels=%<s".formatted(TABLE_SETTINGS, guildId, quote(channelIds)));
	}

	public boolean setEnabledVoice(long guildId, boolean enabled) {
		invalidateSettings(guildId);
		return execute("INSERT INTO %s(guildId, enabledVoice) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET enabledVoice=%<d".formatted(TABLE_SETTINGS, guildId, enabled?1:0));
	}

	public void invalidateSettings(long guildId) {
		settingsCache.pull(guildId);
	}

	// Guild levels
	@NotNull
	public PlayerData getPlayer(long guildId, long userId) {
		String key = PlayerObject.asKey(guildId, userId);
		return playersCache.get(key, (k)->new PlayerData(getPlayerData(guildId, userId)));
	}

	@Nullable
	public PlayerData getPlayer(PlayerObject player) {
		return playersCache.getIfPresent(player.asKey());
	}

	private Map<String, Object> getPlayerData(long guildId, long userId) {
		return selectOne("SELECT * FROM %s WHERE (guildId=%d AND userId=%d)".formatted(TABLE_PLAYERS, guildId, userId), Set.of("textExp", "voiceExp", "lastUpdate"));
	}

	public void updatePlayer(PlayerObject player, PlayerData playerData) {
		execute("INSERT INTO %s(guildId, userId, textExp, voiceExp, globalExp, lastUpdate) VALUES (%d, %d, %d, %d, %d, %d) ON CONFLICT(guildId, userId) DO UPDATE SET textExp=%4$d, voiceExp=%4$d, globalExp=globalExp+%6$d, lastUpdate=%7$d;"
			.formatted(
				TABLE_PLAYERS, player.guildId, player.userId,
				playerData.getExperience(ExpType.TEXT), playerData.getExperience(ExpType.VOICE), playerData.getAddedGlobalExperience(), playerData.getLastUpdate()
			)
		);
	}

	public void addVoiceTime(PlayerObject player, long duration) {
		execute("INSERT INTO %s(guildId, userId, voiceTime) VALUES (%d, %d, %d) ON CONFLICT(guildId, userId) DO UPDATE SET voiceTime=voiceTime+%<d;"
			.formatted(TABLE_PLAYERS, player.guildId, player.userId, duration)
		);
	}

	public long getGlobalExp(long guildId, long userId) {
		Long data = selectOne("SELECT globalExp FROM %s WHERE (guildId=%d AND userId=%d)".formatted(TABLE_PLAYERS, guildId, userId), "globalExp", Long.class);
		return data==null?0:data;
	}

	public long getSumGlobalExp(long userId) {
		Long data = selectOne("SELECT SUM(globalExp) AS sumGlobalExp FROM %s WHERE (userId=%d)".formatted(TABLE_PLAYERS, userId), "sumGlobalExp", Long.class);
		return data==null?0:data;
	}

	public Integer getRankServer(long guildId, long userId, ExpType expType) {
		String type = expType==ExpType.TEXT?"textExp":"voiceExp";
		return selectOne("WITH rankedUsers AS (SELECT userId, guildId, %s, DENSE_RANK() OVER (PARTITION BY guildId ORDER BY %<s DESC) AS rank FROM %s) SELECT rank FROM rankedUsers WHERE (guildId=%d AND userId=%d)"
			.formatted(type, TABLE_PLAYERS, guildId, userId), "rank", Integer.class);
	}

	public Integer getRankGlobal(long userId) {
		return selectOne("WITH rankedUsers AS (SELECT userId, SUM(globalExp) as totalExp, DENSE_RANK() OVER (ORDER BY SUM(globalExp) DESC) AS rank FROM %s GROUP BY userId) SELECT rank FROM rankedUsers WHERE (userId=%d)"
			.formatted(TABLE_PLAYERS, userId), "rank", Integer.class);
	}

	public static class LevelSettings {
		private final boolean enabled, enabledVoice;
		private final Set<Long> exemptChannels;

		public LevelSettings() {
			this.enabled = false;
			this.exemptChannels = Set.of();
			this.enabledVoice = true;
		}

		public LevelSettings(Map<String, Object> data) {
			this.enabled = (int) requireNonNull(data.get("enabled"))==1;
			this.exemptChannels = resolveOrDefault(
				data.get("exemptChannels"),
				o -> Stream.of(String.valueOf(o).split(";"))
					.map(Long::parseLong)
					.collect(Collectors.toSet()),
				Set.of()
			);
			this.enabledVoice = (int) requireNonNull(data.get("enabledVoice"))==1;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public Set<Long> getExemptChannels() {
			return exemptChannels;
		}

		public boolean isExemptChannel(long channelId) {
			return exemptChannels.contains(channelId);
		}

		public boolean isVoiceEnabled() {
			return enabled && enabledVoice;
		}
	}

	public static class PlayerData {
		private long textExperience = 0;
		private long voiceExperience = 0;
		private long addedGlobalExperience = 0;
		private long lastUpdate = 0;

		PlayerData(Map<String, Object> data) {
			if (data != null) {
				BigInteger exp = new BigInteger(CastUtil.getOrDefault(String.valueOf(data.get("textExp")), "0"));
				if (exp.compareTo(BigInteger.valueOf(LevelUtil.getHardCap())) >= 0) {
					this.textExperience = LevelUtil.getHardCap();
				} else {
					this.textExperience = exp.longValue();
				}
				exp = new BigInteger(CastUtil.getOrDefault(String.valueOf(data.get("voiceExp")), "0"));
				if (exp.compareTo(BigInteger.valueOf(LevelUtil.getHardCap())) >= 0) {
					this.voiceExperience = LevelUtil.getHardCap();
				} else {
					this.voiceExperience = exp.longValue();
				}
				this.lastUpdate = getOrDefault(data.get("lastUpdate"), 0L);
			}
		}

		public long getExperience(ExpType expType) {
			return switch (expType) {
				case TEXT -> textExperience;
				case VOICE -> voiceExperience;
				case ALL -> textExperience+voiceExperience;
			};
		}

		public long getAddedGlobalExperience() {
			return addedGlobalExperience;
		}

		public void setExperience(long experience, ExpType expType) {
			switch (expType) {
				case TEXT -> textExperience = experience;
				case VOICE -> voiceExperience = experience;
			}
			this.lastUpdate = Instant.now().toEpochMilli();
		}

		public void incrementExperienceBy(long amount, ExpType expType) {
			switch (expType) {
				case TEXT -> textExperience += amount;
				case VOICE -> voiceExperience += amount;
			}
			this.addedGlobalExperience += amount;
			this.lastUpdate = Instant.now().toEpochMilli();
		}

		public long getLastUpdate() {
			return lastUpdate;
		}
	}
}

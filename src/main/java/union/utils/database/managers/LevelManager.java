package union.utils.database.managers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.Guild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import union.objects.constants.Constants;
import union.utils.CastUtil;
import union.utils.FixedCache;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;
import union.utils.level.LevelUtil;
import union.utils.level.PlayerObject;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static union.utils.CastUtil.*;

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
		return selectOne("SELECT * FROM %s WHERE (guildId=%d)".formatted(TABLE_SETTINGS, guildId), Set.of("enabled", "exemptChannels"));
	}

	public void remove(long guildId) {
		invalidateSettings(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(TABLE_SETTINGS, guildId));
	}

	public void invalidateSettings(long guildId) {
		settingsCache.pull(guildId);
	}

	public boolean setEnabled(long guildId, boolean enabled) {
		invalidateSettings(guildId);
		return execute("INSERT INTO %s(guildId, enabled) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET enabled=%<d".formatted(TABLE_SETTINGS, guildId, enabled?1:0));
	}

	public boolean setExemptChannels(long guildId, @Nullable String channelIds) {
		invalidateSettings(guildId);
		return execute("INSERT INTO %s(guildId, exemptChannels) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET exemptChannels=%<s".formatted(TABLE_SETTINGS, guildId, quote(channelIds)));
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
		return selectOne("SELECT * FROM %s WHERE (guildId=%d AND userId=%d)".formatted(TABLE_PLAYERS, guildId, userId), Set.of("exp", "lastUpdate"));
	}

	public void updatePlayer(PlayerObject player, PlayerData playerData) {
		execute("INSERT INTO %s(guildId, userId, exp, globalExp, lastUpdate) VALUES (%d, %d, %d, %d, %d) ON CONFLICT(guildId, userId) DO UPDATE SET exp=exp+%4$d, globalExp=globalExp+%5$d, lastUpdate=%6$d;"
			.formatted(
				TABLE_PLAYERS, player.guildId, player.userId,
				playerData.getExperience(), playerData.getAddedGlobalExperience(), playerData.getLastUpdate()
			)
		);
	}

	public static class LevelSettings {
		private final boolean enabled;
		private final Set<Long> exemptChannels;
		private final Map<Long, Long> levelRoles;

		public LevelSettings() {
			this.enabled = false;
			this.exemptChannels = Set.of();
			this.levelRoles = Map.of();
		}

		public LevelSettings(Map<String, Object> data) {
			this.enabled = requireNonNull(data.get("enabled"));
			this.exemptChannels = resolveOrDefault(
				data.get("exemptChannels"),
				o -> Stream.of(String.valueOf(o).split(";"))
					.map(Long::parseLong)
					.collect(Collectors.toSet()),
				Set.of()
			);
			this.levelRoles = new HashMap<>();
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

		public Map<Long, Long> getLevelRoles() {
			return levelRoles;
		}

		public Long getLevelRole(long level) {
			return levelRoles.get(level);
		}
	}

	public static class PlayerData {
		private long experience = 0;
		private long addedGlobalExperience = 0;
		private long lastUpdate = 0;

		PlayerData(Map<String, Object> data) {
			if (data != null) {
				BigInteger exp = new BigInteger(CastUtil.getOrDefault(data.get("exp"), "0"));
				if (exp.compareTo(BigInteger.valueOf(LevelUtil.getHardCap())) >= 0) {
					this.experience = LevelUtil.getHardCap();
				} else {
					this.experience = exp.longValue();
				}
				this.lastUpdate = getOrDefault(data.get("lastUpdate"), 0L);
			}
		}

		public long getExperience() {
			return experience;
		}

		public long getAddedGlobalExperience() {
			return addedGlobalExperience;
		}

		public void setExperience(long experience) {
			this.experience = experience;
			this.lastUpdate = Instant.now().toEpochMilli();
		}

		public void incrementExperienceBy(long amount) {
			this.experience += amount;
			this.addedGlobalExperience += amount;
			this.lastUpdate = Instant.now().toEpochMilli();
		}

		public long getLastUpdate() {
			return lastUpdate;
		}

		public boolean exists() {
			return experience > 0;
		}
	}
}

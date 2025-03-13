package union.utils.database.managers;

import static union.utils.CastUtil.getOrDefault;
import static union.utils.CastUtil.resolveOrDefault;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import union.objects.constants.Constants;
import union.utils.FixedCache;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import net.dv8tion.jda.api.entities.Guild;

@SuppressWarnings({"BooleanMethodIsAlwaysInverted", "UnusedReturnValue"})
public class VerifySettingsManager extends LiteDBBase {
	private final Set<String> columns = Set.of("roleId", "mainText", "checkEnabled", "minimumPlaytime", "additionalRoles");

	// Cache
	private final FixedCache<Long, VerifySettings> cache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE);
	private final VerifySettings blankSettings = new VerifySettings();

	public VerifySettingsManager(ConnectionUtil cu) {
		super(cu, "verifySettings");
	}

	public VerifySettings getSettings(Guild guild) {
		return getSettings(guild.getIdLong());
	}

	public VerifySettings getSettings(long guildId) {
		if (cache.contains(guildId))
			return cache.get(guildId);
		VerifySettings settings = applyNonNull(getData(guildId), VerifySettings::new);
		if (settings == null)
			settings = blankSettings;
		cache.put(guildId, settings);
		return settings;
	}

	private Map<String, Object> getData(long guildId) {
		return selectOne("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), columns);
	}

	public void remove(long guildId) throws SQLException {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public void setVerifyRole(long guildId, long roleId) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, roleId) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET roleId=%<d".formatted(table, guildId, roleId));
	}

	public void setMainText(long guildId, String text) throws SQLException {
		invalidateCache(guildId);
		final String textParsed = quote(text.replace("\\n", "<br>"));
		execute("INSERT INTO %s(guildId, mainText) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET mainText=%<s".formatted(table, guildId, textParsed));
	}

	public void setCheckState(long guildId, boolean enabled) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, checkEnabled) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET checkEnabled=%<d".formatted(table, guildId, enabled?1:0));
	}

	public void setRequiredPlaytime(long guildId, int hours) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, minimumPlaytime) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET minimumPlaytime=%<d".formatted(table, guildId, hours));
	}

	public void setAdditionalRoles(long guildId, @Nullable String roleIds) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, additionalRoles) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET additionalRoles=%<s".formatted(table, guildId, quote(roleIds)));
	}

	private void invalidateCache(long guildId) {
		cache.pull(guildId);
	}

	public static class VerifySettings {
		private final Long roleId;
		private final String mainText;
		private final boolean checkEnabled;
		private final int minimumPlaytime;
		private final Set<Long> additionalRoles;

		public VerifySettings() {
			this.roleId = null;
			this.mainText = null;
			this.checkEnabled = false;
			this.minimumPlaytime = -1;
			this.additionalRoles = Set.of();
		}

		public VerifySettings(Map<String, Object> data) {
			this.roleId = getOrDefault(data.get("roleId"), null);
			this.mainText = getOrDefault(data.get("mainText"), null);
			this.checkEnabled = getOrDefault(data.get("checkEnabled"), 0) == 1;
			this.minimumPlaytime = getOrDefault(data.get("minimumPlaytime"), -1);
			this.additionalRoles = resolveOrDefault(
				data.get("additionalRoles"),
				obj-> Stream.of(String.valueOf(obj).split(";"))
					.map(Long::parseLong)
					.collect(Collectors.toSet()),
				Set.of()
			);
		}

		public Long getRoleId() {
			return roleId;
		}

		public String getMainText() {
			return mainText;
		}

		public boolean isCheckEnabled() {
			return checkEnabled;
		}

		public int getMinimumPlaytime() {
			return minimumPlaytime;
		}

		public Set<Long> getAdditionalRoles() {
			return additionalRoles;
		}
	}
}

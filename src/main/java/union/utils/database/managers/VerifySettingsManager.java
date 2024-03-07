package union.utils.database.managers;

import java.util.Map;
import java.util.Set;

import union.objects.constants.Constants;
import union.utils.FixedCache;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;
import static union.utils.CastUtil.castLong;

import net.dv8tion.jda.api.entities.Guild;

public class VerifySettingsManager extends LiteDBBase {

	private final Set<String> columns = Set.of("roleId", "mainText", "checkEnabled");

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
		VerifySettings settings = applyNonNull(getData(guildId), data -> new VerifySettings(data));
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

	public void setVerifyRole(long guildId, long roleId) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, roleId) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET roleId=%<d".formatted(table, guildId, roleId));
	}

	public void setMainText(long guildId, String text) {
		invalidateCache(guildId);
		final String textParsed = quote(text.replace("\\n", "<br>"));
		execute("INSERT INTO %s(guildId, mainText) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET mainText=%<s".formatted(table, guildId, textParsed));
	}

	// manage check for verification role
	public void setCheckState(long guildId, boolean enabled) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, checkEnabled) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET checkEnabled=%<d".formatted(table, guildId, enabled?1:0));
	}

	private void invalidateCache(long guildId) {
		cache.pull(guildId);
	}

	public class VerifySettings {
		private final Long roleId;
		private final String mainText;
		private final boolean checkEnabled;

		public VerifySettings() {
			this.roleId = null;
			this.mainText = null;
			this.checkEnabled = false;
		}

		public VerifySettings(Map<String, Object> data) {
			this.roleId = castLong(data.getOrDefault("roleId", null));
			this.mainText = (String) data.getOrDefault("mainText", null);
			this.checkEnabled = ((int) data.getOrDefault("checkEnabled", 0)) == 1;
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
	}

}

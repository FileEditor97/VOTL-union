package union.utils.database.managers;

import java.util.List;
import java.util.Map;

import net.dv8tion.jda.internal.utils.tuple.Pair;
import union.utils.database.ConnectionUtil;
import union.utils.database.SqlDBBase;
import union.utils.file.SettingsManager;

public class UnionVerifyManager extends SqlDBBase {
	private final String TABLE = "union.users";
	private final SettingsManager settings;

	public UnionVerifyManager(ConnectionUtil cu, SettingsManager settings, String url, String user, String password) {
		super(cu, "%s?user=%s&password=%s".formatted(url, user, password));
		this.settings = settings;
	}

	public String getSteamName(String steam64) {
		if (settings.isDbVerifyDisabled()) return null;
		String data = selectOne(TABLE, "name", "steam_id", steam64);
		if (data == null || data.isBlank()) return null;
		return data;
	}

	// name, avatar
	public Pair<String, String> getSteamInfo(String steam64) {
		if (settings.isDbVerifyDisabled()) return null;
		Map<String, String> data = selectOne(TABLE, List.of("avatar", "name"), "steam_id", steam64);
		if (data == null || data.isEmpty()) return null;
		return Pair.of(data.get("name"), data.get("avatar"));
	}

	public Long getSteam64(String discordId) {
		if (settings.isDbVerifyDisabled()) return null;
		String data = selectOne(TABLE, "steam_id", "discord_id", discordId);
		if (data == null || data.isBlank()) return null;
		return Long.parseLong(data);
	}

	// Check for any changed accounts
	public List<Map<String, String>> updatedAccounts() {
		if (settings.isDbVerifyDisabled()) return List.of();
		return select(TABLE, List.of("discord_id", "steam_id"), "discord_updated", "1");
	}

	public void clearUpdated(String steam64) {
		update(TABLE, "discord_updated", "0", "steam_id", steam64);
	}
}

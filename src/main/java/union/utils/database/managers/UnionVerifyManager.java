package union.utils.database.managers;

import java.util.List;
import java.util.Map;

import union.utils.database.ConnectionUtil;
import union.utils.database.SqlDBBase;

public class UnionVerifyManager extends SqlDBBase {

	private final String TABLE = "union.users";

	public UnionVerifyManager(ConnectionUtil cu, String url, String user, String password) {
		super(cu, "%s?user=%s&password=%s".formatted(url, user, password));
	}

	public String getDiscordId(String steam64) {
		String data = selectOne(TABLE, "discord_id", "steam_id", steam64);
		if (data == null || data.isBlank()) return null;
		return data;
	}

	public String getSteamName(String steam64) {
		String data = selectOne(TABLE, "name", "steam_id", steam64);
		if (data == null || data.isBlank()) return null;
		return data;
	}

	public String getSteamAvatarUrl(String steam64) {
		String data = selectOne(TABLE, "avatar", "steam_id", steam64);
		if (data == null || data.isBlank()) return null;
		return data;
	}

	public String getSteam64(String discordId) {
		String data = selectOne(TABLE, "steam_id", "discord_id", discordId);
		if (data == null || data.isBlank()) return null;
		return data;
	}

	public boolean existsDiscord(String discordId) {
		return selectOne(TABLE, "steam_id", "discord_id", discordId) != null;
	}

	public boolean existsSteam(String steam64) {
		return selectOne(TABLE, "discord_id", "steam_id", steam64) != null;
	}

	// Check for any changed accounts
	public List<Map<String, String>> updatedAccounts() {
		return select(TABLE, List.of("discord_id", "steam_id"), "discord_updated", "1");
	}

	public void clearUpdated(String steam64) {
		update(TABLE, "discord_updated", "0", "steam_id", steam64);
	}
	
}

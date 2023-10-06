package union.utils.database.managers;

import java.util.List;
import java.util.Map;

import union.utils.database.DBUtil;
import union.utils.database.SqlDBBase;

public class UnionVerifyManager extends SqlDBBase {

	private String TABLE;

	public UnionVerifyManager(DBUtil util, String url, String user, String password) {
		super(util, "%s?user=%s&password=%s".formatted(url, user, password));
		this.TABLE = "union.users";
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
		if (selectOne(TABLE, "steam_id", "discord_id", discordId) == null) return false;
		return true;
	}

	public boolean existsSteam(String steam64) {
		if (selectOne(TABLE, "discord_id", "steam_id", steam64) == null) return false;
		return true;
	}

	// Check for any changed accounts
	public List<Map<String, String>> updatedAccounts() {
		List<Map<String, String>> data = select(TABLE, List.of("discord_id", "steam_id"), "discord_updated", "1");
		return data;
	}

	public void clearUpdated(String steam64) {
		update(TABLE, "discord_updated", "0", "steam_id", steam64);
	}
	
}

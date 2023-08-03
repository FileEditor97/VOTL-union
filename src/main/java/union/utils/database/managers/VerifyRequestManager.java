package union.utils.database.managers;

import java.util.List;
import java.util.Map;

import union.utils.database.DBUtil;
import union.utils.database.SqlDBBase;

public class VerifyRequestManager extends SqlDBBase {

	private String TABLE;

	public VerifyRequestManager(DBUtil util, String sqldb) {
		super(util);
		this.TABLE = sqldb + ".users";
	}

	public String getDiscordId(String steam64) {
		List<String> data = select(TABLE, "discord_id", "steam_id", steam64);
		if (data.isEmpty() || data.get(0) == null) return null;
		return data.get(0);
	}

	public String getSteamName(String steam64) {
		List<String> data = select(TABLE, "name", "steam_id", steam64);
		if (data.isEmpty() || data.get(0) == null) return null;
		return data.get(0);
	}

	public String getSteamAvatarUrl(String steam64) {
		List<String> data = select(TABLE, "avatar", "steam_id", steam64);
		if (data.isEmpty() || data.get(0) == null) return null;
		return data.get(0);
	}

	public String getSteam64(String discordId) {
		List<String> data = select(TABLE, "steam_id", "discord_id", discordId);
		if (data.isEmpty() || data.get(0) == null) return null;
		return data.get(0);
	}

	public boolean existsDiscord(String discordId) {
		if (select(TABLE, "steam_id", "discord_id", discordId).isEmpty()) {
			return false;
		}
		return true;
	}

	public boolean existsSteam(String steam64) {
		if (select(TABLE, "discord_id", "steam_id", steam64).isEmpty()) {
			return false;
		}
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

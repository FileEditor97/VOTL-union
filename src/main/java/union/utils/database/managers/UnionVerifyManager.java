package union.utils.database.managers;

import java.util.List;
import java.util.Map;

import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import union.objects.annotation.NotNull;
import union.utils.database.ConnectionUtil;
import union.utils.database.SqlDBBase;
import union.utils.file.SettingsManager;

public class UnionVerifyManager extends SqlDBBase {
	private final String TABLE_VERIFY = "union.users";
	private final String TABLE_FORUM = "union.xf_registered_users";
	private final SettingsManager settings;

	public UnionVerifyManager(ConnectionUtil cu, SettingsManager settings, String url, String user, String password) {
		super(cu, "%s?user=%s&password=%s".formatted(url, user, password));
		this.settings = settings;
	}

	@Nullable
	public String getSteamName(long steam64) {
		if (settings.isDbVerifyDisabled()) return null;
		String data = selectOne(TABLE_VERIFY, "name", "steam_id", steam64);
		if (data == null || data.isBlank()) return null;
		return data;
	}

	// name, avatar
	public Pair<String, String> getSteamInfo(long steam64) {
		if (settings.isDbVerifyDisabled()) return null;
		Map<String, String> data = selectOne(TABLE_VERIFY, List.of("avatar", "name"), "steam_id", steam64);
		if (data == null || data.isEmpty()) return null;
		return Pair.of(data.get("name"), data.get("avatar"));
	}

	@Nullable
	public Long getSteam64(@NotNull String discordId) {
		if (settings.isDbVerifyDisabled()) return null;
		String data = selectOne(TABLE_VERIFY, "steam_id", "discord_id", discordId);
		if (data == null || data.isBlank()) return null;
		return Long.parseLong(data);
	}

	// Check for any changed accounts
	public List<Map<String, String>> updatedAccounts() {
		if (settings.isDbVerifyDisabled()) return List.of();
		return select(TABLE_VERIFY, List.of("discord_id", "steam_id"), "discord_updated", "1");
	}

	public void clearUpdated(String steam64) {
		update(TABLE_VERIFY, "discord_updated", "0", "steam_id", steam64);
	}

	// get Steam64 associated with forum User's ID
	@Nullable
	public Long getSteam64(int xfUserId) {
		if (settings.isDbVerifyDisabled()) return null;
		String data = selectOne(TABLE_FORUM, "steam_id", "xf_user_id", xfUserId);
		if (data == null || data.isBlank()) return null;
		return Long.parseLong(data);
	}

	// get forum User's ID associated with Steam64
	@Nullable
	public Integer getUserId(long steam64) {
		if (settings.isDbVerifyDisabled()) return null;
		String data = selectOne(TABLE_FORUM, "xf_user_id", "steam_id", steam64);
		if (data == null || data.isBlank()) return null;
		return Integer.parseInt(data);
	}
}

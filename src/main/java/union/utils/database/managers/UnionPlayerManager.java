package union.utils.database.managers;

import java.util.Map;

import union.utils.database.DBUtil;
import union.utils.database.SqlDBBase;

import jakarta.annotation.Nonnull;

public class UnionPlayerManager extends SqlDBBase {

	private final String TABLE_PLAYERS = "sam_players";
	private final Map<String, String> databases;

	public UnionPlayerManager(DBUtil util, Map<String, String> databases, String url, String user, String password) {
		super(util, "%s?user=%s&password=%s".formatted(url, user, password));
		this.databases = databases;
	}

	public String getPlayerRank(@Nonnull String guildId, @Nonnull String steamId) {
		// Find corresponding database
		String database = databases.get(guildId);
		if (database == null) return null;
		// Get data from database table
		String data = selectOne(database, TABLE_PLAYERS, "rank", "steamid", steamId);
		return data;
	}

}

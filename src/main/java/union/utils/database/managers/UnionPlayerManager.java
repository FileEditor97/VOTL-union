package union.utils.database.managers;

import java.util.Map;
import java.util.Objects;

import union.objects.annotation.NotNull;
import union.utils.database.ConnectionUtil;
import union.utils.database.SqlDBBase;

public class UnionPlayerManager extends SqlDBBase {

	private final String TABLE_PLAYERS = "sam_players";
	private final Map<String, String> databases;

	public UnionPlayerManager(ConnectionUtil cu, Map<String, String> databases, String url, String user, String password) {
		super(cu, "%s?user=%s&password=%s".formatted(url, user, password));
		this.databases = databases;
	}

	public String getPlayerRank(@NotNull String guildId, @NotNull String steamId) {
		// Find corresponding database
		String database = databases.get(guildId);
		if (database == null) return null;
		// Get data from database table
		return selectOne(database, TABLE_PLAYERS, "rank", "steamid", steamId);
	}

	public PlayerInfo getPlayerInfo(@NotNull String guildId, @NotNull String steamId) {
		// Find corresponding database
		String database = databases.get(guildId);
		if (database == null) return new PlayerInfo(steamId);
		// Get data from database table
		return selectPlayerInfo(database, TABLE_PLAYERS, steamId);
	}

	public static class PlayerInfo {
		private final String steamId;
		private String rank;
		private Long playedHours; // in hours

		public PlayerInfo(String steamId) {
			this.steamId = steamId;
		}

		public PlayerInfo(String steamId, String rank, Long playTimeSeconds) {
			this.steamId = steamId;
			this.rank = rank;
			this.playedHours = Math.floorDiv(playTimeSeconds, 3600);
		}

		public void setInfo(String rank, Long playTimeSeconds) {
			this.rank = rank;
			this.playedHours = Math.floorDiv(playTimeSeconds, 3600);
		}

		public String getSteamId() {
			return steamId;
		}

		public String getRank() {
			return Objects.requireNonNullElse(rank, "-");
		}

		public Long getPlayTime() {
			return Objects.requireNonNullElse(playedHours, 0L);
		}
	}

}

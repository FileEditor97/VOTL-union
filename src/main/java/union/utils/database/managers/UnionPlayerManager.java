package union.utils.database.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import union.objects.annotation.NotNull;
import union.utils.database.ConnectionUtil;
import union.utils.database.SqlDBBase;
import static union.utils.CastUtil.castLong;

public class UnionPlayerManager extends SqlDBBase {

	private final String TABLE_PLAYERS = "sam_players";

	private final Map<Long, Map<String, String>> servers;

	public UnionPlayerManager(ConnectionUtil cu, Map<String, Object> map, String url, String user, String password) {
		super(cu, "%s?user=%s&password=%s".formatted(url, user, password));
		this.servers = convertMap(map);
	}

	public List<String> getPlayerRank(long guildId, @NotNull String steamId) {
		// Find corresponding database
		Map<String, String> dbs = servers.get(guildId);
		if (dbs == null) return List.of();
		// Get data from database tables
		List<String> data = new ArrayList<String>(dbs.size());
		for (String db : dbs.keySet()) {
			String rank = selectOne(db, TABLE_PLAYERS, "rank", "steamid", steamId);
			if (rank != null) data.add(rank);
		};
		return data;
	}

	public Long getPlayTime(long guildId, @NotNull String steamId) throws Exception {
		// Find corresponding database
		Map<String, String> dbs = servers.get(guildId);
		if (dbs == null) throw new Exception("Database not found.");
		// Get data from database tables
		Long playtime = null;
		for (String db : dbs.keySet()) {
			Long time = castLong(selectOne(db, TABLE_PLAYERS, "play_time", "steamid", steamId));
			if (time != null) playtime = playtime==null ? time : playtime+time;
		};
		return playtime;
	}

	public List<PlayerInfo> getPlayerInfo(long guildId, @NotNull String steamId) {
		// Find corresponding database
		Map<String, String> dbs = servers.get(guildId);
		if (dbs == null) return List.of();
		// Get data from database table
		List<PlayerInfo> data = new ArrayList<PlayerInfo>(dbs.size());
		dbs.forEach((db,title) -> {
			data.add(selectPlayerInfo(db, TABLE_PLAYERS, steamId).setServerTitle(title));
		});
		return data;
	}

	public static class PlayerInfo {
		private String serverTitle = null;
		private final String steamId, rank;
		private final Long playedHours; // in hours

		public PlayerInfo(String steamId) {
			this.steamId = steamId;
			this.rank = null;
			this.playedHours = null;
		}

		public PlayerInfo(String steamId, String rank, Long playTimeSeconds) {
			this.steamId = steamId;
			this.rank = rank;
			this.playedHours = Math.floorDiv(playTimeSeconds, 3600);
		}

		public PlayerInfo setServerTitle(String serverTitle) {
			this.serverTitle = serverTitle;
			return this;
		}

		public String getServerTitle() {
			return serverTitle;
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

		public boolean exists() {
			return rank != null;
		}
	}

	@SuppressWarnings("unchecked")
	private Map<Long, Map<String, String>> convertMap(@NotNull Map<String, Object> obj) {
		if (obj.isEmpty()) return Map.of();

		Map<Long, Map<String, String>> map = new HashMap<Long, Map<String, String>>(obj.size());
		obj.forEach((k,v) -> {
			if (v instanceof Map) {
				map.put(castLong(k), (Map<String, String>) v);
			}
		});

		return map;
	}

}

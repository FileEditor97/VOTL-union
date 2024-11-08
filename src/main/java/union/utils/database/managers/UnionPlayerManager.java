package union.utils.database.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import union.objects.annotation.NotNull;
import union.utils.database.ConnectionUtil;
import union.utils.database.SqlDBBase;
import union.utils.file.SettingsManager;

import static union.utils.CastUtil.castLong;

public class UnionPlayerManager extends SqlDBBase {
	private final String SAM_PLAYERS = "sam_players";
	private final String AXE_PLAYERS = "axe_players";
	private final SettingsManager settings;

	public UnionPlayerManager(ConnectionUtil cu, SettingsManager settings, String url, String user, String password) {
		super(cu, "%s?user=%s&password=%s".formatted(url, user, password));
		this.settings = settings;
	}

	public List<String> getPlayerRank(long guildId, @NotNull String steamId) {
		if (settings.isDbPlayerDisabled()) return List.of();
		// Find corresponding database
		Map<String, SettingsManager.GameServerInfo> servers = getServers(guildId);
		if (servers.isEmpty()) return List.of();
		// Get data from database tables
		List<String> data = new ArrayList<>(servers.size());
		for (String db : servers.keySet()) {
			String rank = selectOne(db, SAM_PLAYERS, "rank", "steamid", steamId);
			if (rank != null) data.add(rank);
		}
		return data;
	}

	public Long getPlayTime(long guildId, @NotNull String steamId) throws Exception {
		if (settings.isDbPlayerDisabled()) throw new Exception("Disabled.");
		// Find corresponding database
		Map<String, SettingsManager.GameServerInfo> servers = getServers(guildId);
		if (servers.isEmpty()) throw new Exception("Database not found.");
		// Get data from database tables
		Long playtime = null;
		for (String db : servers.keySet()) {
			Long time = castLong(selectOne(db, SAM_PLAYERS, "play_time", "steamid", steamId));
			if (time != null) playtime = playtime==null ? time : playtime+time;
		}
		return playtime;
	}

	public List<PlayerInfo> getPlayerInfo(long guildId, @NotNull String steamId) {
		if (settings.isDbPlayerDisabled()) return List.of();
		// Find corresponding database
		Map<String, SettingsManager.GameServerInfo> servers = getServers(guildId);
		if (servers.isEmpty()) return List.of();
		// Get data from database table
		Map<String, PlayerInfo> data = selectPlayerInfoList(servers, SAM_PLAYERS, steamId);
		List<PlayerInfo> newData = new ArrayList<>(servers.size());
		servers.forEach((db,info) -> {
			if (data.containsKey(db)) newData.add(data.get(db));
			else newData.add(new PlayerInfo(info));
		});
		return newData;
	}

	public boolean existsAxePlayer(long guildId, @NotNull String steamId) throws Exception {
		if (settings.isDbPlayerDisabled()) throw new Exception("Disabled.");
		// Find corresponding database
		Map<String, SettingsManager.GameServerInfo> servers = getServers(guildId);
		if (servers.isEmpty()) throw new Exception("Database not found.");
		// Get data from database table
		for (String db : servers.keySet()) {
			if (selectOne(db, AXE_PLAYERS, "steamid", "steamid", steamId) != null) return true;
		}
		return false;
	}

	public static class PlayerInfo {
		private final SettingsManager.GameServerInfo serverInfo;
		private final String rank;
		private final Long playedHours; // in hours

		public PlayerInfo(SettingsManager.GameServerInfo serverInfo) {
			this.serverInfo = serverInfo;
			this.rank = null;
			this.playedHours = null;
		}

		public PlayerInfo(SettingsManager.GameServerInfo serverInfo, String rank, Long playTimeSeconds) {
			this.serverInfo = serverInfo;
			this.rank = rank;
			this.playedHours = Math.floorDiv(playTimeSeconds, 3600);
		}

		public SettingsManager.GameServerInfo getServerInfo() {
			return serverInfo;
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

	@NotNull
	private Map<String, SettingsManager.GameServerInfo> getServers(long guildId) {
		return settings.getGameServers(guildId);
	}
}

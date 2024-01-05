package union.utils.database.managers;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import union.utils.database.LiteDBBase;
import union.utils.database.ConnectionUtil;

public class BanManager extends LiteDBBase {

	private final String table = "ban";

	private final List<String> fullBanKeys = List.of("banId", "userId", "userTag", "modId", "modTag", "guildId", "reason", "timeStart", "duration");
	
	public BanManager(ConnectionUtil cu) {
		super(cu);
	}

	// add new ban
	public void add(String userId, String userName, String modId, String modName, String guildId, String reason, Instant timeStart, Duration duration) {
		execute("INSERT INTO %s(userId, userTag, modId, modTag, guildId, reason, timeStart, duration) VALUES (%s, %s, %s, %s, %s, %s, %d, %d)"
			.formatted(table, userId, quote(userName), modId, quote(modName), guildId, quote(reason), timeStart.getEpochSecond(), duration.getSeconds()));
	}

	// get last ban's ID
	public Integer getIncrement() {
		return getIncrement(table);
	}

	// update ban reason
	public void updateReason(Integer banId, String reason) {
		execute("UPDATE %s SET reason=%s WHERE (banId=%d)".formatted(table, quote(reason), banId));
	}

	// update ban duration
	public void updateDuration(Integer banId, Duration duration) {
		execute("UPDATE %s SET duration=%d WHERE (banId=%d)".formatted(table, quote(duration.getSeconds()), banId));
	}

	// get ban info
	public BanData getInfo(Integer banId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (banId=%d)".formatted(table, banId), fullBanKeys);
		if (data==null) return null;
		return new BanData(data);
	}

	// get last 20 bans in guild
	public List<BanData> getGuildAll(String guildId) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%s) ORDER BY banId DESC LIMIT 20".formatted(table, guildId), fullBanKeys);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(map -> new BanData(map)).toList();
	}

	// get last 20 bans in guild by mod
	public List<BanData> getGuildMod(String guildId, String modId) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%s AND modId=%s) ORDER BY banId DESC LIMIT 20".formatted(table, guildId, modId), fullBanKeys);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(map -> new BanData(map)).toList();
	}

	// get last 10 bans in guild for user
	public List<BanData> getGuildUser(String guildId, String userId) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%s AND userId=%s) ORDER BY banId DESC LIMIT 10".formatted(table, guildId, userId), fullBanKeys);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(map -> new BanData(map)).toList();
	}

	// get all active bans
	public List<BanData> getActive(String guildId) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (active=1) ORDER BY banId DESC LIMIT 20".formatted(table), fullBanKeys);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(map -> new BanData(map)).toList();
	}

	// get all active expirable bans
	public List<BanData> getActiveExpirable(String guildId) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (active=1 AND duration>0) ORDER BY banId DESC LIMIT 20".formatted(table), fullBanKeys);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(map -> new BanData(map)).toList();
	}

	// get all active expired bans
	public List<BanData> getExpired() {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (active=1 AND timeStart+duration<%d) ORDER BY banId DESC LIMIT 10".formatted(table, Instant.now().getEpochSecond()), fullBanKeys);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(map -> new BanData(map)).toList();
	}

	// get user active temporary ban data
	public BanData getMemberActive(String userId, String guildId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (guildId=%s AND userId=%s AND active=1)".formatted(table, Instant.now().getEpochSecond()), fullBanKeys);
		if (data == null) return null;
		return new BanData(data);
	}

	// get user active temporary ban data
	public BanData getMemberExpirable(String userId, String guildId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (guildId=%s AND userId=%s AND active=1 AND duration>0)".formatted(table, Instant.now().getEpochSecond()), fullBanKeys);
		if (data == null) return null;
		return new BanData(data);
	}

	public BanData getMemberLast(String userId, String guildId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (guildId=%s AND userId=%s) ORDER BY banId DESC LIMIT 1".formatted(table, Instant.now().getEpochSecond()), fullBanKeys);
		if (data == null) return null;
		return new BanData(data);
	}

	// set ban as expirable
	public void setInactive(Integer banId) {
		execute("UPDATE %s SET active=0 WHERE (banId=%d)".formatted(table, banId));
	}

	public class BanData {
		private final Integer banId;
		private final String userId;
		private final String userTag;
		private final String modId;
		private final String modTag;
		private final String guildId;
		private final String reason;
		private final Instant timeStart;
		private final Duration duration;
		private final Boolean active;

		public BanData(Map<String, Object> map) {
			this.banId = (Integer) map.get("banId");
			this.userId = (String) map.get("userId");
			this.userTag = (String) map.get("userTag");
			this.modId = (String) map.get("modId");
			this.modTag = (String) map.get("modTag");
			this.guildId = (String) map.get("guildId");
			this.reason = (String) map.get("reason");
			this.timeStart = Instant.ofEpochSecond((Integer) map.get("timeStart"));
			this.duration = Duration.ofSeconds((Integer) map.getOrDefault("duration", 0));
			this.active = ((Integer) map.get("active")) == 1;
		}

		public Integer getBanId() {
			return banId;
		}

		public String getUserId() {
			return userId;
		}
		public String getUserTag() {
			return userTag;
		}

		public String getModId() {
			return modId;
		}
		public String getModTag() {
			return modTag;
		}

		public String getGuildId() {
			return guildId;
		}

		public String getReason() {
			return reason;
		}

		public Instant getTimeStart() {
			return timeStart;
		}

		public Duration getDuration() {
			return duration;
		}

		public Boolean isActive() {
			return active;
		}

		public Instant getTimeEnd() {
			return timeStart.plus(duration);
		}
	}

}

package union.utils.database.managers;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import union.utils.database.LiteDBBase;
import union.utils.database.DBUtil;

public class BanManager extends LiteDBBase {

	private final String TABLE = "ban";

	public final Util utils;
	
	public BanManager(DBUtil util) {
		super(util);
		this.utils = new Util();
	}

	// add new ban
	public void add(Integer banId, String userId, String userName, String modId, String modName, String guildId, String reason, Timestamp timeStart, Duration duration) {
		insert(TABLE, List.of("banId", "userId", "userTag", "modId", "modTag", "guildId", "reason", "timeStart", "duration", "expirable"),
			List.of(banId, userId, userName, modId, modName, guildId, reason, timeStart.toString(), duration.toString(), (duration.isZero() ? 0 : 1)));
	}

	// get last ban's ID
	public Integer lastId() {
		Object data = selectLast(TABLE, "banId");
		if (data == null) return 0;
		return Integer.parseInt(data.toString());
	}

	// remove existing ban
	public void remove(Integer banId) {
		delete(TABLE, "banId", banId);
	}

	// update ban reason
	public void updateReason(Integer banId, String reason) {
		update(TABLE, "reason", reason, "banId", banId);
	}

	// update ban duration
	public void updateDuration(Integer banId, Duration duration) {
		update(TABLE, "duration", duration, "banId", duration.toString());
	}

	// get ban info
	public Map<String, Object> getInfo(Integer banId) {
		List<Map<String, Object>> banDataList = select(TABLE, List.of(), "banId", banId);
		if (banDataList.isEmpty() || banDataList.get(0) == null) return Collections.emptyMap();
		return banDataList.get(0);
	}

	// get all bans in guild
	public List<Map<String, Object>> getGuildAll(String guildId) {
		List<Map<String, Object>> banDataList = select(TABLE, List.of(), "guildId", guildId);
		if (banDataList.isEmpty() || banDataList.get(0) == null) return Collections.emptyList();
		return banDataList;
	}

	// get all bans in guild by mod
	public List<Map<String, Object>> getGuildMod(String guildId, String modId) {
		List<Map<String, Object>> banDataList = select(TABLE, List.of(), List.of("guildId", "modId"), List.of(guildId, modId));
		if (banDataList.isEmpty() || banDataList.get(0) == null) return Collections.emptyList();
		return banDataList;
	}

	// get all bans in guild for user
	public List<Map<String, Object>> getGuildUser(String guildId, String userId) {
		List<Map<String, Object>> banDataList = select(TABLE, List.of(), List.of("guildId", "userId"), List.of(guildId, userId));
		if (banDataList.isEmpty() || banDataList.get(0) == null) return Collections.emptyList();
		return banDataList;
	}

	// get ban start time and duration
	public Map<String, Object> getTime(Integer banId) {
		List<Map<String, Object>> banDataList = select(TABLE, List.of("timeStart", "duration"), "banId", banId);
		if (banDataList.isEmpty() || banDataList.get(0) == null) return Collections.emptyMap();
		return banDataList.get(0);
	}

	// get all expirable bans in guild
	public List<Map<String, Object>> getGuildExpirable(String guildId) {
		List<Map<String, Object>> banDataList = select(TABLE, List.of(), List.of("guildId", "expirable"), List.of(guildId, 1));
		if (banDataList.isEmpty() || banDataList.get(0) == null) return Collections.emptyList();
		return banDataList;
	}

	// get all expirable bans
	public List<Map<String, Object>> getExpirable() {
		List<Map<String, Object>> banDataList = select(TABLE, List.of(), "expirable", 1);
		if (banDataList.isEmpty() || banDataList.get(0) == null) return Collections.emptyList();
		return banDataList;
	}

	// get user active temporary ban data
	public Map<String, Object> getMemberExpirable(String userId, String guildId) {
		List<Map<String, Object>> banDataList = select(TABLE, List.of(), List.of("guildId", "userId", "expirable"), List.of(guildId, userId, 1));
		if (banDataList.isEmpty() || banDataList.get(0) == null) return Collections.emptyMap();
		return banDataList.get(0);
	}

	// if user has temporary ban
	public boolean hasExpirable(String userId, String guildId) {
		Object data = selectOne(TABLE, "expirable", List.of("guildId", "userId"), List.of(guildId, userId));
		if (data == null) return false;
		if (data.equals(1)) return true;
		return false;
	}

	// set ban as expirable
	public void setExpirable(Integer banId) {
		update(TABLE, "expirable", 1, "banId", banId);
	}

	// set ban as expirable
	public void setInactive(Integer banId) {
		update(TABLE, "expirable", 0, "banId", banId);
	}

	public class Util {
		public boolean isExpirable(Map<String, Object> banMap) {
			if (banMap.get("expirable").equals(1)) return true;
			return false;
		}

		public boolean isPermament(Map<String, Object> banMap) {
			if (banMap.get("duration").toString().equals(Duration.ZERO.toString())) return true;
			return false;
		}
	}  

}

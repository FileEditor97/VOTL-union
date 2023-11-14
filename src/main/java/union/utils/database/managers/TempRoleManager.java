package union.utils.database.managers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import union.utils.database.DBUtil;
import union.utils.database.LiteDBBase;

public class TempRoleManager extends LiteDBBase {

	private final String TABLE = "tempRoles";
	
	public TempRoleManager(DBUtil util) {
		super(util);
	}

	public void add(String guildId, String roleId, String userId, Boolean deleteAfter, Instant expireAfter) {
		insert(TABLE, List.of("guildId", "roleId", "userId", "deleteAfter", "expireAfter"), List.of(guildId, roleId, userId, (deleteAfter ? 1 : 0), expireAfter.getEpochSecond()));
	}

	public void remove(String roleId, String userId) {
		delete(TABLE, List.of("roleId", "userId"), List.of(roleId, userId));
	}

	public void removeRole(String roleId) {
		deleteAll(TABLE, "roleId", roleId);
	}

	public void removeAll(String guildId) {
		deleteAll(TABLE, "guildId", guildId);
	}

	public void updateTime(String roleId, String userId, Instant expireAfter) {
		update(TABLE, "expireAfter", expireAfter.getEpochSecond(), List.of("roleId", "userId"), List.of(roleId, userId));
	}

	public Instant expireAt(String roleId, String userId) {
		Object data = selectOne(TABLE, "expireAfter", List.of("roleId", "userId"), List.of(roleId, userId));
		if (data == null) return null;
		return Instant.ofEpochSecond((Integer) data);
	}

	public List<Map<String, String>> expiredRoles(Instant now) {
		List<Map<String, String>> data = selectExpiredTempRoles(TABLE, now.getEpochSecond());
		if (data.isEmpty()) return List.of();
		return data;
	}

	public List<Map<String, Object>> getAll(String guildId) {
		List<Map<String, Object>> data = select(TABLE, List.of("roleId", "userId", "expireAfter"), "guildId", guildId);
		return data;
	}

	public Boolean shouldDelete(String roleId) {
		Object data = selectOne(TABLE, "deleteAfter", "roleId", roleId);
		if (data == null) return false;
		return ((Integer) data) == 1;
	}
}

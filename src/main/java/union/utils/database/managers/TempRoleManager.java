package union.utils.database.managers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class TempRoleManager extends LiteDBBase {
	public TempRoleManager(ConnectionUtil cu) {
		super(cu, "tempRoles");
	}

	public boolean add(String guildId, String roleId, String userId, Boolean deleteAfter, Instant expireAfter) {
		return execute("INSERT INTO %s(guildId, roleId, userId, deleteAfter, expireAfter) VALUES (%s, %s, %s, %d, %d)"
			.formatted(table, guildId, roleId, userId, (deleteAfter ? 1 : 0), expireAfter.getEpochSecond()));
	}

	public boolean remove(String roleId, String userId) {
		return execute("DELETE FROM %s WHERE (roleId=%s AND userId=%s)".formatted(table, roleId, userId));
	}

	public void removeRole(String roleId) {
		execute("DELETE FROM %s WHERE (roleId=%s)".formatted(table, roleId));
	}

	public void removeAll(String guildId) {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public boolean updateTime(String roleId, String userId, Instant expireAfter) {
		return execute("UPDATE %s SET expireAfter=%s WHERE (roleId=%s AND userId=%s)".formatted(table, expireAfter.getEpochSecond(), roleId, userId));
	}

	public Instant expireAt(String roleId, String userId) {
		Integer data = selectOne("SELECT expireAfter FROM %s WHERE (roleId=%s AND userId=%s)".formatted(table, roleId, userId), "expireAfter", Integer.class);
		if (data == null) return null;
		return Instant.ofEpochSecond(data);
	}

	public List<Map<String, Object>> expiredRoles(Instant now) {
		return select("SELECT roleId, userId FROM %s WHERE (expireAfter<=%d)".formatted(table, now.getEpochSecond()), Set.of("roleId", "userId"));
	}

	public List<Map<String, Object>> getAll(String guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%s)".formatted(table, guildId), Set.of("roleId", "userId", "expireAfter"));
	}

	public Boolean shouldDelete(String roleId) {
		Integer data = selectOne("SELECT deleteAfter FROM %s WHERE (roleId=%s)".formatted(table, roleId), "deleteAfter", Integer.class);
		return data != null && data == 1;
	}
}

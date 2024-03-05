package union.utils.database.managers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class TempRoleManager extends LiteDBBase {
	
	public TempRoleManager(ConnectionUtil cu) {
		super(cu, "tempRoles");
	}

	public void add(String guildId, String roleId, String userId, Boolean deleteAfter, Instant expireAfter) {
		execute("INSERT INTO %s(guildId, roleId, userId, deleteAfter, expireAfter) VALUES (%s, %s, %s, %d, %d)"
			.formatted(table, guildId, roleId, userId, (deleteAfter ? 1 : 0), expireAfter.getEpochSecond()));
	}

	public void remove(String roleId, String userId) {
		execute("DELETE FROM %s WHERE (roleId=%s AND userId=%s)".formatted(table, roleId, userId));
	}

	public void removeRole(String roleId) {
		execute("DELETE FROM %s WHERE (roleId=%s)".formatted(table, roleId));
	}

	public void removeAll(String guildId) {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public void updateTime(String roleId, String userId, Instant expireAfter) {
		execute("UPDATE %s SET expireAfter=%s WHERE (roleId=%s AND userId=%s)".formatted(table, expireAfter.getEpochSecond(), roleId, userId));
	}

	public Instant expireAt(String roleId, String userId) {
		Integer data = selectOne("SELECT expireAfter FROM %s WHERE (roleId=%s AND userId=%s)".formatted(table, roleId, userId), "expireAfter", Integer.class);
		if (data == null) return null;
		return Instant.ofEpochSecond(data);
	}

	public List<Map<String, Object>> expiredRoles(Instant now) {
		return select("SELECT roleId, userId FROM %s WHERE (expireAfter<=%d)".formatted(table, now.getEpochSecond()), List.of("roleId", "userId"));
	}

	public List<Map<String, Object>> getAll(String guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%s)".formatted(table, guildId), List.of("roleId", "userId", "expireAfter"));
	}

	public Boolean shouldDelete(String roleId) {
		Integer data = selectOne("SELECT deleteAfter FROM %s WHERE (roleId=%s)".formatted(table, roleId), "deleteAfter", Integer.class);
		return data==null ? false : data==1;
	}

}

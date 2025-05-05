package union.utils.database.managers;

import union.objects.constants.Constants;
import union.utils.FixedCache;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import java.sql.SQLException;
import java.util.*;

import static union.utils.CastUtil.castLong;

public class ConnectedRolesManager extends LiteDBBase {
	// cache
	private final FixedCache<Long, List<Long>> roleCache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE); /// guild - watched roles

	public ConnectedRolesManager(ConnectionUtil cu) {
		super(cu, "connectedRoles");
	}

	public void addRole(long roleId, long guildId, long mainRoleId, long mainGuildId) throws SQLException {
		execute("INSERT INTO %s(roleId, mainRoleId, guildId, mainGuildId) VALUES (%d, %d, %d, %d)"
			.formatted(table, roleId, mainRoleId, guildId, mainGuildId));
		invalidateRoleCache(mainGuildId);
	}

	public void removeRole(long roleId) throws SQLException {
		execute("DELETE FROM %s WHERE (roleId = %d OR mainRoleId = %<d)".formatted(table, roleId));
		roleCache.purge(); // sorry ;(
	}

	public void removeGuild(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId = %d OR mainGuildId = %<d)".formatted(table, guildId));
		invalidateRoleCache(guildId);
	}

	public List<Long> getWatchedRoles(long mainGuildId) {
		if (roleCache.contains(mainGuildId))
			return roleCache.get(mainGuildId);
		List<Long> data = getWatchedRolesData(mainGuildId);
		roleCache.put(mainGuildId, data);
		return data;
	}

	public List<Long> getWatchedRolesData(long mainGuildId) {
		return select("SELECT mainRoleId FROM %s WHERE (mainGuildId = %d)".formatted(table, mainGuildId), "mainRoleId", Long.class);
	}

	public List<Long> getConnectedRoles(long mainRoleId) {
		return select("SELECT roleId FROM %s WHERE (mainRoleId = %d)".formatted(table, mainRoleId), "roleId", Long.class);
	}

	public Map<Long, List<Long>> getAllRoles(long mainGuildId) {
		var data = select("SELECT roleId, mainRoleId FROM %s WHERE (mainGuildId = %d)".formatted(table, mainGuildId), Set.of("roleId", "mainRoleId"));
		if (data.isEmpty()) return Map.of();
		Map<Long, List<Long>> roles = new HashMap<>();
		data.forEach(s -> {
			long mainRoleId = castLong(s.get("mainRoleId"));
			roles.computeIfAbsent(mainRoleId, k -> new ArrayList<>()).add(castLong(s.get("roleId")));
		});
		return Collections.unmodifiableMap(roles);
	}

	public boolean isConnected(long roleId, long mainRoleId) {
		return selectOne("SELECT roleId FROM %s WHERE (roleId = %d OR mainRoleId = %d)".formatted(table, roleId, mainRoleId), "roleId", Long.class) != null;
	}

	private void invalidateRoleCache(long mainGuildId) {
		roleCache.pull(mainGuildId);
	}
}

package union.utils.database.managers;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import union.objects.constants.Constants;
import union.utils.CastUtil;
import union.utils.FixedCache;
import union.utils.database.LiteDBBase;
import union.objects.CmdAccessLevel;
import union.utils.database.ConnectionUtil;

public class AccessManager extends LiteDBBase {

	private final String table_role = "accessRole";
	private final String table_user = "accessUser";

	// Cache
	private final FixedCache<Long, Map<Long, CmdAccessLevel>> roleCache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE/2);
	private final FixedCache<Long, List<Long>> operatorCache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE/5);

	public AccessManager(ConnectionUtil cu) {
		super(cu, null);
	}

	public boolean addRole(long guildId, long roleId, CmdAccessLevel level) {
		invalidateRoleCache(guildId);
		return execute("INSERT INTO %s(guildId, roleId, level) VALUES (%s, %s, %d)".formatted(table_role, guildId, roleId, level.getLevel()));
	}

	public boolean addOperator(long guildId, long userId) {
		invalidateOperatorCache(guildId);
		return execute("INSERT INTO %s(guildId, userId, level) VALUES (%s, %s, %d)".formatted(table_user, guildId, userId, CmdAccessLevel.OPERATOR.getLevel()));
	}

	public boolean removeRole(long guildId, long roleId) {
		invalidateRoleCache(guildId);
		return execute("DELETE FROM %s WHERE (roleId=%s)".formatted(table_role, roleId));
	}
	
	public boolean removeUser(long guildId, long userId) {
		invalidateOperatorCache(guildId);
		return execute("DELETE FROM %s WHERE (guildId=%s AND userId=%s)".formatted(table_user, guildId, userId));
	}

	public void removeAll(long guildId) {
		invalidateRoleCache(guildId);
		invalidateOperatorCache(guildId);
		execute("DELETE FROM %1$s WHERE (guildId=%3$s); DELETE FROM %2$s WHERE (guildId=%3$s);".formatted(table_role, table_user, guildId));
	}

	public CmdAccessLevel getRoleLevel(long roleId) {
		Integer data = selectOne("SELECT level FROM %s WHERE (roleId=%s)".formatted(table_role, roleId), "level", Integer.class);
		if (data == null) return CmdAccessLevel.ALL;
		return CmdAccessLevel.byLevel(data);
	}

	public CmdAccessLevel getUserLevel(long guildId, long userId) {
		Integer data = selectOne("SELECT level FROM %s WHERE (guildId=%s AND userId=%s)".formatted(table_user, guildId, userId), "level", Integer.class);
		if (data == null) return null;
		return CmdAccessLevel.byLevel(data);
	}

	public Map<Long, CmdAccessLevel> getAllRoles(long guildId) {
		if (roleCache.contains(guildId))
			return roleCache.get(guildId);
		Map<Long, CmdAccessLevel> data = applyNonNull(getRoleData(guildId), this::parseRoleData);
		if (data==null || data.isEmpty())
			data = Map.of();
		roleCache.put(guildId, data);
		return data;
	}

	public List<Long> getRoles(long guildId, CmdAccessLevel level) {
		return select("SELECT roleId FROM %s WHERE (guildId=%s AND level=%d)".formatted(table_role, guildId, level.getLevel()), "roleId", Long.class);
	}

	public List<Long> getOperators(long guildId) {
		if (operatorCache.contains(guildId))
			return operatorCache.get(guildId);
		List<Long> data = getOperatorsData(guildId);
		if (data.isEmpty())
			data = List.of();
		operatorCache.put(guildId, data);
		return data;
	}

	public boolean isRole(long roleId) {
		return selectOne("SELECT roleId FROM %s WHERE (roleId=%s)".formatted(table_role, roleId), "roleId", Long.class) != null;
	}

	public boolean isOperator(long guildId, long userId) {
		return getOperators(guildId).contains(userId);
	}

	private List<Map<String, Object>> getRoleData(long guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%d)".formatted(table_role, guildId), Set.of("roleId", "level"));
	}

	private List<Long> getOperatorsData(long guildId) {
		return select("SELECT userId FROM %s WHERE (guildId=%d and level=%d)"
			.formatted(table_user, guildId, CmdAccessLevel.OPERATOR.getLevel()), "userId", Long.class);
	}

	private void invalidateRoleCache(long guildId) {
		roleCache.pull(guildId);
	}

	private void invalidateOperatorCache(long guildId) {
		operatorCache.pull(guildId);
	}

	public Map<Long, CmdAccessLevel> parseRoleData(List<Map<String, Object>> data) {
		if (data == null || data.isEmpty()) return Map.of();
		return data.stream().collect(Collectors.toMap(k-> CastUtil.castLong(k.get("roleId")), k-> CmdAccessLevel.byLevel((int) k.get("level"))));
	}

}

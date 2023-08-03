package union.utils.database.managers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import union.utils.database.LiteDBBase;
import union.objects.CmdAccessLevel;
import union.utils.database.DBUtil;

public class AccessManager extends LiteDBBase {

	private final String ROLE_TABLE = "roleAccess";
	private final String USER_TABLE = "userAccess";
	
	public AccessManager(DBUtil util) {
		super(util);
	}

	public void addRole(String guildId, String roleId, CmdAccessLevel level) {
		insert(ROLE_TABLE, List.of("guildId", "roleId", "level"), List.of(guildId, roleId, level.getLevel()));
	}

	public void addUser(String guildId, String userId, CmdAccessLevel level) {
		insert(USER_TABLE, List.of("guildId", "roleId", "level"), List.of(guildId, userId, level.getLevel()));
	}

	public void removeRole(String roleId) {
		delete(ROLE_TABLE, "roleId", roleId);
	}
	
	public void removeUser(String guildId, String userId) {
		delete(USER_TABLE, List.of("guildId", "userId"), List.of(guildId, userId));
	}

	public void removeAll(String guildId) {
		delete(ROLE_TABLE, "guildId", guildId);
		delete(USER_TABLE, "guildId", guildId);
	}

	public CmdAccessLevel getRoleLevel(String roleId) {
		Object data = selectOne(ROLE_TABLE, "level", "roleId", roleId);
		if (data == null) return CmdAccessLevel.ALL;
		return CmdAccessLevel.byLevel((Integer) data);
	}

	public CmdAccessLevel getUserLevel(String guildId, String userId) {
		Object data = selectOne(USER_TABLE, "level", List.of("guildId", "userId"), List.of(guildId, userId));
		if (data == null) return CmdAccessLevel.ALL;
		return CmdAccessLevel.byLevel((Integer) data);
	}

	public List<String> getAllRoles(String guildId) {
		List<Object> data = select(ROLE_TABLE, "roleId", "guildId", guildId);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
	}

	public List<String> getRoles(String guildId, CmdAccessLevel level) {
		List<Object> data = select(ROLE_TABLE, "roleId", List.of("guildId", "level"), List.of(guildId, level.getLevel()));
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
	}

	public List<String> getAllUsers(String guildId) {
		List<Object> data = select(USER_TABLE, "userId", "guildId", guildId);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
	}

	public boolean isMod(String roleId) {
		if (selectOne(ROLE_TABLE, "roleId", List.of("roleId", "level"), List.of(roleId, CmdAccessLevel.MOD)) == null) return false;
		return true;
	}

	public boolean isOperator(String guildId, String userId) {
		if (selectOne(USER_TABLE, "userId", List.of("guildId", "userId", "level"), List.of(guildId, userId, CmdAccessLevel.OPERATOR)) == null) return false;
		return true;
	}

}

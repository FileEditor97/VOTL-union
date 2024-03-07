package union.utils.database.managers;

import java.util.List;

import union.utils.database.LiteDBBase;
import union.objects.CmdAccessLevel;
import union.utils.database.ConnectionUtil;

public class AccessManager extends LiteDBBase {

	private final String table_role = "roleAccess";
	private final String table_user = "userAccess";
	
	public AccessManager(ConnectionUtil cu) {
		super(cu, null);
	}

	public void addRole(String guildId, String roleId, CmdAccessLevel level) {
		execute("INSERT INTO %s(guildId, roleId, level) VALUES (%s, %s, %d)".formatted(table_role, guildId, roleId, level.getLevel()));
	}

	public void addUser(String guildId, String userId, CmdAccessLevel level) {
		execute("INSERT INTO %s(guildId, userId, level) VALUES (%s, %s, %d)".formatted(table_user, guildId, userId, level.getLevel()));
	}

	public void removeRole(String roleId) {
		execute("DELETE FROM %s WHERE (roleId=%s)".formatted(table_role, roleId));
	}
	
	public void removeUser(String guildId, String userId) {
		execute("DELETE FROM %s WHERE (guildId=%s AND userId=%s)".formatted(table_user, guildId, userId));
	}

	public void removeAll(String guildId) {
		execute("DELETE FROM %1$s WHERE (guildId=%3$s); DELETE FROM %2$s WHERE (guildId=%3$s);".formatted(table_role, table_user, guildId));
	}

	public CmdAccessLevel getRoleLevel(String roleId) {
		Integer data = selectOne("SELECT level FROM %s WHERE (roleId=%s)".formatted(table_role, roleId), "level", Integer.class);
		if (data == null) return CmdAccessLevel.ALL;
		return CmdAccessLevel.byLevel(data);
	}

	public CmdAccessLevel getUserLevel(String guildId, String userId) {
		Integer data = selectOne("SELECT level FROM %s WHERE (guildId=%s AND userId=%s)".formatted(table_user, guildId, userId), "level", Integer.class);
		if (data == null) return null;
		return CmdAccessLevel.byLevel(data);
	}

	public List<String> getAllRoles(String guildId) {
		return select("SELECT roleId FROM %s WHERE (guildId=%s)".formatted(table_role, guildId), "roleId", String.class);
	}

	public List<String> getRoles(String guildId, CmdAccessLevel level) {
		return select("SELECT roleId FROM %s WHERE (guildId=%s AND level=%d)".formatted(table_role, guildId, level.getLevel()), "roleId", String.class);
	}

	public List<String> getAllUsers(String guildId) {
		return select("SELECT userId FROM %s WHERE (guildId=%s)".formatted(table_user, guildId), "userId", String.class);
	}

	public List<String> getUsers(String guildId, CmdAccessLevel level) {
		return select("SELECT userId FROM %s WHERE (guildId=%s AND level=%d)".formatted(table_user, guildId, level.getLevel()), "userId", String.class);
	}

	public boolean isRole(String roleId) {
		return selectOne("SELECT roleId FROM %s WHERE (roleId=%s)".formatted(table_role, roleId), "roleId", String.class) != null;
	}

	public boolean isOperator(String guildId, String userId) {
		return selectOne("SELECT userId FROM %s WHERE (guildId=%s AND userId=%s AND level=%d)"
			.formatted(table_user, guildId, userId, CmdAccessLevel.OPERATOR.getLevel()), "userId", String.class) != null;
	}

}

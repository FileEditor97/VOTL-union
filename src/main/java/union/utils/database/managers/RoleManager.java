package union.utils.database.managers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import union.objects.RoleType;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class RoleManager extends LiteDBBase {
	
	private final String table = "requestRole";

	public RoleManager(ConnectionUtil cu) {
		super(cu);
	}

	public void add(String guildId, String roleId, String description, Integer row, RoleType roleType, String discordInvite) {
		execute("INSERT INTO %s(guildId, roleId, description, type, row, discordInvite) VALUES (%s, %s, %s, %d, %d, %s)"
			.formatted(table, guildId, roleId, quote(description), roleType.getType(), Optional.ofNullable(row).orElse(0), quote(discordInvite)));
	}

	public void remove(String roleId) {
		execute("DELETE FROM %s WHERE (roleId=%s)".formatted(table, roleId));
	}

	public void removeAll(String guildId) {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public List<Map<String, Object>> getAll(String guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%s)".formatted(table, guildId), List.of("roleId", "description", "type"));
	}

	public List<Map<String, Object>> getRolesByType(String guildId, RoleType type) {
		return select("SELECT * FROM %s WHERE (guildId=%s AND type=%d)".formatted(table, guildId, type.getType()), List.of("roleId", "description"));
	}

	public List<Map<String, Object>> getAssignable(String guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%s AND type=%d)".formatted(table, guildId, RoleType.ASSIGN.getType()), List.of("roleId", "description", "row"));
	}

	public List<Map<String, Object>> getAssignableByRow(String guildId, Integer row) {
		return select("SELECT * FROM %s WHERE (guildId=%s AND type=%d AND row=%d)".formatted(table, guildId, RoleType.ASSIGN.getType(), row),
			List.of("roleId", "description", "discordInvite"));
	}

	public List<Map<String, Object>> getToggleable(String guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%s AND type=%d)".formatted(table, guildId, RoleType.TOGGLE.getType()), List.of("roleId", "description"));
	}

	public List<Map<String, Object>> getCustom(String guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%s AND type=%d)".formatted(table, guildId, RoleType.CUSTOM.getType()), List.of("roleId", "description"));
	}

	public Map<String, String> getRolesWithInvites(String guildId) {
		List<Map<String, Object>> data = select("SELECT roleId, discordInvite FROM %s WHERE (guildId=%s AND discordInvite IS NOT NULL)".formatted(table, guildId),
			List.of("roleId", "discordInvite"));
		if (data.isEmpty()) return Collections.emptyMap();
		return data.stream().collect(Collectors.toMap(s -> (String) s.get("roleId"), s -> (String) s.get("discordInvite")));
	}

	public Integer getRowSize(String guildId, Integer row) {
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%s AND row=%d)".formatted(table, guildId, row));
	}

	public Integer countRoles(String guildId, RoleType type) {
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%s AND type=%d)".formatted(table, guildId, type.getType()));
	}

	public String getDescription(String roleId) {
		return selectOne("SELECT description FROM %s WHERE (roleId=%s)".formatted(table, roleId), "description", String.class);
	}

	public Integer getType(String roleId) {
		return selectOne("SELECT type FROM %s WHERE (roleId=%s)".formatted(table, roleId), "type", Integer.class);
	}

	public Integer getRow(String roleId) {
		Integer data = selectOne("SELECT row FROM %s WHERE (roleId=%s)".formatted(table, roleId), "row", Integer.class);
		if (data == null) return 0;
		return data;
	}

	public String getInvite(String roleId) {
		return selectOne("SELECT discordInvite FROM %s WHERE (roleId=%s)".formatted(table, roleId), "discordInvite", String.class);
	}

	public void setDescription(String roleId, String description) {
		execute("UPDATE %s SET description=%s WHERE (roleId=%s)".formatted(table, quote(description), roleId));
	}

	public void setRow(String roleId, Integer row) {
		execute("UPDATE %s SET row=%d WHERE (roleId=%s)".formatted(table, Optional.ofNullable(row).orElse(0), roleId));
	}

	public void setInvite(String roleId, String discordInvite) {
		execute("UPDATE %s SET discordInvite=%s WHERE (roleId=%s)".formatted(table, quote(discordInvite), roleId));
	}

	public boolean isToggleable(String roleId) {
		Integer type = getType(roleId);
		return type==null ? false : type==RoleType.TOGGLE.getType();
	}

	public boolean existsRole(String roleId) {
		return selectOne("SELECT roleId FROM %s WHERE (roleId=%s)".formatted(table, roleId), "roleId", String.class) != null;
	}

}

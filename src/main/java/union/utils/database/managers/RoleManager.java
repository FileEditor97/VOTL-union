package union.utils.database.managers;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import union.objects.RoleType;
import union.utils.database.DBUtil;
import union.utils.database.LiteDBBase;

public class RoleManager extends LiteDBBase {
	
	private final String TABLE = "requestRole";

	public RoleManager(DBUtil util) {
		super(util);
	}

	public void add(String guildId, String roleId, String description, RoleType roleType) {
		insert(TABLE, List.of("guildId", "roleId", "description", "type"), List.of(guildId, roleId, description, roleType.getType()));
	}

	public void remove(String roleId) {
		delete(TABLE, "roleId", roleId);
	}

	public void removeAll(String guildId) {
		delete(TABLE, "guildId", guildId);
	}

	public List<Map<String, Object>> getAll(String guildId) {
		List<Map<String, Object>> data = select(TABLE, List.of("roleId", "description", "type"), "guildId", guildId);
		if (data.isEmpty()) return Collections.emptyList();
		return data;
	}

	public List<Map<String, Object>> getRolesByType(String guildId, RoleType type) {
		List<Map<String, Object>> data = select(TABLE, List.of("roleId", "description"), List.of("guildId", "type"), List.of(guildId, type.getType()));
		if (data.isEmpty()) return Collections.emptyList();
		return data;
	}

	public List<Map<String, Object>> getAssignable(String guildId) {
		List<Map<String, Object>> data = select(TABLE, List.of("roleId", "description"), List.of("guildId", "type"), List.of(guildId, RoleType.ASSIGN.getType()));
		if (data.isEmpty()) return Collections.emptyList();
		return data;
	}

	public List<Map<String, Object>> getToggleable(String guildId) {
		List<Map<String, Object>> data = select(TABLE, List.of("roleId", "description"), List.of("guildId", "type"), List.of(guildId, RoleType.TOGGLE.getType()));
		if (data.isEmpty()) return Collections.emptyList();
		return data;
	}

	public List<Map<String, Object>> getCustom(String guildId) {
		List<Map<String, Object>> data = select(TABLE, List.of("roleId", "description"), List.of("guildId", "type"), List.of(guildId, RoleType.CUSTOM.getType()));
		if (data.isEmpty()) return Collections.emptyList();
		return data;
	}

	public String getDescription(String roleId) {
		Object data = selectOne(TABLE, "description", "roleId", roleId);
		if (data == null) return null;
		return String.valueOf(data);
	}

	public void changeType(String roleId, RoleType newType) {
		update(TABLE, "type", newType.getType(), "roleId", roleId);
	}

	public boolean isToggleable(String roleId) {
		if (selectOne(TABLE, "roleId", List.of("roleId", "type"), List.of(roleId, RoleType.TOGGLE.getType())) == null) return false;
		return true;
	}

	public boolean existsRole(String roleId) {
		if (selectOne(TABLE, "roleId", "roleId", roleId) == null) return false;
		return true;
	}

}

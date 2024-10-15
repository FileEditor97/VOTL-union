package union.utils.database.managers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import union.objects.RoleType;
import union.utils.CastUtil;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class RoleManager extends LiteDBBase {

	public RoleManager(ConnectionUtil cu) {
		super(cu, "requestRole");
	}

	public boolean add(String guildId, String roleId, String description, Integer row, RoleType roleType, String discordInvite) {
		return execute("INSERT INTO %s(guildId, roleId, description, type, row, discordInvite) VALUES (%s, %s, %s, %d, %d, %s)"
			.formatted(table, guildId, roleId, quote(description), roleType.getType(), Optional.ofNullable(row).orElse(0), quote(discordInvite)));
	}

	public boolean remove(String roleId) {
		return execute("DELETE FROM %s WHERE (roleId=%s)".formatted(table, roleId));
	}

	public void removeAll(String guildId) {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public List<RoleData> getRolesByType(String guildId, RoleType type) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%s AND type=%d)"
			.formatted(table, guildId, type.getType()), Set.of("roleId", "description"));
		if (data.isEmpty()) return List.of();
		return data.stream().map(m -> new RoleData(m, type)).toList();
	}

	public List<Map<String, Object>> getAssignable(String guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%s AND type IN (%d, %d))"
			.formatted(table, guildId, RoleType.ASSIGN.getType(), RoleType.ASSIGN_TEMP.getType()),
			Set.of("roleId", "description", "row")
		);
	}

	public List<RoleData> getAssignableByRow(String guildId, Integer row) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%s AND type IN (%d, %d) AND row=%d)"
			.formatted(table, guildId, RoleType.ASSIGN.getType(), RoleType.ASSIGN_TEMP.getType(), row),
			Set.of("roleId", "type", "description", "discordInvite")
		);
		if (data.isEmpty()) return List.of();
		return data.stream().map(RoleData::new).toList();
	}

	public List<Map<String, Object>> getToggleable(String guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%s AND type=%d)".formatted(table, guildId, RoleType.TOGGLE.getType()), Set.of("roleId", "description"));
	}

	public List<Map<String, Object>> getCustom(String guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%s AND type=%d)".formatted(table, guildId, RoleType.CUSTOM.getType()), Set.of("roleId", "description"));
	}

	public Map<String, String> getRolesWithInvites(String guildId) {
		List<Map<String, Object>> data = select("SELECT roleId, discordInvite FROM %s WHERE (guildId=%s AND discordInvite IS NOT NULL)".formatted(table, guildId),
			Set.of("roleId", "discordInvite")
		);
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

	public RoleType getType(String roleId) {
		Integer data = selectOne("SELECT type FROM %s WHERE (roleId=%s)".formatted(table, roleId), "type", Integer.class);
		if (data == null) return null;
		return RoleType.byType(data);
	}

	public boolean setDescription(String roleId, String description) {
		return execute("UPDATE %s SET description=%s WHERE (roleId=%s)".formatted(table, quote(description), roleId));
	}

	public boolean setRow(String roleId, Integer row) {
		return execute("UPDATE %s SET row=%d WHERE (roleId=%s)".formatted(table, Optional.ofNullable(row).orElse(0), roleId));
	}

	public boolean setInvite(String roleId, String discordInvite) {
		return execute("UPDATE %s SET discordInvite=%s WHERE (roleId=%s)".formatted(table, quote(discordInvite), roleId));
	}

	public boolean isToggleable(String roleId) {
		RoleType type = getType(roleId);
		return type != null && type.equals(RoleType.TOGGLE);
	}

	public boolean isTemp(String roleId) {
		RoleType type = getType(roleId);
		return type != null && type.equals(RoleType.ASSIGN_TEMP);
	}

	public boolean existsRole(String roleId) {
		return selectOne("SELECT roleId FROM %s WHERE (roleId=%s)".formatted(table, roleId), "roleId", String.class) != null;
	}

	public static class RoleData {
		private final long id;
		private final RoleType type;
		private final String description, discordInvite;

		public RoleData(Map<String, Object> map) {
			String roleId = CastUtil.requireNonNull(map.get("roleId")); // roleId is stored as String in DB
			this.id = CastUtil.castLong(roleId);
			this.type = RoleType.byType(CastUtil.requireNonNull(map.get("type")));
			this.description = CastUtil.getOrDefault(map.get("description"), null);
			this.discordInvite = CastUtil.getOrDefault(map.get("discordInvite"), null);
		}

		public RoleData(Map<String, Object> map, RoleType roleType) {
			String roleId = CastUtil.requireNonNull(map.get("roleId")); // roleId is stored as String in DB
			this.id = CastUtil.castLong(roleId);
			this.type = roleType;
			this.description = CastUtil.getOrDefault(map.get("description"), null);
			this.discordInvite = null;
		}

		public long getIdLong() {
			return id;
		}

		public String getId() {
			return String.valueOf(id);
		}

		public RoleType getType() {
			return type;
		}

		public String getDescription(String defaultValue) {
			return description == null ? defaultValue : description;
		}

		public String getDiscordInvite() {
			return discordInvite;
		}

		public boolean isTemp() {
			return type.equals(RoleType.ASSIGN_TEMP);
		}
	}

}

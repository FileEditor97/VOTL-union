package union.utils.database.managers;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import java.sql.SQLException;
import java.time.Instant;

public class ModifyRoleManager extends LiteDBBase {

	public ModifyRoleManager(ConnectionUtil cu) {
		super(cu, "menuSelectRoles");
		removeExpired();
	}

	public void create(long guildId, long userId, long targetId, Instant expiresAfter) throws SQLException {
		execute("INSERT INTO %s(guildId, userId, targetId, expiresAfter, roles) VALUES (%s, %s, %s, %s, \":::\") ON CONFLICT(guildId, userId, targetId) DO UPDATE SET expiresAfter = %<s, roles = \":::\""
				.formatted(table, guildId, userId, targetId, expiresAfter.getEpochSecond()));
	}

	public void update(long guildId, long userId, long targetId, String newRoles, Instant expiresAfter) throws SQLException {
		execute("UPDATE %S SET expiresAfter=%s, roles=%s WHERE (guildId=%s AND userId=%s AND targetId=%s)"
				.formatted(table, expiresAfter.getEpochSecond(), quote(newRoles), guildId, userId, targetId));
	}

	public void remove(long guildId, long userId, long targetId) {
		try {
			execute("DELETE FROM %s WHERE (guildId=%s AND userId=%s AND targetId=%s)".formatted(table, guildId, userId, targetId));
		} catch (SQLException ignored) {}
	}

	public void removeAll(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	private void removeExpired() {
		try {
			execute("DELETE FROM %s WHERE (expiresAfter=%s)".formatted(table, Instant.now().getEpochSecond()));
		} catch (SQLException ignored) {}
	}

	public String getRoles(long guildId, long userId, long targetId) {
		return selectOne("SELECT roles FROM %s WHERE (guildId=%s AND userId=%s AND targetId=%s)".formatted(table, guildId, userId, targetId), "roles", String.class);
	}

	public Boolean isExpired(long guildId, long userId, long targetId) {
		Long data = selectOne("SELECT expiresAfter FROM %s WHERE (guildId=%s AND userId=%s AND targetId=%s)".formatted(table, guildId, userId, targetId), "expiresAfter", Long.class);
		if (data == null) return true;
		boolean expired = Instant.ofEpochSecond(data).isBefore(Instant.now());
		if (expired) remove(guildId, userId, targetId);
		return expired;
	}

}

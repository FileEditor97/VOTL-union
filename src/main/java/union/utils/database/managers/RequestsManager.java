package union.utils.database.managers;

import java.time.Instant;
import java.util.List;

import union.utils.database.DBUtil;
import union.utils.database.LiteDBBase;

public class RequestsManager extends LiteDBBase {

	private final String TABLE = "requestPrepare";

	public RequestsManager(DBUtil util) {
		super(util);
	}

	/* public void createRoleRequest(String guildId, String userId, String roleIds, long secondsAlive) {
		insert(TABLE, List.of("guildId", "userId", "roleIds", "expiresAt"), List.of(guildId, userId, roleIds, Instant.now().plusSeconds(secondsAlive).getEpochSecond()));
	}

	public void updateRoleRequest(String guildId, String userId, String roleIds, long secondsAlive) {
		update(TABLE, List.of("roleIds", "expiresAt"), List.of(roleIds, Instant.now().plusSeconds(secondsAlive).getEpochSecond()), List.of("guildId", "userId"), List.of(guildId, userId));
	}

	public boolean existsRoleRequest(String guildId, String userId) {
		if (selectOne(TABLE, "userId", List.of("guildId", "userId"), List.of(guildId, userId)) == null) return false;
		return true;
	} */

	public void deleteRequest(String guildId, String userId) {
		delete(TABLE, List.of("guildId", "userId"), List.of(guildId, userId));
	}

	public void purgeExpiredRequests() {
		deleteExpired(TABLE, Instant.now().getEpochSecond());
	}

	/* public List<String> getRoles(String guildId, String userId) {
		Object data = selectOne(TABLE, "roleIds", List.of("guildId", "userId"), List.of(guildId, userId));
		if (data == null || data.equals("")) return Collections.emptyList();
		return Arrays.asList(String.valueOf(data).split(";"));
	} */
	
}

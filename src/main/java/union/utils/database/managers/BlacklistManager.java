package union.utils.database.managers;

import java.util.List;
import java.util.Map;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import jakarta.annotation.Nullable;

public class BlacklistManager extends LiteDBBase {

	private final String table = "blacklist";
	
	public BlacklistManager(ConnectionUtil cu) {
		super(cu);
	}

	public void add(long guildId, int groupId, long userId, @Nullable Long steam64, @Nullable String reason, long modId) {
		execute("INSERT INTO %s(guildId, groupId, userId, steam64, reason, modId) VALUES (%d, %d, %d, %d, %s, %s)"
			.formatted(table, guildId, groupId, userId, steam64, quote(reason), modId));
	}

	public boolean inGroupUser(int groupId, long userId) {
		return selectOne("SELECT userId FROM %s WHERE (groupId=%d AND userId=%d)".formatted(table, groupId, userId), "userId", Long.class) != null;
	}

	public boolean inGroupSteam64(int groupId, long steam64) {
		return selectOne("SELECT steam64 FROM %s WHERE (groupId=%d AND steam64=%d)".formatted(table, groupId, steam64), "steam64", Long.class) != null;
	}

	public void removeUser(int groupId, long userId) {
		execute("DELETE FROM %s WHERE (groupId=%d AND userId=%d)".formatted(table, groupId, userId));
	}

	public void removeSteam64(int groupId, long steam64) {
		execute("DELETE FROM %s WHERE (groupId=%d AND steam64=%d)".formatted(table, groupId, steam64));
	}

	public Map<String, Object> getInfo(int groupId, long userId) {
		return selectOne("SELECT * FROM %s WHERE (groupId=%d AND userId=%d)".formatted(table, groupId, userId), List.of("guildId", "steam64", "reason", "modId"));
	}

	public Long getUserWithSteam64(int groupId, long steam64) {
		return selectOne("SELECT userId FROM %s WHERE (groupId=%d AND steam64=%d)".formatted(table, groupId, steam64), "userId", Long.class);
	}

	public List<Map<String, Object>> getByPage(int groupId, int page) {
		return select("SELECT * FROM %s WHERE (groupId=%d) ORDER BY userId DESC LIMIT 20 OFFSET %d".formatted(table, groupId, (page-1)*20), List.of("guildId", "userId", "steam64", "reason", "modId"));
	}

	public Integer countEntries(int groupId) {
		return count("SELECT COUNT(*) FROM %s WHERE (groupId=%d)".formatted(table, groupId));
	}

}

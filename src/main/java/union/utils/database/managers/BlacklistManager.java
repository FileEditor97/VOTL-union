package union.utils.database.managers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import union.objects.annotation.NotNull;
import union.objects.annotation.Nullable;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class BlacklistManager extends LiteDBBase {
	
	public BlacklistManager(ConnectionUtil cu) {
		super(cu, "blacklist");
	}

	public void add(long guildId, int groupId, long userId, @Nullable Long steam64, @Nullable String reason, long modId) {
		execute("INSERT INTO %s(guildId, groupId, userId, steam64, reason, modId) VALUES (%d, %d, %d, %d, %s, %s)"
			.formatted(table, guildId, groupId, userId, steam64, quote(reason), modId));
	}

	public void addSteam(long guildId, int groupId, @NotNull Long steam64, long modId) {
		execute("INSERT INTO %s(guildId, groupId, userId, steam64, modId) VALUES (%d, %d, -1, %d, %s)"
			.formatted(table, guildId, groupId, steam64, modId));
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

	public List<Map<String, Object>> getByPage(int groupId, int page) {
		return select("SELECT * FROM %s WHERE (groupId=%d) ORDER BY userId DESC LIMIT 20 OFFSET %d".formatted(table, groupId, (page-1)*20), Set.of("guildId", "userId", "steam64", "reason", "modId"));
	}

	public Integer countEntries(int groupId) {
		return count("SELECT COUNT(*) FROM %s WHERE (groupId=%d)".formatted(table, groupId));
	}

}

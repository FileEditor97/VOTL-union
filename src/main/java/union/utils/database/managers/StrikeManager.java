package union.utils.database.managers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import net.dv8tion.jda.internal.utils.tuple.Pair;

public class StrikeManager extends LiteDBBase {
	
	private final String table = "strikeExpire";

	public StrikeManager(ConnectionUtil cu) {
		super(cu);
	}

	public void addStrikes(Long guildId, Long userId, Instant expireAfter, Integer count, String caseInfo) {
		execute("INSERT INTO %s(guildId, userId, expireAfter, count, data) VALUES (%d, %d, %d, %d, %s) ON CONFLICT(guildId, userId) DO UPDATE SET count=count+%d, data=data || '-' || %s"
			.formatted(table, guildId, userId, expireAfter.getEpochSecond(), count, quote(caseInfo), count, quote(caseInfo)));
	}

	public Integer getStrikeCount(Long guildId, Long userId) {
		return selectOne("SELECT count FROM %s WHERE (guildId=%d AND userId=%d)".formatted(table, guildId, userId), "count", Integer.class);
	}

	public List<Map<String, Object>> getExpired(Instant time) {
		return select("SELECT * FROM %s WHERE (expireAfter<%d)".formatted(table, time.getEpochSecond()), List.of("guildId", "userId", "count", "data"));
	}

	public Pair<Integer, String> getData(Long guildId, Long userId) {
		Map<String, Object> data = selectOne("SELECT count, data FROM %s WHERE (guildId=%d AND userId=%d)".formatted(table, guildId, userId), List.of("count", "data"));
		if (data == null) return null;
		return Pair.of((Integer) data.get("count"), (String) data.getOrDefault("data", ""));
	}

	public void removeStrike(Long guildId, Long userId, Instant expireAfter, String newData) {
		execute("UPDATE %s SET expireAfter=%d, count=count-1, data=%s WHERE (guildId=%d AND userId=%d)".formatted(table, expireAfter.getEpochSecond(), quote(newData), guildId, userId));
	}

	public void removeStrike(Long guildId, Long userId, Instant expireAfter, Integer amount, String newData) {
		execute("UPDATE %s SET expireAfter=%d, count=count-%d, data=%s WHERE (guildId=%d AND userId=%d)".formatted(table, expireAfter.getEpochSecond(), amount, quote(newData), guildId, userId));
	}

	public void removeGuildUser(Long guildId, Long userId) {
		execute("DELETE FROM %s WHERE (guildId=%d AND userId=%d)".formatted(table, guildId, userId));
	}

	public void removeGuild(Long guildId) {
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}
	
}

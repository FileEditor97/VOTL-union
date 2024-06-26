package union.utils.database.managers;

import net.dv8tion.jda.internal.utils.tuple.Pair;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TempBanManager extends LiteDBBase {
	public TempBanManager(ConnectionUtil cu) {
		super(cu, "tempBan");
	}

	public void add(long guildId, long userId, Instant until) {
		execute("INSERT INTO %s(guildId, userId, until) VALUES (%s, %s, %s".formatted(table, guildId, userId, until.getEpochSecond()));
	}

	public void remove(long guildId, long userId) {
		execute("DELETE FROM %s WHERE (guildId=%s AND userId=%s)".formatted(table, guildId, userId));
	}

	public void removeGuild(long guildId) {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	/**
	 * @return Pair of guildId and userId
	 */
	public List<Pair<Long, Long>> getExpired() {
		List<Map<String, Object>> data = select("SELECT guildId, userId FROM %s WHERE (until<=%s)".formatted(table, Instant.now().getEpochSecond()), Set.of("guildId", "userId"));
		if (data.isEmpty()) return List.of();
		return data.stream().map(v -> Pair.of((Long) v.get("guildId"), (Long) v.get("userId"))).toList();
	}

}

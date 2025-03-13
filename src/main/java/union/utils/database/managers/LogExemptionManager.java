package union.utils.database.managers;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import union.objects.constants.Constants;
import union.utils.FixedCache;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class LogExemptionManager extends LiteDBBase {

	// Cache
	private final FixedCache<Long, Set<Long>> cache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE);
	
	public LogExemptionManager(ConnectionUtil cu) {
		super(cu, "logExceptions");
	}

	public void addExemption(long guildId, long targetId) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, targetId) VALUES (%s, %s)".formatted(table, guildId, targetId));
	}

	public void removeExemption(long guildId, long targetId) throws SQLException {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%s AND targetId=%s)".formatted(table, guildId, targetId));
	}

	public void removeGuild(long guildId) throws SQLException {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public boolean isExemption(long guildId, long targetId) {
		return getExemptions(guildId).contains(targetId);
	}

	public Set<Long> getExemptions(long guildId) {
		if (cache.contains(guildId))
			return cache.get(guildId);
		List<Long> data = select("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), "targetId", Long.class);
		Set<Long> dataSet = data.isEmpty() ? Set.of() : new HashSet<>(data);
		cache.put(guildId, dataSet);
		return dataSet;
	}

	private void invalidateCache(long guildId) {
		cache.pull(guildId);
	}
}

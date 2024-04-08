package union.utils.database.managers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import union.objects.constants.Constants;
import union.utils.FixedCache;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class LogExceptionManager extends LiteDBBase {

	// Cache
	private final FixedCache<Long, Set<Long>> cache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE*5);
	
	public LogExceptionManager(ConnectionUtil cu) {
		super(cu, "logExceptions");
	}

	public void addException(long guildId, long targetId) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, targetId) VALUES (%s, %s)".formatted(table, guildId, targetId));
	}

	public void removeException(long guildId, long targetId) {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%s AND targetId=%s)".formatted(table, guildId, targetId));
	}

	public void removeGuild(long guildId) {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public boolean isException(long guildId, long targetId) {
		return getExceptions(guildId).contains(targetId);
	}

	public Set<Long> getExceptions(long guildId) {
		if (cache.contains(guildId))
			return cache.get(guildId);
		List<Long> data = select("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), "targetId", Long.class);
		if (data.isEmpty())
			return Set.of();
		Set<Long> dataSet = new HashSet<>(data);
		cache.put(guildId, dataSet);
		return dataSet;
	}

	private void invalidateCache(long guildId) {
		cache.pull(guildId);
	}
}

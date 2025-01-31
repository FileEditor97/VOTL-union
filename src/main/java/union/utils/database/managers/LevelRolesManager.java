package union.utils.database.managers;

import union.objects.constants.Constants;
import union.utils.FixedCache;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LevelRolesManager extends LiteDBBase {

	// cache
	private final FixedCache<Long, Map<Integer, Set<Long>>> cache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE);

	public LevelRolesManager(ConnectionUtil cu) {
		super(cu, "levelRoles");
	}

	public boolean add(long guildId, int level, String roleIds, boolean exact) {
		invalidateCache(guildId);
		return execute("INSERT INTO %s(guildId, level, roles, exact) VALUES (%d, %d, %s, %d) ON COFLICT(guildId, level) DO UPDATE SET roles=%4$s, exact=%5$d".formatted(table, guildId, level, quote(roleIds), exact?1:0));
	}

	public void removeGuild(long guildId) {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public boolean remove(long guildId, int level) {
		invalidateCache(guildId);
		return execute("DELETE FROM %s WHERE (guildId=%d AND level=%d)".formatted(table, guildId, level));
	}

	public Set<Long> getRoles(long guildId, int level) {
		return getAllLevels(guildId).getOrDefault(level, Set.of());
	}

	public Map<Integer, Set<Long>> getAllLevels(long guildId) {
		if (cache.contains(guildId))
			return cache.get(guildId);
		Map<Integer, Set<Long>> data = getData(guildId);
		if (data.isEmpty())
			return Map.of();
		cache.put(guildId, data);
		return data;
	}

	private Map<Integer, Set<Long>> getData(long guildId) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), Set.of("level", "roles"));
		if (data.isEmpty()) return Map.of();
		return data.stream().collect(Collectors.toMap(
			s -> (Integer) s.get("level"),
			s -> Stream.of(String.valueOf(s.get("roles")).split(";"))
				.map(Long::parseLong)
				.collect(Collectors.toSet())
			));
	}

	public int getLevelsCount(long guildId) {
		return getAllLevels(guildId).size();
	}

	private void invalidateCache(long guildId) {
		cache.pull(guildId);
	}

}

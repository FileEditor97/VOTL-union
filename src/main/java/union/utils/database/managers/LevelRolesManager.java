package union.utils.database.managers;

import org.jetbrains.annotations.Nullable;
import union.objects.ExpType;
import union.objects.constants.Constants;
import union.utils.FixedCache;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static union.utils.CastUtil.requireNonNull;

public class LevelRolesManager extends LiteDBBase {

	// cache
	private final FixedCache<Long, LevelRoleData> cache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE);

	public LevelRolesManager(ConnectionUtil cu) {
		super(cu, "levelRoles");
	}

	public boolean add(long guildId, int level, String roleIds, boolean exact, ExpType type) {
		invalidateCache(guildId);
		return execute("INSERT INTO %s(guildId, level, roles, exact, type) VALUES (%d, %d, %s, %d, %d) ON CONFLICT(guildId, level) DO UPDATE SET roles=%4$s, exact=%5$d, type=%6$d".formatted(table, guildId, level, quote(roleIds), exact?1:0, type.ordinal()));
	}

	public void removeGuild(long guildId) {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public boolean remove(long guildId, int level) {
		invalidateCache(guildId);
		return execute("DELETE FROM %s WHERE (guildId=%d AND level=%d)".formatted(table, guildId, level));
	}

	public Set<Long> getRoles(long guildId, int level, ExpType expType) {
		return getAllLevels(guildId).getRoles(expType, level);
	}

	@Nullable
	public LevelRoleData getAllLevels(long guildId) {
		if (cache.contains(guildId))
			return cache.get(guildId);
		LevelRoleData data = getData(guildId);
		cache.put(guildId, data);
		return data;
	}

	private LevelRoleData getData(long guildId) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), Set.of("level", "roles", "type"));
		if (data.isEmpty()) return new LevelRoleData();
		return new LevelRoleData(data);
	}

	public int getLevelsCount(long guildId) {
		return getAllLevels(guildId).size();
	}

	private void invalidateCache(long guildId) {
		cache.pull(guildId);
	}

	public class LevelRoleData {
		private final Map<Integer, Set<Long>> textRoles = new HashMap<>();
		private final Map<Integer, Set<Long>> voiceRoles = new HashMap<>();

		public LevelRoleData() {}

		public LevelRoleData(List<Map<String, Object>> data) {
			data.forEach(map -> {
				int typeValue = requireNonNull(map.get("type"));
				int level = requireNonNull(map.get("level"));
				Set<Long> roleIds = Stream.of(String.valueOf(map.get("roles")).split(";"))
					.map(Long::parseLong)
					.collect(Collectors.toSet());
				switch (typeValue) {
					case 0 -> {
						textRoles.put(level, roleIds);
						voiceRoles.put(level, roleIds);
					}
					case 1 -> textRoles.put(level, roleIds);
					case 2 -> voiceRoles.put(level, roleIds);
				}
			});
		}

		public Set<Long> getRoles(ExpType expType, int level) {
			return getAllRoles(expType).getOrDefault(level, Set.of());
		}

		public Map<Integer, Set<Long>> getAllRoles(ExpType expType) {
			return switch (expType) {
				case TEXT -> textRoles;
				case VOICE -> voiceRoles;
				default -> throw new IllegalStateException("Unexpected value: " + expType);
			};
		}

		public boolean existsAtLevel(int level) {
			return textRoles.containsKey(level) || voiceRoles.containsKey(level);
		}

		public int size() {
			return textRoles.size() + voiceRoles.size();
		}

		public boolean isEmpty() {
			return textRoles.isEmpty() && voiceRoles.isEmpty();
		}
	}

}

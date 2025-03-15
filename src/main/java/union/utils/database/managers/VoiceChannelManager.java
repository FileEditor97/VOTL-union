package union.utils.database.managers;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class VoiceChannelManager extends LiteDBBase {
	// Cache
	private final HashMap<Long, Long> cache = new HashMap<>();

	public VoiceChannelManager(ConnectionUtil cu) {
		super(cu, "voiceChannels");
		cache.putAll(getDbCache());
	}

	public void add(long userId, long channelId) {
		cache.put(userId, channelId);
		try {
			execute("INSERT INTO %s(userId, channelId) VALUES (%d, %d) ON CONFLICT(channelId) DO UPDATE SET channelId=%<d".formatted(table, userId, channelId));
		} catch (SQLException ignored) {}
	}

	public void remove(long channelId) {
		Optional.ofNullable(getUser(channelId)).ifPresent(cache::remove);
		try {
			execute("DELETE FROM %s WHERE (channelId=%d)".formatted(table, channelId));
		} catch (SQLException ignored) {}
	}

	public boolean existsUser(long userId) {
		return cache.containsKey(userId);
	}

	public boolean existsChannel(long channelId) {
		return cache.containsValue(channelId);
	}

	public void setUser(long userId, long channelId) throws SQLException {
		// Remove user with same channelId
		cache.entrySet().stream()
			.filter((e) -> e.getValue().equals(channelId))
			.map(Map.Entry::getKey)
			.findFirst().ifPresent(cache::remove);
		// Add new user
		cache.put(userId, channelId);
		execute("UPDATE %s SET userId=%s WHERE (channelId=%d)".formatted(table, userId, channelId));
	}

	public Long getChannel(long userId) {
		return cache.get(userId);
	}

	public Long getUser(long channelId) {
		return cache.entrySet().stream()
			.filter((e) -> e.getValue().equals(channelId))
			.map(Map.Entry::getKey)
			.findAny().orElse(null);
	}

	private Map<Long, Long> getDbCache() {
		List<Map<String, Object>> data = select("SELECT * FROM %s".formatted(table), Set.of("channelId", "userId"));
		if (data.isEmpty()) return Map.of();
		return data.stream()
			.collect(Collectors.toMap(s -> (Long) s.get("userId"), s -> (Long) s.get("channelId")));
	}
}

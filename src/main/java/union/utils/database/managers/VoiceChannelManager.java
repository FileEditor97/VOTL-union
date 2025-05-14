package union.utils.database.managers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static union.utils.CastUtil.castLong;

public class VoiceChannelManager extends LiteDBBase {
	// Cache
	// user - channel
	private final Cache<Long, Long> cache = Caffeine.newBuilder()
		.build();

	public VoiceChannelManager(ConnectionUtil cu) {
		super(cu, "voiceChannels");
		// try to populate cache
		cache.putAll(getDbCache());
	}

	public void add(long userId, long channelId) {
		cache.put(userId, channelId);
		try {
			execute("INSERT INTO %s(userId, channelId) VALUES (%d, %d) ON CONFLICT(channelId) DO UPDATE SET channelId=%<d".formatted(table, userId, channelId));
		} catch (SQLException ignored) {}
	}

	public void remove(long channelId) {
		Optional.ofNullable(getUser(channelId))
			.ifPresentOrElse(userId -> {
				cache.invalidate(userId);
				try {
					execute("DELETE FROM %s WHERE (userId=%d OR channelId=%d)".formatted(table, userId, channelId));
				} catch (SQLException ignored) {}
			}, () -> {
				try {
					execute("DELETE FROM %s WHERE (channelId=%d)".formatted(table, channelId));
				} catch (SQLException ignored) {}
			});
	}

	public boolean existsUser(long userId) {
		return cache.getIfPresent(userId) != null;
	}

	public boolean existsChannel(long channelId) {
		return cache.getIfPresent(channelId) != null;
	}

	public void setUser(long userId, long channelId) throws SQLException {
		// Remove user with same channelId
		Long cacheChannelId = cache.getIfPresent(userId);
		if (cacheChannelId != null && channelId == cacheChannelId) {
			cache.invalidate(userId);
		}
		// Add new user
		cache.put(userId, channelId);
		execute("UPDATE %s SET userId=%s WHERE (channelId=%d)".formatted(table, userId, channelId));
	}

	public Long getChannel(long userId) {
		return cache.getIfPresent(userId);
	}

	public Long getUser(long channelId) {
		return cache.asMap().entrySet()
			.stream()
			.filter(e -> e.getValue() == channelId)
			.findFirst()
			.map(Map.Entry::getKey)
			.orElse(null);
	}

	private Map<Long, Long> getDbCache() {
		List<Map<String, Object>> data = select("SELECT * FROM %s".formatted(table), Set.of("channelId", "userId"));
		if (data.isEmpty()) return Map.of();
		Map<Long, Long> cacheData = new HashMap<>();
		for (Map<String, Object> row : data) {
			cacheData.put(castLong(row.get("userId")), castLong(row.get("channelId")));
		}
		return cacheData;
	}

	public void checkCache(JDA jda) {
		cache.asMap().forEach((userId, channelId) -> {
			VoiceChannel voiceChannel = jda.getVoiceChannelById(channelId);
			if (voiceChannel == null) {
				remove(channelId);
			} else if (voiceChannel.getMembers().isEmpty()) {
				voiceChannel.delete().queue();
				remove(channelId);
			}
		});
	}
}

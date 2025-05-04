package union.utils;

import net.dv8tion.jda.api.audit.ActionType;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;

public class AlertUtil {

	public static final int DEFAULT_TRIGGER_AMOUNT = 6;

	public static final int FIRST_STAGE_DURATION = 30*60_000; // 30 minutes
	public static final int SECOND_STAGE_DURATION = 240*60_000; // 4 hours

	public static final Set<ActionType> WATCHED_ACTIONS = Set.of(
		ActionType.BAN, ActionType.KICK, ActionType.PRUNE,
		ActionType.CHANNEL_DELETE, ActionType.ROLE_DELETE, ActionType.MESSAGE_DELETE, ActionType.THREAD_DELETE,
		ActionType.INTEGRATION_DELETE, ActionType.AUTO_MODERATION_RULE_DELETE,
		ActionType.GUILD_UPDATE, ActionType.ROLE_UPDATE
	);

	// cache
	private final HashMap<WatchedUser, Data> watchedUsers = new HashMap<>();
	private final HashMap<WatchedUser, Integer> alertPoints = new HashMap<>();

	// watch
	public void watch(long guildId, long userId) {
		final WatchedUser key = new WatchedUser(guildId, userId);
		final Data data = new Data(Instant.now().toEpochMilli(), 0);
		watchedUsers.put(key, data);
	}

	// add to watched and return new data
	@Nullable
	public Data addToWatched(long guildId, long userId) {
		final WatchedUser key = new WatchedUser(guildId, userId);

		Data data = watchedUsers.get(key);
		if (data == null) return null;

		final Data newData = new Data(data.startTime, data.amount+1);
		watchedUsers.put(key, newData);
		return data;
	}

	// add new point
	public int addPoint(long guildId, long userId) {
		final WatchedUser key = new WatchedUser(guildId, userId);

		Integer points = alertPoints.getOrDefault(key, 0);
		points++;

		alertPoints.put(key, points);
		return points;
	}

	// remove
	public void decrease() {
		Iterator<Map.Entry<WatchedUser, Integer>> it = alertPoints.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<WatchedUser, Integer> entry = it.next();

			int newValue = entry.getValue() - 1;
			if (newValue <= 0)
				it.remove();
			else
				entry.setValue(newValue);
		}
	}

	public void checkWatched() {
		final long now = Instant.now().toEpochMilli();

		watchedUsers.entrySet().removeIf(entry -> now - entry.getValue().startTime > SECOND_STAGE_DURATION);
	}

	private record WatchedUser(long guildId, long userId) {
		@Override
		public boolean equals(Object o) {
			if (!(o instanceof WatchedUser cacheUser)) return false;
			return userId == cacheUser.userId && guildId == cacheUser.guildId;
		}

		@Override
		public int hashCode() {
			return Objects.hash(guildId, userId);
		}
	}

	public record Data(long startTime, int amount) {}
}

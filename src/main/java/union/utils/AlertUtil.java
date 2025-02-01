package union.utils;

import net.dv8tion.jda.api.entities.Member;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class AlertUtil {

	public static final int TRIGGER_AMOUNT = 6;

	// cache
	public static HashMap<String, Integer> alertPoints = new HashMap<>();

	// returns new value
	public int add(@NotNull Member member) {
		return add(member.getGuild().getIdLong(), member.getIdLong());
	}

	public int add(long guildId, long userId) {
		final String key = asKey(guildId, userId);

		Integer points = alertPoints.getOrDefault(key, 0);
		points++;

		alertPoints.put(key, points);
		return points;
	}

	public void decrease() {
		Iterator<Map.Entry<String, Integer>> it = alertPoints.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Integer> item = it.next();

			int newValue = item.getValue() - 1;
			if (newValue <= 0)
				it.remove();
			else
				alertPoints.put(item.getKey(), newValue);

		}
	}

	public static String asKey(long guildId, long userId) {
		return guildId + ":" + userId;
	}
}

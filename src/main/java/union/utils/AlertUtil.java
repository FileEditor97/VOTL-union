package union.utils;

import net.dv8tion.jda.api.entities.Member;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;

public class AlertUtil {

	public static final int TRIGGER_AMOUNT = 6;

	// cache
	public static LinkedHashMap<String, Integer> alertPoints = new LinkedHashMap<>();

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
		alertPoints.forEach((k, v) -> {
			int newValue = v - 1;
			if (newValue <= 0)
				alertPoints.remove(k);
			else
				alertPoints.put(k, newValue);
		});
	}

	public static String asKey(long guildId, long userId) {
		return guildId + ":" + userId;
	}
}

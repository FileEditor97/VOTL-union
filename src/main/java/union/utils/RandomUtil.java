package union.utils;

import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.util.List;

@SuppressWarnings("unused")
public class RandomUtil {

	private static final SecureRandom random = new SecureRandom();

	public static int getInteger(int bound) {
		if (bound <= 0) {
			return 0;
		}

		return random.nextInt(bound);
	}

	@NotNull
	public static String pickRandom(@NotNull String... strings) {
		return strings[random.nextInt(strings.length)];
	}

	@NotNull
	public static Object pickRandom(@NotNull List<?> strings) {
		return strings.get(random.nextInt(strings.size()));
	}
}

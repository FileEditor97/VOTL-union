package union.utils.message;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import union.utils.CastUtil;
import union.utils.file.lang.LocaleUtil;

@SuppressWarnings("unused")
public class MessageUtil {

	private static final Random random = new Random();
	private final LocaleUtil lu;

	private static final DecimalFormat decimalFormat = new DecimalFormat("# ### ###");

	private static final Pattern rolePattern = Pattern.compile("<@&(\\d+)>", Pattern.CASE_INSENSITIVE);

	public MessageUtil(LocaleUtil localeUtil) {
		this.lu = localeUtil;
	}

	public static String capitalize(final String str) {
		if (str == null || str.isEmpty()) {
			return "";
		}

		return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
	}

	public static List<Long> getRoleIdsFromString(String text) {
		List<Long> ids = new ArrayList<>();
		if (text.contains("+")) ids.add(0L);

		Matcher roleMatcher = rolePattern.matcher(text);
		while (roleMatcher.find()) {
			ids.add(CastUtil.castLong(roleMatcher.group(1)));
		}
		
		return ids;
	}

	public static Color getColor(String input) {
		input = input.toLowerCase();
		if (!input.equals("random") && !(input.length() == 6 || input.contains(",")))
			return null;

		Color color;

		if (input.equals("random")) {
			int r = random.nextInt(256);
			int g = random.nextInt(256);
			int b = random.nextInt(256);

			return new Color(r, g, b);
		}

		if (input.length() == 6) {
			try {
				color = Color.decode("#"+input);
			} catch (NumberFormatException ignored) {
				return null;
			}
		} else {
			String[] rgb = Arrays.copyOf(input.split(","), 3);
			try {
				color = new Color(Integer.parseInt(rgb[0]), Integer.parseInt(rgb[1]), Integer.parseInt(rgb[2]));
			} catch (NumberFormatException ignored) {
				return null;
			}
		}

		return color;
	}

	public String getFormattedMembers(String... members) {
		if (members.length == 1)
			return "**" + escapeAll(members[0]) + "**";

		StringBuilder builder = new StringBuilder();
		for (String member : members) {
			if (!builder.isEmpty())
				builder.append(", ");

			builder.append("**").append(escapeAll(member)).append("**");
		}

		return replaceLast(builder.toString(), ", ", " "+lu.getText("misc.and")+" ");
	}

	public static String replaceLast(String input, String target, String replacement) {
		if (!input.contains(target))
			return input;

		StringBuilder builder = new StringBuilder(input);
		builder.replace(input.lastIndexOf(target), input.lastIndexOf(target) + 1, replacement);

		return builder.toString();
	}

	public static String formatNumber(long number) {
		return decimalFormat.format(number).strip();
	}

	private static String escapeAll(String name) {
		return name.replace("*", "\\*")
			.replace("_", "\\_")
			.replace("`", "\\`")
			.replace("~", "\\~");
	}

	public static String limitString(String text, int limit) {
		if (text == null) return "";
		if (text.length() > limit)
			return text.substring(0, limit-3) + "...";
		return text;
	}

	public static String formatKey(String text) {
		return capitalize(text).replace("_", " ");
	}

}

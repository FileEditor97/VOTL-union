package union.utils.message;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Random;

import union.App;

import net.dv8tion.jda.api.interactions.DiscordLocale;

public class MessageUtil {

	private final Random random;
	private final LocaleUtil lu;

	private final DecimalFormat decimalFormat = new DecimalFormat("# ### ###");

	public MessageUtil(App bot) {
		this.random = bot.getRandom();
		this.lu = bot.getLocaleUtil();
	}

	public String capitalize(final String str) {
		if (str == null || str.length() == 0) {
			return "";
		}

		final String s0 = str.substring(0, 1).toUpperCase();
		return s0 + str.substring(1);
	}

	public Color getColor(String input) {
		input = input.toLowerCase();
		if (!input.equals("random") && !(input.length() == 6 || input.contains(",")))
			return null;

		Color color = null;

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

	public String getFormattedMembers(DiscordLocale locale, String... members) {
		if (members.length == 1)
			return "**" + escapeAll(members[0]) + "**";

		StringBuilder builder = new StringBuilder();
		for (String member : members) {
			if (builder.length() > 0)
				builder.append(", ");

			builder.append("**").append(escapeAll(member)).append("**");
		}

		return replaceLast(builder.toString(), ", ", " "+lu.getText("misc.and")+" ");
	}

	public String replaceLast(String input, String target, String replacement) {
		if (!input.contains(target))
			return input;

		StringBuilder builder = new StringBuilder(input);
		builder.replace(input.lastIndexOf(target), input.lastIndexOf(target) + 1, replacement);

		return builder.toString();
	}

	public String formatNumber(long number) {
		return decimalFormat.format(number);
	}

	private String escapeAll(String name) {
		return name.replace("*", "\\*")
			.replace("_", "\\_")
			.replace("`", "\\`")
			.replace("~", "\\~");
	}

}

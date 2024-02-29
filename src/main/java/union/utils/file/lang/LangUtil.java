package union.utils.file.lang;

import java.util.List;

import union.App;
import union.objects.annotation.NotNull;

import net.dv8tion.jda.api.interactions.DiscordLocale;

public final class LangUtil {

	private final App bot;
	public LangUtil(App bot) {
		this.bot = bot;
	}
	
	@NotNull
	public String getString(String language, String path) {
		switch (language) {
			case "ru":
				return bot.getFileManager().getString(language, path);
			default: //en
				return bot.getFileManager().getString("en-GB", path);
		}
	}

	@NotNull
	public List<String> getStringList(String language, String path) {
		switch (language) {
			case "ru":
				return bot.getFileManager().getStringList(language, path);
			default: //en
				return bot.getFileManager().getStringList("en-GB", path);
		}
	}

	public enum Language {
		EN_GB ("\uDDEC", "\uDDE7"),
		RU    ("\uDDF7", "\uDDFA"),

		UNKNOWN ("\uDDFA", "\uDDF3");

		@NotNull
		private final String emote;

		Language(String code1, String code2) {
			this.emote = "\uD83C" + code1 + "\uD83C" + code2;
		}

		@NotNull
		public static String getEmote(DiscordLocale locale) {
			String lang = locale.getLocale();
			for (Language language : values()) {
				if (lang.equals(language.name().toLowerCase().replace("_", "-")))
					return language.emote;
			}

			return UNKNOWN.emote;
		}

		@NotNull
		public static String getString(DiscordLocale locale) {
			String lang = locale.getLocale().toLowerCase();
			for (Language language : values()) {
				if (lang.equals(language.name().toLowerCase().replace("_", "-")))
					return language.emote + " " + locale.getNativeName();
			}

			return UNKNOWN.emote + " " + UNKNOWN.name().toLowerCase();
		}
	}
}

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
	public String getString(DiscordLocale locale, String path) {
		switch (locale) {
			case RUSSIAN:
				return bot.getFileManager().getString(locale.getLocale(), path);
			default: //en
				return bot.getFileManager().getString("en-GB", path);
		}
	}

	@NotNull
	public List<String> getStringList(DiscordLocale locale, String path) {
		switch (locale) {
			case RUSSIAN:
				return bot.getFileManager().getStringList(locale.getLocale(), path);
			default: //en
				return bot.getFileManager().getStringList("en-GB", path);
		}
	}

}

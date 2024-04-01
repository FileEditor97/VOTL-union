package union.utils.file.lang;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import union.App;
import union.objects.Emotes;
import union.objects.annotation.NotNull;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

public class LocaleUtil {

	private final App bot;
	private final LangUtil langUtil;
	private final String defaultLanguage;
	private final DiscordLocale defaultLocale;

	public LocaleUtil(App bot, String defaultLanguage, DiscordLocale defaultLocale) {
		this.bot = bot;
		this.langUtil = new LangUtil(bot.getFileManager());
		this.defaultLanguage = defaultLanguage;
		this.defaultLocale = defaultLocale;
	}

	@NotNull
	public String getDefaultLanguage() {
		return defaultLanguage;
	}

	@NotNull
	public String getLocalized(DiscordLocale locale, String path) {
		return Emotes.getWithEmotes(langUtil.getString(locale, path));
	}

	@NotNull
	public String getLocalized(DiscordLocale locale, String path, String user) {
		return getLocalized(locale, path, user, true);
	}

	@NotNull
	public String getLocalized(DiscordLocale locale, String path, String user, boolean format) {
		if (format)
			user = bot.getMessageUtil().getFormattedMembers(locale, user);

		return Objects.requireNonNull(getLocalized(locale, path).replace("{user}", user));
	}

	@NotNull
	public String getLocalized(DiscordLocale locale, String path, String user, String target) {
		target = (target == null ? "null" : target);
		
		return getLocalized(locale, path, user, Collections.singletonList(target), false);
	}

	@NotNull
	public String getLocalized(DiscordLocale locale, String path, String user, List<String> targets) {
		return getLocalized(locale, path, user, targets, false);
	}

	@NotNull
	public String getLocalized(DiscordLocale locale, String path, String user, List<String> targets, boolean format) {
		String targetReplacement = targets.isEmpty() ? "null" : bot.getMessageUtil().getFormattedMembers(locale, targets.toArray(new String[0]));

		return Objects.requireNonNull(getLocalized(locale, path, user, format)
			.replace("{target}", targetReplacement)
			.replace("{targets}", targetReplacement)
		);
	}

	@NotNull
	public Map<DiscordLocale, String> getFullLocaleMap(String path) {
		Map<DiscordLocale, String> localeMap = new HashMap<>();
		for (DiscordLocale locale : bot.getFileManager().getLanguages()) {
			// Also counts en-US as en-GB (otherwise rises problem)
			// Later may be changed
			if (locale.getLocale().equals(DiscordLocale.ENGLISH_UK.getLocale()))
				localeMap.put(DiscordLocale.ENGLISH_US, getLocalized(DiscordLocale.ENGLISH_US, path));
			localeMap.put(locale, getLocalized(locale, path));
		}
		return localeMap;
	}

	@NotNull
	public String getText(@NotNull String path) {
		return getLocalized(defaultLocale, path);
	}

	@NotNull
	public String getText(IReplyCallback replyCallback, @NotNull String path) {
		return getLocalized(replyCallback.getUserLocale(), path);
	}

	@NotNull
	public String getUserText(IReplyCallback replyCallback, @NotNull String path) {
		return getUserText(replyCallback, path, Collections.emptyList(), false);
	}

	@NotNull
	public String getUserText(IReplyCallback replyCallback, @NotNull String path, boolean format) {
		return getUserText(replyCallback, path, Collections.emptyList(), format);
	}

	@NotNull
	public String getUserText(IReplyCallback replyCallback, @NotNull String path, String target) {
		return getUserText(replyCallback, path, Collections.singletonList(target), false);
	}
	
	@NotNull
	public String getUserText(IReplyCallback replyCallback, @NotNull String path, List<String> targets) {
		return getUserText(replyCallback, path, targets, false);
	}

	@NotNull
	private String getUserText(IReplyCallback replyCallback, @NotNull String path, List<String> targets, boolean format) {
		return getLocalized(replyCallback.getUserLocale(), path, replyCallback.getUser().getEffectiveName(), targets, format);
	}

}

package union.utils.message;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import union.App;
import union.objects.Emotes;
import union.objects.command.CommandEvent;
import union.utils.file.lang.LangUtil;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

public class LocaleUtil {

	private final App bot;
	private final LangUtil langUtil;
	private final String defaultLanguage;
	private final DiscordLocale defaultLocale;

	public LocaleUtil(App bot, LangUtil langUtil, String defaultLanguage, DiscordLocale defaultLocale) {
		this.bot = bot;
		this.langUtil = langUtil;
		this.defaultLanguage = defaultLanguage;
		this.defaultLocale = defaultLocale;
	}

	@Nonnull
	public String getDefaultLanguage() {
		return defaultLanguage;
	}

	@Nonnull
	private DiscordLocale getGuildLocale(@Nullable Guild guild) {
		return (guild == null ? defaultLocale : guild.getLocale());
	}

	@Nonnull
	public String getLocalized(DiscordLocale locale, String path) {
		return setPlaceholders(langUtil.getString(locale.getLocale(), path));
	}

	@Nonnull
	public String getLocalized(DiscordLocale locale, String path, String user) {
		return getLocalized(locale, path, user, true);
	}

	@Nonnull
	public String getLocalized(DiscordLocale locale, String path, String user, boolean format) {
		if (format)
			user = bot.getMessageUtil().getFormattedMembers(locale, user);

		return Objects.requireNonNull(getLocalized(locale, path).replace("{user}", user));
	}

	@Nonnull
	public String getLocalized(DiscordLocale locale, String path, String user, String target) {
		target = (target == null ? "null" : target);
		
		return getLocalized(locale, path, user, Collections.singletonList(target), false);
	}

	@Nonnull
	public String getLocalized(DiscordLocale locale, String path, String user, List<String> targets) {
		return getLocalized(locale, path, user, targets, false);
	}

	@Nonnull
	public String getLocalized(DiscordLocale locale, String path, String user, List<String> targets, boolean format) {
		String targetReplacement = targets.isEmpty() ? "null" : bot.getMessageUtil().getFormattedMembers(locale, targets.toArray(new String[0]));

		return Objects.requireNonNull(getLocalized(locale, path, user, format)
			.replace("{target}", targetReplacement)
			.replace("{targets}", targetReplacement)
		);
	}

	@Nonnull
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

	@Nonnull
	private String setPlaceholders(@Nonnull String msg) {
		return Objects.requireNonNull(Emotes.getWithEmotes(msg));
	}

	@Nonnull
	public String getText(@Nonnull String path) {
		return getLocalized(defaultLocale, path);
	}

	@Nonnull
	public String getText(IReplyCallback replyCallback, @Nonnull String path) {
		return getLocalized(replyCallback.getUserLocale(), path);
	}

	@Nonnull
	public String getText(CommandEvent event, @Nonnull String path) {
		return getLocalized(getGuildLocale(event.getGuild()), path);
	}

	@Nonnull
	public String getUserText(IReplyCallback replyCallback, @Nonnull String path) {
		return getUserText(replyCallback, path, Collections.emptyList(), false);
	}

	@Nonnull
	public String getUserText(IReplyCallback replyCallback, @Nonnull String path, boolean format) {
		return getUserText(replyCallback, path, Collections.emptyList(), format);
	}

	@Nonnull
	public String getUserText(IReplyCallback replyCallback, @Nonnull String path, String target) {
		return getUserText(replyCallback, path, Collections.singletonList(target), false);
	}
	
	@Nonnull
	public String getUserText(IReplyCallback replyCallback, @Nonnull String path, List<String> targets) {
		return getUserText(replyCallback, path, targets, false);
	}

	@Nonnull
	private String getUserText(IReplyCallback replyCallback, @Nonnull String path, List<String> targets, boolean format) {
		return getLocalized(replyCallback.getUserLocale(), path, replyCallback.getUser().getEffectiveName(), targets, format);
	}

}

package union.utils;

import java.time.Duration;
import java.time.Instant;

import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import union.objects.CaseType;
import union.objects.annotation.NotNull;
import union.objects.annotation.Nullable;
import union.objects.constants.Constants;
import union.utils.database.DBUtil;
import union.utils.file.lang.LocaleUtil;
import union.utils.message.TimeUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.DiscordLocale;

public class ModerationUtil {

	private final DBUtil dbUtil;
	private final LocaleUtil lu;

	public ModerationUtil(DBUtil dbUtil, LocaleUtil lu) {
		this.dbUtil = dbUtil;
		this.lu = lu;
	}
	
	@Nullable
	public MessageEmbed getDmEmbed(CaseType type, Guild guild, String reason, Duration duration, User mod, boolean canAppeal) {
		DiscordLocale locale = guild.getLocale();
		int level;
		String text;
		switch (type) {
			case BAN -> {
				level = dbUtil.getGuildSettings(guild).getInformBan().getLevel();
				if (level == 0) return null;
				if (level >= 2)
					text = duration.isZero() ?
						lu.getLocalized(locale, "logger_embed.pm.banned_perm")
						:
						lu.getLocalized(locale, "logger_embed.pm.banned_temp");
				else
					text = lu.getLocalized(locale, "logger_embed.pm.banned");
			}
			case KICK -> {
				level = dbUtil.getGuildSettings(guild).getInformKick().getLevel();
				if (level == 0) return null;
				text = lu.getLocalized(locale, "logger_embed.pm.kicked");
			}
			case MUTE -> {
				level = dbUtil.getGuildSettings(guild).getInformMute().getLevel();
				if (level == 0) return null;
				text = lu.getLocalized(locale, "logger_embed.pm.muted");
			}
			case STRIKE_1, STRIKE_2, STRIKE_3 -> {
				level = dbUtil.getGuildSettings(guild).getInformStrike().getLevel();
				if (level == 0) return null;
				text = lu.getLocalized(locale, "logger_embed.pm.strike").formatted(
					lu.getLocalized(locale, "logger_embed.pm.strike"+(type.getType()-20)),
					"%s"
				);
			}
			default -> {
				return null;
			}
		}
		
		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
			.setDescription(formatText(text, guild, level >= 2 ? reason : null, level >= 2 ? duration : null, level >= 3 ? mod : null));
		if (type.equals(CaseType.BAN)) {
			String link = dbUtil.getGuildSettings(guild).getAppealLink();
			if (link != null && canAppeal)
				builder.appendDescription(lu.getLocalized(locale, "logger_embed.pm.appeal").formatted(link));
		}

		return builder.build();
	}

	@Nullable
	public MessageEmbed getDelstrikeEmbed(int amount, Guild guild, User mod) {
		int level = dbUtil.getGuildSettings(guild).getInformDelstrike().getLevel();
		if (level == 0) return null;
		String text = lu.getLocalized(guild.getLocale(), "logger_embed.pm.delstrike").formatted(amount);
		return new EmbedBuilder().setColor(Constants.COLOR_WARNING)
			.setDescription(formatText(text, guild, null, null, level >= 3 ? mod : null))
			.build();
	}

	@NotNull
	public MessageEmbed getReasonUpdateEmbed(DiscordLocale locale, Guild guild, Instant timestamp, CaseType caseType, String oldReason, String newReason) {
		if (oldReason == null) oldReason = "-";
		if (caseType.equals(CaseType.MUTE)) {
			// if is mute
			return new EmbedBuilder().setColor(Constants.COLOR_WARNING)
				.setDescription(lu.getLocalized(locale, "logger_embed.pm.reason_mute").formatted(guild.getName(), TimeUtil.formatTime(timestamp, false)))
				.appendDescription("\n\n**Old**: ||`"+oldReason+"`||\n**New**: `"+newReason+"`")
				.build();
		} else {
			// else is strike
			return new EmbedBuilder().setColor(Constants.COLOR_WARNING)
				.setDescription(lu.getLocalized(locale, "logger_embed.pm.reason_strike").formatted(guild.getName(), TimeUtil.formatTime(timestamp, false)))
				.appendDescription("\n\n**Old**: ||`"+oldReason+"`||\n**New**: `"+newReason+"`")
				.build();
		}
	}

	private String formatText(String text, Guild guild, String reason, Duration duration, User mod) {
		String newText = (duration == null) ? text : text.replace("{time}", TimeUtil.durationToLocalizedString(lu, guild.getLocale(), duration));
		StringBuilder builder = new StringBuilder(newText.replace("{guild}", guild.getName()));
		if (reason != null) builder.append(" | ").append(reason);
		if (mod != null) builder.append("\n\\- ").append(mod.getGlobalName());
		return builder.toString();
	}

	public MessageEmbed actionEmbed(DiscordLocale locale, int localCaseId, String actionPath, User target, User mod, String reason, String logUrl) {
		return new ActionEmbedBuilder(locale, localCaseId, target, mod, reason)
			.setDescription(lu.getLocalized(locale, actionPath))
			.addLink(logUrl)
			.build();
	}

	public MessageEmbed actionEmbed(DiscordLocale locale, int localCaseId, String actionPath, User target, User mod, String reason, Duration duration, String logUrl) {
		return new ActionEmbedBuilder(locale, localCaseId, target, mod, reason)
			.setDescription(lu.getLocalized(locale, actionPath)
				.formatted(TimeUtil.formatDuration(lu, locale, Instant.now(), duration)))
			.addLink(logUrl)
			.build();
	}

	public EmbedBuilder actionEmbed(DiscordLocale locale, int localCaseId, String actionPath, String typePath, User target, User mod, String reason, String logUrl) {
		return new ActionEmbedBuilder(locale, localCaseId, target, mod, reason)
			.setDescription(lu.getLocalized(locale, actionPath)
				.formatted(lu.getLocalized(locale, typePath)))
			.addLink(logUrl)
			.getBuilder();
	}

	@Nullable
	public MessageEmbed getGameStrikeEmbed(GuildChannel channel, User mod, String reason) {
		int level = dbUtil.getGuildSettings(channel.getGuild()).getInformStrike().getLevel();
		if (level == 0) return null;
		String text = lu.getLocalized(channel.getGuild().getLocale(), "logger_embed.pm.gamestrike").formatted(channel.getName(), channel.getJumpUrl());
		return new EmbedBuilder().setColor(Constants.COLOR_WARNING)
			.setDescription(formatText(text, channel.getGuild(), reason, null, level >= 3 ? mod : null))
			.build();
	}

	public class ActionEmbedBuilder {
		private final DiscordLocale locale;
		private final EmbedBuilder embedBuilder = new EmbedBuilder();

		public ActionEmbedBuilder(DiscordLocale locale, int caseLocalId, User target, User mod, String reason) {
			embedBuilder.setColor(Constants.COLOR_SUCCESS)
				.addField(lu.getLocalized(locale, "logger.user"), "%s (%s)".formatted(target.getName(), target.getAsMention()), true)
				.addField(lu.getLocalized(locale, "logger.reason"), reason, true)
				.addField(lu.getLocalized(locale, "logger.moderation.mod"), "%s (%s)".formatted(mod.getName(), mod.getAsMention()), false)
				.setTimestamp(Instant.now())
				.setFooter("#"+caseLocalId);
			this.locale = locale;
		}

		public ActionEmbedBuilder setDescription(String text) {
			embedBuilder.setDescription(text);
			return this;
		}

		public ActionEmbedBuilder addLink(String logUrl) {
			if (logUrl!=null)
				embedBuilder.appendDescription(
					lu.getLocalized(locale, "logger.moderation.log_url").formatted(logUrl)
				);
			return this;
		}

		public EmbedBuilder getBuilder() {
			return embedBuilder;
		}

		public MessageEmbed build() {
			return embedBuilder.build();
		}
	}
}

package union.commands.moderation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import union.App;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CaseType;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.database.managers.CaseManager.CaseData;
import union.utils.exception.FormatterException;
import union.utils.message.TimeUtil;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class BanCmd extends CommandBase {
	
	public BanCmd(App bot) {
		super(bot);
		this.name = "ban";
		this.path = "bot.moderation.ban";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
			new OptionData(OptionType.STRING, "time", lu.getText(path+".time.help")),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help")).setMaxLength(400),
			new OptionData(OptionType.BOOLEAN, "delete", lu.getText(path+".delete.help")),
			new OptionData(OptionType.BOOLEAN, "can_appeal", lu.getText(path+".can_appeal.help"))
		);
		this.botPermissions = new Permission[]{Permission.BAN_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 5;
		this.cooldownScope = CooldownScope.GUILD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();
		Guild guild = Objects.requireNonNull(event.getGuild());

		User tu = event.optUser("user");
		if (tu == null) {
			editError(event, path+".not_found");
			return;
		}
		if (event.getUser().equals(tu) || event.getJDA().getSelfUser().equals(tu)) {
			editError(event, path+".not_self");
			return;
		}

		final Duration duration;
		try {
			duration = TimeUtil.stringToDuration(event.optString("time"), false);
		} catch (FormatterException ex) {
			editError(event, ex.getPath());
			return;
		}

		String reason = event.optString("reason", lu.getLocalized(event.getGuildLocale(), path+".no_reason"));
		guild.retrieveBan(tu).queue(ban -> {
			CaseData oldBanData = bot.getDBUtil().cases.getMemberActive(tu.getIdLong(), guild.getIdLong(), CaseType.BAN);
			if (oldBanData != null) {
				// Active expirable ban
				if (duration.isZero()) {
					// make current temporary ban inactive
					bot.getDBUtil().cases.setInactive(oldBanData.getCaseIdInt());
					// create new entry
					Member mod = event.getMember();
					bot.getDBUtil().cases.add(CaseType.BAN, tu.getIdLong(), tu.getName(), mod.getIdLong(), mod.getUser().getName(),
						guild.getIdLong(), reason, Instant.now(), duration);
					CaseData newBanData = bot.getDBUtil().cases.getMemberLast(tu.getIdLong(), guild.getIdLong());
					// create embed
					MessageEmbed embed = bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getGuildText(event, path+".success")
							.formatted(TimeUtil.formatDuration(lu, event.getGuildLocale(), Instant.now(), duration)))
						.addField(lu.getGuildText(event, "logger.user"), "%s (%s)".formatted(tu.getName(), tu.getAsMention()), true)
						.addField(lu.getGuildText(event, "logger.reason"), reason, true)
						.addField(lu.getGuildText(event, "logger.moderation.mod"), "%s (%s)".formatted(mod.getUser().getName(), mod.getAsMention()), false)
						.build();
					// log ban
					bot.getLogger().mod.onNewCase(guild, tu, newBanData);

					// reply and add blacklist button
					event.getHook().editOriginalEmbeds(embed).setActionRow(
						Button.danger("blacklist:"+ban.getUser().getId(), "Blacklist").withEmoji(Emoji.fromUnicode("🔨")),
						Button.secondary("sync_ban:"+tu.getId(), "Group ban"),
						Button.secondary("sync_kick:"+tu.getId(), "Group kick")
					).queue();
				} else {
					// already has expirable ban (show caseID and use /duration to change time)
					MessageEmbed embed = bot.getEmbedUtil().getEmbed(Constants.COLOR_WARNING)
						.setDescription(lu.getText(event, path+".already_temp").replace("{id}", oldBanData.getCaseId()))
						.build();
					event.getHook().editOriginalEmbeds(embed).queue();
				}
			} else {
				// user has permanent ban, but not in DB
				// create new case for manual ban (that is not in DB)
				Member mod = event.getMember();
				bot.getDBUtil().cases.add(CaseType.BAN, tu.getIdLong(), tu.getName(), mod.getIdLong(), mod.getUser().getName(),
					guild.getIdLong(), reason, Instant.now(), Duration.ZERO);
				CaseData newBanData = bot.getDBUtil().cases.getMemberLast(tu.getIdLong(), guild.getIdLong());
				// log
				bot.getLogger().mod.onNewCase(guild, tu, newBanData);
				// create embed
				MessageEmbed embed = bot.getEmbedUtil().getEmbed(Constants.COLOR_WARNING)
					.setDescription(lu.getText(event, path+".already_banned"))
					.addField(lu.getText(event, "logger_embed.ban.short_title"), lu.getText(event, "logger_embed.ban.short_info")
						.replace("{username}", ban.getUser().getEffectiveName())
						.replace("{reason}", Optional.ofNullable(ban.getReason()).orElse("*none*"))
						, false)
					.build();
				// reply and add blacklist button
				event.getHook().editOriginalEmbeds(embed).setActionRow(
					Button.danger("blacklist:"+ban.getUser().getId(), "Blacklist").withEmoji(Emoji.fromUnicode("🔨")),
					Button.secondary("sync_ban:"+tu.getId(), "Group ban"),
					Button.secondary("sync_kick:"+tu.getId(), "Group kick")
				).queue();
			}
		},
		failure -> {
			// checks if thrown something except from "ban not found"
			if (!failure.getMessage().startsWith("10026")) {
				bot.getAppLogger().warn(failure.getMessage());
				editError(event, path+".ban_abort", failure.getMessage());
				return;
			}

			Member tm = event.optMember("user");
			Member mod = event.getMember();
			if (tm != null) {
				if (!guild.getSelfMember().canInteract(tm)) {
					editError(event, path+".ban_abort", "Bot can't interact with target member.");
					return;
				}
				if (bot.getCheckUtil().hasHigherAccess(tm, mod)) {
					editError(event, path+".higher_access");
					return;
				}
				if (!mod.canInteract(tm)) {
					editError(event, path+".ban_abort", "You can't interact with target member.");
					return;
				}
			}

			tu.openPrivateChannel().queue(pm -> {
				MessageEmbed embed = bot.getModerationUtil().getDmEmbed(CaseType.BAN, guild, reason, duration, mod.getUser(), event.optBoolean("can_appeal", true));
				if (embed == null) return;
				pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
			});

			guild.ban(tu, (event.optBoolean("delete", true) ? 10 : 0), TimeUnit.HOURS).reason(reason).queueAfter(3, TimeUnit.SECONDS, done -> {
				// fail-safe check if user has temporal ban (to prevent auto unban)
				CaseData oldBanData = bot.getDBUtil().cases.getMemberActive(tu.getIdLong(), guild.getIdLong(), CaseType.BAN);
				if (oldBanData != null) {
					bot.getDBUtil().cases.setInactive(oldBanData.getCaseIdInt());
				}
				// add info to db
				bot.getDBUtil().cases.add(CaseType.BAN, tu.getIdLong(), tu.getName(), mod.getIdLong(), mod.getUser().getName(),
					guild.getIdLong(), reason, Instant.now(), duration);
				CaseData newBanData = bot.getDBUtil().cases.getMemberLast(tu.getIdLong(), guild.getIdLong());
				// create embed
				MessageEmbed embed = bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getGuildText(event, path+".success")
						.formatted(TimeUtil.formatDuration(lu, event.getGuildLocale(), Instant.now(), duration)))
					.addField(lu.getGuildText(event, "logger.user"), "%s (%s)".formatted(tu.getName(), tu.getAsMention()), true)
					.addField(lu.getGuildText(event, "logger.reason"), reason, true)
					.addField(lu.getGuildText(event, "logger.moderation.mod"), "%s (%s)".formatted(mod.getUser().getName(), mod.getAsMention()), false)
					.build();
				// log ban
				bot.getLogger().mod.onNewCase(guild, tu, newBanData);

				// if permanent - add button to blacklist target
				if (duration.isZero())
					event.getHook().editOriginalEmbeds(embed).setActionRow(
						Button.danger("blacklist:"+tu.getId(), "Blacklist").withEmoji(Emoji.fromUnicode("🔨")),
						Button.secondary("sync_ban:"+tu.getId(), "Group ban"),
						Button.secondary("sync_kick:"+tu.getId(), "Group kick")
					).queue();
				else
					event.getHook().editOriginalEmbeds(embed).queue();
			},
			failed -> editError(event, path+".ban_abort", failed.getMessage()));
		});
	}
	
}

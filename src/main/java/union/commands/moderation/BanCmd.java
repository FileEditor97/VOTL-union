package union.commands.moderation;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.EmbedBuilder;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CaseType;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.CaseProofUtil;
import union.utils.database.managers.CaseManager.CaseData;
import union.utils.exception.AttachmentParseException;
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
	
	public BanCmd() {
		this.name = "ban";
		this.path = "bot.moderation.ban";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
			new OptionData(OptionType.STRING, "time", lu.getText(path+".time.help")),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help")).setMaxLength(400),
			new OptionData(OptionType.ATTACHMENT, "proof", lu.getText(path+".proof.help")),
			new OptionData(OptionType.BOOLEAN, "delete", lu.getText(path+".delete.help")),
			new OptionData(OptionType.BOOLEAN, "can_appeal", lu.getText(path+".can_appeal.help"))
		);
		this.botPermissions = new Permission[]{Permission.BAN_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 15;
		this.cooldownScope = CooldownScope.GUILD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();
		Guild guild = Objects.requireNonNull(event.getGuild());

		// Resolve user and check permission
		User tu = event.optUser("user");
		if (tu == null) {
			editError(event, path+".not_found");
			return;
		}
		if (event.getUser().equals(tu) || event.getJDA().getSelfUser().equals(tu)) {
			editError(event, path+".not_self");
			return;
		}

		// Ban duration
		final Duration duration;
		try {
			duration = TimeUtil.stringToDuration(event.optString("time"), false);
		} catch (FormatterException ex) {
			editError(event, ex.getPath());
			return;
		}

		// Get proof
		final CaseProofUtil.ProofData proofData;
		try {
			proofData = CaseProofUtil.getData(event);
		} catch (AttachmentParseException e) {
			editError(event, e.getPath(), e.getMessage());
			return;
		}

		String reason = event.optString("reason", lu.getLocalized(event.getGuildLocale(), path+".no_reason"));
		guild.retrieveBan(tu).queue(ban -> {
			CaseData oldBanData = bot.getDBUtil().cases.getMemberActive(tu.getIdLong(), guild.getIdLong(), CaseType.BAN);
			if (oldBanData != null) {
				// Active temporal ban
				if (duration.isZero()) {
					// set current ban inactive
					try {
						bot.getDBUtil().cases.setInactive(oldBanData.getRowId());
					} catch (SQLException e) {
						editErrorDatabase(event, e, "Failed to remove previous ban.");
						return;
					}
					// create new entry
					Member mod = event.getMember();
					CaseData newBanData;
					try {
						newBanData = bot.getDBUtil().cases.add(CaseType.BAN, tu.getIdLong(), tu.getName(), mod.getIdLong(), mod.getUser().getName(),
							guild.getIdLong(), reason, Instant.now(), duration);
					} catch (SQLException e) {
						editErrorDatabase(event, e, "Failed to create new case.");
						return;
					}
					// log ban
					bot.getLogger().mod.onNewCase(guild, tu, newBanData, proofData).thenAccept(logUrl -> {
						// Add log url to db
						bot.getDBUtil().cases.setLogUrl(newBanData.getRowId(), logUrl);
						// reply and add blacklist button
						event.getHook().editOriginalEmbeds(
							bot.getModerationUtil().actionEmbed(guild.getLocale(), newBanData.getLocalIdInt(),
								path+".success", tu, mod.getUser(), reason, duration, logUrl)
						).setActionRow(
							Button.danger("blacklist:"+ban.getUser().getId(), "Blacklist").withEmoji(Emoji.fromUnicode("🔨")),
							Button.secondary("sync_ban:"+tu.getId(), "Group ban"),
							Button.secondary("sync_kick:"+tu.getId(), "Group kick")
						).queue();
					});
				} else {
					// already has temporal ban (return case ID and use /duration to change time)
					MessageEmbed embed = bot.getEmbedUtil().getEmbed(Constants.COLOR_WARNING)
						.setDescription(lu.getText(event, path+".already_temp").replace("{id}", oldBanData.getLocalId()))
						.build();
					editEmbed(event, embed);
				}
			} else {
				// user has permanent ban, but not in DB
				// create new case for manual ban (that is not in DB)
				Member mod = event.getMember();
				CaseData newBanData;
				try {
					newBanData = bot.getDBUtil().cases.add(CaseType.BAN, tu.getIdLong(), tu.getName(), mod.getIdLong(), mod.getUser().getName(),
						guild.getIdLong(), reason, Instant.now(), Duration.ZERO);
				} catch (SQLException e) {
					editErrorDatabase(event, e, "Failed to create new case.");
					return;
				}
				// log ban
				bot.getLogger().mod.onNewCase(guild, tu, newBanData, proofData).thenAccept(logUrl -> {
					// Add log url to db
					bot.getDBUtil().cases.setLogUrl(newBanData.getRowId(), logUrl);
					// create embed
					EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed(Constants.COLOR_WARNING)
						.setDescription(lu.getText(event, path+".already_banned"))
						.addField(lu.getText(event, "logger.moderation.ban.short_title"), lu.getText(event, "logger.moderation.ban.short_info")
								.replace("{username}", ban.getUser().getEffectiveName())
								.replace("{reason}", Optional.ofNullable(ban.getReason()).orElse("*none*"))
							, false);
					// reply and add blacklist button
					event.getHook().editOriginalEmbeds(embedBuilder.build()).setActionRow(
						Button.danger("blacklist:"+ban.getUser().getId(), "Blacklist").withEmoji(Emoji.fromUnicode("🔨")),
						Button.secondary("sync_ban:"+tu.getId(), "Group ban"),
						Button.secondary("sync_kick:"+tu.getId(), "Group kick")
					).queue();
				});
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
				final String text = bot.getModerationUtil().getDmText(CaseType.BAN, guild, reason, duration, mod.getUser(), event.optBoolean("can_appeal", true));
				if (text == null) return;
				pm.sendMessage(text).setSuppressEmbeds(true)
					.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
			});

			guild.ban(tu, (event.optBoolean("delete", true) ? 10 : 0), TimeUnit.HOURS).reason(reason).queueAfter(3, TimeUnit.SECONDS, done -> {
				// fail-safe check if user has temporal ban (to prevent auto unban)
				CaseData oldBanData = bot.getDBUtil().cases.getMemberActive(tu.getIdLong(), guild.getIdLong(), CaseType.BAN);
				if (oldBanData != null) {
					try {
						bot.getDBUtil().cases.setInactive(oldBanData.getRowId());
					} catch (SQLException ignored) {}
				}
				// add info to db
					CaseData newBanData;
					try {
						newBanData = bot.getDBUtil().cases.add(CaseType.BAN, tu.getIdLong(), tu.getName(), mod.getIdLong(), mod.getUser().getName(),
							guild.getIdLong(), reason, Instant.now(), duration);
					} catch (SQLException e) {
						editErrorDatabase(event, e, "Failed to create new case.");
						return;
					}
					// log ban
				bot.getLogger().mod.onNewCase(guild, tu, newBanData, proofData).thenAccept(logUrl -> {
					// Add log url to db
					bot.getDBUtil().cases.setLogUrl(newBanData.getRowId(), logUrl);
					// create embed
					MessageEmbed embed = bot.getModerationUtil().actionEmbed(guild.getLocale(), newBanData.getLocalIdInt(),
						path+".success", tu, mod.getUser(), reason, duration, logUrl);
					// if permanent - add button to blacklist target
					if (duration.isZero())
						event.getHook().editOriginalEmbeds(embed).setActionRow(
							Button.danger("blacklist:"+tu.getId(), "Blacklist").withEmoji(Emoji.fromUnicode("🔨")),
							Button.secondary("sync_ban:"+tu.getId(), "Group ban"),
							Button.secondary("sync_kick:"+tu.getId(), "Group kick")
						).queue();
					else
						editEmbed(event, embed);
				});
			},
			failed -> editError(event, path+".ban_abort", failed.getMessage()));
		});
	}
	
}

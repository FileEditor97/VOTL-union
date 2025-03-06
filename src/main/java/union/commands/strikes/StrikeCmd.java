package union.commands.strikes;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.utils.TimeFormat;

import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CaseType;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.PunishActions;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.CaseProofUtil;
import union.utils.database.managers.CaseManager.CaseData;
import union.utils.exception.AttachmentParseException;
import union.utils.message.TimeUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.internal.utils.tuple.Pair;

public class StrikeCmd extends CommandBase {
	
	public StrikeCmd() {
		this.name = "strike";
		this.path = "bot.moderation.strike";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
			new OptionData(OptionType.INTEGER, "severity", lu.getText(path+".severity.help"), true).addChoices(List.of(
				new Choice(lu.getText(path+".severity.minor"), 1).setNameLocalizations(lu.getLocaleMap(path+".severity.minor")),
				new Choice(lu.getText(path+".severity.severe"), 2).setNameLocalizations(lu.getLocaleMap(path+".severity.severe")),
				new Choice(lu.getText(path+".severity.extreme"), 3).setNameLocalizations(lu.getLocaleMap(path+".severity.extreme"))
			)),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help"), true).setMaxLength(400),
			new OptionData(OptionType.ATTACHMENT, "proof", lu.getText(path+".proof.help"))
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.STRIKES;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 15;
		this.cooldownScope = CooldownScope.USER;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		Member tm = event.optMember("user");
		if (tm == null) {
			editError(event, path+".not_found");
			return;
		}
		if (event.getUser().equals(tm.getUser()) || tm.getUser().isBot()) {
			editError(event, path+".not_self");
			return;
		}

		// Check if target has strike cooldown
		Guild guild = Objects.requireNonNull(event.getGuild());
		int strikeCooldown = bot.getDBUtil().getGuildSettings(guild).getStrikeCooldown();
		if (strikeCooldown > 0) {
			Instant lastAddition = bot.getDBUtil().strike.getLastAddition(guild.getIdLong(), tm.getIdLong());
			if (lastAddition != null && lastAddition.isAfter(Instant.now().minus(strikeCooldown, ChronoUnit.MINUTES))) {
				// Cooldown active
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_FAILURE)
					.setDescription(lu.getText(event, path+".cooldown").formatted(TimeFormat.RELATIVE.format(lastAddition.plus(strikeCooldown, ChronoUnit.MINUTES))))
					.build()
				);
				return;
			}
		}

		// Get proof
		final CaseProofUtil.ProofData proofData;
		try {
			proofData = CaseProofUtil.getData(event);
		} catch (AttachmentParseException e) {
			editError(event, e.getPath(), e.getMessage());
			return;
		}

		String reason = event.optString("reason");
		Integer strikeAmount = event.optInteger("severity", 1);
		CaseType type = CaseType.byType(20 + strikeAmount);

		Member mod = event.getMember();
		tm.getUser().openPrivateChannel().queue(pm -> {
			Button button = Button.secondary("strikes:"+guild.getId(), lu.getLocalized(guild.getLocale(), "logger_embed.pm.button_strikes"));
			final String text = bot.getModerationUtil().getDmText(type, guild, reason, null, mod.getUser(), false);
			if (text == null) return;
			pm.sendMessage(text).addActionRow(button).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
		});
		
		// add info to db
		CaseData caseData = bot.getDBUtil().cases.add(type, tm.getIdLong(), tm.getUser().getName(), mod.getIdLong(), mod.getUser().getName(),
			guild.getIdLong(), reason, Instant.now(), null);
		// add strikes
		Field action = executeStrike(guild.getLocale(), guild, tm, strikeAmount, caseData.getRowId());
		// log
		bot.getLogger().mod.onNewCase(guild, tm.getUser(), caseData, proofData).thenAccept(logUrl -> {
			// Add log url to db
			bot.getDBUtil().cases.setLogUrl(caseData.getRowId(), logUrl);
			// send reply
			EmbedBuilder builder = bot.getModerationUtil().actionEmbed(guild.getLocale(), caseData.getLocalIdInt(),
				path+".success", type.getPath(), tm.getUser(), mod.getUser(), reason, logUrl);
			if (action != null) builder.addField(action);

			editEmbed(event, builder.build());
		});
	}

	private Field executeStrike(DiscordLocale locale, Guild guild, Member target, Integer addAmount, int caseRowId) {
		// Add strike(-s) to DB
		bot.getDBUtil().strike.addStrikes(guild.getIdLong(), target.getIdLong(),
			Instant.now().plus(bot.getDBUtil().getGuildSettings(guild).getStrikeExpires(), ChronoUnit.DAYS),
			addAmount, caseRowId+"-"+addAmount);
		// Get strike new strike amount
		Integer strikes = bot.getDBUtil().strike.getStrikeCount(guild.getIdLong(), target.getIdLong());
		// Get actions for strike amount
		Pair<Integer, String> punishActions = bot.getDBUtil().autopunish.getTopAction(guild.getIdLong(), strikes);
		if (punishActions == null) return null;

		List<PunishActions> actions = PunishActions.decodeActions(punishActions.getLeft());
		if (actions.isEmpty()) return null;
		String data = punishActions.getRight();

		// Check if user can interact and target is not automod exception or higher
		if (!guild.getSelfMember().canInteract(target)) return new Field(
			lu.getLocalized(locale, path+".autopunish_error"),
			lu.getLocalized(locale, path+".autopunish_higher"),
			false
		);
		CmdAccessLevel targetLevel = bot.getCheckUtil().getAccessLevel(target);
		if (targetLevel.satisfies(CmdAccessLevel.HELPER)) return new Field(
			lu.getLocalized(locale, path+".autopunish_error"),
			lu.getLocalized(locale, path+".autopunish_exception"),
			false
		);

		// Execute
		StringBuilder builder = new StringBuilder();
		if (actions.contains(PunishActions.KICK)) {
			if (targetLevel.satisfies(CmdAccessLevel.EXEMPT)) {
				builder.append(":warning: Not banned, use is exempt from bans.")
					.append("\n");
			} else {
				String reason = lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes);
				// Send PM to user
				target.getUser().openPrivateChannel().queue(pm -> {
					final String text = bot.getModerationUtil().getDmText(CaseType.KICK, guild, reason, null, null, false);
					if (text == null) return;
					pm.sendMessage(text).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
				});

				guild.kick(target).reason(reason).queueAfter(3, TimeUnit.SECONDS, done -> {
						// add case to DB
						CaseData caseData = bot.getDBUtil().cases.add(CaseType.KICK, target.getIdLong(), target.getUser().getName(), 0, "Autopunish",
							guild.getIdLong(), reason, Instant.now(), null);
						// log case
						bot.getLogger().mod.onNewCase(guild, target.getUser(), caseData).thenAccept(logUrl -> {
							bot.getDBUtil().cases.setLogUrl(caseData.getRowId(), logUrl);
						});
					},
					failure -> bot.getAppLogger().error("Strike punishment execution, Kick member", failure));
				builder.append(lu.getLocalized(locale, PunishActions.KICK.getPath()))
					.append("\n");
			}
		}
		if (actions.contains(PunishActions.BAN)) {
			if (targetLevel.satisfies(CmdAccessLevel.EXEMPT)) {
				builder.append(":warning: Not banned, use is exempt from bans.")
					.append("\n");
			} else {
				Duration duration = null;
				try {
					duration = Duration.ofSeconds(Long.parseLong(PunishActions.BAN.getMatchedValue(data)));
				} catch (NumberFormatException ignored) {}
				if (duration != null && !duration.isZero()) {
					String reason = lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes);
					Duration durationCopy = duration;
					// Send PM to user
					target.getUser().openPrivateChannel().queue(pm -> {
						final String text = bot.getModerationUtil().getDmText(CaseType.BAN, guild, reason, durationCopy, null, true);
						if (text == null) return;
						pm.sendMessage(text).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
					});

					guild.ban(target, 0, TimeUnit.SECONDS).reason(lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes)).queue(done -> {
							// add case to DB
							CaseData caseData = bot.getDBUtil().cases.add(CaseType.BAN, target.getIdLong(), target.getUser().getName(), 0, "Autopunish",
								guild.getIdLong(), reason, Instant.now(), durationCopy);
							// log case
							bot.getLogger().mod.onNewCase(guild, target.getUser(), caseData).thenAccept(logUrl -> {
								bot.getDBUtil().cases.setLogUrl(caseData.getRowId(), logUrl);
							});
						},
						failure -> bot.getAppLogger().error("Strike punishment execution, Ban member", failure));
					builder.append(lu.getLocalized(locale, PunishActions.BAN.getPath()))
						.append(" ").append(lu.getLocalized(locale, path + ".for")).append(" ")
						.append(TimeUtil.durationToLocalizedString(lu, locale, duration))
						.append("\n");
				}
			}
		}
		if (actions.contains(PunishActions.REMOVE_ROLE)) {
			Long roleId = null;
			try {
				roleId = Long.valueOf(PunishActions.REMOVE_ROLE.getMatchedValue(data));
			} catch (NumberFormatException ignored) {}
			if (roleId != null) {
				Role role = guild.getRoleById(roleId);
				if (role != null && guild.getSelfMember().canInteract(role)) {
					// Apply action, result will be in logs
					guild.removeRoleFromMember(target, role).reason(lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes)).queueAfter(5, TimeUnit.SECONDS, done -> {
						// log action
						bot.getLogger().role.onRoleRemoved(guild, bot.JDA.getSelfUser(), target.getUser(), role);
					},
					failure -> bot.getAppLogger().error("Strike punishment execution, Remove role", failure));
					builder.append(lu.getLocalized(locale, PunishActions.REMOVE_ROLE.getPath()))
							.append(" ").append(role.getName())
							.append("\n");
				}
			}
		}
		if (actions.contains(PunishActions.ADD_ROLE)) {
			Long roleId = null;
			try {
				roleId = Long.valueOf(PunishActions.ADD_ROLE.getMatchedValue(data));
			} catch (NumberFormatException ignored) {}
			if (roleId != null) {
				Role role = guild.getRoleById(roleId);
				if (role != null && guild.getSelfMember().canInteract(role)) {
					// Apply action, result will be in logs
					guild.addRoleToMember(target, role).reason(lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes)).queueAfter(5, TimeUnit.SECONDS, done -> {
						// log action
						bot.getLogger().role.onRoleAdded(guild, bot.JDA.getSelfUser(), target.getUser(), role);
					},
					failure -> bot.getAppLogger().error("Strike punishment execution, Add role", failure));
					builder.append(lu.getLocalized(locale, PunishActions.ADD_ROLE.getPath()))
							.append(" ").append(role.getName())
							.append("\n");
				}
			}
		}
		if (actions.contains(PunishActions.TEMP_ROLE)) {
			Long roleId = null;
			try {
				roleId = Long.valueOf(PunishActions.TEMP_ROLE.getMatchedValue(data, 1));
			} catch (NumberFormatException ignored) {}
			Duration duration = null;
			try {
				duration = Duration.ofSeconds(Long.parseLong(PunishActions.TEMP_ROLE.getMatchedValue(data, 2)));
			} catch (NumberFormatException ignored) {}
			if (roleId != null && duration != null) {
				final Duration durationCopy = duration;
				Role role = guild.getRoleById(roleId);
				if (role != null && guild.getSelfMember().canInteract(role)) {
					// Apply action, result will be in logs
					guild.addRoleToMember(target, role).reason(lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes)).queueAfter(5, TimeUnit.SECONDS, done -> {
						// Add temp
						bot.getDBUtil().tempRole.add(guild.getIdLong(), role.getIdLong(), target.getIdLong(), false, Instant.now().plus(durationCopy));
						// log action
						bot.getLogger().role.onTempRoleAdded(guild, bot.JDA.getSelfUser(), target.getUser(), role, durationCopy, false);
						},
						failure -> bot.getAppLogger().error("Strike punishment execution, Add temp role", failure)
					);
					builder.append(lu.getLocalized(locale, PunishActions.TEMP_ROLE.getPath()))
						.append(" ").append(role.getName())
						.append(" (").append(TimeUtil.durationToLocalizedString(lu, locale, durationCopy))
						.append(")\n");
				}
			}
		}
		if (actions.contains(PunishActions.MUTE)) {
			Duration duration = null;
			try {
				duration = Duration.ofSeconds(Long.parseLong(PunishActions.MUTE.getMatchedValue(data)));
			} catch (NumberFormatException ignored) {}
			if (duration != null && !duration.isZero()) {
				String reason = lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes);
				// Send PM to user
				target.getUser().openPrivateChannel().queue(pm -> {
					final String text = bot.getModerationUtil().getDmText(CaseType.MUTE, guild, reason, null, null, false);
					if (text == null) return;
					pm.sendMessage(text).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
				});

				Duration durationCopy = duration;
				guild.timeoutFor(target, duration).reason(lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes)).queue(done -> {
						// add case to DB
						CaseData caseData = bot.getDBUtil().cases.add(CaseType.MUTE, target.getIdLong(), target.getUser().getName(), 0, "Autopunish",
							guild.getIdLong(), reason, Instant.now(), durationCopy);
						// log case
						bot.getLogger().mod.onNewCase(guild, target.getUser(), caseData).thenAccept(logUrl -> {
							bot.getDBUtil().cases.setLogUrl(caseData.getRowId(), logUrl);
						});
					},
					failure -> bot.getAppLogger().error("Strike punishment execution, Mute member", failure));
				builder.append(lu.getLocalized(locale, PunishActions.MUTE.getPath()))
					.append(" ").append(lu.getLocalized(locale, path + ".for")).append(" ")
					.append(TimeUtil.durationToLocalizedString(lu, locale, duration))
					.append("\n");
			}
		}

		if (builder.isEmpty()) return null;
		
		return new Field(
			lu.getLocalized(locale, path+".autopunish_title").formatted(strikes),
			builder.toString(),
			false
		);
	}

}

package union.commands.strikes;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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
import union.utils.database.managers.GuildSettingsManager;
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

		String reason = bot.getModerationUtil().parseReasonMentions(event, this);
		Integer strikeAmount = event.optInteger("severity", 1);
		CaseType type = CaseType.byType(20 + strikeAmount);

		Member mod = event.getMember();
		// inform
		final GuildSettingsManager.DramaLevel dramaLevel = bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaLevel();
		tm.getUser().openPrivateChannel().queue(pm -> {
			Button button = Button.secondary("strikes:"+guild.getId(), lu.getLocalized(guild.getLocale(), "logger_embed.pm.button_strikes"));
			final String text = bot.getModerationUtil().getDmText(type, guild, reason, null, mod.getUser(), false);
			if (text == null) return;
			pm.sendMessage(text).setSuppressEmbeds(true)
				.addActionRow(button)
				.queue(null, new ErrorHandler().handle(ErrorResponse.CANNOT_SEND_TO_USER, (failure) -> {
					if (dramaLevel.equals(GuildSettingsManager.DramaLevel.ONLY_BAD_DM)) {
						TextChannel dramaChannel = Optional.ofNullable(bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaChannelId())
							.map(event.getJDA()::getTextChannelById)
							.orElse(null);
						if (dramaChannel != null) {
							final MessageEmbed dramaEmbed = bot.getModerationUtil().getDramaEmbed(CaseType.KICK, event.getGuild(), tm, reason, null);
							if (dramaEmbed == null) return;
							dramaChannel.sendMessage("||%s||".formatted(tm.getAsMention()))
								.addEmbeds(dramaEmbed)
								.queue();
						}
					}
				}));
		});
		if (dramaLevel.equals(GuildSettingsManager.DramaLevel.ALL)) {
			TextChannel dramaChannel = Optional.ofNullable(bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaChannelId())
				.map(event.getJDA()::getTextChannelById)
				.orElse(null);
			if (dramaChannel != null) {
				final MessageEmbed dramaEmbed = bot.getModerationUtil().getDramaEmbed(type, event.getGuild(), tm, reason, null);
				if (dramaEmbed != null) {
					dramaChannel.sendMessageEmbeds(dramaEmbed).queue();
				}
			}
		}
		
		// add info to db
		CaseData caseData;
		try {
			caseData = bot.getDBUtil().cases.add(type, tm.getIdLong(), tm.getUser().getName(), mod.getIdLong(), mod.getUser().getName(),
				guild.getIdLong(), reason, Instant.now(), null);
		} catch (SQLException e) {
			editErrorDatabase(event, e, "Failed to create new case.");
			return;
		}
		// add strikes
		final Field action;
		try {
			action = executeStrike(guild.getLocale(), guild, tm, strikeAmount, caseData.getRowId());
		} catch (Exception e) {
			editErrorOther(event, e.getMessage());
			return;
		}
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

	private Field executeStrike(DiscordLocale locale, Guild guild, Member target, Integer addAmount, int caseRowId) throws Exception {
		// Add strike(-s) to DB
		try {
			bot.getDBUtil().strike.addStrikes(guild.getIdLong(), target.getIdLong(),
				Instant.now().plus(bot.getDBUtil().getGuildSettings(guild).getStrikeExpires(), ChronoUnit.DAYS),
				addAmount, caseRowId+"-"+addAmount);
		} catch (SQLException ex) {
			throw new Exception("Case was created, but strike information was not added to the database (internal error)!");
		}
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

		final String reason = strikes>1 ? strikes>=5 ?
			lu.getLocalized(locale, path+".autopunish_reason_5").formatted(strikes) :
			lu.getLocalized(locale, path+".autopunish_reason_2").formatted(strikes) :
			lu.getLocalized(locale, path+".autopunish_reason_1");

		// Execute
		StringBuilder builder = new StringBuilder();
		if (actions.contains(PunishActions.KICK)) {
			if (targetLevel.satisfies(CmdAccessLevel.EXEMPT)) {
				builder.append(":warning: Not kicked, use is exempt from kick.")
					.append("\n");
			} else {
				// Send PM to user
				target.getUser().openPrivateChannel().queue(pm -> {
					final String text = bot.getModerationUtil().getDmText(CaseType.KICK, guild, reason, null, null, false);
					if (text == null) return;
					pm.sendMessage(text).setSuppressEmbeds(true)
						.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
				});

				guild.kick(target).reason(reason).queueAfter(3, TimeUnit.SECONDS, done -> {
					// add case to DB
					try {
						CaseData caseData = bot.getDBUtil().cases.add(CaseType.KICK, target.getIdLong(), target.getUser().getName(), 0, "Autopunish",
							guild.getIdLong(), reason, Instant.now(), null);
						// log case
						bot.getLogger().mod.onNewCase(guild, target.getUser(), caseData).thenAccept(logUrl -> {
							bot.getDBUtil().cases.setLogUrl(caseData.getRowId(), logUrl);
						});
					} catch (SQLException ignored) {}
				}, failure ->
					bot.getAppLogger().error("Strike punishment execution, Kick member", failure)
				);
				builder.append(lu.getLocalized(locale, PunishActions.KICK.getPath()))
					.append("\n");
			}
		}
		if (actions.contains(PunishActions.BAN)) {
			if (targetLevel.satisfies(CmdAccessLevel.EXEMPT)) {
				builder.append(":warning: Not banned, use is exempt from bans.")
					.append("\n");
			} else {
				try {
					Duration duration = Duration.ofSeconds(Long.parseLong(PunishActions.BAN.getMatchedValue(data)));

					// Send PM to user
					target.getUser().openPrivateChannel().queue(pm -> {
						final String text = bot.getModerationUtil().getDmText(CaseType.BAN, guild, reason, duration, null, true);
						if (text == null) return;
						pm.sendMessage(text).setSuppressEmbeds(true)
							.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
					});

					guild.ban(target, 0, TimeUnit.SECONDS).reason(lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes)).queue(done -> {
						// add case to DB
						try {
							CaseData caseData = bot.getDBUtil().cases.add(CaseType.BAN, target.getIdLong(), target.getUser().getName(), 0, "Autopunish",
								guild.getIdLong(), reason, Instant.now(), duration);
							// log case
							bot.getLogger().mod.onNewCase(guild, target.getUser(), caseData).thenAccept(logUrl -> {
								bot.getDBUtil().cases.setLogUrl(caseData.getRowId(), logUrl);
							});
						} catch (SQLException ignored) {}
					}, failure ->
						bot.getAppLogger().error("Strike punishment execution, Ban member", failure)
					);
					builder.append(lu.getLocalized(locale, PunishActions.BAN.getPath()))
						.append(" ").append(lu.getLocalized(locale, path + ".for")).append(" ")
						.append(TimeUtil.durationToLocalizedString(lu, locale, duration))
						.append("\n");
				} catch (NumberFormatException ignored) {}
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
					guild.removeRoleFromMember(target, role).reason(reason).queueAfter(5, TimeUnit.SECONDS, done -> {
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
					guild.addRoleToMember(target, role).reason(reason).queueAfter(5, TimeUnit.SECONDS, done -> {
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
			try {
				final long roleId = Long.parseLong(PunishActions.TEMP_ROLE.getMatchedValue(data, 1));
				final Duration duration = Duration.ofSeconds(Long.parseLong(PunishActions.TEMP_ROLE.getMatchedValue(data, 2)));

				Role role = guild.getRoleById(roleId);
				if (role != null && guild.getSelfMember().canInteract(role)) {
					// Apply action, result will be in logs
					guild.addRoleToMember(target, role).reason(reason).queueAfter(5, TimeUnit.SECONDS, done -> {
						// Add temp
						try {
							bot.getDBUtil().tempRole.add(guild.getIdLong(), role.getIdLong(), target.getIdLong(), false, Instant.now().plus(duration));
							// log action
							bot.getLogger().role.onTempRoleAdded(guild, bot.JDA.getSelfUser(), target.getUser(), role, duration, false);
						} catch (SQLException ignored) {}
					}, failure ->
						bot.getAppLogger().error("Strike punishment execution, Add temp role", failure)
					);
					builder.append(lu.getLocalized(locale, PunishActions.TEMP_ROLE.getPath()))
						.append(" ").append(role.getName())
						.append(" (").append(TimeUtil.durationToLocalizedString(lu, locale, duration))
						.append(")\n");
				}
			} catch (NumberFormatException ignored) {}
		}
		if (actions.contains(PunishActions.MUTE)) {
			try {
				final Duration duration = Optional.of(Duration.ofSeconds(Long.parseLong(PunishActions.MUTE.getMatchedValue(data))))
					.filter(d -> d.toDaysPart() <= 28)
					.orElse(Duration.ofDays(28));
				if (!duration.isZero()) {
					// Send PM to user
					target.getUser().openPrivateChannel().queue(pm -> {
						final String text = bot.getModerationUtil().getDmText(CaseType.MUTE, guild, reason, duration, null, false);
						if (text == null) return;
						pm.sendMessage(text).setSuppressEmbeds(true)
							.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
					});

					guild.timeoutFor(target, duration).reason(lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes)).queue(done -> {
						try {
							// add case to DB
							CaseData caseData = bot.getDBUtil().cases.add(CaseType.MUTE, target.getIdLong(), target.getUser().getName(), 0, "Autopunish",
								guild.getIdLong(), reason, Instant.now(), duration);
							// log case
							bot.getLogger().mod.onNewCase(guild, target.getUser(), caseData).thenAccept(logUrl -> {
								bot.getDBUtil().cases.setLogUrl(caseData.getRowId(), logUrl);
							});
						} catch (SQLException ignored) {}
					}, failure ->
						bot.getAppLogger().error("Strike punishment execution, Mute member", failure)
					);
					builder.append(lu.getLocalized(locale, PunishActions.MUTE.getPath()))
						.append(" ").append(lu.getLocalized(locale, path + ".for")).append(" ")
						.append(TimeUtil.durationToLocalizedString(lu, locale, duration))
						.append("\n");
				}
			} catch (NumberFormatException ignored) {}
		}

		if (builder.isEmpty()) return null;
		
		return new Field(
			lu.getLocalized(locale, path+".autopunish_title").formatted(strikes),
			builder.toString(),
			false
		);
	}

}

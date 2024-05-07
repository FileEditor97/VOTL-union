package union.commands.strikes;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import union.App;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CaseType;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.PunishActions;
import union.objects.constants.CmdCategory;
import union.utils.database.managers.CaseManager.CaseData;
import union.utils.message.TimeUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
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
	
	public StrikeCmd(App bot) {
		super(bot);
		this.name = "strike";
		this.path = "bot.moderation.strike";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
			new OptionData(OptionType.INTEGER, "severity", lu.getText(path+".severity.help"), true).addChoices(List.of(
				new Choice(lu.getText(path+".severity.minor"), 1).setNameLocalizations(lu.getFullLocaleMap(path+".severity.minor")),
				new Choice(lu.getText(path+".severity.severe"), 2).setNameLocalizations(lu.getFullLocaleMap(path+".severity.severe")),
				new Choice(lu.getText(path+".severity.extreme"), 3).setNameLocalizations(lu.getFullLocaleMap(path+".severity.extreme"))
			)),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help"), true).setMaxLength(400)
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.STRIKES;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 5;
		this.cooldownScope = CooldownScope.GUILD;
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

		Guild guild = Objects.requireNonNull(event.getGuild());
		String reason = event.optString("reason");
		Integer strikeAmount = event.optInteger("severity", 1);
		CaseType type = CaseType.byType(20 + strikeAmount);

		Member mod = event.getMember();
		tm.getUser().openPrivateChannel().queue(pm -> {
			Button button = Button.secondary("strikes:"+guild.getId(), lu.getLocalized(guild.getLocale(), "logger_embed.pm.button_strikes"));
			MessageEmbed embed = bot.getModerationUtil().getDmEmbed(type, guild, reason, null, mod.getUser(), false);
			if (embed == null) return;
			pm.sendMessageEmbeds(embed).addActionRow(button).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
		});
		
		// add info to db
		bot.getDBUtil().cases.add(type, tm.getIdLong(), tm.getUser().getName(), mod.getIdLong(), mod.getUser().getName(),
			guild.getIdLong(), reason, Instant.now(), null);
		CaseData caseData = bot.getDBUtil().cases.getMemberLast(tm.getIdLong(), guild.getIdLong());
		// add strikes
		Field action = executeStrike(event.getUserLocale(), guild, tm, strikeAmount, caseData.getCaseIdInt());
		// log
		bot.getLogger().mod.onNewCase(guild, tm.getUser(), caseData);
		// send reply
		EmbedBuilder builder = bot.getModerationUtil().actionEmbed(guild.getLocale(), caseData.getCaseIdInt(),
				path+".success", type.getPath(), tm.getUser(), mod.getUser(), reason);
		if (action != null) builder.addField(action);

		editHookEmbed(event, builder.build());
	}

	private Field executeStrike(DiscordLocale locale, Guild guild, Member target, Integer addAmount, Integer caseId) {
		// Add strike(-s) to DB
		bot.getDBUtil().strike.addStrikes(guild.getIdLong(), target.getIdLong(),
			Instant.now().plus(bot.getDBUtil().getGuildSettings(guild).getStrikeExpires(), ChronoUnit.DAYS),
			addAmount, caseId+"-"+addAmount);
		// Get strike new strike amount
		Integer strikes = bot.getDBUtil().strike.getStrikeCount(guild.getIdLong(), target.getIdLong());
		// Get actions for strike amount
		Pair<Integer, String> punishActions = bot.getDBUtil().autopunish.getAction(guild.getIdLong(), strikes);
		if (punishActions == null) return null;

		List<PunishActions> actions = PunishActions.decodeActions(punishActions.getLeft());
		if (actions.isEmpty()) return null;
		String data = punishActions.getRight();

		// Check if user can interact and target is not server's moderator or higher
		if (!guild.getSelfMember().canInteract(target)) return null;
		if (bot.getCheckUtil().getAccessLevel(target).isHigherThan(CmdAccessLevel.ALL)) return null;

		// Execute
		StringBuilder builder = new StringBuilder();	// message
		if (actions.contains(PunishActions.KICK)) {
			String reason = lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes);
			// Send PM to user
			target.getUser().openPrivateChannel().queue(pm -> {
				MessageEmbed embed = bot.getModerationUtil().getDmEmbed(CaseType.KICK, guild, reason, null, null, false);
				if (embed == null) return;
				pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
			});

			guild.kick(target).reason(reason).queueAfter(3, TimeUnit.SECONDS, done -> {
				// add case to DB
				bot.getDBUtil().cases.add(CaseType.KICK, target.getIdLong(), target.getUser().getName(), 0, "Autopunish",
					guild.getIdLong(), reason, Instant.now(), null);
				CaseData caseData = bot.getDBUtil().cases.getMemberLast(target.getIdLong(), guild.getIdLong());
				// log case
				bot.getLogger().mod.onNewCase(guild, target.getUser(), caseData);
			},
			failure -> bot.getAppLogger().error("Strike punishment execution, Kick member", failure));
			builder.append(lu.getLocalized(locale, PunishActions.KICK.getPath()))
				.append("\n");
		}
		if (actions.contains(PunishActions.BAN)) {
			Duration duration = null;
			try {
				duration = Duration.ofSeconds(Long.parseLong(PunishActions.BAN.getMatchedValue(data)));
			} catch (NumberFormatException ignored) {}
			if (duration != null && !duration.isZero()) {
				String reason = lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes);
				Duration durationCopy = duration;
				// Send PM to user
				target.getUser().openPrivateChannel().queue(pm -> {
					MessageEmbed embed = bot.getModerationUtil().getDmEmbed(CaseType.BAN, guild, reason, durationCopy, null, true);
					if (embed == null) return;
					pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
				});

				guild.ban(target, 0, TimeUnit.SECONDS).reason(lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes)).queue(done -> {
					// add case to DB
					bot.getDBUtil().cases.add(CaseType.BAN, target.getIdLong(), target.getUser().getName(), 0, "Autopunish",
						guild.getIdLong(), reason, Instant.now(), durationCopy);
					CaseData caseData = bot.getDBUtil().cases.getMemberLast(target.getIdLong(), guild.getIdLong());
					// log case
					bot.getLogger().mod.onNewCase(guild, target.getUser(), caseData);
				},
				failure -> bot.getAppLogger().error("Strike punishment execution, Ban member", failure));
				builder.append(lu.getLocalized(locale, PunishActions.BAN.getPath()))
						.append(" ").append(lu.getLocalized(locale, path + ".for")).append(" ")
						.append(TimeUtil.durationToLocalizedString(lu, locale, duration))
						.append("\n");
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
					MessageEmbed embed = bot.getModerationUtil().getDmEmbed(CaseType.MUTE, guild, reason, null, null, false);
					if (embed == null) return;
					pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
				});

				Duration durationCopy = duration;
				guild.timeoutFor(target, duration).reason(lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes)).queue(done -> {
					// add case to DB
					bot.getDBUtil().cases.add(CaseType.MUTE, target.getIdLong(), target.getUser().getName(), 0, "Autopunish",
						guild.getIdLong(), reason, Instant.now(), durationCopy);
					CaseData caseData = bot.getDBUtil().cases.getMemberLast(target.getIdLong(), guild.getIdLong());
					// log case
					bot.getLogger().mod.onNewCase(guild, target.getUser(), caseData);
				},
				failure -> bot.getAppLogger().error("Strike punishment execution, Mute member", failure));
				builder.append(lu.getLocalized(locale, PunishActions.MUTE.getPath()))
						.append(" ").append(lu.getLocalized(locale, path + ".for")).append(" ")
						.append(TimeUtil.durationToLocalizedString(lu, locale, duration))
						.append("\n");
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
					guild.removeRoleFromMember(target, role).reason(lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes)).queue(done -> {
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
					guild.addRoleToMember(target, role).reason(lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes)).queue(done -> {
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

		if (builder.isEmpty()) return null;
		
		return new Field(
			lu.getLocalized(locale, path+".autopunish_title").formatted(strikes),
			builder.toString(),
			false
		);

	}

}

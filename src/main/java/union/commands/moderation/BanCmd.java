package union.commands.moderation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import union.App;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.base.waiter.EventWaiter;
import union.commands.CommandBase;
import union.objects.CaseType;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.database.managers.CaseManager.CaseData;
import union.utils.exception.FormatterException;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class BanCmd extends CommandBase {

	private EventWaiter waiter;
	
	public BanCmd(App bot, EventWaiter waiter) {
		super(bot);
		this.name = "ban";
		this.path = "bot.moderation.ban";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
			new OptionData(OptionType.STRING, "time", lu.getText(path+".time.help")),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help")).setMaxLength(400),
			new OptionData(OptionType.BOOLEAN, "delete", lu.getText(path+".delete.help")),
			new OptionData(OptionType.BOOLEAN, "dm", lu.getText(path+".dm.help"))
		);
		this.botPermissions = new Permission[]{Permission.BAN_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 5;
		this.cooldownScope = CooldownScope.GUILD;
		this.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();
		
		User targetUser = event.optUser("user");
		String time = event.optString("time");
		String reason = event.optString("reason", lu.getLocalized(event.getGuildLocale(), path+".no_reason"));
		Boolean delete = event.optBoolean("delete", true);
		Boolean dm = event.optBoolean("dm", true);

		buildReply(event, targetUser, time, reason, delete, dm);
	}

	private void buildReply(SlashCommandEvent event, User tu, String time, String reason, Boolean delete, Boolean dm) {
		Guild guild = Objects.requireNonNull(event.getGuild());

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
			duration = bot.getTimeUtil().stringToDuration(time, false);
		} catch (FormatterException ex) {
			editError(event, ex.getPath());
			return;
		}

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
					MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
						.setColor(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".ban_success")
							.replace("{user_tag}", tu.getName())
							.replace("{duration}", lu.getText(event, "logger.permanently"))
							.replace("{reason}", reason))
						.build();
					// log ban
					bot.getLogListener().mod.onNewCase(event, tu, newBanData);

					// ask for ban sync
					event.getHook().editOriginalEmbeds(embed).queue(msg -> {
						buttonSync(event, msg, tu, reason);
					});
				} else {
					// already has expirable ban (show caseID and use /duration to change time)
					MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
						.setColor(Constants.COLOR_WARNING)
						.setDescription(lu.getText(event, path+".already_temp").replace("{id}", oldBanData.getCaseId()))
						.build();
					event.getHook().editOriginalEmbeds(embed).queue();
				}
				return;
			}

			// user has permament ban
			String br = ban.getReason();
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_WARNING)
				.setDescription(lu.getText(event, path+".already_banned"))
				.addField(lu.getText(event, "logger.ban.short_title"), lu.getText(event, "logger.ban.short_info")
					.replace("{username}", ban.getUser().getEffectiveName())
					.replace("{reason}", Optional.ofNullable(br).orElse("*none*"))
					, false)
				.build();
			event.getHook().editOriginalEmbeds(embed).queue(msg -> {
				buttonSync(event, msg, ban.getUser(), ban.getReason());
			});
		},
		failure -> {
			// checks if thrown something except from "ban not found"
			if (!failure.getMessage().startsWith("10026")) {
				bot.getLogger().warn(failure.getMessage());
				editError(event, "errors.unknown", failure.getMessage());
				return;
			}

			Member tm = event.optMember("user");
			Member mod = event.getMember();
			if (tm != null) {
				if (!guild.getSelfMember().canInteract(tm)) {
					editError(event, path+".ban_abort");
					return;
				}
				if (bot.getCheckUtil().hasHigherAccess(tm, mod)) {
					editError(event, path+".higher_access");
					return;
				}
			}

			if (dm) {
				tu.openPrivateChannel().queue(pm -> {
					DiscordLocale locale = guild.getLocale();
					String link = bot.getDBUtil().guild.getAppealLink(guild.getId());
					MessageEmbed embed = new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
						.setDescription(duration.isZero() ? 
							lu.getLocalized(locale, "logger.pm.banned").formatted(guild.getName(), reason)
							:
							lu.getLocalized(locale, "logger.pm.banned_temp").formatted(guild.getName(), bot.getTimeUtil().durationToLocalizedString(locale, duration), reason)
						)
						.appendDescription(link != null ? lu.getLocalized(locale, "logger.pm.appeal").formatted(link) : "")
						.build();
					pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
				});
			}

			guild.ban(tu, (delete ? 10 : 0), TimeUnit.HOURS).reason(reason).queueAfter(3, TimeUnit.SECONDS, done -> {
				// fail-safe check if has expirable ban (to prevent auto unban)
				CaseData oldBanData = bot.getDBUtil().cases.getMemberActive(tu.getIdLong(), guild.getIdLong(), CaseType.BAN);
				if (oldBanData != null) {
					bot.getDBUtil().cases.setInactive(oldBanData.getCaseIdInt());
				}
				// add info to db
				bot.getDBUtil().cases.add(CaseType.BAN, tu.getIdLong(), tu.getName(), mod.getIdLong(), mod.getUser().getName(),
					guild.getIdLong(), reason, Instant.now(), duration);
				CaseData newBanData = bot.getDBUtil().cases.getMemberLast(tu.getIdLong(), guild.getIdLong());
				// create embed
				MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
					.setColor(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".ban_success")
						.replace("{user_tag}", tu.getName())
						.replace("{duration}", duration.isZero() ? lu.getText(event, "logger.permanently") : 
							lu.getText(event, "logger.temporary")
								.formatted( bot.getTimeUtil().formatTime(Instant.now().plus(duration), true))
						)
						.replace("{reason}", reason))
					.build();
				// log ban
				bot.getLogListener().mod.onNewCase(event, tu, newBanData);

				// ask for ban sync
				event.getHook().editOriginalEmbeds(embed).queue(msg -> {
					if (duration.isZero()) buttonSync(event, msg, tu, reason);
				});
			},
			failed -> {
				editError(event, "errors.unknown", failed.getMessage());
			});
		});
	}

	private void buttonSync(SlashCommandEvent event, final Message message, User tu, String reason) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.OPERATOR)) return;
		String guildId = event.getGuild().getId();

		List<Integer> groupIds = new ArrayList<Integer>();
		groupIds.addAll(bot.getDBUtil().group.getOwnedGroups(guildId));
		groupIds.addAll(bot.getDBUtil().group.getManagedGroups(guildId));
		if (groupIds.isEmpty()) return;

		EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
			.setDescription(lu.getText(event, path+".sync.title"));
		StringSelectMenu menu = StringSelectMenu.create("groupId")
			.setPlaceholder(lu.getText(event, path+".sync.value"))
			.addOptions(groupIds.stream().map(groupId ->
				SelectOption.of(bot.getDBUtil().group.getName(groupId), groupId.toString()).withDescription("ID: "+groupId)
			).collect(Collectors.toList()))
			.setMaxValues(5)
			.build();

		message.replyEmbeds(builder.build()).setActionRow(menu).queue(msg -> {
			waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getMessageId().equals(msg.getId()) && e.getUser().equals(event.getUser()),
				selectEvent -> {
					List<SelectOption> selected = selectEvent.getSelectedOptions();
					
					for (SelectOption option : selected) {
						Integer groupId = Integer.parseInt(option.getValue());
						Optional.ofNullable(bot.getHelper()).ifPresent(helper -> helper.runBan(groupId, event.getGuild(), tu, reason));
					}

					selectEvent.editMessageEmbeds(builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".sync.done")).build())
						.setComponents().queue();
				},
				15,
				TimeUnit.SECONDS,
				() -> msg.delete().queue()
			);
		});
	}
	
}

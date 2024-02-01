package union.commands.moderation;

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

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class KickCmd extends CommandBase {

	private EventWaiter waiter;
	
	public KickCmd (App bot, EventWaiter waiter) {
		super(bot);
		this.name = "kick";
		this.path = "bot.moderation.kick";
		this.options = List.of(
			new OptionData(OptionType.USER, "member", lu.getText(path+".member.help"), true),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help")).setMaxLength(400),
			new OptionData(OptionType.BOOLEAN, "dm", lu.getText(path+".dm.help"))
		);
		this.botPermissions = new Permission[]{Permission.KICK_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 10;
		this.cooldownScope = CooldownScope.GUILD;
		this.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();
		Guild guild = Objects.requireNonNull(event.getGuild());

		Member tm = event.optMember("member");
		if (tm == null) {
			editError(event, path+".not_found");
			return;
		}
		if (event.getMember().equals(tm) || guild.getSelfMember().equals(tm)) {
			editError(event, path+".not_self");
			return;
		}

		String reason = event.optString("reason", lu.getLocalized(event.getGuildLocale(), path+".no_reason"));
		if (event.optBoolean("dm", true)) {
			tm.getUser().openPrivateChannel().queue(pm -> {
				MessageEmbed embed = new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
					.setDescription(lu.getLocalized(guild.getLocale(), "logger.pm.kicked").formatted(guild.getName(), reason))
					.build();
				pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
			});
		}

		Member mod = event.getMember();
		if (!guild.getSelfMember().canInteract(tm)) {
			editError(event, path+".kick_abort", "Bot can't interact with target member.");
			return;
		}
		if (bot.getCheckUtil().hasHigherAccess(tm, mod)) {
			editError(event, path+".higher_access");
			return;
		}
		if (!mod.canInteract(tm)) {
			editError(event, path+".kick_abort", "You can't interact with target member.");
			return;
		}

		tm.kick().reason(reason).queueAfter(2, TimeUnit.SECONDS, done -> {
			// add info to db
			bot.getDBUtil().cases.add(CaseType.KICK, tm.getIdLong(), tm.getUser().getName(), mod.getIdLong(), mod.getUser().getName(),
				guild.getIdLong(), reason, Instant.now(), null);
			CaseData kickData = bot.getDBUtil().cases.getMemberLast(tm.getIdLong(), guild.getIdLong());
			// create embed
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".kick_success")
					.replace("{user_tag}", tm.getUser().getName())
					.replace("{reason}", reason))
				.build();
			// log ban
			bot.getLogListener().mod.onNewCase(guild, tm.getUser(), kickData);

			// ask for kick sync
			event.getHook().editOriginalEmbeds(embed).queue(msg -> {
				buttonSync(event, msg, tm.getUser(), reason);
			});
		},
		failure -> {
			editError(event, "errors.error", failure.getMessage());
		});
	}

	private void buttonSync(SlashCommandEvent event, final Message message, User tu, String reason) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.OPERATOR)) return;
		long guildId = event.getGuild().getIdLong();

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
						Optional.ofNullable(bot.getHelper()).ifPresent(helper -> helper.runKick(groupId, event.getGuild(), tu, reason));
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

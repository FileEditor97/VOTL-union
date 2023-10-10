package union.commands.moderation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import union.App;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.command.CooldownScope;
import union.objects.command.SlashCommandEvent;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

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

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;

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
		event.deferReply(false).queue();
		
		Member targetMember = event.optMember("member");
		String reason = event.optString("reason", lu.getLocalized(event.getGuildLocale(), path+".no_reason"));
		Boolean dm = event.optBoolean("dm", false);

		buildReply(event, targetMember, reason, dm);
	}

	private void buildReply(SlashCommandEvent event, Member tm, String reason, Boolean dm) {
		Guild guild = Objects.requireNonNull(event.getGuild());

		if (tm == null) {
			editError(event, path+".not_found");
			return;
		}
		if (event.getMember().equals(tm) || guild.getSelfMember().equals(tm)) {
			editError(event, path+".not_self");
			return;
		}

		if (dm) {
			tm.getUser().openPrivateChannel().queue(pm -> {
				MessageEmbed embed = new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
					.setDescription(lu.getLocalized(guild.getLocale(), "logger.pm.kicked").formatted(guild.getName(), reason))
					.build();
				pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
			});
		}

		if (!guild.getSelfMember().canInteract(tm)) {
			editError(event, path+".kick_abort");
			return;
		}
		if (bot.getCheckUtil().hasHigherAccess(tm, event.getMember())) {
			editError(event, path+".higher_access");
			return;
		}

		tm.kick().reason(reason).queueAfter(1, TimeUnit.SECONDS, done -> {
			// create embed
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
			.setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getText(event, path+".kick_success")
				.replace("{user_tag}", tm.getUser().getName())
				.replace("{reason}", reason))
			.build();
			// ask for kick sync
			event.getHook().editOriginalEmbeds(embed).queue(msg -> {
				buttonSync(event, msg, tm.getUser(), reason);
			});
			
			// log kick
			bot.getLogListener().mod.onKick(event, tm.getUser(), event.getUser(), reason);
			
		},
		failure -> {
			editError(event, "errors.unknown", failure.getMessage());
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

package union.commands.moderation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import union.App;
import union.base.command.SlashCommandEvent;
import union.base.waiter.EventWaiter;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

public class UnbanCmd extends CommandBase {

	private EventWaiter waiter;
	
	public UnbanCmd(App bot, EventWaiter waiter) {
		super(bot);
		this.name = "unban";
		this.path = "bot.moderation.unban";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help")).setMaxLength(400)
		);
		this.botPermissions = new Permission[]{Permission.BAN_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();
		Guild guild = Objects.requireNonNull(event.getGuild());

		User tu = event.optUser("user");
		String reason = event.optString("reason", lu.getText(event, path+".no_reason"));

		if (tu == null) {
			editError(event, path+".not_found");
			return;
		}
		if (event.getUser().equals(tu) || event.getJDA().getSelfUser().equals(tu)) {
			editError(event, path+".not_self");
			return;
		}

		guild.retrieveBan(tu).queue(ban -> {
			// perform unban
			guild.unban(tu).reason(reason).queue();
			// create embed
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".unban_success")
					.replace("{user_tag}", tu.getName())
					.replace("{reason}", reason))
				.build();
			// ask for unban sync
			event.getHook().editOriginalEmbeds(embed).queue(msg -> {
				buttonSync(event, msg, tu, reason);
			});

			// log unban
			bot.getLogListener().mod.onUnban(event, event.getMember(), ban, reason);
		},
		failure -> {
			// create embed
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_FAILURE)
				.setDescription(lu.getText(event, path+".no_ban")
					.replace("{user_tag}", tu.getName()))
				.build();
			// ask for unban sync
			event.getHook().editOriginalEmbeds(embed).queue(msg -> {
				buttonSync(event, msg, tu, reason);
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
						Optional.ofNullable(bot.getHelper()).ifPresent(helper -> helper.runUnban(groupId, event.getGuild(), tu, reason));
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

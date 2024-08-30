package union.commands.moderation;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import union.base.command.CooldownScope;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.base.waiter.EventWaiter;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

public class SyncCmd extends CommandBase {

	private static EventWaiter waiter;
	
	public SyncCmd(EventWaiter waiter) {
		this.name = "sync";
		this.path = "bot.moderation.sync";
		this.children = new SlashCommand[]{new Kick()};
		this.botPermissions = new Permission[]{Permission.KICK_MEMBERS, Permission.BAN_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.OPERATOR;
		SyncCmd.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Kick extends SlashCommand {
		
		public Kick() {
			this.name = "kick";
			this.path = "bot.moderation.sync.kick";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
				new OptionData(OptionType.INTEGER, "group", lu.getText(path+".group.help"), true, true).setMinValue(0)
			);
			this.cooldownScope = CooldownScope.GUILD;
			this.cooldown = 20;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			User target = event.optUser("user");
			if (target == null) {
				editError(event, path+".not_found");
				return;
			}
			if (event.getUser().equals(target) || event.getJDA().getSelfUser().equals(target)) {
				editError(event, path+".not_self");
				return;
			}
			if (event.getGuild().isMember(target)) {
				editError(event, path+".is_member");
				return;
			}

			Integer groupId = event.optInteger("group");
			long guildId = event.getGuild().getIdLong();
			if ( !(bot.getDBUtil().group.isOwner(groupId, guildId) || bot.getDBUtil().group.canManage(groupId, guildId)) ) {
				editError(event, path+".no_group", "Group ID: `"+groupId+"`");
				return;
			}

			ActionRow button = ActionRow.of(
				Button.of(ButtonStyle.PRIMARY, "button:confirm", lu.getText(event, path+".button_confirm"))
			);
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed()
				.setDescription(lu.getText(event, path+".embed_title"))
				.build()
			).setComponents(button).queue(msg -> {
				waiter.waitForEvent(
					ButtonInteractionEvent.class,
					e -> msg.getId().equals(e.getMessageId()) && e.getComponentId().equals("button:confirm"),
					action -> {
						if (bot.getDBUtil().group.countMembers(groupId) < 1) {
							editError(event, path+".no_guilds");
							return;
						}

						event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
							.setDescription(lu.getText(event, path+".done"))
							.build()
						).setComponents().queue();
						// Perform action using Helper bot
						Optional.ofNullable(bot.getHelper()).ifPresent(helper -> helper.runKick(groupId, event.getGuild(), target, "Manual kick"));
					},
					20,
					TimeUnit.SECONDS,
					() -> event.getHook().editOriginalComponents(ActionRow.of(Button.of(ButtonStyle.SECONDARY, "timed_out", "Timed out").asDisabled())).queue()
				);
			});
		}
	}

}

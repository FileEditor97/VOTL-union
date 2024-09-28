package union.commands.ticketing;

import java.util.Collections;
import java.util.List;

import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class RemoveUserCmd extends CommandBase {
	
	public RemoveUserCmd() {
		this.name = "remove";
		this.path = "bot.ticketing.remove";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
		);
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
		this.accessLevel = CmdAccessLevel.HELPER;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();
		
		String channelId = event.getChannel().getId();
		String authorId = bot.getDBUtil().ticket.getUserId(channelId);
		if (authorId == null) {
			// If this channel is not a ticket
			editError(event, path+".not_ticket");
			return;
		}
		if (bot.getDBUtil().ticket.isClosed(channelId)) {
			// Ticket is closed
			event.getChannel().delete().queue();
			return;
		}
		User user = event.optUser("user");
		if (user.equals(event.getUser()) || user.equals(bot.JDA.getSelfUser()) || user.getId().equals(authorId)) {
			editError(event, path+".not_self");
			return;
		}

		if (event.getChannelType().equals(ChannelType.GUILD_PRIVATE_THREAD)) {
			// Thread
			event.getChannel().asThreadChannel().removeThreadMember(user).queue(done -> {
				event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").replace("{user}", user.getAsMention()))
					.build()
				).setAllowedMentions(Collections.emptyList()).queue();
			},
			failure -> editError(event, path+".failed", failure.getMessage()));
		} else {
			// TextChannel
			try {
				event.getChannel().asTextChannel().getManager()
					.removePermissionOverride(user.getIdLong())
					.queue(done -> event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".done").replace("{user}", user.getAsMention()))
						.build()
					).setAllowedMentions(Collections.emptyList()).queue()
				);
			} catch (PermissionException ex) {
				editError(event, path+".failed", ex.getMessage());
			}
		}
	}

}

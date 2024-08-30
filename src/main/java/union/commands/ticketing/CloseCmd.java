package union.commands.ticketing;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import java.util.List;

public class CloseCmd extends CommandBase {

	public CloseCmd() {
		this.name = "close";
		this.path = "bot.ticketing.close";
		this.options = List.of(
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help")).setMaxLength(200)
		);
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		String channelId = event.getChannel().getId();
		String authorId = bot.getDBUtil().ticket.getUserId(channelId);
		if (authorId == null) {
			// If this channel is not a ticket
			createError(event, path+".not_ticket");
			return;
		}
		if (bot.getDBUtil().ticket.isClosed(channelId)) {
			// Ticket is closed
			event.getChannel().delete().queue();
			return;
		}

		String reason = event.optString(
			"reason",
			bot.getDBUtil().ticket.getUserId(channelId).equals(event.getUser().getId()) ? "Closed by ticket's author" : "Closed by Support"
		);
		event.replyEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
			.setDescription(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.delete_countdown"))
			.build()
		).queue(hook -> {	
			bot.getTicketUtil().closeTicket(channelId, event.getUser(), reason, failure -> {
				hook.editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.close_failed")).queue();
				bot.getAppLogger().error("Couldn't close ticket with channelID: {}", channelId, failure);
			});
		});
	}

}

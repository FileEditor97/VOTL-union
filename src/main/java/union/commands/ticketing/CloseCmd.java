package union.commands.ticketing;

import union.App;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.command.SlashCommandEvent;
import union.objects.constants.CmdCategory;

public class CloseCmd extends CommandBase {

	public CloseCmd(App bot) {
		super(bot);
		this.name = "close";
		this.path = "bot.ticketing.close";
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
		this.accessLevel = CmdAccessLevel.MOD;
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
		if (!bot.getDBUtil().ticket.isOpened(channelId)) {
			// Ticket is closed
			event.getChannel().delete().queue();
			return;
		}
		event.deferReply(true).queue();
		String reason = bot.getDBUtil().ticket.getUserId(channelId).equals(event.getUser().getId()) ? "Closed by ticket's author" : "Closed by Support";
		bot.getTicketUtil().closeTicket(channelId, event.getUser(), reason, failure -> {
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.close_failed")).queue();
			bot.getLogger().error("Couldn't close ticket with channelID:"+channelId, failure);
		});
	}

}

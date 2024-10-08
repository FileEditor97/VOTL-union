package union.commands.other;

import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.constants.CmdCategory;

public class PingCmd extends CommandBase {
	
	public PingCmd() {
		this.name = "ping";
		this.path = "bot.other.ping";
		this.category = CmdCategory.OTHER;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.reply(lu.getText(event, path+".loading")).setEphemeral(true).queue();

		event.getJDA().getRestPing().queue(time -> {
			editMsg(event,
				lu.getText(event, "bot.other.ping.info_full")
					.replace("{websocket}", event.getJDA().getGatewayPing()+"")
					.replace("{rest}", time+"")
			);
		});	
	}
}

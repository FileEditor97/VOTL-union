package union.commands.owner;

import union.App;
import union.commands.CommandBase;
import union.objects.command.SlashCommandEvent;
import union.objects.constants.CmdCategory;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

public class ShutdownCmd extends CommandBase {

	public ShutdownCmd(App bot) {
		super(bot);
		this.name = "shutdown";
		this.path = "bot.owner.shutdown";
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.guildOnly = false;
	}
	
	@Override
	protected void execute(SlashCommandEvent event) {
		createReply(event, "Shutting down...");
		event.getJDA().getPresence().setPresence(OnlineStatus.IDLE, Activity.competing("Shutting down..."));
		bot.getHelper().getLogger().info("Shutting down");
		bot.getHelper().getJDA().shutdown();
		bot.getLogger().info("Shutting down, by '%s'".formatted(event.getUser().getName()));
		event.getJDA().shutdown();
	}
}

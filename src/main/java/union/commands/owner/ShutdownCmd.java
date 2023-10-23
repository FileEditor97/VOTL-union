package union.commands.owner;

import union.App;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.constants.CmdCategory;

import java.util.Optional;

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
		Optional.ofNullable(bot.getHelper()).ifPresent(helper -> helper.getLogger().info("Shutting down"));
		Optional.ofNullable(bot.getHelper()).ifPresent(helper -> helper.getJDA().shutdown());
		bot.getLogger().info("Shutting down, by '%s'".formatted(event.getUser().getName()));
		event.getJDA().shutdown();
	}
}

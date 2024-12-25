package union.commands.owner;

import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.helper.Helper;
import union.objects.constants.CmdCategory;

import java.util.Optional;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

public class ShutdownCmd extends CommandBase {

	public ShutdownCmd() {
		this.name = "shutdown";
		this.path = "bot.owner.shutdown";
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.guildOnly = false;
	}
	
	@Override
	protected void execute(SlashCommandEvent event) {
		event.reply("Shutting down...").queue();
		// Set presence
		event.getJDA().getPresence().setPresence(OnlineStatus.IDLE, Activity.competing("Shutting down..."));
		// Shutdown helper
		Optional.ofNullable(Helper.getInstance()).ifPresent(helper -> helper.getLogger().info("Shutting down"));
		Optional.ofNullable(Helper.getInstance()).ifPresent(helper -> helper.getJDA().shutdown());
		// Shutdown other
		bot.shutdownUtils();
		// Finish
		bot.getAppLogger().info("Shutting down, by '{}'", event.getUser().getName());
		event.getJDA().shutdown();
	}
}

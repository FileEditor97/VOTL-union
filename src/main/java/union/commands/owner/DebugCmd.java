package union.commands.owner;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import union.App;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.constants.CmdCategory;

import java.io.File;
import java.util.List;

public class DebugCmd extends CommandBase {

	public DebugCmd(App bot) {
		super(bot);
		this.name = "debug";
		this.path = "bot.owner.debug";
		this.options = List.of(
			new OptionData(OptionType.BOOLEAN, "debug_logs", lu.getText(path+".debug_logs.help")),
			new OptionData(OptionType.STRING, "date", lu.getText(path+".date.help")).setRequiredLength(10,10)
		);
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		if (event.optBoolean("debug_logs", false)) {
			event.replyFiles(FileUpload.fromData(new File("./logs/App-debug.log"))).queue();
		} else {
			String date = event.optString("date");
			event.replyFiles(FileUpload.fromData(new File("./logs/App%s.log".formatted(date!=null?"."+date:"")))).queue();
		}
	}

}

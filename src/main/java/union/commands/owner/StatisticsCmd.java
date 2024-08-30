package union.commands.owner;

import union.App;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import java.util.Map;

public class StatisticsCmd extends CommandBase {
	public StatisticsCmd() {
		this.name = "statistics";
		this.path = "bot.owner.statistics";
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(true).queue();

		StringBuilder builder = new StringBuilder("```\n");
		App.getInstance().getClient().getCommandUses()
			.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(m -> builder.append("% 4d | %s\n".formatted(m.getValue(), m.getKey())));

		if (builder.length() < 6) {
			event.getHook().editOriginal(Constants.FAILURE+" Empty").queue();
		} else {
			builder.append("```");
			if (builder.length() > 2048) builder.setLength(2048);
			event.getHook().editOriginal(builder.toString()).queue();
		}
	}
}

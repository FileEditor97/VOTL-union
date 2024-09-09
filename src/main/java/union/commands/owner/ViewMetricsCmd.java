package union.commands.owner;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.metrics.Metrics;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import java.util.List;

public class ViewMetricsCmd extends CommandBase {
	public ViewMetricsCmd() {
		this.name = "metrics";
		this.path = "bot.owner.metrics";
		this.children = new SlashCommand[]{new CommandInfo(), new Stats()};
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class CommandInfo extends SlashCommand {
		public CommandInfo() {
			this.name = "command";
			this.path = "bot.owner.metrics.command";
			this.options = List.of(
				new OptionData(OptionType.STRING, "command-name", lu.getText(path+".command-name.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			String commandName = event.optString("command-name");

			long received = Metrics.commandsReceived.labelValue(commandName).get();
			if (received == 0) {
				editError(event, path+".not_found");
				return;
			}
			long executed = Metrics.commandsExecuted.labelValue(commandName).get();
			long exceptions = Metrics.commandExceptions.labelValue(commandName).get();
			// Execution time in seconds
			double percentile95 = Metrics.executionTime.labelValue(commandName).getPercentile(95) * 1000;
			double percentile90 = Metrics.executionTime.labelValue(commandName).getPercentile(80) * 1000;
			double average = Metrics.executionTime.labelValue(commandName).getAverage() * 1000;

			MessageEmbed embed = bot.getEmbedUtil().getEmbed()
				.setTitle("Full name: "+commandName)
				.addField("Count", "Executed: `%s`/`%s`\nException caught: `%s`".formatted(executed, received, exceptions), false)
				.addField("Execution time", "Average: `%.2f` ms\n95%%: `%.2f` ms | 90%%: `%.2f` ms".formatted(average, percentile95, percentile90), false)
				.build();

			editHookEmbed(event, embed);
		}
	}

	public class Stats extends SlashCommand {
		public Stats() {
			this.name = "stats";
			this.path = "bot.owner.metrics.stats";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			StringBuilder builder = new StringBuilder("```\n");
			Metrics.commandsReceived.collect()
				.forEach((k,v) -> builder.append("%4d | %s\n".formatted(v, k)));

			if (builder.length() < 6) {
				event.getHook().editOriginal(Constants.FAILURE+" Empty").queue();
			} else {
				builder.append("```");
				if (builder.length() > 2048) builder.setLength(2048);
				event.getHook().editOriginal(builder.toString()).queue();
			}
		}
	}
}
package union.commands.owner;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.style.XYStyler;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.metrics.Metrics;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.services.ScheduledMetrics;
import union.services.ping.PingRecord;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ViewMetricsCmd extends CommandBase {
	public ViewMetricsCmd() {
		this.name = "metrics";
		this.path = "bot.owner.metrics";
		this.children = new SlashCommand[]{new CommandInfo(), new CommandStats(), new ButtonStats(), new Ping()};
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

			editEmbed(event, embed);
		}
	}

	public class CommandStats extends SlashCommand {
		public CommandStats() {
			this.name = "command-stats";
			this.path = "bot.owner.metrics.command-stats";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			StringBuilder builder = new StringBuilder("```\n");
			Metrics.commandsReceived.collect()
				.entrySet()
				.stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
				.forEach(e -> builder.append("%4d | %s\n".formatted(e.getValue(), e.getKey())));

			if (builder.length() < 6) {
				event.getHook().editOriginal(Constants.FAILURE+" Empty").queue();
			} else {
				builder.append("```");
				if (builder.length() > 2048) builder.setLength(2048);
				event.getHook().editOriginal(builder.toString()).queue();
			}
		}
	}

	public class ButtonStats extends SlashCommand {
		public ButtonStats() {
			this.name = "button-stats";
			this.path = "bot.owner.metrics.button-stats";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			StringBuilder builder = new StringBuilder("```\n");
			Metrics.interactionReceived.collect()
				.entrySet()
				.stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
				.forEach(e -> builder.append("%4d | %s\n".formatted(e.getValue(), e.getKey())));

			if (builder.length() < 6) {
				event.getHook().editOriginal(Constants.FAILURE+" Empty").queue();
			} else {
				builder.append("```");
				if (builder.length() > 2048) builder.setLength(2048);
				event.getHook().editOriginal(builder.toString()).queue();
			}
		}
	}

	public class Ping extends SlashCommand {
		public Ping() {
			this.name = "ping";
			this.path = "bot.owner.metrics.ping";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			File chartFile = generateGraph(ScheduledMetrics.pingDataStore.getRecords());

			if (chartFile != null) {
				event.getHook().editOriginalAttachments(FileUpload.fromData(chartFile)).queue();
			} else {
				editError(event, path+".failed");
			}
		}

		private File generateGraph(Deque<PingRecord> pingRecords) {
			List<Date> timestamps = pingRecords.stream()
				.map(record -> Date.from(record.timestamp()))
				.toList();

			List<Long> webSocketPings = pingRecords.stream()
				.map(PingRecord::webSocketPing)
				.toList();

			List<Long> restPings = pingRecords.stream()
				.map(PingRecord::restPing)
				.toList();

			// Create the chart
			XYChart chart = new XYChartBuilder()
				.width(800)
				.height(600)
				.title("Ping Metrics")
				.xAxisTitle("Timestamp")
				.yAxisTitle("Ping (ms)")
				.build();

			// Format x-axis as time
			chart.getStyler().setDatePattern("HH:mm:ss")
				.setLegendPosition(XYStyler.LegendPosition.InsideNE)
				.setMarkerSize(0);

			// Add series to the chart
			chart.addSeries("WebSocket Ping", timestamps, webSocketPings);
			chart.addSeries("REST Ping", timestamps, restPings);

			// Save chart as PNG
			File chartFile = new File("ping_chart.png");
			try {
				BitmapEncoder.saveBitmap(chart, chartFile.getAbsolutePath(), BitmapEncoder.BitmapFormat.PNG);
				return chartFile;
			} catch (IOException e) {
				bot.getAppLogger().error("Error generating ping chart", e);
			}
			return null;
		}
	}
}

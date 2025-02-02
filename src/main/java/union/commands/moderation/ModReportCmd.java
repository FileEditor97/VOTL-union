package union.commands.moderation;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeFormat;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Collectors;

public class ModReportCmd extends CommandBase {
	public ModReportCmd() {
		this.name = "modreport";
		this.path = "bot.moderation.modreport";
		this.children = new SlashCommand[]{
			new Setup(), new Delete()
		};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Setup extends SlashCommand {
		public Setup() {
			this.name = "setup";
			this.path = "bot.moderation.modreport.setup";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT),
				new OptionData(OptionType.INTEGER, "interval", lu.getText(path+".interval.help"), true)
					.setRequiredRange(5, 30)
					.addChoices(
						new Command.Choice(lu.getText(path+".weekly"), 7),
						new Command.Choice(lu.getText(path+".biweekly"), 14),
						new Command.Choice(lu.getText(path+".monthly"), 30)
					),
				new OptionData(OptionType.STRING, "roles", lu.getText(path+".roles.help"), true),
				new OptionData(OptionType.STRING, "first_report", lu.getText(path+".first_report.help"))
					.setRequiredLength(10, 16)
			);
		}

		// If time is not included - default to 3:00
		private final DateTimeFormatter DATE_TIME_FORMAT = new DateTimeFormatterBuilder()
			.appendPattern("dd/MM/yyyy")
			.optionalStart()
			.appendPattern(" HH:mm")
			.optionalEnd()
			.parseDefaulting(ChronoField.HOUR_OF_DAY, 3)
			.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
			.toFormatter();

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			List<Role> roles = event.optMentions("roles").getRoles();
			if (roles.isEmpty() || roles.size()>4) {
				editError(event, path+".no_roles");
				return;
			}

			int interval = event.optInteger("interval", 7);

			LocalDateTime firstReport;
			if (event.hasOption("first_report")) {
				String input = event.optString("first_report");
				try {
					firstReport = LocalDateTime.parse(input, DATE_TIME_FORMAT);
				} catch (DateTimeParseException ex) {
					System.out.println(ex.getMessage());
					editError(event, path+".failed_parse", ex.getMessage());
					return;
				}
			} else {
				// Next monday OR first month day at 3:00 (server time)
				if (interval == 30)
					firstReport = LocalDateTime.now().with(TemporalAdjusters.firstDayOfNextMonth()).withHour(3);
				else
					firstReport = LocalDateTime.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).withHour(3);
			}
			if (firstReport.isBefore(LocalDateTime.now())) {
				editError(event, path+".wrong_date");
				return;
			}

			GuildChannel channel = event.optGuildChannel("channel");

			String roleIds = roles.stream()
				.map(Role::getId)
				.collect(Collectors.joining(";"));

			// Add to DB
			if (!bot.getDBUtil().modReport.setup(
				event.getGuild().getIdLong(), channel.getIdLong(), roleIds,
				firstReport, interval
			)) {
				editErrorUnknown(event, "Database error.");
				return;
			}
			// Reply
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(
					TimeFormat.DATE_TIME_SHORT.format(firstReport.atZone(ZoneOffset.UTC)), channel.getAsMention(),
					interval, roles.stream().map(Role::getAsMention).collect(Collectors.joining(", "))
				))
				.build());
		}
	}

	private class Delete extends SlashCommand {
		public Delete() {
			this.name = "delete";
			this.path = "bot.moderation.modreport.delete";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			if (!bot.getDBUtil().modReport.removeGuild(event.getGuild().getIdLong())) {
				editErrorUnknown(event, "Database error.");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done"))
				.build());
		}
	}
}

package union.commands.ticketing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class TicketCountCmd extends CommandBase {
	
	public TicketCountCmd() {
		this.name = "tcount";
		this.path = "bot.ticketing.tcount";
		this.options = List.of(new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
			new OptionData(OptionType.STRING, "start_date", lu.getText(path+".start_date.help")),
			new OptionData(OptionType.STRING, "end_date", lu.getText(path+".end_date.help"))
		);
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
		this.accessLevel = CmdAccessLevel.MOD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		String afterDate = event.optString("start_date");
		String beforeDate = event.optString("end_date");
		Instant afterTime = null;
		Instant beforeTime = null;

		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		try {
			if (afterDate != null) afterTime = LocalDate.parse(afterDate, inputFormatter).atStartOfDay(ZoneId.systemDefault()).toInstant();
			if (beforeDate != null) beforeTime = LocalDate.parse(beforeDate, inputFormatter).atStartOfDay(ZoneId.systemDefault()).toInstant();
		} catch (Exception ex) {
			editError(event, path+".failed_parse", ex.getMessage());
			return;
		}

		if (beforeTime == null) beforeTime = Instant.now();
		if (afterTime == null) afterTime = Instant.now().minus(7, ChronoUnit.DAYS);
		if (beforeTime.isBefore(afterTime)) {
			editError(event, path+".wrong_date");
			return;
		}

		User user = event.optUser("user");
		int countRoles = bot.getDBUtil().ticket.countTicketsByMod(event.getGuild().getId(), user.getId(), afterTime, beforeTime, true);
		int countOther = bot.getDBUtil().ticket.countTicketsByMod(event.getGuild().getId(), user.getId(), afterTime, beforeTime, false);

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
		editEmbed(event, bot.getEmbedUtil().getEmbed()
			.setTitle("`"+formatter.format(afterTime)+"` - `"+formatter.format(beforeTime)+"`")
			.setDescription(lu.getText(event, path+".done").replace("{user}", user.getAsMention()).replace("{id}", user.getId())
				.replace("{roles}", Integer.toString(countRoles)).replace("{other}", Integer.toString(countOther)))
			.build());
	}

}

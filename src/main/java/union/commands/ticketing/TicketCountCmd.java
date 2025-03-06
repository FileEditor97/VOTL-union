package union.commands.ticketing;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
		LocalDateTime afterTime = null;
		LocalDateTime beforeTime = null;

		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		try {
			if (afterDate != null) afterTime = LocalDateTime.parse(afterDate, inputFormatter);
			if (beforeDate != null) beforeTime = LocalDateTime.parse(beforeDate, inputFormatter);
		} catch (Exception ex) {
			editError(event, path+".failed_parse", ex.getMessage());
			return;
		}

		if (beforeTime == null) beforeTime = LocalDateTime.now();
		if (afterTime == null) afterTime = LocalDateTime.now().minusDays(7);
		if (beforeTime.isBefore(afterTime)) {
			editError(event, path+".wrong_date");
			return;
		}

		User user = event.optUser("user");
		long userId = user.getIdLong();
		int countRoles = bot.getDBUtil().ticket.countTicketsByMod(event.getGuild().getIdLong(), userId, afterTime, beforeTime, true);
		int countOther = bot.getDBUtil().ticket.countTicketsByMod(event.getGuild().getIdLong(), userId, afterTime, beforeTime, false);

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);
		editEmbed(event, bot.getEmbedUtil().getEmbed()
			.setTitle("`"+formatter.format(afterTime)+"` - `"+formatter.format(beforeTime)+"`")
			.setDescription(lu.getText(event, path+".done").replace("{user}", user.getAsMention()).replace("{id}", user.getId())
				.replace("{roles}", Integer.toString(countRoles)).replace("{other}", Integer.toString(countOther)))
			.build());
	}

}

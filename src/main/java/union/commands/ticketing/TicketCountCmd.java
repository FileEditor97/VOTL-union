package union.commands.ticketing;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import union.App;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.command.SlashCommandEvent;
import union.objects.constants.CmdCategory;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class TicketCountCmd extends CommandBase {
	
	public TicketCountCmd(App bot) {
		super(bot);
		this.name = "tcount";
		this.path = "bot.ticketing.tcount";
		this.options = List.of(new OptionData(OptionType.USER, "user", path+".user.help", true), 
			new OptionData(OptionType.INTEGER, "days", path+".days.help", false).setRequiredRange(1, 31));
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
		this.accessLevel = CmdAccessLevel.MOD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		User user = event.optUser("user");
		Integer days = event.optInteger("days", 7);

		String guildId = event.getGuild().getId();

		Instant afterTime = Instant.now().minus(days, ChronoUnit.DAYS);

		Integer count = bot.getDBUtil().ticket.countTicketsByMod(guildId, user.getId(), afterTime);

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
		createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
			.setTitle("`"+formatter.format(afterTime)+"` - `"+formatter.format(Instant.now())+"`")
			.setDescription(lu.getText(event, path+".done").replace("{user}", user.getAsMention()).replace("{id}", user.getId()).replace("{count}", count.toString()))
			.build());
	}

}

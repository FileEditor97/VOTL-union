package union.commands.moderation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import union.App;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CaseType;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class ModStatsCmd extends CommandBase {
	
	public ModStatsCmd(App bot) {
		super(bot);
		this.name = "modstats";
		this.path = "bot.moderation.modstats";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"))
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 15;
		this.cooldownScope = CooldownScope.USER;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();
		Member mod = event.optMember("user", event.getMember());
		long guildId = event.getGuild().getIdLong();

		Map<Integer, Integer> countTotal = bot.getDBUtil().cases.countCasesByMod(guildId, mod.getIdLong());
		final int rolesTotal = bot.getDBUtil().ticket.countTicketsByMod(event.getGuild().getId(), mod.getId(), true);
		if (countTotal.isEmpty() && rolesTotal==0) {
			editError(event, path+".empty");
			return;
		}

		Map<Integer, Integer> count30 = bot.getDBUtil().cases.countCasesByMod(guildId, mod.getIdLong(), Instant.now().minus(30, ChronoUnit.DAYS));
		final int roles30 = bot.getDBUtil().ticket.countTicketsByMod(event.getGuild().getId(), mod.getId(), Instant.now().minus(30, ChronoUnit.DAYS), true);

		Map<Integer, Integer> count7 = bot.getDBUtil().cases.countCasesByMod(guildId, mod.getIdLong(), Instant.now().minus(7, ChronoUnit.DAYS));
		final int roles7 = bot.getDBUtil().ticket.countTicketsByMod(event.getGuild().getId(), mod.getId(), Instant.now().minus(7, ChronoUnit.DAYS), true);

		EmbedBuilder embedBuilder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setAuthor(mod.getUser().getName(), null, mod.getEffectiveAvatarUrl())
			.setTitle(lu.getText(event, path+".title"))
			.setFooter("ID: "+mod.getId())
			.setTimestamp(Instant.now());

		StringBuilder builder = new StringBuilder("```\n#         ")
			.append(lu.getText(event, path+".seven")).append(" | ")
			.append(lu.getText(event, path+".thirty")).append(" | ")
			.append(lu.getText(event, path+".all")).append("\n");
		final int length7 = lu.getText(event, path+".seven").length();
		final int length30 = lu.getText(event, path+".thirty").length();

		builder.append(buildLine(lu.getText(event, path+".strikes"), countStrikes(count7), countStrikes(count30), countStrikes(countTotal), length7, length30))
			.append(buildLine(lu.getText(event, path+".mutes"), getCount(count7, CaseType.MUTE), getCount(count30, CaseType.MUTE), getCount(countTotal, CaseType.MUTE), length7, length30))
			.append(buildLine(lu.getText(event, path+".kicks"), getCount(count7, CaseType.KICK), getCount(count30, CaseType.KICK), getCount(countTotal, CaseType.KICK), length7, length30))
			.append(buildLine(lu.getText(event, path+".bans"), getCount(count7, CaseType.BAN), getCount(count30, CaseType.BAN), getCount(countTotal, CaseType.BAN), length7, length30))
			.append(buildTotal(lu.getText(event, path+".total"), getTotal(count7), getTotal(count30), getTotal(countTotal), length7, length30))
			.append("\n")
			.append(buildLine(lu.getText(event, path+".roles"), roles7, roles30, rolesTotal, length7, length30))
			.append("```");

		editHookEmbed(event, embedBuilder.setDescription(builder.toString()).build());
	}

	private String buildLine(String text, int count7, int count30, int countTotal, int length7, int length30) {
		return String.format("%-9s %-"+length7+"s | %-"+length30+"s | %s\n", text, count7, count30, countTotal);
	}

	private String buildTotal(String text, int count7, int count30, int countTotal, int length7, int length30) {
		return String.format("%-9s %-"+length7+"s | %-"+length30+"s | %s\n", "-"+text+"-", count7, count30, countTotal);
	}

	private int countStrikes(Map<Integer, Integer> data) {
		return getCount(data, CaseType.STRIKE_1)+getCount(data, CaseType.STRIKE_2)+getCount(data, CaseType.STRIKE_3);
	}

	private int getTotal(Map<Integer, Integer> data) {
		return data.values().stream().reduce(0, Integer::sum);
	}

	private int getCount(Map<Integer, Integer> data, CaseType type) {
		return data.getOrDefault(type.getType(), 0);
	}

}

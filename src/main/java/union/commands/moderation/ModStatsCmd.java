package union.commands.moderation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.utils.FileUpload;
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
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import union.utils.imagegen.renders.ModStatsRender;

public class ModStatsCmd extends CommandBase {
	
	public ModStatsCmd() {
		this.name = "modstats";
		this.path = "bot.moderation.modstats";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help")),
			new OptionData(OptionType.STRING, "start_date", lu.getText(path+".start_date.help")),
			new OptionData(OptionType.STRING, "end_date", lu.getText(path+".end_date.help")),
			new OptionData(OptionType.BOOLEAN, "as_text", lu.getText(path+".as_text.help"))
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

		if (event.hasOption("start_date") || event.hasOption("end_date"))
			returnIntervalStats(event);
		else
			returnFullStats(event);
	}

	private void returnIntervalStats(SlashCommandEvent event) {
		User mod = event.optUser("user", event.getUser());
		long guildId = event.getGuild().getIdLong();

		String afterDate = event.optString("start_date");
		String beforeDate = event.optString("end_date");
		Instant afterTime;
		Instant beforeTime;

		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		try {
			beforeTime = beforeDate!=null
				? LocalDate.parse(beforeDate, inputFormatter).atStartOfDay(ZoneId.systemDefault()).toInstant()
				: Instant.now();
			afterTime = afterDate!=null
				? LocalDate.parse(afterDate, inputFormatter).atStartOfDay(ZoneId.systemDefault()).toInstant()
				: Instant.now().minus(7, ChronoUnit.DAYS);
		} catch (Exception ex) {
			editError(event, path+".failed_parse", ex.getMessage());
			return;
		}
		if (beforeTime.isBefore(afterTime)) {
			editError(event, path+".wrong_date");
			return;
		}

		int countRoles = bot.getDBUtil().ticket.countTicketsByMod(event.getGuild().getId(), mod.getId(), afterTime, beforeTime, true);
		Map<Integer, Integer> countCases = bot.getDBUtil().cases.countCasesByMod(guildId, mod.getIdLong(), afterTime, beforeTime);

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
		String intervalText = "%s\n`%s` - `%s`".formatted(lu.getText(event, path+".title"), formatter.format(afterTime), formatter.format(beforeTime));
		EmbedBuilder embedBuilder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setAuthor(mod.getName(), null, mod.getEffectiveAvatarUrl())
			.setTitle(intervalText)
			.setFooter("ID: "+mod.getId())
			.setTimestamp(Instant.now());

		String builder = "```\n" +
			buildLine(lu.getText(event, path + ".strikes"), countStrikes(countCases)) +
			buildLine(lu.getText(event, path + ".game_strikes"), getCount(countCases, CaseType.GAME_STRIKE)) +
			buildLine(lu.getText(event, path + ".mutes"), getCount(countCases, CaseType.MUTE)) +
			buildLine(lu.getText(event, path + ".kicks"), getCount(countCases, CaseType.KICK)) +
			buildLine(lu.getText(event, path + ".bans"), getCount(countCases, CaseType.BAN)) +
			buildTotal(lu.getText(event, path + ".total"), getTotal(countCases)) +
			"\n" +
			buildLine(lu.getText(event, path + ".roles"), countRoles) +
			"```";

		editEmbed(event, embedBuilder.setDescription(builder).build());
	}

	private void returnFullStats(SlashCommandEvent event) {
		User mod = event.optUser("user", event.getUser());
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

		if (!event.optBoolean("as_text", false)) {
			ModStatsRender render = new ModStatsRender(event.getGuildLocale(), mod.getName(),
				countTotal, count30, count7, rolesTotal, roles30, roles7);

			String attachmentName = "%s-%s-modstats-%s.png".formatted(event.getGuild().getId(), mod.getId(), Instant.now().getEpochSecond());

			EmbedBuilder embedBuilder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
				.setAuthor(mod.getName(), null, mod.getEffectiveAvatarUrl())
				.setImage("attachment://" + attachmentName)
				.setFooter("ID: "+mod.getId())
				.setTimestamp(Instant.now());

			try {
				event.getHook().editOriginalEmbeds(embedBuilder.build()).setFiles(FileUpload.fromData(
					new ByteArrayInputStream(render.renderToBytes()),
					attachmentName
				)).queue();
			} catch (IOException e) {
				App.getInstance().getAppLogger().error("Failed to generate the rank background: {}", e.getMessage(), e);
				editError(event, path+".failed_image", "Rendering exception");
			}
			return;
		}

		EmbedBuilder embedBuilder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setAuthor(mod.getName(), null, mod.getEffectiveAvatarUrl())
			.setTitle(lu.getText(event, path+".title"))
			.setFooter("ID: "+mod.getId())
			.setTimestamp(Instant.now());

		final String sevenText = lu.getText(event, path+".seven");
		final String thirtyText = lu.getText(event, path+".thirty");
		StringBuilder builder = new StringBuilder("```\n#         ")
			.append(sevenText).append(" | ")
			.append(thirtyText).append(" | ")
			.append(lu.getText(event, path+".all")).append("\n");
		final int length7 = sevenText.length()-1;
		final int length30 = thirtyText.length()-1;

		builder.append(buildLine(lu.getText(event, path+".strikes"), countStrikes(count7), countStrikes(count30), countStrikes(countTotal), length7, length30))
			.append(buildLine(lu.getText(event, path+".game_strikes"), getCount(count7, CaseType.GAME_STRIKE), getCount(count30, CaseType.GAME_STRIKE), getCount(countTotal, CaseType.GAME_STRIKE), length7, length30))
			.append(buildLine(lu.getText(event, path+".mutes"), getCount(count7, CaseType.MUTE), getCount(count30, CaseType.MUTE), getCount(countTotal, CaseType.MUTE), length7, length30))
			.append(buildLine(lu.getText(event, path+".kicks"), getCount(count7, CaseType.KICK), getCount(count30, CaseType.KICK), getCount(countTotal, CaseType.KICK), length7, length30))
			.append(buildLine(lu.getText(event, path+".bans"), getCount(count7, CaseType.BAN), getCount(count30, CaseType.BAN), getCount(countTotal, CaseType.BAN), length7, length30))
			.append(buildTotal(lu.getText(event, path+".total"), getTotal(count7), getTotal(count30), getTotal(countTotal), length7, length30))
			.append("\n")
			.append(buildLine(lu.getText(event, path+".roles"), roles7, roles30, rolesTotal, length7, length30))
			.append("```");

		editEmbed(event, embedBuilder.setDescription(builder.toString()).build());
	}

	private String buildLine(String text, int count7, int count30, int countTotal, int length7, int length30) {
		return String.format("%-10s %-"+length7+"s |  %-"+length30+"s |  %s\n", text, count7, count30, countTotal);
	}

	private String buildLine(String text, int count) {
		return String.format("%-10s %s\n", text, count);
	}

	private String buildTotal(String text, int count7, int count30, int countTotal, int length7, int length30) {
		return String.format("%-10s %-"+length7+"s |  %-"+length30+"s |  %s\n", "-"+text+"-", count7, count30, countTotal);
	}

	private String buildTotal(String text, int count) {
		return String.format("%-10s %s\n", "-"+text+"-", count);
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

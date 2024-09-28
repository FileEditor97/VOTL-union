package union.commands.moderation;

import java.util.List;

import net.dv8tion.jda.api.utils.TimeFormat;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.database.managers.CaseManager.CaseData;
import union.utils.file.lang.LocaleUtil;
import union.utils.message.TimeUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class ModLogsCmd extends CommandBase {
	
	public ModLogsCmd() {
		this.name = "modlogs";
		this.path = "bot.moderation.modlogs";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help")),
			new OptionData(OptionType.INTEGER, "page", lu.getText(path+".page.help")).setMinValue(1),
			new OptionData(OptionType.BOOLEAN, "only_active", lu.getText(path+".only_active.help"))
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.cooldown = 15;
		this.cooldownScope = CooldownScope.USER;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		User tu;
		if (event.hasOption("user")) {
			tu = event.optUser("user");
			if (!tu.equals(event.getUser()) && !bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.MOD)) {
				editError(event, path+".no_perms");
				return;
			}
		} else {
			tu = event.getUser();
		}

		long guildId = event.getGuild().getIdLong();
		long userId = tu.getIdLong();
		final int page = event.optInteger("page", 1);
		final List<CaseData> cases = event.optBoolean("only_active", false) ?
			bot.getDBUtil().cases.getGuildUser(guildId, userId, page, true) :
			bot.getDBUtil().cases.getGuildUser(guildId, userId, page);
		if (cases.isEmpty()) {
			editEmbed(event, bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".empty")).build());
			return;
		}
		int pages = (int) Math.ceil(bot.getDBUtil().cases.countCases(guildId, userId)/10.0);

		editEmbed(event, buildEmbed(lu, event.getUserLocale(), tu, cases, page, pages).build());
	}

	public static EmbedBuilder buildEmbed(LocaleUtil lu, DiscordLocale locale, User tu, List<CaseData> cases, int page, int pages) {
		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
				.setTitle(lu.getLocalized(locale, "bot.moderation.modlogs.title").formatted(tu.getName(), page, pages))
				.setFooter(lu.getLocalized(locale, "bot.moderation.modlogs.footer").formatted(tu.getId()));
		cases.forEach(c -> {
			String temp = c.getLogUrl()==null ? "" : " - [Link](%s)".formatted(c.getLogUrl());
			StringBuilder stringBuilder = new StringBuilder()
					.append("> ").append(TimeFormat.DATE_TIME_SHORT.format(c.getTimeStart())).append(temp).append("\n")
					.append(lu.getLocalized(locale, "bot.moderation.modlogs.mod").formatted(c.getModTag()));
			if (!c.getDuration().isNegative())
				stringBuilder.append(lu.getLocalized(locale, "bot.moderation.modlogs.duration").formatted(TimeUtil.formatDuration(lu, locale, c.getTimeStart(), c.getDuration())));
			stringBuilder.append(lu.getLocalized(locale, "bot.moderation.modlogs.reason").formatted(c.getReason()));

			builder.addField("%s  #`%s`| %s".formatted(c.isActive()?"🟥":"⬛", c.getLocalIdInt(), lu.getLocalized(locale, c.getCaseType().getPath())),
					stringBuilder.toString(), false);
		});

		return builder;
	}

}

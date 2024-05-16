package union.menus;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.utils.TimeFormat;
import union.App;
import union.base.command.CooldownScope;
import union.base.command.UserContextMenu;
import union.base.command.UserContextMenuEvent;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.Constants;
import union.utils.database.managers.CaseManager;
import union.utils.message.TimeUtil;

import java.util.List;

public class ActiveModlogsContext extends UserContextMenu {

	public ActiveModlogsContext(App bot) {
		this.bot = bot;
		this.lu = bot.getLocaleUtil();
		this.name = "activelogs";
		this.path = "menus.activelogs";
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 6;
		this.cooldownScope = CooldownScope.USER;
	}

	@Override
	protected void execute(UserContextMenuEvent event) {
		event.deferReply(true).queue();
		User user = event.getTarget();

		long guildId = event.getGuild().getIdLong();
		long userId = user.getIdLong();
		List<CaseManager.CaseData> cases = bot.getDBUtil().cases.getGuildUser(guildId, userId, 1, true);
		if (cases.isEmpty()) {
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, "bot.moderation.modlogs.empty")).build()).queue();
			return;
		}
		int pages = (int) Math.ceil(bot.getDBUtil().cases.countCases(guildId, userId)/10.0);

		DiscordLocale locale = event.getUserLocale();
		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
				.setTitle(lu.getLocalized(locale, "bot.moderation.modlogs.title").formatted(user.getName(), 1, pages))
				.setDescription(lu.getLocalized(locale, path+".full"))
				.setFooter(lu.getLocalized(locale, "bot.moderation.modlogs.footer"));
		cases.forEach(c -> {
			StringBuilder stringBuilder = new StringBuilder()
					.append("> ").append(TimeFormat.DATE_TIME_SHORT.format(c.getTimeStart())).append("\n")
					.append(lu.getLocalized(locale, "bot.moderation.modlogs.target").formatted(c.getTargetTag(), c.getTargetId()))
					.append(lu.getLocalized(locale, "bot.moderation.modlogs.mod").formatted(c.getModTag()));
			if (!c.getDuration().isNegative())
				stringBuilder.append(lu.getLocalized(locale, "bot.moderation.modlogs.duration").formatted(TimeUtil.formatDuration(lu, locale, c.getTimeStart(), c.getDuration())));
			stringBuilder.append(lu.getLocalized(locale, "bot.moderation.modlogs.reason").formatted(c.getReason()));

			builder.addField(lu.getLocalized(locale, "bot.moderation.modlogs.case").formatted(c.isActive()?"🟥":"⬛", c.getCaseIdInt(), lu.getLocalized(locale, c.getCaseType().getPath())),
					stringBuilder.toString(), false);
		});

		event.getHook().editOriginalEmbeds(builder.build()).queue();
	}

}

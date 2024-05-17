package union.menus;

import net.dv8tion.jda.api.entities.User;
import union.App;
import union.base.command.CooldownScope;
import union.base.command.UserContextMenu;
import union.base.command.UserContextMenuEvent;
import union.commands.moderation.ModLogsCmd;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.utils.database.managers.CaseManager;

import java.util.List;

public class ModlogsContext extends UserContextMenu {

	public ModlogsContext(App bot) {
		this.bot = bot;
		this.lu = bot.getLocaleUtil();
		this.name = "modlogs";
		this.path = "menus.modlogs";
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
		List<CaseManager.CaseData> cases = bot.getDBUtil().cases.getGuildUser(guildId, userId, 1);
		if (cases.isEmpty()) {
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, "bot.moderation.modlogs.empty")).build()).queue();
			return;
		}
		int pages = (int) Math.ceil(bot.getDBUtil().cases.countCases(guildId, userId)/10.0);

		event.getHook().editOriginalEmbeds(
				ModLogsCmd.buildEmbed(lu, event.getUserLocale(), user, cases, 1, pages)
						.setDescription(lu.getLocalized(event.getUserLocale(), path+".full"))
						.build()
		).queue();
	}

}

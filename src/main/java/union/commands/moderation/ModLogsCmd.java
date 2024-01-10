package union.commands.moderation;

import java.util.List;

import union.App;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class ModLogsCmd extends CommandBase {
	
	public ModLogsCmd(App bot) {
		super(bot);
		this.name = "modlogs";
		this.path = "bot.moderation.modlogs";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help")),
			new OptionData(OptionType.INTEGER, "page", lu.getText(path+".page.help")).setMinValue(1)
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.cooldown = 10;
		this.cooldownScope = CooldownScope.USER;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		Member tm;
		if (event.hasOption("user")) {
			tm = event.optMember("user", event.getMember());
			if (!tm.equals(event.getMember()) && !bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.MOD)) {
				editError(event, path+".no_perms");
				return;
			}
		} else {
			tm = event.getMember();
		}

		long guildId = event.getGuild().getIdLong();
		long userId = tm.getIdLong();
		Integer page = event.optInteger("page", 1);
		List<CaseData> cases = bot.getDBUtil().cases.getGuildUser(guildId, userId, event.optInteger("page", 1));
		if (cases.isEmpty()) {
			editError(event, path+".empty");
			return;
		}
		Integer pages = bot.getDBUtil().cases.countPages(guildId, userId);

		DiscordLocale locale = event.getUserLocale();
		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setTitle(lu.getLocalized(locale, path+".title").formatted(tm.getUser().getName(), page, pages));
		cases.forEach(c -> {
			StringBuffer buffer = new StringBuffer()
				.append(lu.getLocalized(locale, path+".type").formatted(lu.getLocalized(locale, c.getCaseType().getPath())))
				.append(lu.getLocalized(locale, path+".target").formatted(c.getTargetTag(), c.getTargetId()))
				.append(lu.getLocalized(locale, path+".mod").formatted(c.getModTag()))
				.append(lu.getLocalized(locale, path+".duration").formatted(c.getDuration().isZero() ?
					lu.getLocalized(locale, path+".permament") :
					bot.getTimeUtil().durationToLocalizedString(locale, c.getDuration())
				))
				.append(lu.getLocalized(locale, path+".reason").formatted(c.getReason()));
			builder.addField((c.isActive() ? "🟥" : "⬜" ) + lu.getLocalized(locale, path+".case").formatted(c.getCaseId()), buffer.toString(), false);
		});

		editHookEmbed(event, builder.build());
	}

}

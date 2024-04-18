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
import union.utils.message.TimeUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
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

		User tu;
		if (event.hasOption("user")) {
			tu = event.optUser("user", event.getUser());
			if (!tu.equals(event.getUser()) && !bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.MOD)) {
				editError(event, path+".no_perms");
				return;
			}
		} else {
			tu = event.getUser();
		}

		long guildId = event.getGuild().getIdLong();
		long userId = tu.getIdLong();
		Integer page = event.optInteger("page", 1);
		List<CaseData> cases = bot.getDBUtil().cases.getGuildUser(guildId, userId, event.optInteger("page", 1));
		if (cases.isEmpty()) {
			editHookEmbed(event, bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".empty")).build());
			return;
		}
		int pages = (int) Math.ceil(bot.getDBUtil().cases.countCases(guildId, userId)/10.0);

		DiscordLocale locale = event.getUserLocale();
		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setTitle(lu.getLocalized(locale, path+".title").formatted(tu.getName(), page, pages))
			.setFooter(lu.getLocalized(locale, path+".footer"));
		cases.forEach(c -> {
			StringBuffer buffer = new StringBuffer()
				.append("> `"+lu.getLocalized(locale, c.getCaseType().getPath())+"`\n")
				.append(lu.getLocalized(locale, path+".target").formatted(c.getTargetTag(), c.getTargetId()))
				.append(lu.getLocalized(locale, path+".mod").formatted(c.getModTag()));
			if (!c.getDuration().isNegative())
				buffer.append(TimeUtil.formatDuration(lu, locale, c.getTimeStart(), c.getDuration()));
			buffer.append(lu.getLocalized(locale, path+".reason").formatted(c.getReason()));

			builder.addField((c.isActive() ? "🟥" : "⬜" ) + " " + lu.getLocalized(locale, path+".case").formatted(c.getCaseIdInt()), buffer.toString(), false);
		});

		editHookEmbed(event, builder.build());
	}

}

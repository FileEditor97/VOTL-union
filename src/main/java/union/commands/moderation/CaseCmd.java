package union.commands.moderation;

import java.util.List;

import union.App;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.utils.database.managers.BanManager.BanData;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class CaseCmd extends CommandBase {

	public CaseCmd(App bot) {
		super(bot);
		this.name = "case";
		this.path = "bot.moderation.case";
		this.options = List.of(
			new OptionData(OptionType.INTEGER, "id", lu.getText(path+".id.help"), true).setMinValue(1)
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
	}
	
	@Override
	protected void execute(SlashCommandEvent event) {
		BanData banData = bot.getDBUtil().ban.getInfo(event.optInteger("id"));
		if (banData == null || event.getGuild().getId() != banData.getGuildId()) {
			createError(event, path+".not_found");
			return;
		}
		MessageEmbed embed = bot.getLogUtil().banEmbed(event.getUserLocale(), banData);

		createReplyEmbed(event, embed);
	}

}

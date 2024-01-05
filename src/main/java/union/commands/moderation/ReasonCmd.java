package union.commands.moderation;

import java.util.List;

import union.App;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.database.managers.BanManager.BanData;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class ReasonCmd extends CommandBase {
	
	public ReasonCmd(App bot) {
		super(bot);
		this.name = "reason";
		this.path = "bot.moderation.reason";
		this.options = List.of(
			new OptionData(OptionType.INTEGER, "id", lu.getText(path+".id.help"), true).setMinValue(1),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help"), true).setMaxLength(400)
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();
		Integer caseId = event.optInteger("id");
		BanData banData = bot.getDBUtil().ban.getInfo(caseId);
		if (banData == null || event.getGuild().getId() != banData.getGuildId()) {
			editError(event, path+".not_found");
			return;
		}

		String newReason = event.optString("reason");
		bot.getDBUtil().ban.updateReason(caseId, newReason);

		MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
			.setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getText(event, path+".done").replace("{reason}", newReason))
			.build();
		editHookEmbed(event, embed);

		bot.getLogListener().mod.onChangeReason(event, banData, newReason);
	}
}

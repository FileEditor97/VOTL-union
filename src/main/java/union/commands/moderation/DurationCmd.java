package union.commands.moderation;

import java.time.Duration;
import java.util.List;

import union.App;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.database.managers.CaseManager.CaseData;
import union.utils.exception.FormatterException;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class DurationCmd extends CommandBase {
	
	public DurationCmd(App bot) {
		super(bot);
		this.name = "duration";
		this.path = "bot.moderation.duration";
		this.options = List.of(
			new OptionData(OptionType.INTEGER, "id", lu.getText(path+".id.help"), true).setMinValue(1),
			new OptionData(OptionType.STRING, "time", lu.getText(path+".time.help"), true).setMaxLength(20)
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();
		Integer caseId = event.optInteger("id", 0);
		CaseData caseData = bot.getDBUtil().cases.getInfo(caseId);
		if (caseData == null || event.getGuild().getIdLong() != caseData.getGuildId()) {
			editError(event, path+".not_found");
			return;
		}

		final Duration duration;
		try {
			duration = bot.getTimeUtil().stringToDuration(event.optString("time"), false);
		} catch (FormatterException ex) {
			editError(event, ex.getPath());
			return;
		}

		if (!caseData.isActive()) {
			editError(event, path+".is_expired");
			return;
		}

		bot.getDBUtil().cases.updateDuration(caseId, duration);

		String newTime = duration.isZero() ? lu.getText(event, "logger.permanently") : lu.getText(event, "logger.temporary")
			.replace("{time}", bot.getTimeUtil().formatTime(caseData.getTimeStart().plus(duration), false));
		MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
			.setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getText(event, path+".done").replace("{duration}", newTime))
			.build();
		editHookEmbed(event, embed);

		bot.getLogListener().mod.onChangeDuration(event, caseData, event.getMember(), newTime);
	}
}

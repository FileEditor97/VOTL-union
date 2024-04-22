package union.commands.moderation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import union.App;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CaseType;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.database.managers.CaseManager.CaseData;
import union.utils.exception.FormatterException;
import union.utils.message.TimeUtil;

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
		Integer caseId = event.optInteger("id");
		CaseData caseData = bot.getDBUtil().cases.getInfo(caseId);
		if (caseData == null || event.getGuild().getIdLong() != caseData.getGuildId()) {
			editError(event, path+".not_found");
			return;
		}

		Duration newDuration;
		try {
			newDuration = TimeUtil.stringToDuration(event.optString("time"), false);
		} catch (FormatterException ex) {
			editError(event, ex.getPath());
			return;
		}

		if (!( caseData.isActive() && (caseData.getCaseType().equals(CaseType.MUTE) || caseData.getCaseType().equals(CaseType.BAN)) )) {
			editError(event, path+".is_expired");
			return;
		}

		if (caseData.getCaseType().equals(CaseType.MUTE)) {
			if (newDuration.isZero()) {
				editError(event, "errors.error", "Duration must be larger than 1 minute");
				return;
			}
			event.getGuild().retrieveMemberById(caseData.getTargetId()).queue(target -> {
				if (caseData.getTimeStart().plus(newDuration).isAfter(Instant.now())) {
					// time out member for new time
					target.timeoutUntil(caseData.getTimeStart().plus(newDuration)).reason("Duration change by "+event.getUser().getName()).queue();
				} else {
					// time will be expired, remove time out
					target.removeTimeout().reason("Expired").queue();
					bot.getDBUtil().cases.setInactive(caseId);
				}
			});
		}
		bot.getDBUtil().cases.updateDuration(caseId, newDuration);
		
		String newTime = TimeUtil.formatDuration(lu, event.getUserLocale(), caseData.getTimeStart(), newDuration);
		MessageEmbed embed = bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
			.setDescription(lu.getText(event, path+".done").replace("{id}", caseId.toString()).replace("{duration}", newTime))
			.build();
		editHookEmbed(event, embed);

		bot.getLogger().mod.onChangeDuration(event, caseData, event.getMember(), newTime);
	}
}

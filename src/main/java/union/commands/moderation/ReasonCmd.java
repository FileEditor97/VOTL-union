package union.commands.moderation;

import java.sql.SQLException;
import java.util.List;

import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class ReasonCmd extends CommandBase {
	
	public ReasonCmd() {
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
		Integer localId = event.optInteger("id");
		CaseData caseData = bot.getDBUtil().cases.getInfo(event.getGuild().getIdLong(), localId);
		if (caseData == null) {
			editError(event, path+".not_found");
			return;
		}
		if (!caseData.isActive()) {
			editError(event, path+".not_active");
			return;
		}

		String newReason = event.optString("reason");

		try {
			bot.getDBUtil().cases.updateReason(caseData.getRowId(), newReason);
		} catch (SQLException e) {
			editErrorDatabase(event, e, "Failed to create new case.");
			return;
		}

		switch (caseData.getCaseType()) {
			case MUTE -> {
				// Check if inform with reason is disabled
				if (bot.getDBUtil().getGuildSettings(event.getGuild()).getInformMute().getLevel() < 2) break;
				// Retrieve user by guild and send dm
				event.getGuild().retrieveMemberById(caseData.getTargetId()).queue(member -> {
					member.getUser().openPrivateChannel().queue(pm -> {
						MessageEmbed embed = bot.getModerationUtil().getReasonUpdateEmbed(event.getGuildLocale(), event.getGuild(), caseData.getTimeStart(), caseData.getCaseType(), caseData.getReason(), newReason);
						pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
					});
				}, failure -> new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER));
			}
			case STRIKE_1, STRIKE_2, STRIKE_3 -> {
				// Check if inform with reason is disabled
				if (bot.getDBUtil().getGuildSettings(event.getGuild()).getInformStrike().getLevel() < 2) break;
				// Retrieve user by guild and send dm
				event.getGuild().retrieveMemberById(caseData.getTargetId()).queue(member -> {
					member.getUser().openPrivateChannel().queue(pm -> {
						MessageEmbed embed = bot.getModerationUtil().getReasonUpdateEmbed(event.getGuildLocale(), event.getGuild(), caseData.getTimeStart(), caseData.getCaseType(), caseData.getReason(), newReason);
						pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
					});
				}, failure -> new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER));
			}
			default -> {}
		}

		editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
			.setDescription(lu.getText(event, path+".done").formatted(localId, newReason))
			.build()
		);

		bot.getLogger().mod.onChangeReason(event, caseData, event.getMember(), newReason);
	}
}

package union.commands.verification;

import java.util.List;

import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class VerifyRoleCmd extends CommandBase {
	
	public VerifyRoleCmd() {
		this.name = "verifyrole";
		this.path = "bot.verification.verifyrole";
		this.options = List.of(
			new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true)
		);
		this.module = CmdModule.VERIFICATION;
		this.category = CmdCategory.VERIFICATION;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Guild guild = event.getGuild();
		if (bot.getDBUtil().getVerifySettings(guild).isCheckEnabled()) {
			createError(event, path+".disable_check");
			return;
		}
		Role role = event.optRole("role");
		if (role == null || role.isPublicRole() || role.isManaged() || !guild.getSelfMember().canInteract(role)) {
			createError(event, path+".no_role");
			return;
		}

		bot.getDBUtil().verifySettings.setVerifyRole(guild.getIdLong(), role.getIdLong());

		createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
			.setDescription(lu.getText(event, path+".done").replace("{role}", role.getAsMention()))
			.build());
	}

}

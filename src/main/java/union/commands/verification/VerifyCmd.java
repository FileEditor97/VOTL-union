package union.commands.verification;

import java.sql.SQLException;
import java.util.List;

import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class VerifyCmd extends CommandBase {
	
	public VerifyCmd() {
		this.name = "verify";
		this.path = "bot.verification.verify";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
		);
		this.botPermissions = new Permission[]{Permission.MANAGE_ROLES};
		this.module = CmdModule.VERIFICATION;
		this.category = CmdCategory.VERIFICATION;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		Member member = event.optMember("user");
		if (member == null || member.getUser().isBot()) {
			editError(event, path+".no_user");
			return;
		}

		Guild guild = event.getGuild();
		Long roleId = bot.getDBUtil().getVerifySettings(guild).getRoleId();
		if (roleId == null) {
			editError(event, path+".not_setup");
			return;
		}
		Role role = guild.getRoleById(roleId);
		if (role == null) {
			editErrorOther(event, "Role not found by the ID: "+roleId);
			return;
		}

		if (member.getRoles().contains(role)) {
			try {
				bot.getDBUtil().verifyCache.addForcedUser(member.getIdLong());
			} catch (SQLException e) {
				editErrorDatabase(event, e, "verify add forced");
				return;
			}
			editError(event, "bot.verification.user_verified");
		} else {
			guild.addRoleToMember(member, role).reason(String.format("Manual verification by %s", event.getUser().getName())).queue(
				success -> {
					try {
						bot.getDBUtil().verifyCache.addForcedUser(member.getIdLong());
					} catch (SQLException e) {
						editErrorDatabase(event, e, "verify add forced");
						return;
					}

					bot.getLogger().verify.onVerified(member.getUser(), null, guild);
					editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".done")).build());
				},
				failure -> {
					editError(event, "bot.verification.failed_role");
					bot.getAppLogger().info("Was unable to add verify role to user in {} ({})", guild.getName(), guild.getId(), failure);
				}
			);
		}
	}

}

package union.commands.verification;

import java.util.List;

import union.App;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.command.SlashCommandEvent;
import union.objects.constants.CmdCategory;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class VerifyCmd extends CommandBase {
	
	public VerifyCmd(App bot) {
		super(bot);
		this.name = "verify";
		this.path = "bot.verification.verify";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
		);
		this.botPermissions = new Permission[]{Permission.MANAGE_ROLES};
		this.module = CmdModule.VERIFICATION;
		this.category = CmdCategory.VERIFICATION;
		this.accessLevel = CmdAccessLevel.MOD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Member member = event.optMember("user");
		Guild guild = event.getGuild();
		if (member == null || member.getUser().isBot()) {
			createError(event, path+".no_user");
			return;
		}

		String roleId = bot.getDBUtil().verify.getVerifyRole(guild.getId());
		if (roleId == null) {
			createError(event, path+".not_setup");
			return;
		}
		Role role = guild.getRoleById(roleId);
		if (role == null) {
			createError(event, "errors.unknown", "Role not found by ID: "+roleId);
			return;
		}

		if (member.getRoles().contains(role)) {
			if (bot.getDBUtil().verifyCache.isVerified(member.getId())) {
				bot.getDBUtil().verifyCache.setForced(member.getId());
			} else {
				bot.getDBUtil().verifyCache.addForcedUser(member.getId());
			}
			createError(event, "bot.verification.user_verified");
			return;
		}

		guild.addRoleToMember(member, role).reason(String.format("Manual verification by %s", event.getUser().getName())).queue(
			success -> {
				bot.getLogListener().onVerified(member.getUser(), null, guild);
				if (bot.getDBUtil().verifyCache.isVerified(member.getId())) {
					bot.getDBUtil().verifyCache.setForced(member.getId());
				} else {
					bot.getDBUtil().verifyCache.addForcedUser(member.getId());
				}
				createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event).setDescription(lu.getText(event, path+".done")).build());
			},
			failure -> {
				createError(event, "bot.verification.failed_role");
				bot.getLogger().info(String.format("Was unable to add verify role to user in %s (%s)", guild.getName(), guild.getId()), failure);
			});
	}

}

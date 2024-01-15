package union.commands.verification;

import java.util.List;

import union.App;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
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
		event.deferReply(true).queue();

		Member member = event.optMember("user");
		if (member == null || member.getUser().isBot()) {
			editError(event, path+".no_user");
			return;
		}

		Guild guild = event.getGuild();
		String roleId = bot.getDBUtil().verify.getVerifyRole(guild.getId());
		if (roleId == null) {
			editError(event, path+".not_setup");
			return;
		}
		Role role = guild.getRoleById(roleId);
		if (role == null) {
			editError(event, "errors.unknown", "Role not found by ID: "+roleId);
			return;
		}

		if (member.getRoles().contains(role)) {
			bot.getDBUtil().verifyCache.addForcedUser(member.getIdLong());
			editError(event, "bot.verification.user_verified");
		} else {
			guild.addRoleToMember(member, role).reason(String.format("Manual verification by %s", event.getUser().getName())).queue(
				success -> {
					bot.getLogListener().verify.onVerified(member.getUser(), null, guild);
					bot.getDBUtil().verifyCache.addForcedUser(member.getIdLong());

					editHookEmbed(event, bot.getEmbedUtil().getEmbed(event).setDescription(lu.getText(event, path+".done")).build());
				},
				failure -> {
					editError(event, "bot.verification.failed_role");
					bot.getLogger().info(String.format("Was unable to add verify role to user in %s (%s)", guild.getName(), guild.getId()), failure);
				}
			);
		}
		
	}

}

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

public class UnverifyCmd extends CommandBase {
	
	public UnverifyCmd(App bot) {
		super(bot);
		this.name = "unverify";
		this.path = "bot.verification.unverify";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help")).setMaxLength(200)
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
		if (member == null) {
			createError(event, path+".no_user");
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

		if (!member.getRoles().contains(role)) {
			createError(event, "bot.verification.user_not_verified");
			return;
		}

		String reason = event.optString("reason", lu.getLocalized(event.getGuildLocale(), path+".no_reason"));

		guild.removeRoleFromMember(member, role).reason(String.format("Manual unverification by %s | %s", event.getUser().getName(), reason)).queue(
			success -> {
				bot.getLogListener().verify.onUnverified(member.getUser(), null, guild, reason);
				bot.getDBUtil().verifyCache.removeByDiscord(member.getId());
				createReplyEmbed(event, bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".done")).build());
			},
			failure -> {
				createError(event, "bot.verification.failed_role");
				bot.getLogger().info(String.format("Was unable to remove verify role to user in %s (%s)", guild.getName(), guild.getId()), failure);
			});
	}

}

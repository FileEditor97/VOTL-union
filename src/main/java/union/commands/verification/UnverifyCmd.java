package union.commands.verification;

import java.util.List;

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
	
	public UnverifyCmd() {
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
		event.deferReply(true).queue();

		Member member = event.optMember("user");
		if (member == null) {
			editError(event, path+".no_user");
		}

		Guild guild = event.getGuild();
		Long roleId = bot.getDBUtil().getVerifySettings(guild).getRoleId();
		if (roleId == null) {
			editError(event, path+".not_setup");
			return;
		}
		Role role = guild.getRoleById(roleId);
		if (role == null) {
			editError(event, "errors.error", "Role not found by the ID: "+roleId);
			return;
		}

		if (!member.getRoles().contains(role)) {
			editError(event, "bot.verification.user_not_verified");
			return;
		}

		String reason = event.optString("reason", lu.getLocalized(event.getGuildLocale(), path+".no_reason"));

		guild.removeRoleFromMember(member, role).reason(String.format("Manual unverification by %s | %s", event.getUser().getName(), reason)).queue(
			success -> {
				bot.getLogger().verify.onUnverified(member.getUser(), null, guild, reason);
				bot.getDBUtil().verifyCache.removeByDiscord(member.getIdLong());
				editHookEmbed(event, bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".done")).build());
			},
			failure -> {
				editError(event, "bot.verification.failed_role");
				bot.getAppLogger().info(String.format("Was unable to remove verify role to user in %s (%s)", guild.getName(), guild.getId()), failure);
			});
	}

}

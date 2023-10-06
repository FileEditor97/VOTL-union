package union.commands.roles;

import java.util.List;
import java.util.concurrent.TimeUnit;

import union.App;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.command.CooldownScope;
import union.objects.command.SlashCommandEvent;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class CheckRankCmd extends CommandBase {
	
	public CheckRankCmd(App bot) {
		super(bot);
		this.name = "checkrank";
		this.path = "bot.roles.checkrank";
		this.options = List.of(
			new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
			new OptionData(OptionType.STRING, "rank", lu.getText(path+".rank.help"), true)
				.addChoice("VIP/Donate Admin", "vip/adminpay")
				.addChoice("Admin (⚠️)", "admin")
				.addChoice("Eventmaster (⚠️)", "eventmaster")
		);
		this.category = CmdCategory.ROLES;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.cooldownScope = CooldownScope.GUILD;
		this.cooldown = 360;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Guild guild = event.getGuild();
		Role role = event.optRole("role");
		if (role == null) {
			createError(event, path+".no_role");
			return;
		}
		if (role.isPublicRole() || role.isManaged() || !guild.getSelfMember().canInteract(role) || role.hasPermission(Permission.ADMINISTRATOR)) {
			createError(event, path+".incorrect_role");
			return;
		}

		// Check if this guild has connected DB table
		if (bot.getFileManager().getNullableString("config", "central-dbs."+guild.getId()) == null) {
			createError(event, "errors.unknown", "This Discord server is not connected to the database. Will not run!");
			return;
		}
		event.deferReply().queue();

		String requiredRank = event.optString("rank");

		// Retrieve members with this role
		event.getGuild().findMembersWithRoles(role).setTimeout(4, TimeUnit.SECONDS).onSuccess(members -> {
			Integer maxSize = members.size();
			if (maxSize > 200) {
				editError(event, "errors.unknown", "Amount of members to be processed reached maximum limit of **200**! Manually clear the selected role.");
				return;
			}
			Integer removed = 0;
			for (Member member : members) {
				String steam64 = bot.getDBUtil().verifyCache.getSteam64(member.getId());
				if (steam64 == null) {
					guild.removeRoleFromMember(member, role).reason("Not verified").queue();
					removed++;
					continue;
				}
				String steamId = bot.getSteamUtil().convertSteam64toSteamID(steam64);
				String rank = bot.getDBUtil().unionPlayers.getPlayerRank(guild.getId(), steamId);
				if (!requiredRank.contains(rank)) {
					guild.removeRoleFromMember(member, role).reason("User is not "+requiredRank).queue((null), new ErrorHandler().ignore(ErrorResponse.MISSING_PERMISSIONS));
					removed++;
				}
			}
			editHookEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{role}", role.getAsMention()).replace("{count}", removed.toString()).replace("{max}", maxSize.toString()))
				.build()
			);
		}).onError(failure -> {
			editError(event, "errors.unknown", failure.getMessage());
		});
	}

}

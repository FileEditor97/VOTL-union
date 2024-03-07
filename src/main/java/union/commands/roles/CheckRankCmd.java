package union.commands.roles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import union.App;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.message.SteamUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

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
			createError(event, "errors.error", "This Discord server is not connected to the database. Will not run!");
			return;
		}

		EmbedBuilder builder = bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".started"));
		event.replyEmbeds(builder.build()).queue();

		// Retrieve members with this role
		event.getGuild().findMembersWithRoles(role).setTimeout(4, TimeUnit.SECONDS).onSuccess(members -> {
			Integer maxSize = members.size();
			if (maxSize == 0) {
				editError(event, path+".empty");
				return;
			}
			if (maxSize > 200) {
				editError(event, "errors.error", "Amount of members to be processed reached maximum limit of **200**! Manually clear the selected role.");
				return;
			}
			editHookEmbed(event, builder.appendDescription(lu.getText(event, path+".estimate").formatted(maxSize)).build());

			String requiredRank = event.optString("rank");

			List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
			for (Member member : members) {
				Long steam64 = bot.getDBUtil().verifyCache.getSteam64(member.getIdLong());
				if (steam64 == null) {
					completableFutures.add(guild.removeRoleFromMember(member, role).reason("Not verified").submit().exceptionally(ex -> null));
					continue;
				}
				String steamId = SteamUtil.convertSteam64toSteamID(steam64);
				String rank = bot.getDBUtil().unionPlayers.getPlayerRank(guild.getId(), steamId);
				if (rank == null || !requiredRank.contains(rank)) {
					completableFutures.add(guild.removeRoleFromMember(member, role).reason("User is not "+requiredRank).submit().exceptionally(ex -> null));
				}
			}

			CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
				.whenComplete((done, exception) -> {
					if (exception != null) {
						editError(event, "errors.unknown", exception.getMessage());
					} else {
						Integer removed = 0;
						for (CompletableFuture<Void> future : completableFutures) {
							if (!future.isCompletedExceptionally()) removed++;
						}

						// Log
						bot.getLogListener().role.onCheckRank(guild, event.getUser(), role, requiredRank);
						// Send reply
						editHookEmbed(event, builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".done")
							.replace("{role}", role.getName()).replace("{count}", removed.toString()).replace("{max}", maxSize.toString())
						).build());
					}
				}).thenRun(guild::pruneMemberCache); // Prune member cache;
		}).onError(failure -> {
			editError(event, "errors.error", failure.getMessage());
		});
	}

}

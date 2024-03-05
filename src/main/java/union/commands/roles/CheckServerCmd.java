package union.commands.roles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import union.App;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class CheckServerCmd extends CommandBase {
	
	public CheckServerCmd(App bot) {
		super(bot);
		this.name = "checkservers";
		this.path = "bot.roles.checkservers";
		this.options = List.of(
			new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
			new OptionData(OptionType.STRING, "server", lu.getText(path+".server.help"), true)
		);
		this.category = CmdCategory.ROLES;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.cooldownScope = CooldownScope.GUILD;
		this.cooldown = 120;
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

		// Check if guild is accessable by helper bot
		if (bot.getHelper() == null) {
			createError(event, path+".no_helper");
			return;
		}
		String guildId = event.optString("server");
		Guild targetGuild = bot.getHelper().getJDA().getGuildById(guildId);
		if (targetGuild == null || targetGuild.equals(guild)) {
			createError(event, path+".no_guild");
			return;
		}
		String guildName = targetGuild.getName();
		
		EmbedBuilder builder = bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".started"));
		event.replyEmbeds(builder.build()).queue();

		// Retrieve members with this role
		guild.findMembersWithRoles(role).setTimeout(4, TimeUnit.SECONDS).onSuccess(members -> {
			Integer maxSize = members.size();
			if (maxSize == 0) {
				editError(event, path+".empty");
				return;
			}
			if (maxSize > 400) {
				editError(event, "errors.error", "Amount of members to be processed reached maximum limit of **400**! Manually clear the selected role.");
				return;
			}
			editHookEmbed(event, builder.appendDescription(lu.getText(event, path+".estimate").formatted(Math.round(maxSize*0.7))).build());

			/* 1. If user is not in target server:
			Try remove the role from user in this server
			 If success - return "true"
			 If failed (ex, lacks permissions or other) - return "false"
			2. If user is in target server:
			 return "false" 
			So count only those, fromn whom role was removed*/
			List<CompletableFuture<Boolean>> completableFutures = new ArrayList<>();
			for (Member member : members) {
				completableFutures.add(targetGuild.retrieveMember(member).submit()
					.exceptionally(ex -> null)
					.thenCompose(m -> m == null ?
						guild.removeRoleFromMember(member, role).reason("Not inside server '%s'".formatted(guildName)).submit()
							.thenCompose(v -> CompletableFuture.completedFuture(true))
							.exceptionallyCompose(ex -> CompletableFuture.completedFuture(false))
						: 
						CompletableFuture.completedFuture(false)
					)
				);
			}

			CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
				.whenComplete((done, exception) -> {
					if (exception != null) {
						editError(event, "errors.unknown", exception.getMessage());
					} else {
						Integer removed = 0;
						for (CompletableFuture<Boolean> future : completableFutures) {
							try {
								if (!future.isCompletedExceptionally() && future.get().equals(true)) removed++;
							} catch (InterruptedException | ExecutionException ex) {
								bot.getLogger().error("At CheckServerCmd\n", ex);
								editError(event, "errors.unknown", ex.getLocalizedMessage());
							}
						}

						// Log
						bot.getLogListener().role.onRoleCheckChildGuild(guild, event.getUser(), role, targetGuild);
						// Send reply
						editHookEmbed(event, builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".done")
							.replace("{role}", role.getName()).replace("{count}", removed.toString())
							.replace("{max}", maxSize.toString()).replace("{guild}", guildName)
						).build());
					}
				}).thenRun(() -> {
					// Prune member cache
					guild.pruneMemberCache();
					targetGuild.pruneMemberCache();
				});
		}).onError(failure -> {
			editError(event, "errors.error", failure.getMessage());
		});
	}

}

package union.commands.roles;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import union.App;
import union.base.command.CooldownScope;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class RoleCmd extends CommandBase {
	
	public RoleCmd(App bot) {
		super(bot);
		this.name = "role";
		this.path = "bot.roles.role";
		this.children = new SlashCommand[]{new Add(bot), new Remove(bot), new RemoveAll(bot)};
		this.category = CmdCategory.ROLES;
		this.module = CmdModule.ROLES;
		this.accessLevel = CmdAccessLevel.HELPER;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}
	
	private class Add extends SlashCommand {

		public Add(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "add";
			this.path = "bot.roles.role.add";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = Objects.requireNonNull(event.getGuild());
			// Check role
			Role role = event.optRole("role");
			if (role == null) {
				createError(event, path+".no_role");
				return;
			}
			if (role.isPublicRole() || role.isManaged() || !guild.getSelfMember().canInteract(role) || role.hasPermission(Permission.ADMINISTRATOR)) {
				createError(event, path+".incorrect_role");
				return;
			}
			// Check member
			Member member = event.optMember("user");
			if (member == null) {
				createError(event, path+".no_member");
				return;
			}
			
			guild.addRoleToMember(member, role).reason("by "+event.getMember().getEffectiveName()).queue(done -> {
				// Log
				bot.getLogListener().role.onRoleAdded(guild, event.getUser(), member.getUser(), role);
				// Send reply
				createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
					.setColor(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").replace("{role}", role.getName()).replace("{user}", member.getEffectiveName()))
					.build());
			}, failure -> {
				createError(event, path+".failed", failure.getMessage());
			});
		}

	}

	private class Remove extends SlashCommand {

		public Remove(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "remove";
			this.path = "bot.roles.role.remove";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = Objects.requireNonNull(event.getGuild());
			// Check role
			Role role = event.optRole("role");
			if (role == null) {
				createError(event, path+".no_role");
				return;
			}
			if (role.isPublicRole() || role.isManaged() || !guild.getSelfMember().canInteract(role) || role.hasPermission(Permission.ADMINISTRATOR)) {
				createError(event, path+".incorrect_role");
				return;
			}
			// Check member
			Member member = event.optMember("user");
			if (member == null) {
				createError(event, path+".no_member");
				return;
			}
			
			guild.removeRoleFromMember(member, role).reason("by "+event.getMember().getEffectiveName()).queue(done -> {
				// Log
				bot.getLogListener().role.onRoleRemoved(guild, event.getUser(), member.getUser(), role);
				// Send reply
				createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
					.setColor(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").replace("{role}", role.getName()).replace("{user}", member.getEffectiveName()))
					.build());
			}, failure -> {
				createError(event, path+".failed", failure.getMessage());
			});
		}
		
	}

	private class RemoveAll extends SlashCommand {

		public RemoveAll(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "removeall";
			this.path = "bot.roles.role.removeall";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true)
			);
			this.accessLevel = CmdAccessLevel.ADMIN;
			this.cooldownScope = CooldownScope.GUILD;
			this.cooldown = 30;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = Objects.requireNonNull(event.getGuild());
			// Check role
			Role role = event.optRole("role");
			if (role == null) {
				createError(event, path+".no_role");
				return;
			}
			if (role.isPublicRole() || role.isManaged() || !guild.getSelfMember().canInteract(role) || role.hasPermission(Permission.ADMINISTRATOR)) {
				createError(event, path+".incorrect_role");
				return;
			}

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event).setDescription(lu.getText(event, path+".started"));
			event.replyEmbeds(builder.build()).queue();

			event.getGuild().findMembersWithRoles(role).setTimeout(4, TimeUnit.SECONDS).onSuccess(members -> {
				Integer maxSize = members.size();
				if (maxSize == 0) {
					editError(event, path+".empty");
					return;
				}
				if (maxSize > 400) {
					editError(event, "errors.error", "Amount of members to be processed reached maximum limit of **400**! Manually clear the selected role.");
					return;
				}
				editHookEmbed(event, builder.appendDescription(lu.getText(event, path+".estimate").formatted(maxSize)).build());

				String reason = "by "+event.getMember().getEffectiveName();
				List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
				for (Member member : members) {
					completableFutures.add(guild.removeRoleFromMember(member, role).reason(reason).submit().exceptionally(ex -> null));
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
							bot.getLogListener().role.onRoleRemovedAll(guild, event.getUser(), role);
							// Send reply
							editHookEmbed(event, builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".done")
								.replace("{role}", role.getName()).replace("{count}", removed.toString()).replace("{max}", maxSize.toString())
							).build());
						}
					}).thenRun(guild::pruneMemberCache); // Prune member cache
			}).onError(failure -> {
				editError(event, "errors.error", failure.getMessage());
			});
		}
		
	}

}

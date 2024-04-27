package union.commands.roles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
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
		this.children = new SlashCommand[]{new Add(bot), new Remove(bot), new RemoveAll(bot), new Modify(bot)};
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
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
				new OptionData(OptionType.ROLE, "role1", lu.getText(path+".role1.help"), true),
				new OptionData(OptionType.ROLE, "role2", lu.getText(path+".role2.help"), false),
				new OptionData(OptionType.ROLE, "role3", lu.getText(path+".role3.help"), false)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = Objects.requireNonNull(event.getGuild());

			// Get roles
			List<Role> roles = new ArrayList<>(3);
			Role role = event.optRole("role1");
			if (role != null) roles.add(role);

			role = event.optRole("role2");
			if (role != null) roles.add(role);

			role = event.optRole("role3");
			if (role != null) roles.add(role);

			if (roles.isEmpty()) {
				editError(event, path+".invalid_args");
				return;
			}

			// Check roles
			Role publicRole = guild.getPublicRole();
			for (Role r : roles) {
				if (r.equals(publicRole) || r.isManaged() || !event.getMember().canInteract(r) || !guild.getSelfMember().canInteract(r) || r.hasPermission(Permission.ADMINISTRATOR)) {
					createError(event, path+".incorrect_role", "Role: "+r.getAsMention());
					return;
				}
			}

			// Check member
			Member member = event.optMember("user");
			if (member == null) {
				createError(event, path+".no_member");
				return;
			}

			List<Role> finalRoles = new ArrayList<>(member.getRoles());
			finalRoles.addAll(roles);
			
			guild.modifyMemberRoles(member, finalRoles).reason("by "+event.getMember().getEffectiveName()).queue(done -> {
				String rolesString = roles.stream().map(Role::getAsMention).collect(Collectors.joining(", "));
				// Log
				bot.getLogger().role.onRolesAdded(guild, event.getUser(), member.getUser(), rolesString);
				// Send reply
				createReplyEmbed(event, false, bot.getEmbedUtil().getEmbed()
					.setColor(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").replace("{roles}", rolesString).replace("{user}", member.getAsMention()))
					.build());
			},
			failure -> createError(event, path+".failed", failure.getMessage()));
		}

	}

	private class Remove extends SlashCommand {

		public Remove(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "remove";
			this.path = "bot.roles.role.remove";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
				new OptionData(OptionType.ROLE, "role1", lu.getText(path+".role1.help"), true),
				new OptionData(OptionType.ROLE, "role2", lu.getText(path+".role2.help"), false),
				new OptionData(OptionType.ROLE, "role3", lu.getText(path+".role3.help"), false)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = Objects.requireNonNull(event.getGuild());
			
			// Get roles
			List<Role> roles = new ArrayList<>(3);
			Role role = event.optRole("role1");
			if (role != null) roles.add(role);

			role = event.optRole("role2");
			if (role != null) roles.add(role);

			role = event.optRole("role3");
			if (role != null) roles.add(role);

			if (roles.isEmpty()) {
				editError(event, path+".invalid_args");
				return;
			}

			// Check roles
			Role publicRole = guild.getPublicRole();
			for (Role r : roles) {
				if (r.equals(publicRole) || r.isManaged() || !event.getMember().canInteract(r) || !guild.getSelfMember().canInteract(r) || r.hasPermission(Permission.ADMINISTRATOR)) {
					createError(event, path+".incorrect_role", "Role: "+r.getAsMention());
					return;
				}
			}

			// Check member
			Member member = event.optMember("user");
			if (member == null) {
				createError(event, path+".no_member");
				return;
			}

			List<Role> finalRoles = new ArrayList<>(member.getRoles());
			finalRoles.removeAll(roles);
			
			guild.modifyMemberRoles(member, finalRoles).reason("by "+event.getMember().getEffectiveName()).queue(done -> {
				String rolesString = roles.stream().map(Role::getAsMention).collect(Collectors.joining(", "));
				// Log
				bot.getLogger().role.onRolesRemoved(guild, event.getUser(), member.getUser(), rolesString);
				// Send reply
				createReplyEmbed(event, false, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").replace("{roles}", rolesString).replace("{user}", member.getAsMention()))
					.build());
			},
			failure -> createError(event, path+".failed", failure.getMessage()));
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

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".started"));
			event.replyEmbeds(builder.build()).queue();

			event.getGuild().findMembersWithRoles(role).setTimeout(4, TimeUnit.SECONDS).onSuccess(members -> {
				int maxSize = members.size();
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
							int removed = 0;
							for (CompletableFuture<Void> future : completableFutures) {
								if (!future.isCompletedExceptionally()) removed++;
							}
							// Log
							bot.getLogger().role.onRoleRemovedAll(guild, event.getUser(), role);
							// Send reply
							editHookEmbed(event, builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".done")
								.replace("{role}", role.getName()).replace("{count}", Integer.toString(removed)).replace("{max}", Integer.toString(maxSize))
							).build());
						}
					}).thenRun(guild::pruneMemberCache); // Prune member cache
			}).onError(failure -> editError(event, "errors.error", failure.getMessage()));
		}
		
	}

	private class Modify extends SlashCommand {
		public Modify(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "modify";
			this.path = "bot.roles.role.modify";
			this.options = List.of(
					new OptionData(OptionType.USER, "user", lu.getText(path + ".user.help"), true)
			);
			this.cooldownScope = CooldownScope.USER;
			this.cooldown = 15;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Member target = event.optMember("user");
			if (target == null) {
				editError(event, path+".no_user");
				return;
			}
			if (!event.getMember().canInteract(target) || target.getUser().isBot()) {
				editError(event, path+".incorrect_user");
				return;
			}
			List<Role> userRoles = target.getRoles();
			List<Role> allRoles = event.getGuild().getRoleCache().asList();

			long guildId = event.getGuild().getIdLong();

			List<ActionRow> actionRows = new ArrayList<>();
			StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("role:manage-select:1:"+target.getId()).setRequiredRange(0, 25);
			List<SelectOption> roleOptions = new ArrayList<>();
			List<String> defaultValues = new ArrayList<>();
			int menuId = 2;
			Member selfMember = event.getGuild().getSelfMember();
			for (Role role : allRoles) {
				if (role.isManaged() || !selfMember.canInteract(role) || role.hasPermission(Permission.ADMINISTRATOR) || role.getIdLong()==guildId) continue;
				SelectOption option = SelectOption.of(role.getName(), role.getId());

				if (roleOptions.size() >= 25) {
					// Append builder to the list and create new builder
					menuBuilder.addOptions(roleOptions).setDefaultValues(defaultValues);
					actionRows.add(ActionRow.of(menuBuilder.build()));
					// Clear options and default values
					roleOptions.clear();
					defaultValues.clear();
					// Allow maximum 4 rows (fifth for button)
					if (actionRows.size() >= 4) break;
					// Else create new builder
					menuBuilder = StringSelectMenu.create("role:manage-select:"+menuId+":"+target.getId()).setRequiredRange(0, 25);
					menuId++;
				}
				roleOptions.add(option);
				if (userRoles.contains(role)) defaultValues.add(role.getId());
			}
			if (!roleOptions.isEmpty()) {
				menuBuilder.addOptions(roleOptions).setDefaultValues(defaultValues);
				actionRows.add(ActionRow.of(menuBuilder.build()));
			}
			if (actionRows.isEmpty()) {
				editError(event, path+".no_roles");
				return;
			}
			actionRows.add(ActionRow.of(Button.primary("role:manage-confirm:"+target.getId(), lu.getText(event, path+".button"))));

			bot.getDBUtil().modifyRole.create(event.getGuild().getIdLong(), event.getUser().getIdLong(),
					target.getIdLong(), Instant.now().plus(2, ChronoUnit.MINUTES));

			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getText(event, path+".title").formatted(target.getAsMention()))
					.build()
			).setComponents(actionRows).queue();
		}
	}

}

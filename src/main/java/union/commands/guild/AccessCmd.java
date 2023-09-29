package union.commands.guild;

import java.util.List;
import java.util.Objects;

import union.App;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.command.SlashCommand;
import union.objects.command.SlashCommandEvent;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

public class AccessCmd extends CommandBase {

	public AccessCmd(App bot) {
		super(bot);
		this.name = "access";
		this.path = "bot.guild.access";
		this.children = new SlashCommand[]{new View(bot), new AddRole(bot), new RemoveRole(bot), new AddOperator(bot), new RemoveOperator(bot)};
		this.category = CmdCategory.GUILD;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class View extends SlashCommand {

		public View(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "view";
			this.path = "bot.guild.access.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();
			
			List<String> helperIds = bot.getDBUtil().access.getRoles(guildId, CmdAccessLevel.HELPER);
			List<String> modIds = bot.getDBUtil().access.getRoles(guildId, CmdAccessLevel.MOD);
			List<String> userIds = bot.getDBUtil().access.getAllUsers(guildId);

			EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getText(event, "bot.guild.access.view.embed.title"));

			if (helperIds.isEmpty() && modIds.isEmpty() && userIds.isEmpty()) {
				editHookEmbed(event, 
					embedBuilder.setDescription(
						lu.getText(event, "bot.guild.access.view.embed.none_found")
					).build()
				);
				return;
			}

			StringBuilder sb = new StringBuilder();

			sb.append(lu.getText(event, "bot.guild.access.view.embed.helper")).append("\n");
			if (helperIds.isEmpty()) sb.append("> %s".formatted(lu.getText(event, "bot.guild.access.view.embed.none")));
			else for (String roleId : helperIds) {
				Role role = guild.getRoleById(roleId);
				if (role == null) {
					bot.getDBUtil().access.removeRole(roleId);
					continue;
				}
				sb.append("> %s `%s`\n".formatted(role.getAsMention(), roleId));
			}

			sb.append(lu.getText(event, "bot.guild.access.view.embed.mod")).append("\n");
			if (modIds.isEmpty()) sb.append("> %s".formatted(lu.getText(event, "bot.guild.access.view.embed.none")));
			else for (String roleId : modIds) {
				Role role = guild.getRoleById(roleId);
				if (role == null) {
					bot.getDBUtil().access.removeRole(roleId);
					continue;
				}
				sb.append("> %s `%s`\n".formatted(role.getAsMention(), roleId));
			}

			sb.append("\n").append(lu.getText(event, "bot.guild.access.view.embed.operator")).append("\n");
			if (userIds.isEmpty()) sb.append("> %s".formatted(lu.getText(event, "bot.guild.access.view.embed.none")));
			else for (String userId : userIds) {
				UserSnowflake user = User.fromId(userId);
				sb.append("> %s `%s`\n".formatted(user.getAsMention(), userId));
			}

			embedBuilder.setDescription(sb);
			editHookEmbed(event, embedBuilder.build());
		}

	}

	private class AddRole extends SlashCommand {

		public AddRole(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "role";
			this.path = "bot.guild.access.add.role";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.INTEGER, "access_level", lu.getText(path+".access_level.help"), true)
					.addChoice("Helper", CmdAccessLevel.HELPER.getLevel())
					.addChoice("Moderator", CmdAccessLevel.MOD.getLevel())
			);
			this.subcommandGroup = new SubcommandGroupData("add", lu.getText("bot.guild.access.add.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Role role = event.optRole("role");
			if (role == null) {
				editError(event, "bot.guild.access.add.no_role");
				return;
			}

			String roleId = role.getId();
			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();

			if (role.isManaged() || !guild.getSelfMember().canInteract(role) || role.hasPermission(Permission.ADMINISTRATOR)) {
				editError(event, "bot.guild.access.add.incorrect_role");
				return;
			}
			if (bot.getDBUtil().access.isRole(roleId)) {
				editError(event, "bot.guild.access.add.role.already");
				return;
			}

			CmdAccessLevel level = CmdAccessLevel.byLevel(event.optInteger("access_level"));
			bot.getDBUtil().access.addRole(guildId, roleId, level);

			EmbedBuilder embed = bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, "bot.guild.access.add.role.done")
					.replace("{role}", role.getAsMention())
					.replace("{level}", level.getName())
				)
				.setColor(Constants.COLOR_SUCCESS);
			editHookEmbed(event, embed.build());
		}

	}

	private class RemoveRole extends SlashCommand {

		public RemoveRole(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "role";
			this.path = "bot.guild.access.remove.role";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("remove", lu.getText("bot.guild.access.remove.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Role role = event.optRole("role");
			if (role == null) {
				editError(event, "bot.guild.access.remove.no_role");
				return;
			}

			String roleId = role.getId();

			CmdAccessLevel level = bot.getDBUtil().access.getRoleLevel(roleId);
			if (level.equals(CmdAccessLevel.ALL)) {
				editError(event, "bot.guild.access.remove.role.no_access");
			}

			bot.getDBUtil().access.removeRole(roleId);

			EmbedBuilder embed = bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, "bot.guild.access.remove.role.done")
					.replace("{role}", role.getAsMention())
					.replace("{level}", level.getName())
				)
				.setColor(Constants.COLOR_SUCCESS);
			editHookEmbed(event, embed.build());
		}

	}

	private class AddOperator extends SlashCommand {

		public AddOperator(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "operator";
			this.path = "bot.guild.access.add.operator";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("add", lu.getText("bot.guild.access.add.help"));
			this.accessLevel = CmdAccessLevel.OWNER;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Member member = event.optMember("user");
			if (member == null) {
				editError(event, "bot.guild.access.add.no_member");
				return;
			}

			String userId = member.getId();
			String guildId = event.getGuild().getId();

			if (member.isOwner() || member.getUser().isBot()) {
				editError(event, "bot.guild.access.add.incorrect_user");
				return;
			}
			if (bot.getDBUtil().access.isOperator(guildId, userId)) {
				editError(event, "bot.guild.access.add.user_already");
				return;
			}
			bot.getDBUtil().access.addUser(guildId, userId, CmdAccessLevel.OPERATOR);
			EmbedBuilder embed = bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, "bot.guild.access.add.operator.done").replace("{user}", member.getAsMention()))
				.setColor(Constants.COLOR_SUCCESS);
			editHookEmbed(event, embed.build());
		}

	}

	private class RemoveOperator extends SlashCommand {

		public RemoveOperator(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "operator";
			this.path = "bot.guild.access.remove.operator";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("remove", lu.getText("bot.guild.access.remove.help"));
			this.accessLevel = CmdAccessLevel.OWNER;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			User user = event.optUser("user");
			if (user == null) {
				editError(event, "bot.guild.access.remove.no_user");
				return;
			}

			String userId = user.getId();
			String guildId = event.getGuild().getId();

			if (!bot.getDBUtil().access.isOperator(guildId, userId)) {
				editError(event, "bot.guild.access.remove.operator.not_operator");
				return;
			}
			bot.getDBUtil().access.removeUser(guildId, userId);
			EmbedBuilder embed = bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, "bot.guild.access.remove.operator.done").replace("{user}", user.getAsMention()))
				.setColor(Constants.COLOR_SUCCESS);
			editHookEmbed(event, embed.build());
		}

	}

}

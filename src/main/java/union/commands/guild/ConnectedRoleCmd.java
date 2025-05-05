package union.commands.guild;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.helper.Helper;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import java.sql.SQLException;
import java.util.List;

public class ConnectedRoleCmd extends CommandBase {

	public ConnectedRoleCmd() {
		this.name = "connected";
		this.path = "bot.guild.connected";
		this.children = new SlashCommand[]{new AddRole(), new RemoveRole(), new View()};
		this.category = CmdCategory.GUILD;
		this.module = CmdModule.ROLES;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
	}

	private class AddRole extends SlashCommand {
		public AddRole() {
			this.name = "add";
			this.path = "bot.guild.connected.add";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path + ".role.help"), true),
				new OptionData(OptionType.STRING, "group_role", lu.getText(path + ".group_role.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			if (Helper.getInstance() == null) {
				editError(event, "bot.guild.connected.helper_off");
				return;
			}

			Role mainRole = event.optRole("role");

			long groupRoleId;
			try {
				groupRoleId = event.optLong("group_role");
			} catch (NumberFormatException e) {
				editErrorOther(event, e.getMessage());
				return;
			}
			Role groupRole = Helper.getInstance().getJDA().getRoleById(groupRoleId);
			if (groupRole == null) {
				editError(event, path+".role_not_found", "Role ID: "+groupRoleId);
				return;
			}

			if (bot.getDBUtil().connectedRoles.isConnected(groupRoleId, mainRole.getIdLong())) {
				editError(event, path+".already", "Role %s and *%s*(`%s`)".formatted(mainRole.getAsMention(), groupRole.getName(), groupRoleId));
				return;
			}

			try {
				bot.getDBUtil().connectedRoles.addRole(groupRoleId, groupRole.getGuild().getIdLong(), mainRole.getIdLong(), event.getGuild().getIdLong());
			} catch (SQLException e) {
				editErrorDatabase(event, e, "connected role add");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done")
					.formatted(mainRole.getAsMention(), groupRole.getName(), groupRoleId, groupRole.getGuild().getName()))
				.build());
		}
	}

	private class RemoveRole extends SlashCommand {
		public RemoveRole() {
			this.name = "remove";
			this.path = "bot.guild.connected.remove";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path + ".role.help")),
				new OptionData(OptionType.STRING, "group_role", lu.getText(path + ".group_role.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			if (Helper.getInstance() == null) {
				editError(event, "bot.guild.connected.helper_off");
				return;
			}

			if (event.hasOption("role")) {
				Role role = event.optRole("role");

				try {
					bot.getDBUtil().connectedRoles.removeRole(role.getIdLong());
				} catch (SQLException e) {
					editErrorDatabase(event, e, "connected role remove");
					return;
				}

				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_main").formatted(role.getAsMention()))
					.build());
			} else if (event.hasOption("group_role")) {
				long roleId;
				try {
					roleId = event.optLong("group_role");
				} catch (NumberFormatException e) {
					editErrorOther(event, e.getMessage());
					return;
				}

				try {
					bot.getDBUtil().connectedRoles.removeRole(roleId);
				} catch (SQLException e) {
					editErrorDatabase(event, e, "connected role remove");
					return;
				}

				Role role = Helper.getInstance().getJDA().getRoleById(roleId);
				if (role != null) {
					editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".done_group").formatted("*%s* (`%d`)".formatted(role.getName(), roleId)))
						.build());
				} else {
					editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".done_group").formatted("*unknown* (`%d`)".formatted(roleId)))
						.build());
				}
			} else {
				editError(event, path+".no_option");
			}
		}
	}

	private class View extends SlashCommand {
		public View() {
			this.name = "view";
			this.path = "bot.guild.connected.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			if (Helper.getInstance() == null) {
				editError(event, "bot.guild.connected.helper_off");
				return;
			}

			var roles = bot.getDBUtil().connectedRoles.getAllRoles(event.getGuild().getIdLong());
			if (roles.isEmpty()) {
				editEmbed(event, bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getText(event, path+".empty"))
					.build());
				return;
			}

			StringBuilder sb = new StringBuilder();
			roles.forEach((key, value) -> {
				long mainRoleId = key;
				Role mainRole = event.getGuild().getRoleById(mainRoleId);
				if (mainRole == null) {
					ignoreExc(() -> bot.getDBUtil().connectedRoles.removeRole(mainRoleId));
					return;
				}

				StringBuilder buffer = new StringBuilder();
				for (Long roleId : value) {
					Role role = Helper.getInstance().getJDA().getRoleById(roleId);
					if (role == null) ignoreExc(() -> bot.getDBUtil().connectedRoles.removeRole(role.getIdLong()));
					else buffer.append("> ")
						.append(role.getName())
						.append(" | `")
						.append(role.getGuild().getName())
						.append("`\n");
				}
				if (buffer.isEmpty()) return;

				sb.append("### ")
					.append(mainRole.getAsMention())
					.append(":\n")
					.append(buffer)
					.append("\n");
			});

			if (sb.isEmpty()) {
				editEmbed(event, bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getText(event, path+".empty"))
					.build());
			} else {
				editEmbed(event, bot.getEmbedUtil().getEmbed()
					.setTitle(lu.getText(event, path+".title"))
					.setDescription(sb.toString())
					.build());
			}
		}
	}
}
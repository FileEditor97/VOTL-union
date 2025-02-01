package union.commands.ticketing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.RoleType;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.database.managers.RoleManager;
import union.utils.invite.InviteImpl;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class RolesSetupCmd extends CommandBase {
	
	public RolesSetupCmd() {
		this.name = "rolesetup";
		this.path = "bot.ticketing.rolesetup";
		this.children = new SlashCommand[]{new Add(), new Update(), new Remove(), new View()};
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Add extends SlashCommand {
		public Add() {
			this.name = "add";
			this.path = "bot.ticketing.rolesetup.add";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.STRING, "type", lu.getText(path+".type.help"), true)
					.addChoices(List.of(
						new Choice(lu.getText(RoleType.ASSIGN.getPath()), RoleType.ASSIGN.toString()),
						new Choice(lu.getText(RoleType.ASSIGN_TEMP.getPath()), RoleType.ASSIGN_TEMP.toString()),
						new Choice(lu.getText(RoleType.TOGGLE.getPath()), RoleType.TOGGLE.toString()),
						new Choice(lu.getText(RoleType.CUSTOM.getPath()), RoleType.CUSTOM.toString())
					)),
				new OptionData(OptionType.STRING, "description", lu.getText(path+".description.help"), false)
					.setMaxLength(80),
				new OptionData(OptionType.INTEGER, "row", lu.getText(path+".row.help"), false)
					.addChoices(List.of(
						new Choice("1", 1),
						new Choice("2", 2),
						new Choice("3", 3)
					)),
				new OptionData(OptionType.STRING, "invite", lu.getText(path+".invite.help"), false)
					.setMaxLength(40)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			long guildId = event.getGuild().getIdLong();
			
			Role role = event.optRole("role");
			if (role == null || role.hasPermission(Permission.ADMINISTRATOR, Permission.MANAGE_ROLES, Permission.MANAGE_SERVER)) {
				editError(event, path+".no_role");
				return;
			}
			if (!event.getGuild().getSelfMember().canInteract(role)) {
				editError(event, path+".cant_interact");
				return;
			}
			if (bot.getDBUtil().role.existsRole(role.getIdLong())) {
				editError(event, path+".exists");
				return;
			}

			RoleType type = RoleType.byName(event.optString("type"));
			switch (type) {
				case ASSIGN, ASSIGN_TEMP -> {
					int row = event.optInteger("row", 0);
					if (row == 0) {
						for (int i = 1; i <= 3; i++) {
							if (bot.getDBUtil().role.getRowSize(guildId, i) < 25) {
								row = i;
								break;
							}
						}
						if (row == 0) {
							editError(event, path+".rows_max");
							return;
						}
					} else {
						if (bot.getDBUtil().role.getRowSize(guildId, row) >= 25) {
							editError(event, path+".row_max", "Row: %s".formatted(row));
							return;
						}
					}
					String link = event.optString("invite", "").replaceFirst("(https://)?(discord)?(\\.?gg/)?", "").trim();
					if (!link.isBlank()) {
						final int rowTemp = row;
						InviteImpl.resolve(bot.JDA, link, false).queue(invite -> {
								if (!invite.isFromGuild() || invite.isTemporal()) {
									editError(event, path+".invalid_invite", "Not server type invite");
									return;
								}
								if (!bot.getDBUtil().role.add(guildId, role.getIdLong(), event.optString("description", "NULL"), rowTemp, type, invite.getUrl())) {
									editErrorUnknown(event, "Database error.");
									return;
								}
								sendSuccess(event, type, role);
							},
							failure -> editError(event, path+".invalid_invite", "Link `%s`\n%s".formatted(link, failure.toString())));
					} else {
						if (!bot.getDBUtil().role.add(guildId, role.getIdLong(), event.optString("description", "NULL"), row, type, "NULL")) {
							editErrorUnknown(event, "Database error.");
							return;
						}
						sendSuccess(event, type, role);
					}
				}
				case TOGGLE -> {
					if (bot.getDBUtil().role.getToggleable(guildId).size() >= 5) {
						editError(event, path+".toggle_max");
						return;
					}
					String description = event.optString("description", role.getName());
					if (!bot.getDBUtil().role.add(guildId, role.getIdLong(), description, null, RoleType.TOGGLE, "NULL")) {
						editErrorUnknown(event, "Database error.");
						return;
					}
					sendSuccess(event, type, role);
				}
				case CUSTOM -> {
					if (bot.getDBUtil().role.getCustom(guildId).size() >= 25) {
						editError(event, path+".custom_max");
						return;
					}
					if (!bot.getDBUtil().role.add(guildId, role.getIdLong(), event.optString("description", "NULL"), null, RoleType.CUSTOM, "NULL")) {
						editErrorUnknown(event, "Database error.");
						return;
					}
					sendSuccess(event, type, role);
				}
				default -> editError(event, path+".no_type");
			}
		}

		private void sendSuccess(SlashCommandEvent event, RoleType type, Role role) {
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{role}", role.getAsMention()).replace("{type}", lu.getText(event, type.getPath())))
				.build());
		}
	}

	private class Update extends SlashCommand {
		public Update() {
			this.name = "update";
			this.path = "bot.ticketing.rolesetup.update";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.STRING, "description", lu.getText(path+".description.help"))
					.setMaxLength(80),
				new OptionData(OptionType.INTEGER, "row", lu.getText(path+".row.help"))
					.addChoices(List.of(
						new Choice("1", 1),
						new Choice("2", 2),
						new Choice("3", 3)
					)),
				new OptionData(OptionType.STRING, "invite", lu.getText(path+".invite.help"))
					.setMaxLength(40)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Role role = event.optRole("role");
			if (role == null) {
				editError(event, path+".no_role");
				return;
			}
			if (!bot.getDBUtil().role.existsRole(role.getIdLong())) {
				editError(event, path+".not_exists");
				return;
			}

			StringBuffer response = new StringBuffer();

			if (event.hasOption("description")) {
				String description = event.optString("description");
				if (description.equalsIgnoreCase("null")) description = null;

				if (bot.getDBUtil().role.isToggleable(role.getIdLong())) {
					if (description == null) {
						description = role.getName();
						response.append(lu.getText(event, path+".default_description"));
					} else {
						response.append(lu.getText(event, path+".changed_description").replace("{text}", description));
					}
				} else {
					if (description == null) {
						description = "NULL";
						response.append(lu.getText(event, path+".default_description"));
					} else {
						response.append(lu.getText(event, path+".changed_description").replace("{text}", description));
					}
				}
				if (!bot.getDBUtil().role.setDescription(role.getIdLong(), description)) {
					editErrorUnknown(event, "Database error.");
					return;
				}
			}

			if (event.hasOption("row")) {
				Integer row = event.optInteger("row");
				if (!bot.getDBUtil().role.setRow(role.getIdLong(), row)) {
					editErrorUnknown(event, "Database error.");
					return;
				}
				response.append(lu.getText(event, path+".changed_row").replace("{row}", row.toString()));
			}

			if (event.hasOption("invite")) {
				String link = event.optString("invite").replaceFirst("(https://)?(discord)?(\\.?gg/)?", "").trim();
				if (link.equalsIgnoreCase("null")) {
					if (!bot.getDBUtil().role.setInvite(role.getIdLong(), "NULL")) {
						editErrorUnknown(event, "Database error.");
						return;
					}
					response.append(lu.getText(event, path+".default_invite"));
					sendReply(event, response, role);
				} else {
					InviteImpl.resolve(bot.JDA, link, false).queue(invite -> {
						if (invite.isFromGuild() && invite.isTemporal()) {
							response.append(lu.getText(event, path+".invalid_invite"));
						} else {
							if (!bot.getDBUtil().role.setInvite(role.getIdLong(), invite.getUrl())) {
								editErrorUnknown(event, "Database error.");
								return;
							}
							response.append(lu.getText(event, path+".changed_invite").replace("{link}", invite.getUrl()));
						}
						sendReply(event, response, role);
					}, failure -> {
						response.append(lu.getText(event, path+".invalid_invite"));
						sendReply(event, response, role);
					});
				}
			} else {
				sendReply(event, response, role);
			}
		}

		private void sendReply(SlashCommandEvent event, StringBuffer response, Role role) {
			if (response.isEmpty()) {
				editError(event, path+".no_options");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".embed_title").replace("{role}", role.getAsMention()))
				.appendDescription(response.toString())
				.build());
		}
	}

	private class Remove extends SlashCommand {
		public Remove() {
			this.name = "remove";
			this.path = "bot.ticketing.rolesetup.remove";
			this.options = List.of(
				new OptionData(OptionType.STRING, "id", lu.getText(path+".id.help"), true)
					.setMaxLength(30)
			);
		}

		Pattern rolePattern = Pattern.compile("^<@&(\\d+)>$");

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			String input = event.optString("id").trim();

			Matcher matcher = rolePattern.matcher(input);
			long roleId = Long.parseLong(matcher.find() ? matcher.group(1) : input);

			if (!bot.getDBUtil().role.existsRole(roleId)) {
				editError(event, path+".no_role");
				return;
			}
			if (!bot.getDBUtil().role.remove(roleId)) {
				editErrorUnknown(event, "Database error.");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{id}", String.valueOf(roleId)))
				.build());
		}
	}

	private class View extends SlashCommand {
		public View() {
			this.name = "view";
			this.path = "bot.ticketing.rolesetup.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Guild guild = event.getGuild();
			long guildId = guild.getIdLong();
			EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".title"));

			for (RoleType type : RoleType.values()) {
				switch (type) {
					case ASSIGN -> {
						for (int row = 1; row <= 3; row++) {
							List<RoleManager.RoleData> roles = bot.getDBUtil().role.getAssignableByRow(guildId, row);
							String title = "%s-%s | %s".formatted(lu.getText(event, type.getPath()), row, bot.getDBUtil().getTicketSettings(guild).getRowText(row));
							if (roles.isEmpty()) {
								builder.addField(title, lu.getText(event, path + ".none"), false);
							} else {
								generateField(guild, title, roles).forEach(builder::addField);
							}
						}
					}
					case TOGGLE, CUSTOM -> {
						List<RoleManager.RoleData> roles = bot.getDBUtil().role.getRolesByType(guildId, type);
						String title = lu.getText(event, type.getPath());
						if (roles.isEmpty()) {
							builder.addField(title, lu.getText(event, path+".none"), false);
						} else {
							generateField(guild, title, roles).forEach(builder::addField);
						}
					}
					default -> {} // ignore other
				}
			}

			event.getHook().editOriginalEmbeds(builder.build()).queue();
		}

		private List<Field> generateField(final Guild guild, final String title, final List<RoleManager.RoleData> roles) {
			List<Field> fields = new ArrayList<>();
			StringBuffer buffer = new StringBuffer();
			roles.forEach(data -> {
				Role role = guild.getRoleById(data.getIdLong());
				if (role == null) {
					bot.getDBUtil().role.remove(data.getIdLong());
					return;
				}
				String withLink = Optional.ofNullable(data.getDiscordInvite()).map(l -> "[`%s`](%s)".formatted(data.getId(), l)).orElse("`%s`".formatted(data.getId()));
				buffer.append("%s%s %s | %s\n".formatted(data.isTemp()?"⏰ ":"", role.getAsMention(), withLink, data.getDescription("")));
				if (buffer.length() > 900) {
					fields.add(new Field((fields.isEmpty() ? title : ""), buffer.toString(), false));
					buffer.setLength(0);
				}
			});
			if (!buffer.isEmpty()) {
				fields.add(new Field((fields.isEmpty() ? title : ""), buffer.toString(), false));
			}
			return fields;
		}
	}

}

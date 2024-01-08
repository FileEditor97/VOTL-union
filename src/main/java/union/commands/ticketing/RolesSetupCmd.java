package union.commands.ticketing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import union.App;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.RoleType;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
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
	
	public RolesSetupCmd(App bot) {
		super(bot);
		this.name = "rolesetup";
		this.path = "bot.ticketing.rolesetup";
		this.children = new SlashCommand[]{new Add(bot), new Update(bot), new Remove(bot), new View(bot)};
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Add extends SlashCommand {

		public Add(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "add";
			this.path = "bot.ticketing.rolesetup.add";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.STRING, "type", lu.getText(path+".type.help"), true)
					.addChoices(List.of(
						new Choice(lu.getText(RoleType.ASSIGN.getPath()), RoleType.ASSIGN.toString()),
						new Choice(lu.getText(RoleType.TOGGLE.getPath()), RoleType.TOGGLE.toString()),
						new Choice(lu.getText(RoleType.CUSTOM.getPath()), RoleType.CUSTOM.toString())
					)),
				new OptionData(OptionType.STRING, "description", lu.getText(path+".description.help"), false)
					.setMaxLength(100),
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
			String guildId = event.getGuild().getId();
			Role role = event.optRole("role");
			if (role == null || role.hasPermission(Permission.ADMINISTRATOR, Permission.MANAGE_ROLES, Permission.MANAGE_SERVER)) {
				createError(event, path+".no_role");
				return;
			}
			if (!event.getGuild().getSelfMember().canInteract(role)) {
				createError(event, path+".cant_interact");
				return;
			}
			if (bot.getDBUtil().role.existsRole(role.getId())) {
				createError(event, path+".exists");
				return;
			}
			event.deferReply(true).queue();
			String type = event.optString("type");
			if (type.equals(RoleType.ASSIGN.toString())) {
				Integer row = event.optInteger("row", 0);
				if (row == 0) {
					for (Integer i = 1; i <= 3; i++) {
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
				String link = event.optString("invite", "").replaceFirst("(https:\\/\\/)?(discord)?(\\.?gg\\/)?", "").trim();
				if (!link.isBlank()) {
					final Integer frow = row;
					InviteImpl.resolve(bot.JDA, link, false).queue(invite -> {
						if (invite.isFromGuild() && invite.isExpirable()) {
							editError(event, path+".invalid_invite", "Not server type invite");
							return;
						}
						bot.getDBUtil().role.add(guildId, role.getId(), event.optString("description", "NULL"), frow, RoleType.ASSIGN, invite.getUrl());
						sendSuccess(event, type, role);
					}, failure -> {
						editError(event, path+".invalid_invite", "Link `%s`\n%s".formatted(link, failure.toString()));
					});
				} else {
					bot.getDBUtil().role.add(guildId, role.getId(), event.optString("description", "NULL"), row, RoleType.ASSIGN, "NULL");
					sendSuccess(event, type, role);
				}
			} else if (type.equals(RoleType.TOGGLE.toString())) {
				if (bot.getDBUtil().role.getToggleable(guildId).size() >= 5) {
					editError(event, path+".toggle_max");
					return;
				}
				String description = event.optString("description", role.getName());
				description = description.substring(0, Math.min(description.length(), 80));
				bot.getDBUtil().role.add(guildId, role.getId(), description, null, RoleType.TOGGLE, "NULL");
				sendSuccess(event, type, role);
			} else if (type.equals(RoleType.CUSTOM.toString())) {
				if (bot.getDBUtil().role.getCustom(guildId).size() >= 25) {
					editError(event, path+".custom_max");
					return;
				}
				bot.getDBUtil().role.add(guildId, role.getId(), event.optString("description", "NULL"), null, RoleType.CUSTOM, "NULL");
				sendSuccess(event, type, role);
			} else {
				editError(event, path+".no_type");
			}
		}

		private void sendSuccess(SlashCommandEvent event, String type, Role role) {
			editHookEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{role}", role.getAsMention()).replace("{type}", type))
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}

	}

	private class Update extends SlashCommand {

		public Update(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "update";
			this.path = "bot.ticketing.rolesetup.update";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.STRING, "description", lu.getText(path+".description.help"))
					.setMaxLength(100),
				new OptionData(OptionType.INTEGER, "row", lu.getText(path+".row.help"))
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
			Role role = event.optRole("role");
			if (role == null) {
				createError(event, path+".no_role");
				return;
			}
			if (!bot.getDBUtil().role.existsRole(role.getId())) {
				createError(event, path+".not_exists");
				return;
			}
			
			event.deferReply(true).queue();
			StringBuffer response = new StringBuffer();

			if (event.hasOption("description")) {
				String description = event.optString("description");
				if (description.toLowerCase().equals("null")) description = null;

				Integer oldType = bot.getDBUtil().role.getType(role.getId());
				if (oldType.equals(RoleType.TOGGLE.getType())) {
					if (description == null) {
						description = role.getName();
						response.append(lu.getText(event, path+".default_description"));
					} else {
						response.append(lu.getText(event, path+".changed_description").replace("{text}", description));
					}
					description = description.substring(0, Math.min(description.length(), 80));
				} else {
					if (description == null) {
						description = "NULL";
						response.append(lu.getText(event, path+".default_description"));
					} else {
						response.append(lu.getText(event, path+".changed_description").replace("{text}", description));
					}
				}
				bot.getDBUtil().role.setDescription(role.getId(), description);
			}

			if (event.hasOption("row")) {
				Integer row = event.optInteger("row");
				bot.getDBUtil().role.setRow(role.getId(), row);
				response.append(lu.getText(event, path+".changed_row").replace("{row}", row.toString()));
			}

			if (event.hasOption("invite")) {
				String link = event.optString("invite").replaceFirst("(https:\\/\\/)?(discord)?(\\.?gg\\/)?", "").trim();
				if (link.toLowerCase().equals("null")) {
					bot.getDBUtil().role.setInvite(role.getId(), "NULL");
					response.append(lu.getText(event, path+".default_invite"));
					sendReply(event, response, role);
				} else {
					InviteImpl.resolve(bot.JDA, link, false).queue(invite -> {
						if (invite.isFromGuild() && invite.isExpirable()) {
							response.append(lu.getText(event, path+".invalid_invite"));
						} else {
							bot.getDBUtil().role.setInvite(role.getId(), invite.getUrl());
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
			editHookEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".embed_title").replace("{role}", role.getAsMention()))
				.appendDescription(response.toString())
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}

	}

	private class Remove extends SlashCommand {

		public Remove(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "remove";
			this.path = "bot.ticketing.rolesetup.remove";
			this.options = List.of(
				new OptionData(OptionType.STRING, "id", lu.getText(path+".id.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String roleId = event.optString("id", "0");
			if (!bot.getDBUtil().role.existsRole(roleId)) {
				createError(event, path+".no_role");
				return;
			}
			bot.getDBUtil().role.remove(roleId);
			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{id}", roleId))
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}
		
	}

	private class View extends SlashCommand {

		public View(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "view";
			this.path = "bot.ticketing.rolesetup.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Guild guild = event.getGuild();
			String guildId = guild.getId();
			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getText(event, path+".title"));
			
			for (RoleType type : RoleType.values()) {
				if (type.equals(RoleType.ASSIGN)) {
					for (Integer row = 1; row <= 3; row++) {
						List<Map<String, Object>> roles = bot.getDBUtil().role.getAssignableByRow(guildId, row);
						String title = "%s-%s | %s".formatted(lu.getText(event, type.getPath()), row, bot.getDBUtil().ticketSettings.getRowText(guildId, row));
						if (roles.isEmpty()) {
							builder.addField(title, lu.getText(event, path+".none"), false);
						} else {
							generateField(guild, title, roles).forEach(field -> builder.addField(field));
						}
					}
				} else {
					List<Map<String, Object>> roles = bot.getDBUtil().role.getRolesByType(guildId, type);
					String title = lu.getText(event, type.getPath());
					if (roles.isEmpty()) {
						builder.addField(title, lu.getText(event, path+".none"), false);
					} else {
						generateField(guild, title, roles).forEach(field -> builder.addField(field));
					}
				}
			}

			event.getHook().editOriginalEmbeds(builder.build()).queue();
		}

	}
	
	private List<Field> generateField(final Guild guild, final String title, final List<Map<String, Object>> roles) {
		List<Field> fields = new ArrayList<Field>();
		StringBuffer buffer = new StringBuffer();
		roles.forEach(data -> {
			String roleId = data.get("roleId").toString();
			Role role = guild.getRoleById(roleId);
			if (role == null) {
				bot.getDBUtil().role.remove(roleId);
				return;
			}
			String withLink = Optional.ofNullable(data.get("discordInvite")).map(l -> "[`%s`](%s)".formatted(roleId, l)).orElse("`%s`".formatted(roleId));
			buffer.append(String.format("%s %s | %s\n", role.getAsMention(), withLink, data.get("description")));
			if (buffer.length() > 900) {
				fields.add(new Field((fields.isEmpty() ? title : ""), buffer.toString(), false));
				buffer.setLength(0);
			}
		});
		if (buffer.length() != 0) {
			fields.add(new Field((fields.isEmpty() ? title : ""), buffer.toString(), false));
		}
		return fields;
	}

}

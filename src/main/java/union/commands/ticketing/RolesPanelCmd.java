package union.commands.ticketing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import union.App;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.RoleType;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.message.MessageUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class RolesPanelCmd extends CommandBase {
	
	public RolesPanelCmd(App bot) {
		super(bot);
		this.name = "rolepanel";
		this.path = "bot.ticketing.rolespanel";
		this.children = new SlashCommand[]{new Create(bot), new Update(bot), new RowText(bot), new OtherRole(bot)};
		this.botPermissions = new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS};
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Create extends SlashCommand {

		public Create(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "create";
			this.path = "bot.ticketing.rolespanel.create";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			String guildId = guild.getId();
			TextChannel channel =  (TextChannel) event.optGuildChannel("channel");
			if (channel == null) {
				createError(event, path+".no_channel", "Received: No channel");
				return;
			}

			Integer assignRolesSize = bot.getDBUtil().role.countRoles(guildId, RoleType.ASSIGN);
			List<Map<String, Object>> toggleRoles = bot.getDBUtil().role.getToggleable(guildId);
			if (assignRolesSize == 0 && toggleRoles.isEmpty()) {
				createError(event, path+".empty_roles");
				return;
			}
			List<ActionRow> actionRows = new ArrayList<ActionRow>();

			if (assignRolesSize > 0) {
				actionRows.add(ActionRow.of(Button.success("role:start_request", lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.button_request"))));
			}
			actionRows.add(ActionRow.of(Button.danger("role:remove", lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.button_remove"))));
			if (!toggleRoles.isEmpty()) {
				List<Button> buttons = new ArrayList<Button>();
				toggleRoles.forEach(data -> {
					if (buttons.size() >= 5) return;
					String roleId = data.get("roleId").toString();
					Role role = guild.getRoleById(roleId);
					if (role == null) return;
					String description = data.get("description").toString();
					buttons.add(Button.primary("role:toggle:"+roleId, MessageUtil.limitString(description, 80)));
				});
				actionRows.add(ActionRow.of(buttons));
			}
			if (bot.getDBUtil().role.getRolesWithInvites(guildId).size() > 0) {
				actionRows.add(ActionRow.of(Button.secondary("invites", lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.button_invites"))));
			}

			MessageEmbed embed = new EmbedBuilder()
				.setColor(bot.getDBUtil().getGuildSettings(guild).getColor())
				.setTitle(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.role_title"))
				.setDescription(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.role_value"))
				.setFooter(guild.getName(), guild.getIconUrl())
				.build();

			channel.sendMessageEmbeds(embed).addComponents(actionRows).queue();

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{channel}", channel.getAsMention()))
				.build()
			);
		}

	}

	private class Update extends SlashCommand {

		public Update(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "update";
			this.path = "bot.ticketing.rolespanel.update";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			String guildId = guild.getId();
			GuildChannel channel = event.optGuildChannel("channel");
			if (channel == null) {
				createError(event, path+".no_channel", "Received: No channel");
				return;
			}
			TextChannel tc = (TextChannel) channel;

			String latestId = tc.getLatestMessageId();
			tc.retrieveMessageById(latestId).queue(msg -> {
				if (!msg.getAuthor().equals(event.getJDA().getSelfUser())) {
					createError(event, path+".not_found", "Not bot's message");
					return;
				}

				Integer assignRolesSize = bot.getDBUtil().role.countRoles(guildId, RoleType.ASSIGN);
				List<Map<String, Object>> toggleRoles = bot.getDBUtil().role.getToggleable(guildId);
				if (assignRolesSize == 0 && toggleRoles.isEmpty()) {
					createError(event, path+".empty_roles");
					return;
				}
				List<ActionRow> actionRows = new ArrayList<ActionRow>();

				if (assignRolesSize > 0) {
					actionRows.add(ActionRow.of(Button.success("role:start_request", lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.button_request"))));
				}
				actionRows.add(ActionRow.of(Button.danger("role:remove", lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.button_remove"))));
				if (!toggleRoles.isEmpty()) {
					List<Button> buttons = new ArrayList<Button>();
					toggleRoles.forEach(data -> {
						if (buttons.size() >= 5) return;
						String roleId = data.get("roleId").toString();
						Role role = guild.getRoleById(roleId);
						if (role == null) return;
						String description = data.get("description").toString();
						buttons.add(Button.primary("role:toggle:"+roleId, MessageUtil.limitString(description, 80)));
					});
					actionRows.add(ActionRow.of(buttons));
				}
				if (bot.getDBUtil().role.getRolesWithInvites(guildId).size() > 0) {
					actionRows.add(ActionRow.of(Button.secondary("invites", lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.button_invites"))));
				}
				
				msg.editMessageComponents(actionRows).queue();

				createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").replace("{channel}", tc.getAsMention()))
					.build()
				);
			}, failure -> {
				createError(event, path+".not_found", failure.getMessage());
			});
		}

	}

	private class RowText extends SlashCommand {

		public RowText(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "row";
			this.path = "bot.ticketing.rolespanel.row";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "row", lu.getText(path+".row.help"), true)
					.setRequiredRange(1, 3),
				new OptionData(OptionType.STRING, "text", lu.getText(path+".text.help"), true)
					.setMaxLength(150)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer row = event.optInteger("row");
			String text = event.optString("text");

			bot.getDBUtil().ticketSettings.setRowText(event.getGuild().getIdLong(), row, text);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{row}", row.toString()).replace("{text}", text))
				.build());
		}

	}

	private class OtherRole extends SlashCommand {

		public OtherRole(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "other";
			this.path = "bot.ticketing.rolespanel.other";
			this.options = List.of(
				new OptionData(OptionType.BOOLEAN, "enabled", lu.getText(path+".enabled.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			boolean enabled = event.optBoolean("enabled");

			bot.getDBUtil().ticketSettings.setOtherRole(event.getGuild().getIdLong(), enabled);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(String.valueOf(enabled)))
				.build());
		}

	}

}

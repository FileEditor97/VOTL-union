package union.commands.ticketing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import union.App;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.command.SlashCommand;
import union.objects.command.SlashCommandEvent;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

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
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

public class TicketPanelCmd extends CommandBase {
	
	public TicketPanelCmd(App bot) {
		super(bot);
		this.name = "tpanel";
		this.path = "bot.ticketing.tpanel";
		this.children = new SlashCommand[]{new Create(bot), new Update(bot)};
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
			this.path = "bot.ticketing.tpanel.create";
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

			List<Map<String, Object>> assignRoles = bot.getDBUtil().role.getAssignable(guildId);
			List<Map<String, Object>> toggleRoles = bot.getDBUtil().role.getToggleable(guildId);
			if (assignRoles.isEmpty() && toggleRoles.isEmpty()) {
				createError(event, path+".empty_roles");
				return;
			}
			List<ActionRow> actionRows = new ArrayList<ActionRow>();

			if (!assignRoles.isEmpty()) {
				List<SelectOption> options = new ArrayList<SelectOption>();
				assignRoles.forEach(data -> {
					if (options.size() >= 24) return;
					String roleId = data.get("roleId").toString();
					Role role = guild.getRoleById(roleId);
					if (role == null) return;
					String description = Objects.requireNonNullElse(data.get("description"), "").toString();
					options.add(SelectOption.of(role.getName(), roleId).withDescription(description));
				});
				options.add(SelectOption.of(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.other"), "other"));
				StringSelectMenu menu = StringSelectMenu.create("menu:role_add")
					.setPlaceholder(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.select"))
					.setMaxValues(options.size())
					.addOptions(options)
					.build();
				actionRows.add(ActionRow.of(menu));
			}
			actionRows.add(ActionRow.of(Button.danger("role_remove", lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.button_remove"))));
			if (!toggleRoles.isEmpty()) {
				List<Button> buttons = new ArrayList<Button>();
				toggleRoles.forEach(data -> {
					if (buttons.size() >= 5) return;
					String roleId = data.get("roleId").toString();
					Role role = guild.getRoleById(roleId);
					if (role == null) return;
					String description = data.get("description").toString();
					buttons.add(Button.primary("toggle:"+roleId, (description.length() > 100 ? description.substring(0, 100) : description)));
				});
				actionRows.add(ActionRow.of(buttons));
			}

			MessageEmbed embed = new EmbedBuilder()
				.setColor(bot.getDBUtil().guild.getColor(guildId))
				.setTitle(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.role_title"))
				.setDescription(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.role_value"))
				.setFooter(guild.getName(), guild.getIconUrl())
				.build();

			tc.sendMessageEmbeds(embed).addComponents(actionRows).queue();

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{channel}", tc.getAsMention()))
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}

	}

	private class Update extends SlashCommand {

		public Update(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "update";
			this.path = "bot.ticketing.tpanel.update";
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

				List<Map<String, Object>> assignRoles = bot.getDBUtil().role.getAssignable(guildId);
				List<Map<String, Object>> toggleRoles = bot.getDBUtil().role.getToggleable(guildId);
				if (assignRoles.isEmpty() && toggleRoles.isEmpty()) {
					createError(event, path+".empty_roles");
					return;
				}
				List<ActionRow> actionRows = new ArrayList<ActionRow>();

				if (!assignRoles.isEmpty()) {
					List<SelectOption> options = new ArrayList<SelectOption>();
					assignRoles.forEach(data -> {
						if (options.size() >= 24) return;
						String roleId = data.get("roleId").toString();
						Role role = guild.getRoleById(roleId);
						if (role == null) return;
						String description = Objects.requireNonNullElse(data.get("description"), "").toString();
						options.add(SelectOption.of(role.getName(), roleId).withDescription(description));
					});
					options.add(SelectOption.of(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.other"), "other"));
					StringSelectMenu menu = StringSelectMenu.create("menu:role_add")
						.setPlaceholder(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.select"))
						.setMaxValues(options.size())
						.addOptions(options)
						.build();
					actionRows.add(ActionRow.of(menu));
				}
				actionRows.add(ActionRow.of(Button.danger("role_remove", lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.button_remove"))));
				if (!toggleRoles.isEmpty()) {
					List<Button> buttons = new ArrayList<Button>();
					toggleRoles.forEach(data -> {
						if (buttons.size() >= 5) return;
						String roleId = data.get("roleId").toString();
						Role role = guild.getRoleById(roleId);
						if (role == null) return;
						String description = data.get("description").toString();
						buttons.add(Button.primary("toggle:"+roleId, (description.length() > 100 ? description.substring(0, 100) : description)));
					});
					actionRows.add(ActionRow.of(buttons));
				}
				
				msg.editMessageComponents(actionRows).queue();

				createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getText(event, path+".done").replace("{channel}", tc.getAsMention()))
					.setColor(Constants.COLOR_SUCCESS)
					.build());
			}, failure -> {
				createError(event, path+".not_found", failure.getMessage());
			});
		}

	}

}

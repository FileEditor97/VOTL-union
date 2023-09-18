package union.commands.ticketing;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.internal.interactions.component.ButtonImpl;

import union.App;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.command.SlashCommand;
import union.objects.command.SlashCommandEvent;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

public class TicketPanelCmd extends CommandBase {
	
	public TicketPanelCmd(App bot) {
		super(bot);
		this.name = "ticket";
		this.path = "bot.ticketing.ticket";
		this.children = new SlashCommand[]{new NewPanel(bot), new ModifyPanel(bot), new ViewPanel(bot), new SendPanel(bot), new DeletePanel(bot),
			new CreateTag(bot), new ModifyTag(bot), new ViewTag(bot), new DeleteTag(bot),
			new CloseTicket(bot), new RCloseTicket(bot), new ClaimTicket(bot), new UnclaimTicket(bot), new AddUserInTicket(bot), new RemoveUserInTicket(bot),
			new Automation(bot)};
		this.category = CmdCategory.TICKETING;
		this.accessLevel = CmdAccessLevel.MOD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}


	// Panel tools

	private class NewPanel extends SlashCommand {

		public NewPanel(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "new";
			this.path = "bot.ticketing.ticket.panels.new";
			this.options = List.of(
				new OptionData(OptionType.STRING, "embed_title", lu.getText(path+".embed_title.help"), true)
					.setMaxLength(256),
				new OptionData(OptionType.STRING, "embed_description", lu.getText(path+".embed_description.help"))
					.setMaxLength(2000),
				new OptionData(OptionType.STRING, "embed_image", lu.getText(path+".embed_image.help")),
				new OptionData(OptionType.STRING, "embed_footer", lu.getText(path+".embed_footer.help"))
					.setMaxLength(2048)
			);
			this.subcommandGroup = new SubcommandGroupData("panels", lu.getText("bot.ticketing.ticket.panels.help"));
			this.accessLevel = CmdAccessLevel.ADMIN;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			if (bot.getDBUtil().panels.countPanels(event.getGuild().getId()) >= 20) {
				createError(event, path+".max_panels", "Maximum panels by server: %d".formatted(20));
				return;
			}

			String title = event.optString("embed_title");
			String description = event.optString("embed_description");
			String image = event.optString("embed_image");
			String footer = event.optString("embed_footer");

			if (isInvalidURL(image)) {
				createError(event, path+".image_not_valid", "Received unvalid URL: `%s`".formatted(image));
				return;
			}

			bot.getDBUtil().panels.createPanel(event.getGuild().getId(), title, description, image, footer);
			Integer panelId = bot.getDBUtil().panels.getIncrement();

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{title}", title).replace("{id}", panelId.toString()))
				.build());
		}

	}

	private class ModifyPanel extends SlashCommand {

		public ModifyPanel(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "modify";
			this.path = "bot.ticketing.ticket.panels.modify";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "panel_id", lu.getText(path+".panel_id.help"), true, true).setMinValue(1),
				new OptionData(OptionType.STRING, "embed_title", lu.getText(path+".embed_title.help")),
				new OptionData(OptionType.STRING, "embed_description", lu.getText(path+".embed_description.help")),
				new OptionData(OptionType.STRING, "embed_image", lu.getText(path+".embed_image.help")),
				new OptionData(OptionType.STRING, "embed_footer", lu.getText(path+".embed_footer.help"))
			);
			this.subcommandGroup = new SubcommandGroupData("panels", lu.getText("bot.ticketing.ticket.panels.help"));
			this.accessLevel = CmdAccessLevel.ADMIN;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer panelId = event.optInteger("panel_id");
			String guildId = bot.getDBUtil().panels.getGuildId(panelId);
			if (guildId == null || !guildId.equals(event.getGuild().getId())) {
				createError(event, path+".not_found", "Received ID: %s".formatted(panelId));
				return;
			}

			String title = event.optString("embed_title");
			String description = event.optString("embed_description");
			String image = event.optString("embed_image");
			String footer = event.optString("embed_footer");

			if (isInvalidURL(image)) {
				createError(event, path+".image_not_valid", "Received unvalid URL: `%s`".formatted(image));
				return;
			}
			
			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event);
			if (title != null)			builder.addField(lu.getText(event, path+".changed_title"), title, true);
			if (description != null)	builder.addField(lu.getText(event, path+".changed_description"), description, true);
			if (image != null)			builder.addField(lu.getText(event, path+".changed_image"), image, true);
			if (footer != null)			builder.addField(lu.getText(event, path+".changed_footer"), footer, true);
			
			if (builder.getFields().isEmpty()) {
				createError(event, path+".no_options");
				return;
			} else {
				bot.getDBUtil().panels.updatePanel(panelId, title, description, image, footer);
				createReplyEmbed(event, builder.setColor(Constants.COLOR_SUCCESS)
					.setTitle(lu.getText(event, path+".done"))
					.build());
			}
		}

	}

	private class ViewPanel extends SlashCommand {

		public ViewPanel(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "view";
			this.path = "bot.ticketing.ticket.panels.view";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "panel_id", lu.getText(path+".panel_id.help"), true, true).setMinValue(1)
			);
			this.subcommandGroup = new SubcommandGroupData("panels", lu.getText("bot.ticketing.ticket.panels.help"));
			this.accessLevel = CmdAccessLevel.ADMIN;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer panelId = event.optInteger("panel_id");
			String guildId = bot.getDBUtil().panels.getGuildId(panelId);
			if (guildId == null || !guildId.equals(event.getGuild().getId())) {
				createError(event, path+".not_found", "Received ID: %s".formatted(panelId));
				return;
			}
			event.deferReply(true).queue();

			List<Button> buttons = buildTicketTags(panelId);
			if (buttons.isEmpty())
				event.getHook().editOriginalEmbeds(buildPanelEmbed(event.getGuild(), panelId)).queue();
			else
				event.getHook().editOriginalEmbeds(buildPanelEmbed(event.getGuild(), panelId)).setComponents(ActionRow.of(buttons).asDisabled()).queue();
		}

	}

	private class SendPanel extends SlashCommand {

		public SendPanel(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "send";
			this.path = "bot.ticketing.ticket.panels.send";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "panel_id", lu.getText(path+".panel_id.help"), true, true).setMinValue(1),
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true).setChannelTypes(ChannelType.TEXT)
			);
			this.subcommandGroup = new SubcommandGroupData("panels", lu.getText("bot.ticketing.ticket.panels.help"));
			this.accessLevel = CmdAccessLevel.ADMIN;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer panelId = event.optInteger("panel_id");
			String guildId = bot.getDBUtil().panels.getGuildId(panelId);
			if (guildId == null || !guildId.equals(event.getGuild().getId())) {
				createError(event, path+".not_found", "Received ID: %s".formatted(panelId));
				return;
			}
			TextChannel channel = (TextChannel) event.optGuildChannel("channel");
			if (!channel.canTalk()) {
				createError(event, path+".cant_send", "Channel: %s".formatted(channel.getAsMention()));
				return;
			}
			event.deferReply(true).queue();

			List<Button> buttons = buildTicketTags(panelId);
			if (buttons.isEmpty())
				channel.sendMessageEmbeds(buildPanelEmbed(event.getGuild(), panelId)).queue();
			else
				channel.sendMessageEmbeds(buildPanelEmbed(event.getGuild(), panelId)).setActionRow(buttons).queue();

			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{channel}", channel.getAsMention()))
				.setColor(Constants.COLOR_SUCCESS)
				.build()
			).queue();
		}

	}

	private class DeletePanel extends SlashCommand {

		public DeletePanel(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "delete";
			this.path = "bot.ticketing.ticket.panels.delete";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "panel_id", lu.getText(path+".panel_id.help"), true, true).setMinValue(1)
			);
			this.subcommandGroup = new SubcommandGroupData("panels", lu.getText("bot.ticketing.ticket.panels.help"));
			this.accessLevel = CmdAccessLevel.ADMIN;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer panelId = event.optInteger("panel_id");
			String guildId = bot.getDBUtil().panels.getGuildId(panelId);
			if (guildId == null || !guildId.equals(event.getGuild().getId())) {
				createError(event, path+".not_found", "Received ID: %s".formatted(panelId));
				return;
			}

			bot.getDBUtil().panels.delete(panelId);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").formatted(panelId))
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}

	}


	// Tag tools

	private class CreateTag extends SlashCommand {

		public CreateTag(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "create";
			this.path = "bot.ticketing.ticket.tags.create";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "panel_id", lu.getText(path+".panel_id.help"), true, true).setMinValue(1),
				new OptionData(OptionType.INTEGER, "tag_type", lu.getText(path+".tag_type.help"), true).addChoices(List.of(
					new Choice("Thread", 1),
					new Choice("Channel", 2)
				)),
				new OptionData(OptionType.STRING, "button_text", lu.getText(path+".button_text.help")).setMaxLength(80),
				new OptionData(OptionType.STRING, "emoji", lu.getText(path+".emoji.help")).setMaxLength(20),
				new OptionData(OptionType.CHANNEL, "location", lu.getText(path+".location.help")).setChannelTypes(ChannelType.CATEGORY),
				new OptionData(OptionType.STRING, "message", lu.getText(path+".message.help")).setMaxLength(2000),
				new OptionData(OptionType.STRING, "support_roles", lu.getText(path+".support_roles.help")),
				new OptionData(OptionType.STRING, "ticket_name", lu.getText(path+".ticket_name.help")).setMaxLength(60),
				new OptionData(OptionType.INTEGER, "button_style", lu.getText(path+".button_style.help")).addChoices(List.of(
					new Choice("Blue", ButtonStyle.PRIMARY.getKey()),
					new Choice("Gray (default)", ButtonStyle.SECONDARY.getKey()),
					new Choice("Green", ButtonStyle.SUCCESS.getKey()),
					new Choice("Red", ButtonStyle.DANGER.getKey())
				))
			);
			this.subcommandGroup = new SubcommandGroupData("tags", lu.getText("bot.ticketing.ticket.tags.help"));
			this.accessLevel = CmdAccessLevel.ADMIN;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer panelId = event.optInteger("panel_id");
			String guildId = bot.getDBUtil().panels.getGuildId(panelId);
			if (guildId == null || !guildId.equals(event.getGuild().getId())) {
				createError(event, path+".not_found", "Received ID: %s".formatted(panelId));
				return;
			}

			if (bot.getDBUtil().tags.countPanelTags(panelId) >= 5) {
				createError(event, path+".max_tags", "Maximum tags on 1 panel: %d".formatted(5));
				return;
			}
			event.deferReply(true).queue();

			Integer type = event.optInteger("tag_type", 1);
			String buttonName = event.optString("button_text", "Create ticket");
			String emoji = event.optString("emoji");
			String categoryId = Optional.ofNullable((Category) event.optGuildChannel("location")).map(Category::getId).orElse(null);
			String message = event.optString("message");
			String ticketName = event.optString("ticket_name", "ticket-");
			ButtonStyle buttonStyle = ButtonStyle.fromKey(event.optInteger("button_style", 1));

			List<Role> supportRoles = Optional.ofNullable(event.optMentions("support_roles")).map(Mentions::getRoles).orElse(Collections.emptyList());
			String supportRoleIds = null;
			if (supportRoles.size() > 0) {
				if (supportRoles.size() > 6) {
					editError(event, path+".too_many_roles", "Provided: %d".formatted(supportRoles.size()));
					return;
				}
				supportRoleIds = supportRoles.stream().map(Role::getId).collect(Collectors.joining(";"));
			}

			bot.getDBUtil().tags.createTag(guildId, panelId, type, buttonName, emoji, categoryId, message, supportRoleIds, ticketName, buttonStyle.getKey());
			Integer tagId = bot.getDBUtil().tags.getIncrement();

			editHookEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{tag}", tagId.toString()).replace("{panel}", panelId.toString()))
				.build());
		}

	}

	private class ModifyTag extends SlashCommand {

		public ModifyTag(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "modify";
			this.path = "bot.ticketing.ticket.tags.modify";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "tag_id", lu.getText(path+".tag_id.help"), true, true).setMinValue(1),
				new OptionData(OptionType.INTEGER, "tag_type", lu.getText(path+".tag_type.help")).addChoices(List.of(
					new Choice("Thread", 1),
					new Choice("Channel", 2)
				)),
				new OptionData(OptionType.STRING, "button_text", lu.getText(path+".button_text.help")).setMaxLength(80),
				new OptionData(OptionType.STRING, "emoji", lu.getText(path+".emoji.help")).setMaxLength(20),
				new OptionData(OptionType.CHANNEL, "location", lu.getText(path+".location.help")).setChannelTypes(ChannelType.CATEGORY),
				new OptionData(OptionType.STRING, "message", lu.getText(path+".message.help")).setMaxLength(2000),
				new OptionData(OptionType.STRING, "support_roles", lu.getText(path+".support_roles.help")),
				new OptionData(OptionType.STRING, "ticket_name", lu.getText(path+".ticket_name.help")).setMaxLength(60),
				new OptionData(OptionType.INTEGER, "button_style", lu.getText(path+".button_style.help")).addChoices(List.of(
					new Choice("Blue", ButtonStyle.PRIMARY.getKey()),
					new Choice("Gray (default)", ButtonStyle.SECONDARY.getKey()),
					new Choice("Green", ButtonStyle.SUCCESS.getKey()),
					new Choice("Red", ButtonStyle.DANGER.getKey())
				))
			);
			this.subcommandGroup = new SubcommandGroupData("tags", lu.getText("bot.ticketing.ticket.tags.help"));
			this.accessLevel = CmdAccessLevel.ADMIN;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer tagId = event.optInteger("tag_id");
			String guildId = bot.getDBUtil().tags.getGuildId(tagId);
			if (guildId == null || !guildId.equals(event.getGuild().getId())) {
				createError(event, path+".not_found", "Received ID: %s".formatted(tagId));
				return;
			}

			String buttonText = event.optString("button_text");
			String emoji = event.optString("emoji");
			ButtonStyle buttonStyle = ButtonStyle.fromKey(event.optInteger("button_style"));
			Integer type = event.optInteger("tag_type");
			String ticketName = event.optString("ticket_name");
			Category category = (Category) event.optGuildChannel("location");
			Mentions mentions = event.optMentions("support_roles");
			String message = event.optString("message");

			List<Role> supportRoles = mentions.getRoles();
			if (supportRoles.size() > 6) {
				createError(event, path+".too_many_roles", "Provided: %d".formatted(supportRoles.size()));
				return;
			}

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event);
			if (buttonText != null)		builder.addField(lu.getText(event, path+".changed_text"), buttonText, true);
			if (emoji != null)			builder.addField(lu.getText(event, path+".changed_emoji"), emoji, true);
			if (buttonStyle != null)	builder.addField(lu.getText(event, path+".changed_style"), buttonStyle.toString(), true);
			if (type != null)			builder.addField(lu.getText(event, path+".changed_type"), (type > 1 ? "Channel" : "Thread"), true);
			if (ticketName != null)		builder.addField(lu.getText(event, path+".changed_name"), ticketName, true);
			if (category != null)		builder.addField(lu.getText(event, path+".changed_location"), category.getAsMention(), true);
			if (!supportRoles.isEmpty())	builder.addField(lu.getText(event, path+".changed_roles"), ticketName, false);
			if (message != null)		builder.addField(lu.getText(event, path+".changed_message"), message, false);
			
			if (builder.getFields().isEmpty()) {
				createError(event, path+".no_options");
				return;
			} else {
				String supportRoleIds = supportRoles.stream().map(Role::getId).collect(Collectors.joining(";"));
				bot.getDBUtil().tags.updateTag(tagId, type, buttonText, emoji, category.getId(), message, supportRoleIds, ticketName, buttonStyle.getKey());
				createReplyEmbed(event, builder.setColor(Constants.COLOR_SUCCESS)
					.setTitle(lu.getText(event, path+".done"))
					.build());
			}
		}

	}

	private class ViewTag extends SlashCommand {

		public ViewTag(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "view";
			this.path = "bot.ticketing.ticket.tags.view";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "tag_id", lu.getText(path+".tag_id.help"), true, true).setMinValue(1)
			);
			this.subcommandGroup = new SubcommandGroupData("tags", lu.getText("bot.ticketing.ticket.tags.help"));
			this.accessLevel = CmdAccessLevel.ADMIN;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer tagId = event.optInteger("tag_id");
			String guildId = bot.getDBUtil().tags.getGuildId(tagId);
			if (guildId == null || !guildId.equals(event.getGuild().getId())) {
				createError(event, path+".not_found", "Received ID: %s".formatted(tagId));
				return;
			}

			Map<String, Object> tag = bot.getDBUtil().tags.getTag(tagId);
			if (tag == null) {
				createError(event, path+".not_found", "No record found");
				return;
			}
			event.deferReply(true).queue();

			String buttonText = (String) tag.get("buttonText");
			ButtonStyle style = ButtonStyle.fromKey((int) tag.get("buttonStyle"));
			Emoji emoji = Optional.ofNullable(tag.get("emoji")).map(data -> Emoji.fromFormatted((String) data)).orElse(null);
			
			Integer tagType = (Integer) tag.get("tagType");
			String ticketName = (String) tag.get("ticketName");

			String message = Optional.ofNullable((String) tag.get("message")).orElse(lu.getText(event, path+".none"));
			String category = Optional.ofNullable((String) tag.get("location")).map(id -> event.getGuild().getCategoryById(id).getAsMention()).orElse(lu.getText(event, path+".none"));
			
			String supportRoleIds = (String) tag.get("supportRoles");
			String roles = Optional.ofNullable(supportRoleIds).map(ids -> Stream.of(ids.split(";")).map(id -> event.getGuild().getRoleById(id).getAsMention()).collect(Collectors.joining(", "))).orElse(null); ;
			
			Button button = new ButtonImpl("tag_preview", buttonText, style, null, true, emoji);
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(event)
				.setTitle("Tag ID: %s".formatted(tagId))
				.addField(lu.getText(event, path+".type"), (tagType > 1 ? "Channel" : "Thread"), true)
				.addField(lu.getText(event, path+".name"), "`%s`".formatted(ticketName), true)
				.addField(lu.getText(event, path+".location"), category, true)
				.addField(lu.getText(event, path+".roles"), Optional.ofNullable(roles).orElse(lu.getText(event, path+".none")), false)
				.addField(lu.getText(event, path+".message"), message, false)
				.build()
			).setActionRow(button).queue();
		}

	}

	private class DeleteTag extends SlashCommand {

		public DeleteTag(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "delete";
			this.path = "bot.ticketing.ticket.tags.delete";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "tag_id", lu.getText(path+".tag_id.help"), true).setMinValue(1)
			);
			this.subcommandGroup = new SubcommandGroupData("tags", lu.getText("bot.ticketing.ticket.tags.help"));
			this.accessLevel = CmdAccessLevel.ADMIN;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer tagId = event.optInteger("tag_id");
			String guildId = bot.getDBUtil().tags.getGuildId(tagId);
			if (guildId == null || !guildId.equals(event.getGuild().getId())) {
				createError(event, path+".not_found", "Received ID: %s".formatted(tagId));
				return;
			}

			bot.getDBUtil().tags.delete(tagId);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").formatted(tagId))
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}

	}


	// Ticket commands

	private class CloseTicket extends SlashCommand {

		public CloseTicket(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "close";
			this.path = "bot.ticketing.ticket.close";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "reason", lu.getText(path+".reason.help"), true).setMinValue(1)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {

		}

	}

	private class RCloseTicket extends SlashCommand {

		public RCloseTicket(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "rclose";
			this.path = "bot.ticketing.ticket.rclose";
		}

		@Override
		protected void execute(SlashCommandEvent event) {

		}

	}

	private class ClaimTicket extends SlashCommand {

		public ClaimTicket(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "claim";
			this.path = "bot.ticketing.ticket.claim";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {

		}

	}

	private class UnclaimTicket extends SlashCommand {

		public UnclaimTicket(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "unclaim";
			this.path = "bot.ticketing.ticket.unclaim";
		}

		@Override
		protected void execute(SlashCommandEvent event) {

		}

	}

	private class AddUserInTicket extends SlashCommand {

		public AddUserInTicket(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "add";
			this.path = "bot.ticketing.ticket.add";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {

		}

	}

	private class RemoveUserInTicket extends SlashCommand {

		public RemoveUserInTicket(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "remove";
			this.path = "bot.ticketing.ticket.remove";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {

		}

	}

	private class Automation extends SlashCommand {

		public Automation(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "automation";
			this.path = "bot.ticketing.ticket.automation";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "autoclose", lu.getText(path+".autoclose.help"))
					.setRequiredRange(0, 72),
				new OptionData(OptionType.BOOLEAN, "author_left", lu.getText(path+".author_left.help"))
			);
			this.accessLevel = CmdAccessLevel.ADMIN;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String guildId = event.getGuild().getId();
			if (!bot.getDBUtil().ticketSettings.exists(guildId)) {
				bot.getDBUtil().ticketSettings.add(guildId);
			}
			
			StringBuffer response = new StringBuffer();
			if (event.hasOption("autoclose")) {
				Integer time = event.optInteger("autoclose");
				bot.getDBUtil().ticketSettings.setAutocloseTime(guildId, time);
				response.append(lu.getText(event, path+".changed_autoclose").replace("{time}", time.toString()));
			}
			if (event.hasOption("author_left")) {
				Boolean left = event.optBoolean("author_left");
				bot.getDBUtil().ticketSettings.setAutocloseLeft(guildId, left);
				response.append(lu.getText(event, path+".changed_left").replace("{left}", left ? Constants.SUCCESS : Constants.FAILURE));
			}
			
			if (response.isEmpty()) {
				createError(event, path+".no_options");
				return;
			}
			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".embed_title"))
				.appendDescription(response.toString())
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}

	}

	private boolean isInvalidURL(String urlString) {
		if (urlString == null) return false;
		try {
			URL url = new URL(urlString);
			url.toURI();
			return false;
		} catch (Exception e) {
			return true;
		}
	}

	private MessageEmbed buildPanelEmbed(Guild guild, Integer panelId) {
		Map<String, String> panel = bot.getDBUtil().panels.getPanel(panelId);
		EmbedBuilder builder = new EmbedBuilder().setColor(bot.getDBUtil().guild.getColor(guild.getId()));
		Optional.of(panel.get("title")).ifPresent(builder::setTitle);
		Optional.ofNullable(panel.get("description")).ifPresent(builder::setDescription);
		Optional.ofNullable(panel.get("image")).ifPresent(builder::setImage);
		Optional.ofNullable(panel.get("footer")).ifPresent(builder::setFooter);
		return builder.build();
	}

	private List<Button> buildTicketTags(Integer panelId) {
		List<Button> buttons = new ArrayList<Button>(5);
		bot.getDBUtil().tags.getPanelTags(panelId).forEach(tag -> {
			Integer tagId = (Integer) tag.get("tagId");
			String buttonText = (String) tag.get("buttonText");
			ButtonStyle style = ButtonStyle.fromKey((int) tag.get("buttonStyle"));
			Emoji emoji = Optional.ofNullable(tag.get("emoji")).map(data -> Emoji.fromFormatted((String) data)).orElse(null);
			buttons.add(new ButtonImpl("tag:"+tagId, buttonText, style, null, false, emoji));
		});
		return buttons;
	}
	
}

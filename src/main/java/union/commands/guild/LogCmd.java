package union.commands.guild;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import union.App;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.base.waiter.EventWaiter;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.LogChannels;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.exception.CheckException;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

public class LogCmd extends CommandBase {

	private static EventWaiter waiter;
	
	public LogCmd(App bot, EventWaiter waiter) {
		super(bot);
		this.name = "log";
		this.path = "bot.guild.log";
		this.children = new SlashCommand[]{new Setup(bot), new Manage(bot)};
		this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL, Permission.VIEW_AUDIT_LOGS, Permission.MESSAGE_SEND};
		this.category = CmdCategory.GUILD;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.ADMIN;
		LogCmd.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Setup extends SlashCommand {

		public Setup(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "setup";
			this.path = "bot.guild.log.setup";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			GuildChannel channel = event.optGuildChannel("channel");
			if (channel == null) {
				createError(event, path+".no_channel");
				return;
			}
			
			try {
				bot.getCheckUtil().hasPermissions(event, event.getGuild(), event.getMember(), true, channel,
					new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS});
			} catch (CheckException ex) {
				createReply(event, ex.getCreateData());
				return;
			}

			TextChannel tc = (TextChannel) channel;
			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS);
			
			bot.getDBUtil().guild.setupLogChannels(event.getGuild().getId(), channel.getId());
			tc.sendMessageEmbeds(builder.setDescription(lu.getLocalized(event.getGuildLocale(), path+".as_log")).build()).queue();
			createReplyEmbed(event, builder.setDescription(lu.getText(event, path+".done").replace("{channel}", tc.getAsMention())).build());
		}

	}

	private class Manage extends SlashCommand {

		public Manage(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "manage";
			this.path = "bot.guild.log.manage";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getText(event, path+".title"));

			Guild guild = event.getGuild();
			Map<LogChannels, String> logChannels = bot.getDBUtil().guild.getAllLogChannels(guild.getId());
			if (logChannels == null) { 
				builder.setDescription(lu.getText(event, path+".none"));
				editHookEmbed(event, builder.build());
				return;
			}

			String none = lu.getText(event, "bot.guild.log.types.none");
			logChannels.forEach((lc, id) -> {
				String text = Optional.ofNullable(id).map(guild::getTextChannelById).map(TextChannel::getAsMention).orElse(none);
				builder.appendDescription("%s - %s\n".formatted(lu.getText(event, lc.getPath()), text));
			});

			ActionRow buttons = ActionRow.of(
				Button.of(ButtonStyle.PRIMARY, "button:change", lu.getText(event, path+".button_change")),
				Button.of(ButtonStyle.DANGER, "button:remove", lu.getText(event, path+".button_remove"))
			);
			event.getHook().editOriginalEmbeds(builder.build()).setComponents(buttons).queue(msg -> {
				waiter.waitForEvent(
					ButtonInteractionEvent.class,
					e -> msg.getId().equals(e.getMessageId()) && (e.getComponentId().equals("button:change") || e.getComponentId().equals("button:remove")),
					buttonEvent -> buttonPressedAction(buttonEvent, event, msg.getId()),
					30,
					TimeUnit.SECONDS,
					() -> event.getHook().editOriginalComponents(buttons.asDisabled()).queue()
				);
			});
		}

		private void buttonPressedAction(ButtonInteractionEvent interaction, SlashCommandEvent event, String msgId) {
			interaction.deferEdit().queue();
			String guildId = event.getGuild().getId();
			String buttonPressed = interaction.getComponentId();

			if (buttonPressed.equals("button:remove")) {
				bot.getDBUtil().guild.setupLogChannels(guildId, "NULL");
				MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getText(event, path+".removed"))
					.build();
				event.getHook().editOriginalEmbeds(embed).setComponents().queue();
			} else {
				StringSelectMenu menu = StringSelectMenu.create("menu:log_type")
					.setPlaceholder(lu.getText(event, path+".menu_type"))
					.addOptions(
						Arrays.stream(LogChannels.values()).map(lc -> SelectOption.of(lu.getText(event, lc.getPath()), lc.getName())).toList()
					)
					.build();
					event.getHook().editOriginalComponents(ActionRow.of(menu)).queue();

				waiter.waitForEvent(
					StringSelectInteractionEvent.class,
					e -> msgId.equals(e.getMessageId()) && e.getComponentId().equals("menu:log_type"),
					selectEvent -> typeSelected(selectEvent, event, msgId),
					30,
					TimeUnit.SECONDS,
					() -> event.getHook().editOriginalComponents(ActionRow.of(
						menu.createCopy().setPlaceholder(lu.getText(event, "errors.timed_out")).setDisabled(true).build())
					).queue()
				);
			}
		}

		private void typeSelected(StringSelectInteractionEvent interaction, SlashCommandEvent event, String msgId) {
			interaction.deferEdit().queue();
			LogChannels logChannel = LogChannels.of(interaction.getValues().get(0));

			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".select_channel").replace("{type}", lu.getText(event, logChannel.getPath())))
				.build();
			
			EntitySelectMenu menu = EntitySelectMenu.create("menu:channel", SelectTarget.CHANNEL)
				.setChannelTypes(ChannelType.TEXT)
				.setPlaceholder(lu.getText(event, path+".menu_channel"))
				.setRequiredRange(1, 1)
				.build();
			event.getHook().editOriginalEmbeds(embed).setActionRow(menu).queue();
			
			waiter.waitForEvent(
				EntitySelectInteractionEvent.class,
				e -> msgId.equals(e.getMessageId()) && e.getComponentId().equals("menu:channel"),
				selectEvent -> channelSelected(selectEvent, event, msgId, logChannel),
				30,
				TimeUnit.SECONDS,
				() -> event.getHook().editOriginalComponents(ActionRow.of(
					menu.createCopy().setPlaceholder(lu.getText(event, "errors.timed_out")).setDisabled(true).build())
				).queue()
			);
		}

		private void channelSelected(EntitySelectInteractionEvent interaction, SlashCommandEvent event, String msgId, LogChannels logChannel) {
			interaction.deferEdit().queue();

			GuildChannel channel = null;
			try {
				channel = interaction.getMentions().getChannels().get(0);
			} catch (IllegalStateException ex) {}
			
			if (channel == null || !channel.getType().equals(ChannelType.TEXT)) {
				event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, path+".no_channel")).setComponents().queue();
				return;
			}
			try {
				bot.getCheckUtil().hasPermissions(event, event.getGuild(), event.getMember(), true, channel,
					new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS});
			} catch (CheckException ex) {
				event.getHook().editOriginal(ex.getEditData()).queue();
			}

			TextChannel newTc = (TextChannel) channel;
			bot.getDBUtil().guild.setLogChannel(logChannel, event.getGuild().getId(), newTc.getId());
			
			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS);
			
			newTc.sendMessageEmbeds(builder.setDescription(lu.getLocalized(event.getGuildLocale(), path+".as_log")
				.replace("{type}", lu.getLocalized(event.getGuildLocale(), logChannel.getPath()))).build()).queue();
			event.getHook().editOriginalEmbeds(builder.setDescription(lu.getText(event, path+".done")
				.replace("{channel}", newTc.getAsMention())).build()).setComponents().queue();
		}

	}

}

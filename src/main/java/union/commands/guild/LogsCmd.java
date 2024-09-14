package union.commands.guild;

import static union.utils.CastUtil.castLong;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.objects.logs.LogType;
import union.utils.database.managers.GuildLogsManager.LogSettings;
import union.utils.database.managers.GuildLogsManager.WebhookData;
import union.utils.exception.CheckException;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Icon.IconType;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

public class LogsCmd extends CommandBase {
	
	public LogsCmd() {
		this.name = "logs";
		this.path = "bot.guild.logs";
		this.children = new SlashCommand[]{new Enable(), new Disable(), new View(),
			new AddException(), new RemoveException(), new ViewException()};
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.category = CmdCategory.GUILD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Enable extends SlashCommand {
		public Enable() {
			this.name = "enable";
			this.path = "bot.guild.logs.manage.enable";
			this.options = List.of(
				new OptionData(OptionType.STRING, "type", lu.getText(path+".type.help"), true)
					.addChoices(LogType.asChoices(lu)),
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
			this.subcommandGroup = new SubcommandGroupData("manage", lu.getText("bot.guild.logs.manage.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			TextChannel channel = (TextChannel) event.optGuildChannel("channel");
			if (channel == null) {
				editError(event, path+".no_channel");
				return;
			}
			
			try {
				bot.getCheckUtil().hasPermissions(event, event.getGuild(), event.getMember(), true, channel,
					new Permission[]{Permission.VIEW_CHANNEL, Permission.MANAGE_WEBHOOKS});
			} catch (CheckException ex) {
				editHook(event, ex.getEditData());
				return;
			}

			LogType type = LogType.of(event.optString("type"));
			String text = lu.getText(event, type.getPathName());

			try {
				WebhookData oldData = bot.getDBUtil().logs.getLogWebhook(type, event.getGuild().getIdLong());
				if (oldData != null) {
					event.getJDA().retrieveWebhookById(oldData.getWebhookId())
							.queue(webhook -> webhook.delete(oldData.getToken()).reason("Log disabled").queue());
				}
				Icon icon = null;
				try {
					icon = Icon.from(new URL(Constants.AVATAR_URL).openStream(), IconType.PNG);
				} catch (Exception ignored) {}
				channel.createWebhook(lu.getText(type.getPathName())).setAvatar(icon).reason("By "+event.getUser().getName()).queue(webhook -> {
					// Add to DB
					WebhookData data = new WebhookData(channel.getIdLong(), webhook.getIdLong(), webhook.getToken());
					bot.getDBUtil().logs.setLogWebhook(type, event.getGuild().getIdLong(), data);
					// Reply
					webhook.sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getLocalized(event.getGuildLocale(), path+".as_log").formatted(text))
						.build()
					).queue();
					editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".done").formatted(channel.getAsMention(), text))
						.build()
					);
				});
			} catch (Exception ex) {
				editError(event, "errors.error", ex.getMessage());
			}	
		}
	}

	private class Disable extends SlashCommand {
		public Disable() {
			this.name = "disable";
			this.path = "bot.guild.logs.manage.disable";
			this.options = List.of(
				new OptionData(OptionType.STRING, "type", lu.getText(path+".type.help"), true)
					.addChoice("All logs", "all")
					.addChoices(LogType.asChoices(lu))
			);
			this.subcommandGroup = new SubcommandGroupData("manage", lu.getText("bot.guild.logs.manage.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			long guildId = event.getGuild().getIdLong();

			String input = event.optString("type");
			if (input.equals("all")) {
				// Delete all logging webhooks
				LogSettings settings = bot.getDBUtil().logs.getSettings(guildId);
				if (!settings.isEmpty()) {
					for (WebhookData data : settings.getWebhooks()) {
						event.getJDA().retrieveWebhookById(data.getWebhookId())
								.queue(webhook -> webhook.delete(data.getToken()).reason("Log disabled").queue());
					}
				}
				// Remove guild from db
				bot.getDBUtil().logs.removeGuild(guildId);
				// Reply
				editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_all"))
					.build()
				);
			} else {
				LogType type = LogType.of(input);
				WebhookData data = bot.getDBUtil().logs.getLogWebhook(type, guildId);
				if (data != null) {
					event.getJDA().retrieveWebhookById(data.getWebhookId())
							.queue(webhook -> webhook.delete(data.getToken()).reason("Log disabled").queue());
				}
				bot.getDBUtil().logs.removeLogWebhook(type, guildId);
				editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").formatted(lu.getText(event, type.getPathName())))
					.build()
				);
			}
		}
	}

	private class View extends SlashCommand {
		public View() {
			this.name = "view";
			this.path = "bot.guild.logs.manage.view";
			this.subcommandGroup = new SubcommandGroupData("manage", lu.getText("bot.guild.logs.manage.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Guild guild = event.getGuild();

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".title"));

			LogSettings settings = bot.getDBUtil().getLogSettings(guild);
			if (settings == null || settings.isEmpty()) {
				editHookEmbed(event, builder
					.setDescription(lu.getText(event, path+".none"))
					.build()
				);
				return;
			}

			settings.getChannels().forEach((type, channelId) -> {
				String text = Optional.ofNullable(channelId).map(guild::getTextChannelById).map(TextChannel::getAsMention).orElse(Constants.NONE);
				builder.appendDescription("%s - %s\n".formatted(lu.getText(event, type.getPathName()), text));
			});

			editHookEmbed(event, builder.build());
		}
	}

	private class AddException extends SlashCommand {
		public AddException() {
			this.name = "add";
			this.path = "bot.guild.logs.exceptions.add";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "target", lu.getText(path+".target.help"), true)
					.setChannelTypes(ChannelType.TEXT, ChannelType.CATEGORY)
			);
			this.subcommandGroup = new SubcommandGroupData("exceptions", lu.getText("bot.guild.logs.exceptions.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			GuildChannelUnion channelUnion = event.optChannel("target");
			long guildId = event.getGuild().getIdLong();
			switch (channelUnion.getType()) {
				case TEXT -> {
					if (bot.getDBUtil().logExceptions.isException(event.getGuild().getIdLong(), channelUnion.getIdLong())) {
						editError(event, path+".already", "Channel: "+channelUnion.getAsMention());
						return;
					}
				}
				case CATEGORY -> {
					if (bot.getDBUtil().logExceptions.isException(event.getGuild().getIdLong(), channelUnion.getIdLong())) {
						editError(event, path+".already", "Category: "+channelUnion.getName());
						return;
					}
				}
				default -> {
					editError(event, path+".not_found");
					return;
				}
			}
			bot.getDBUtil().logExceptions.addException(guildId, channelUnion.getIdLong());
			editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(channelUnion.getName()))
				.build()
			);

			
			
			
		}
	}

	private class RemoveException extends SlashCommand {
		public RemoveException() {
			this.name = "remove";
			this.path = "bot.guild.logs.exceptions.remove";
			this.options = List.of(
				new OptionData(OptionType.STRING, "id", lu.getText(path+".id.help"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("exceptions", lu.getText("bot.guild.logs.exceptions.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			Long targetId;
			try {
				targetId = castLong(event.optString("id"));
				if (targetId == null) throw new NumberFormatException("Value is empty or Null.");
			} catch (NumberFormatException ex) {
				editError(event, path+".not_found", ex.getMessage());
				return;
			}
			long guildId = event.getGuild().getIdLong();
			if (!bot.getDBUtil().logExceptions.isException(event.getGuild().getIdLong(), targetId)) {
				editError(event, path+".not_found", "Provided ID: "+targetId);
				return;
			}
			bot.getDBUtil().logExceptions.removeException(guildId, targetId);
			editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted("'"+targetId+"'"))
				.build()
			);
		}
	}

	private class ViewException extends SlashCommand {
		public ViewException() {
			this.name = "view";
			this.path = "bot.guild.logs.exceptions.view";
			this.subcommandGroup = new SubcommandGroupData("exceptions", lu.getText("bot.guild.logs.exceptions.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Set<Long> targets = bot.getDBUtil().logExceptions.getExceptions(event.getGuild().getIdLong());
			EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(path+".title"));
			if (targets.isEmpty()) {
				builder.setDescription(lu.getText(event, path+".none"));
			} else {
				targets.forEach(id -> builder.appendDescription("<#%s> (%<s)\n".formatted(id)));
			}
			editHookEmbed(event, builder.build());
		}
	}
}

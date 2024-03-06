package union.commands.guild;

import java.util.List;
import java.util.Optional;

import union.App;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.LogChannels;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.database.managers.GuildLogsManager.LogSettings;
import union.utils.database.managers.GuildLogsManager.WebhookData;
import union.utils.exception.CheckException;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class LogsCmd extends CommandBase {
	
	public LogsCmd(App bot) {
		super(bot);
		this.name = "logs";
		this.path = "bot.guild.logs";
		this.children = new SlashCommand[]{new Enable(bot), new Disable(bot), new View(bot)};
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.category = CmdCategory.GUILD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Enable extends SlashCommand {
		public Enable(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "enable";
			this.path = "bot.guild.logs.enable";
			this.options = List.of(
				new OptionData(OptionType.STRING, "type", lu.getText(path+".type.help"), true)
					.addChoices(LogChannels.asChoices(lu)),
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
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

			LogChannels type = LogChannels.of(event.optString("type"));
			String text = lu.getText(event, type.getPathName());

			try {
				channel.createWebhook(lu.getText(type.getPathName())).reason("By "+event.getUser().getName()).queue(webhook -> {
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
		public Disable(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "disable";
			this.path = "bot.guild.logs.disable";
			this.options = List.of(
				new OptionData(OptionType.STRING, "type", lu.getText(path+".type.help"), true)
					.addChoice("All logs", "all")
					.addChoices(LogChannels.asChoices(lu))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			String input = event.optString("type");
			if (input == "all") {
				bot.getDBUtil().logs.removeGuild(event.getGuild().getIdLong());
				editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_all"))
					.build()
				);
			} else {
				LogChannels type = LogChannels.of(input);
				bot.getDBUtil().logs.removeLogWebhook(type, event.getGuild().getIdLong());
				editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").formatted(lu.getText(event, type.getPathName())))
					.build()
				);
			}
		}
	}

	private class View extends SlashCommand {
		public View(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "view";
			this.path = "bot.guild.logs.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Guild guild = event.getGuild();

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".title"));

			LogSettings settings = bot.getDBUtil().logs.getSettings(guild.getIdLong());
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
}

package union.commands.guild;

import java.awt.Color;
import java.net.URL;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import union.App;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.Emotes;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class SetupCmd extends CommandBase {

	public SetupCmd(App bot) {
		super(bot);
		this.name = "setup";
		this.path = "bot.guild.setup";
		this.children = new SlashCommand[]{new Main(bot), new PanelColor(bot), new AppealLink(bot), new ReportChannel(bot), 
			new Voice(bot), new VoicePanel(bot), new VoiceName(bot), new VoiceLimit(bot)
		};
		this.category = CmdCategory.GUILD;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Main extends SlashCommand {
		
		public Main(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "main";
			this.path = "bot.guild.setup.main";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();

			if (bot.getDBUtil().guild.add(guildId)) {
				createReplyEmbed(event, 
					bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getText(event, path+".done"))
						.setColor(Constants.COLOR_SUCCESS)
						.build()
				);
				bot.getLogger().info(String.format("Added server through setup '%s' (%s) to DB.", guild.getName(), guildId));
			} else {
				createReplyEmbed(event, 
					bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getText(event, path+".exists"))
						.setColor(Constants.COLOR_WARNING)
						.build()
				);
			}
		}

	}

	private class PanelColor extends SlashCommand {

		public PanelColor(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "color";
			this.path = "bot.guild.setup.color";
			this.options = List.of(
				new OptionData(OptionType.STRING, "color", lu.getText(path+".color.help"), true).setRequiredLength(5, 11)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String guildId = event.getGuild().getId();
			String text = event.optString("color");

			Color color = bot.getMessageUtil().getColor(text);
			if (color == null) {
				createError(event, path+".no_color");
				return;
			}
			bot.getDBUtil().guild.setColor(guildId, color.getRGB() & 0xFFFFFF);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{color}", "#"+Integer.toHexString(color.getRGB() & 0xFFFFFF)))
				.setColor(color)
				.build());
		}

	}

	private class AppealLink extends SlashCommand {

		public AppealLink(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "appeal";
			this.path = "bot.guild.setup.appeal";
			this.options = List.of(
				new OptionData(OptionType.STRING, "link", lu.getText(path+".link.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String guildId = event.getGuild().getId();
			String text = event.optString("link");

			if (!isValidURL(text)) {
				createError(event, path+".not_valid", "Received unvalid URL: `%s`".formatted(text));
				return;
			}

			bot.getDBUtil().guild.setAppealLink(guildId, text);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{link}", text))
				.build());
		}

		private boolean isValidURL(String urlString) {
			try {
				URL url = new URL(urlString);
				url.toURI();
				return true;
			} catch (Exception e) {
				return false;
			}
		}

	}

	private class ReportChannel extends SlashCommand {

		public ReportChannel(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "report";
			this.path = "bot.guild.setup.report";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String guildId = event.getGuild().getId();
			MessageChannel channel = event.optMessageChannel("channel");

			if (!channel.canTalk()) {
				createError(event, path+".cant_send");
			}

			bot.getDBUtil().guild.setReportChannelId(guildId, channel.getId());

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{channel}", channel.getAsMention()))
				.build());
		}

	}

	private class Voice extends SlashCommand {

		public Voice(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "create";
			this.path = "bot.guild.setup.voice.create";
			this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_MOVE_OTHERS};
			this.subcommandGroup = new SubcommandGroupData("voice", lu.getText("bot.guild.setup.voice.help"));
			this.module = CmdModule.VOICE;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Guild guild = event.getGuild();
			String guildId = guild.getId();

			try {
				guild.createCategory(lu.getLocalized(event.getGuildLocale(), path+".category_name"))
					.addPermissionOverride(guild.getBotRole(), Arrays.asList(getBotPermissions()), null)
					.queue(
						category -> {
							try {
								category.createVoiceChannel(lu.getLocalized(event.getGuildLocale(), path+".channel_name"))
									.addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VOICE_SPEAK))
									.queue(
										channel -> {
											bot.getDBUtil().guildVoice.setup(guildId, category.getId(), channel.getId());
											bot.getLogger().info("Voice setup done in guild `"+guild.getName()+"'("+guildId+")");
											editHookEmbed(event, 
												bot.getEmbedUtil().getEmbed(event)
													.setDescription(lu.getText(event, path+".done").replace("{channel}", channel.getAsMention()))
													.setColor(Constants.COLOR_SUCCESS)
													.build()
											);
										}
									);
							} catch (InsufficientPermissionException ex) {
								editPermError(event, ex.getPermission(), true);
							}
						}
					);
			} catch (InsufficientPermissionException ex) {
				editPermError(event, ex.getPermission(), true);
			}
		}
	}

	private class VoicePanel extends SlashCommand {

		public VoicePanel(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "panel";
			this.path = "bot.guild.setup.voice.panel";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
			this.botPermissions = new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS};
			this.subcommandGroup = new SubcommandGroupData("voice", lu.getText("bot.guild.setup.voice.help"));
			this.module = CmdModule.VOICE;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			MessageChannel channel = event.optMessageChannel("channel");
			if (channel == null || !channel.canTalk()) {
				createError(event, path+".no_channel", "Received: "+(channel == null ? "No channel" : channel.getAsMention()));
			}

			Button lock = Button.danger("voice:lock", lu.getLocalized(event.getGuildLocale(), path+".lock")).withEmoji(Emoji.fromUnicode("🔒"));
			Button unlock = Button.success("voice:unlock", lu.getLocalized(event.getGuildLocale(), path+".unlock")).withEmoji(Emoji.fromUnicode("🔓"));
			Button ghost = Button.danger("voice:ghost", lu.getLocalized(event.getGuildLocale(), path+".ghost")).withEmoji(Emoji.fromUnicode("👻"));
			Button unghost = Button.success("voice:unghost", lu.getLocalized(event.getGuildLocale(), path+".unghost")).withEmoji(Emoji.fromUnicode("👁️"));
			Button name = Button.secondary("voice:name", lu.getLocalized(event.getGuildLocale(), path+".name")).withEmoji(Emoji.fromUnicode("🔡"));
			Button limit = Button.secondary("voice:limit", lu.getLocalized(event.getGuildLocale(), path+".limit")).withEmoji(Emoji.fromUnicode("🔢"));
			Button permit = Button.success("voice:permit", lu.getLocalized(event.getGuildLocale(), path+".permit")).withEmoji(Emotes.ADDUSER.getEmoji());
			Button reject = Button.danger("voice:reject", lu.getLocalized(event.getGuildLocale(), path+".reject")).withEmoji(Emotes.REMOVEUSER.getEmoji());
			Button perms = Button.secondary("voice:perms", lu.getLocalized(event.getGuildLocale(), path+".perms")).withEmoji(Emotes.SETTINGS_2.getEmoji());
			Button delete = Button.danger("voice:delete", lu.getLocalized(event.getGuildLocale(), path+".delete")).withEmoji(Emoji.fromUnicode("🔴"));

			ActionRow row1 = ActionRow.of(unlock, lock);
			ActionRow row2 = ActionRow.of(unghost, ghost);
			ActionRow row3 = ActionRow.of(name, limit);
			ActionRow row4 = ActionRow.of(permit, reject, perms);
			ActionRow row5 = ActionRow.of(delete);
			channel.sendMessageEmbeds(
				new EmbedBuilder().setColor(Constants.COLOR_DEFAULT).setTitle(lu.getLocalized(event.getGuildLocale(), path+".embed_title"))
					.setDescription(lu.getLocalized(event.getGuildLocale(), path+".embed_value").replace("{id}", bot.getDBUtil().guildVoice.getChannel(event.getGuild().getId()))).build()
			).addComponents(row1, row2, row3, row4, row5).queue();

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{channel}", channel.getAsMention()))
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}
	}

	private class VoiceName extends SlashCommand {

		public VoiceName(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "name";
			this.path = "bot.guild.setup.voice.name";
			this.options = List.of(
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true)
					.setMaxLength(100)
			);
			this.subcommandGroup = new SubcommandGroupData("voice", lu.getText("bot.guild.setup.voice.help"));
			this.module = CmdModule.VOICE;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String filName = event.optString("name", lu.getLocalized(event.getGuildLocale(), "bot.voice.listener.default_name"));

			if (filName.isBlank()) {
				createError(event, path+".invalid_range");
				return;
			}

			bot.getDBUtil().guildVoice.setName(event.getGuild().getId(), filName);

			createReplyEmbed(event,
				bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getText(event, path+".done").replace("{value}", filName))
					.build()
			);
		}

	}

	private class VoiceLimit extends SlashCommand {

		public VoiceLimit(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "limit";
			this.path = "bot.guild.setup.voice.limit";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "limit", lu.getText(path+".limit.help"), true)
					.setRequiredRange(0, 99)
			);
			this.subcommandGroup = new SubcommandGroupData("voice", lu.getText("bot.guild.setup.voice.help"));
			this.module = CmdModule.VOICE;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer filLimit = event.optInteger("limit");

			bot.getDBUtil().guildVoice.setLimit(event.getGuild().getId(), filLimit);

			createReplyEmbed(event,
				bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getText(event, path+".done").replace("{value}", filLimit.toString()))
					.build()
			);
		}

	}

}

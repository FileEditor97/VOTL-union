package union.commands.guild;

import java.awt.Color;
import java.net.URL;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.dv8tion.jda.api.entities.Mentions;
import union.App;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.Emotes;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.database.managers.GuildSettingsManager.ModerationInformLevel;
import union.utils.message.MessageUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
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
		this.children = new SlashCommand[]{new PanelColor(bot), new AppealLink(bot), new ReportChannel(bot), new Anticrash(bot),
			new VoiceCreate(bot), new VoiceSelect(bot), new VoicePanel(bot), new VoiceName(bot), new VoiceLimit(bot),
			new Strikes(bot), new InformLevel(bot)
		};
		this.category = CmdCategory.GUILD;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

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
			long guildId = event.getGuild().getIdLong();
			String text = event.optString("color");

			Color color = MessageUtil.getColor(text);
			if (color == null) {
				createError(event, path+".no_color");
				return;
			}
			bot.getDBUtil().guildSettings.setColor(guildId, color.getRGB() & 0xFFFFFF);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(color.getRGB())
				.setDescription(lu.getText(event, path+".done").replace("{color}", "#"+Integer.toHexString(color.getRGB() & 0xFFFFFF)))
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
			long guildId = event.getGuild().getIdLong();
			String text = event.optString("link");

			if (!isValidURL(text)) {
				createError(event, path+".not_valid", "Received invalid URL: `%s`".formatted(text));
				return;
			}

			bot.getDBUtil().guildSettings.setAppealLink(guildId, text);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
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
			long guildId = event.getGuild().getIdLong();
			MessageChannel channel = event.optMessageChannel("channel");

			if (!channel.canTalk()) {
				createError(event, path+".cant_send");
			}

			bot.getDBUtil().guildSettings.setReportChannelId(guildId, channel.getIdLong());

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{channel}", channel.getAsMention()))
				.build());
		}
	}

	private class Anticrash extends SlashCommand {
		public Anticrash(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "anticrash";
			this.path = "bot.guild.setup.anticrash";
			this.options = List.of(
				new OptionData(OptionType.BOOLEAN, "enabled", lu.getText(path+".enabled.help"), true),
				new OptionData(OptionType.STRING, "ping", lu.getText(path+".ping.help"))
			);
			this.accessLevel = CmdAccessLevel.OPERATOR;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			boolean enabled = event.optBoolean("enabled");

			long guildId = event.getGuild().getIdLong();
			bot.getDBUtil().guildSettings.setAnticrash(guildId, enabled);

			if (event.hasOption("ping")) {
				Mentions mentions = event.optMentions("ping");
				String ping = null;
				if (!mentions.getRoles().isEmpty() || !mentions.getMembers().isEmpty()) {
					Set<String> pingSet = new HashSet<>();
					pingSet.addAll(mentions.getRoles().stream().limit(4).map(r -> "<@&"+r.getIdLong()+">").toList());
					pingSet.addAll(mentions.getMembers().stream().limit(4).map(r -> "<@"+r.getIdLong()+">").toList());

					ping = String.join(" ", pingSet);
				}
				bot.getDBUtil().guildSettings.setAnticrashPing(guildId, ping);

				createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_full").formatted(
						enabled ? Constants.SUCCESS : Constants.FAILURE,
						ping==null ? "developer" : ping
					))
					.build());
			} else {
				createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").formatted(enabled ? Constants.SUCCESS : Constants.FAILURE))
					.build());
			}
		}
	}

	private class VoiceCreate extends SlashCommand {
		public VoiceCreate(App bot) {
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
			long guildId = guild.getIdLong();

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
											bot.getDBUtil().guildVoice.setup(guildId, category.getIdLong(), channel.getIdLong());
											editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
												.setDescription(lu.getText(event, path+".done").replace("{channel}", channel.getAsMention()))
												.build());
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

	private class VoiceSelect extends SlashCommand {
		public VoiceSelect(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "select";
			this.path = "bot.guild.setup.voice.select";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.name"), true)
					.setChannelTypes(ChannelType.VOICE)
			);
			this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_MOVE_OTHERS};
			this.subcommandGroup = new SubcommandGroupData("voice", lu.getText("bot.guild.setup.voice.help"));
			this.module = CmdModule.VOICE;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Guild guild = event.getGuild();
			long guildId = guild.getIdLong();

			VoiceChannel channel = (VoiceChannel) event.optGuildChannel("channel");
			Category category = channel.getParentCategory();
			if (category == null) {
				editError(event, path+".no_category");
				return;
			}

			try {
				category.upsertPermissionOverride(guild.getBotRole()).setAllowed(getBotPermissions()).queue(doneCategory -> {
					channel.upsertPermissionOverride(guild.getPublicRole()).setDenied(Permission.VOICE_SPEAK).queue(doneChannel -> {
						bot.getDBUtil().guildVoice.setup(guildId, category.getIdLong(), channel.getIdLong());
						editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
							.setDescription(lu.getText(event, path+".done").replace("{channel}", channel.getAsMention()))
							.build());
					});
				});
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
			Button permit = Button.success("voice:permit", lu.getLocalized(event.getGuildLocale(), path+".permit")).withEmoji(Emotes.ADDUSER.getEmoji());
			Button reject = Button.danger("voice:reject", lu.getLocalized(event.getGuildLocale(), path+".reject")).withEmoji(Emotes.REMOVEUSER.getEmoji());
			Button perms = Button.secondary("voice:perms", lu.getLocalized(event.getGuildLocale(), path+".perms")).withEmoji(Emotes.SETTINGS_2.getEmoji());
			Button delete = Button.danger("voice:delete", lu.getLocalized(event.getGuildLocale(), path+".delete")).withEmoji(Emoji.fromUnicode("🔴"));

			ActionRow row1 = ActionRow.of(unlock, lock);
			ActionRow row2 = ActionRow.of(unghost, ghost);
			ActionRow row4 = ActionRow.of(permit, reject, perms);
			ActionRow row5 = ActionRow.of(delete);

			Long channelId = bot.getDBUtil().guildVoice.getChannelId(event.getGuild().getIdLong());
			channel.sendMessageEmbeds(new EmbedBuilder()
				.setColor(Constants.COLOR_DEFAULT)
				.setTitle(lu.getLocalized(event.getGuildLocale(), path+".embed_title"))
				.setDescription(lu.getLocalized(event.getGuildLocale(), path+".embed_value").replace("{id}", String.valueOf(channelId)))
				.build()
			).addComponents(row1, row2, row4, row5).queue();

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{channel}", channel.getAsMention()))
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

			bot.getDBUtil().guildVoice.setName(event.getGuild().getIdLong(), filName);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{value}", filName))
				.build());
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

			bot.getDBUtil().guildVoice.setLimit(event.getGuild().getIdLong(), filLimit);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{value}", filLimit.toString()))
				.build());
		}
	}

	private class Strikes extends SlashCommand {

		public Strikes(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "strikes";
			this.path = "bot.guild.setup.strikes";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "time", lu.getText(path+".time.help"), true)
					.setRequiredRange(1, 30)
			);
			this.module = CmdModule.STRIKES;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer hours = event.optInteger("time");

			bot.getDBUtil().guildSettings.setStrikeExpiresAfter(event.getGuild().getIdLong(), hours);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(hours))
				.build());
		}

	}

	private class InformLevel extends SlashCommand {
		public InformLevel(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "dm_inform";
			this.path = "bot.guild.setup.dm_inform";
			this.options = List.of(
				new OptionData(OptionType.STRING, "action", lu.getText(path+".action.help"), true)
					.addChoice("Ban", "ban")
					.addChoice("Kick", "kick")
					.addChoice("Mute", "mute")
					.addChoice("Strike", "strike")
					.addChoice("Delstrike", "delstrike"),
				new OptionData(OptionType.INTEGER, "level", lu.getText(path+".level.help"), true)
					.addChoices(ModerationInformLevel.asChoices(lu))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			long guildId = event.getGuild().getIdLong();

			String action = event.optString("action");
			ModerationInformLevel informLevel = ModerationInformLevel.byLevel(event.optInteger("level"));
			switch (action) {
				case "ban" -> bot.getDBUtil().guildSettings.setInformBanLevel(guildId, informLevel);
				case "kick" -> bot.getDBUtil().guildSettings.setInformKickLevel(guildId, informLevel);
				case "mute" -> bot.getDBUtil().guildSettings.setInformMuteLevel(guildId, informLevel);
				case "strike" -> bot.getDBUtil().guildSettings.setInformStrikeLevel(guildId, informLevel);
				case "delstrike" -> bot.getDBUtil().guildSettings.setInformDelstrikeLevel(guildId, informLevel);
				default -> {
					editError(event, path+".unknown", action);
					return;
				}
			}

			editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(action, lu.getText(event, informLevel.getPath())))
				.build());
		}
	}

}

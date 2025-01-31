package union.commands.guild;

import java.awt.Color;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.Mentions;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.commands.Command;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.database.managers.GuildSettingsManager.AnticrashAction;
import union.utils.database.managers.GuildSettingsManager.ModerationInformLevel;
import union.utils.database.managers.LevelManager;
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

	public SetupCmd() {
		this.name = "setup";
		this.path = "bot.guild.setup";
		this.children = new SlashCommand[]{new PanelColor(), new AppealLink(), new ReportChannel(), new Anticrash(),
			new VoiceCreate(), new VoiceSelect(), new VoicePanel(), new VoiceName(), new VoiceLimit(),
			new Strikes(), new InformLevel(), new RoleWhitelist(), new Levels(),
			new SetLevelRoles(), new RemoveLevelRoles(), new ViewLevelRoles(),
			new AddLevelExempt(), new RemoveLevelExempt(), new ViewLevelExempt(), new ClearLevelExempt()
		};
		this.category = CmdCategory.GUILD;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class PanelColor extends SlashCommand {
		public PanelColor() {
			this.name = "color";
			this.path = "bot.guild.setup.color";
			this.options = List.of(
				new OptionData(OptionType.STRING, "color", lu.getText(path+".color.help"), true).setRequiredLength(5, 11)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			long guildId = event.getGuild().getIdLong();
			String text = event.optString("color");

			Color color = MessageUtil.getColor(text);
			if (color == null) {
				editError(event, path+".no_color");
				return;
			}
			if (!bot.getDBUtil().guildSettings.setColor(guildId, color.getRGB() & 0xFFFFFF)) {
				editErrorUnknown(event, "Database error.");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(color.getRGB())
				.setDescription(lu.getText(event, path+".done").replace("{color}", "#"+Integer.toHexString(color.getRGB() & 0xFFFFFF)))
				.build());
		}
	}

	private class AppealLink extends SlashCommand {
		public AppealLink() {
			this.name = "appeal";
			this.path = "bot.guild.setup.appeal";
			this.options = List.of(
				new OptionData(OptionType.STRING, "link", lu.getText(path+".link.help"), true),
				new OptionData(OptionType.STRING, "rules_link", lu.getText(path+".rules_link.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			long guildId = event.getGuild().getIdLong();
			String link = event.optString("link");

			if (isInvalidURL(link)) {
				editError(event, path+".not_valid", "Received invalid appeal URL: `%s`".formatted(link));
				return;
			}

			if (!bot.getDBUtil().guildSettings.setAppealLink(guildId, link)) {
				editErrorUnknown(event, "Database error.");
				return;
			}
			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".add_appeal").replace("{link}", link));

			if (event.hasOption("rules_link")) {
				String rulesLink = event.optString("rules_link");
				if (isInvalidURL(rulesLink)) {
					editError(event, path+".not_valid", "Received invalid rules URL: `%s`".formatted(rulesLink));
					return;
				}
				if (!bot.getDBUtil().guildSettings.setRulesLink(guildId, rulesLink)) {
					editErrorUnknown(event, "Database error.");
					return;
				}
				builder.appendDescription(lu.getText(event, path+".add_rules").replace("{link}", rulesLink));
			}

			editEmbed(event, builder.build());
		}

		private boolean isInvalidURL(String urlString) {
			if (urlString.equalsIgnoreCase("NULL")) return false;
			try {
				URL url = new URL(urlString);
				url.toURI();
				return false;
			} catch (Exception e) {
				return true;
			}
		}
	}

	private class ReportChannel extends SlashCommand {
		public ReportChannel() {
			this.name = "report";
			this.path = "bot.guild.setup.report";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			long guildId = event.getGuild().getIdLong();
			MessageChannel channel = event.optMessageChannel("channel");

			if (!channel.canTalk()) {
				editError(event, path+".cant_send");
			}

			if (!bot.getDBUtil().guildSettings.setReportChannelId(guildId, channel.getIdLong())) {
				editErrorUnknown(event, "Database error.");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{channel}", channel.getAsMention()))
				.build());
		}
	}

	private class Anticrash extends SlashCommand {
		public Anticrash() {
			this.name = "anticrash";
			this.path = "bot.guild.setup.anticrash";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "action", lu.getText(path+".action.help"), true)
					.addChoices(
						new Command.Choice("Disabled", 0),
						new Command.Choice("Remove all roles", 1),
						new Command.Choice("Kick", 2),
						new Command.Choice("Ban", 3)
					),
				new OptionData(OptionType.STRING, "ping", lu.getText(path+".ping.help"))
			);
			this.accessLevel = CmdAccessLevel.OPERATOR;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			AnticrashAction action = AnticrashAction.byValue(event.optInteger("action", 0));

			long guildId = event.getGuild().getIdLong();
			if (!bot.getDBUtil().guildSettings.setAnticrash(guildId, action)) {
				editErrorUnknown(event, "Database error.");
				return;
			}
			// Clear anticrash cache (easier to purge all cache, than for of each member)
			bot.getDBUtil().guildSettings.purgeAnticrashCache();

			if (event.hasOption("ping")) {
				Mentions mentions = event.optMentions("ping");
				String ping = null;
				if (!mentions.getRoles().isEmpty() || !mentions.getMembers().isEmpty()) {
					Set<String> pingSet = new HashSet<>();
					pingSet.addAll(mentions.getRoles().stream().limit(4).map(r -> "<@&"+r.getIdLong()+">").toList());
					pingSet.addAll(mentions.getMembers().stream().limit(4).map(r -> "<@"+r.getIdLong()+">").toList());

					ping = String.join(" ", pingSet);
				}
				if (!bot.getDBUtil().guildSettings.setAnticrashPing(guildId, ping)) {
					editErrorUnknown(event, "Database error.");
					return;
				}

				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_full").formatted(
						action.name().toLowerCase(),
						ping==null ? "developer" : ping
					))
					.build());
			} else {
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").formatted(action.name().toLowerCase()))
					.build());
			}
		}
	}

	private class VoiceCreate extends SlashCommand {
		public VoiceCreate() {
			this.name = "create";
			this.path = "bot.guild.setup.voice.create";
			this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_MOVE_OTHERS};
			this.subcommandGroup = new SubcommandGroupData("voice", lu.getText("bot.guild.setup.voice.help"));
			this.module = CmdModule.VOICE;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

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
											if (!bot.getDBUtil().guildVoice.setup(guildId, category.getIdLong(), channel.getIdLong())) {
												editErrorUnknown(event, "Database error.");
												return;
											}
											editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
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
		public VoiceSelect() {
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
			event.deferReply().queue();

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
						if (!bot.getDBUtil().guildVoice.setup(guildId, category.getIdLong(), channel.getIdLong())) {
							editErrorUnknown(event, "Database error.");
							return;
						}
						editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
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
		public VoicePanel() {
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
			event.deferReply().queue();
			MessageChannel channel = event.optMessageChannel("channel");
			if (channel == null || !channel.canTalk()) {
				editError(event, path+".no_channel", "Received: "+(channel == null ? "No channel" : channel.getAsMention()));
			}

			Button lock = Button.danger("voice:lock", lu.getLocalized(event.getGuildLocale(), path+".lock")).withEmoji(Emoji.fromUnicode("🔒"));
			Button unlock = Button.success("voice:unlock", lu.getLocalized(event.getGuildLocale(), path+".unlock")).withEmoji(Emoji.fromUnicode("🔓"));
			Button ghost = Button.danger("voice:ghost", lu.getLocalized(event.getGuildLocale(), path+".ghost")).withEmoji(Emoji.fromUnicode("👻"));
			Button unghost = Button.success("voice:unghost", lu.getLocalized(event.getGuildLocale(), path+".unghost")).withEmoji(Emoji.fromUnicode("👁️"));
			Button permit = Button.success("voice:permit", lu.getLocalized(event.getGuildLocale(), path+".permit")).withEmoji(Emoji.fromUnicode("➕"));
			Button reject = Button.danger("voice:reject", lu.getLocalized(event.getGuildLocale(), path+".reject")).withEmoji(Emoji.fromUnicode("➖"));
			Button perms = Button.secondary("voice:perms", lu.getLocalized(event.getGuildLocale(), path+".perms")).withEmoji(Emoji.fromUnicode("⚙️"));
			Button delete = Button.danger("voice:delete", lu.getLocalized(event.getGuildLocale(), path+".delete")).withEmoji(Emoji.fromUnicode("🗑️"));

			ActionRow row1 = ActionRow.of(unlock, lock);
			ActionRow row2 = ActionRow.of(unghost, ghost);
			ActionRow row4 = ActionRow.of(permit, reject, perms);
			ActionRow row5 = ActionRow.of(delete);

			Long channelId = bot.getDBUtil().getVoiceSettings(event.getGuild()).getChannelId();
			channel.sendMessageEmbeds(new EmbedBuilder()
				.setColor(Constants.COLOR_DEFAULT)
				.setTitle(lu.getLocalized(event.getGuildLocale(), path+".embed_title"))
				.setDescription(lu.getLocalized(event.getGuildLocale(), path+".embed_value").replace("{id}", String.valueOf(channelId)))
				.build()
			).addComponents(row1, row2, row4, row5).queue();

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{channel}", channel.getAsMention()))
				.build());
		}
	}

	private class VoiceName extends SlashCommand {
		public VoiceName() {
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
			event.deferReply().queue();
			String filName = event.optString("name", lu.getLocalized(event.getGuildLocale(), "bot.voice.listener.default_name"));

			if (filName.isBlank()) {
				editError(event, path+".invalid_range");
				return;
			}

			if (!bot.getDBUtil().guildVoice.setName(event.getGuild().getIdLong(), filName)) {
				editErrorUnknown(event, "Database error.");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{value}", filName))
				.build());
		}
	}

	private class VoiceLimit extends SlashCommand {
		public VoiceLimit() {
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
			event.deferReply().queue();
			Integer filLimit = event.optInteger("limit");

			if (!bot.getDBUtil().guildVoice.setLimit(event.getGuild().getIdLong(), filLimit)) {
				editErrorUnknown(event, "Database error.");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{value}", filLimit.toString()))
				.build());
		}
	}

	private class Strikes extends SlashCommand {
		public Strikes() {
			this.name = "strikes";
			this.path = "bot.guild.setup.strikes";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "expires_after", lu.getText(path+".expires_after.help"))
					.setRequiredRange(1, 30),
				new OptionData(OptionType.INTEGER, "cooldown", lu.getText(path+".cooldown.help"))
					.setRequiredRange(0, 30)
			);
			this.module = CmdModule.STRIKES;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			if (event.getOptions().isEmpty()) {
				editError(event, path+".no_options");
				return;
			}

			StringBuilder builder = new StringBuilder(lu.getText(event, path+".embed_title"));
			Integer expiresAfter = event.optInteger("expires_after");
			if (expiresAfter != null) {
				if (!bot.getDBUtil().guildSettings.setStrikeExpiresAfter(event.getGuild().getIdLong(), expiresAfter)) {
					editErrorUnknown(event, "Database error.");
					return;
				}
				builder.append(lu.getText(event, path+".expires_changed").formatted(expiresAfter));
			}
			Integer cooldown = event.optInteger("cooldown");
			if (cooldown != null) {
				if (!bot.getDBUtil().guildSettings.setStrikeCooldown(event.getGuild().getIdLong(), cooldown)) {
					editErrorUnknown(event, "Database error.");
					return;
				}
				builder.append(lu.getText(event, path+".cooldown_changed").formatted(cooldown));
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(builder.toString())
				.build());
		}
	}

	private class InformLevel extends SlashCommand {
		public InformLevel() {
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

		Set<String> acceptedOptions = Set.of("ban", "kick", "mute", "strike", "delstrike");

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			long guildId = event.getGuild().getIdLong();

			String action = event.optString("action");
			if (!acceptedOptions.contains(action)) {
				editError(event, path+".unknown", action);
				return;
			}
			ModerationInformLevel informLevel = ModerationInformLevel.byLevel(event.optInteger("level"));
			if (switch (action) {
				case "ban" -> !bot.getDBUtil().guildSettings.setInformBanLevel(guildId, informLevel);
				case "kick" -> !bot.getDBUtil().guildSettings.setInformKickLevel(guildId, informLevel);
				case "mute" -> !bot.getDBUtil().guildSettings.setInformMuteLevel(guildId, informLevel);
				case "strike" -> !bot.getDBUtil().guildSettings.setInformStrikeLevel(guildId, informLevel);
				case "delstrike" -> !bot.getDBUtil().guildSettings.setInformDelstrikeLevel(guildId, informLevel);
				default -> true;
			}) {
				editErrorUnknown(event, "Database error.");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(action, lu.getText(event, informLevel.getPath())))
				.build());
		}
	}

	private class RoleWhitelist extends SlashCommand {
		public RoleWhitelist() {
			this.name = "role_whitelist";
			this.path = "bot.guild.setup.role_whitelist";
			this.options = List.of(
				new OptionData(OptionType.BOOLEAN, "enable", lu.getText(path+".enable.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			boolean enabled = event.optBoolean("enable");
			// DB
			if (!bot.getDBUtil().guildSettings.setRoleWhitelist(event.getGuild().getIdLong(), enabled)) {
				editErrorUnknown(event, "Database error.");
				return;
			}
			// Reply
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(enabled?Constants.SUCCESS:Constants.FAILURE))
				.build());
		}
	}

	private class Levels extends SlashCommand {
		public Levels() {
			this.name = "levels";
			this.path = "bot.guild.setup.levels";
			this.options = List.of(
				new OptionData(OptionType.BOOLEAN, "enabled", lu.getText(path+".enabled.help")),
				new OptionData(OptionType.BOOLEAN, "voice_enabled", lu.getText(path+".voice_enabled.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			StringBuilder response = new StringBuilder();

			if (event.getOptions().isEmpty()) {
				event.deferReply(true).queue();
				// Return overview
				LevelManager.LevelSettings settings = bot.getDBUtil().levels.getSettings(event.getGuild());

				response.append("\n> Leveling enabled: ").append(settings.isEnabled()?Constants.SUCCESS:Constants.FAILURE);
				response.append("\n> Grant xp for voice activity: ").append(settings.isVoiceEnabled()?Constants.SUCCESS:Constants.FAILURE);

				editEmbed(event, bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getText(event, path+".embed_view"))
					.appendDescription(response.toString())
					.build()
				);
			} else {
				event.deferReply().queue();
				// Edit settings
				if (event.hasOption("enabled")) {
					final boolean enabled = event.optBoolean("enabled");

					if (bot.getDBUtil().levels.setEnabled(event.getGuild().getIdLong(), enabled)) {
						editErrorDatabase(event, "leveling settings set enabled");
						return;
					}
					response.append(lu.getText(event, path+".changed_enabled").formatted(enabled ? Constants.SUCCESS : Constants.FAILURE));
				}
				if (event.hasOption("voice_enabled")) {
					final boolean enabled = event.optBoolean("voice_enabled");

					if (bot.getDBUtil().levels.setEnabledVoice(event.getGuild().getIdLong(), enabled)) {
						editErrorDatabase(event, "leveling settings set voice enabled");
						return;
					}
					response.append(lu.getText(event, path+".changed_voice").formatted(enabled ? Constants.SUCCESS : Constants.FAILURE));
				}

				if (response.isEmpty()) {
					editErrorUnknown(event, "Response for ticket settings is empty.");
				} else {
					editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".embed_changes"))
						.appendDescription(response.toString())
						.build()
					);
				}
			}
		}
	}

	private class SetLevelRoles extends SlashCommand {
		public SetLevelRoles() {
			this.name = "set";
			this.path = "bot.guild.setup.level_roles.set";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "level", lu.getText(path+".level.help"), true)
					.setRequiredRange(1, 10_000),
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("level_roles", lu.getText("bot.guild.setup.level_roles.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			int level = event.optInteger("level");
			Role role = event.optRole("role");
			if (role == null) {
				editError(event, path+".invalid_args");
				return;
			}

			String denyReason = bot.getCheckUtil().denyRole(role, event.getGuild(), event.getMember(), true);
			if (denyReason != null) {
				editError(event, path+".incorrect_role", denyReason);
				return;
			}
			if (bot.getDBUtil().levelRoles.getLevelsCount(event.getGuild().getIdLong()) >= 40) {
				editError(event, path+".limit");
				return;
			}

			if (!bot.getDBUtil().levelRoles.add(event.getGuild().getIdLong(), level, role.getId(), true)) {
				editErrorDatabase(event, "level roles set");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(role.getAsMention(), level))
				.build()
			);
		}
	}

	private class RemoveLevelRoles extends SlashCommand {
		public RemoveLevelRoles() {
			this.name = "remove";
			this.path = "bot.guild.setup.level_roles.remove";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "level", lu.getText(path+".level.help"), true)
					.setRequiredRange(1, 10_000)
			);
			this.subcommandGroup = new SubcommandGroupData("level_roles", lu.getText("bot.guild.setup.level_roles.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			int level = event.optInteger("level");

			Set<Long> roles = bot.getDBUtil().levelRoles.getRoles(event.getGuild().getIdLong(), level);
			if (roles.isEmpty()) {
				editError(event, path+".empty");
				return;
			}

			if (!bot.getDBUtil().levelRoles.remove(event.getGuild().getIdLong(), level)) {
				editErrorDatabase(event, "level roles remove");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(level))
				.build()
			);
		}
	}

	private class ViewLevelRoles extends SlashCommand {
		public ViewLevelRoles() {
			this.name = "view";
			this.path = "bot.guild.setup.level_roles.view";
			this.subcommandGroup = new SubcommandGroupData("level_roles", lu.getText("bot.guild.setup.level_roles.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Map<Integer, Set<Long>> data = bot.getDBUtil().levelRoles.getAllLevels(event.getGuild().getIdLong());
			if (data.isEmpty()) {
				editError(event, path+".empty");
				return;
			}

			StringBuilder response = new StringBuilder();
			data.forEach((level, roles) -> {
				response.append("\n> `%5d` - ".formatted(level))
					.append(roles.stream().map("<@&%s>"::formatted).collect(Collectors.joining(", ")));
			});

			editEmbed(event, bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".title"))
				.setDescription(response.toString())
				.build()
			);
		}
	}

	private class AddLevelExempt extends SlashCommand {
		public AddLevelExempt() {
			this.name = "add";
			this.path = "bot.guild.setup.level_exempt.add";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT, ChannelType.VOICE, ChannelType.CATEGORY, ChannelType.GUILD_PUBLIC_THREAD, ChannelType.STAGE)
			);
			this.subcommandGroup = new SubcommandGroupData("level_exempt", lu.getText("bot.guild.setup.level_exempt.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			GuildChannel channel = event.optGuildChannel("channel");
			if (channel == null) {
				editError(event, path+".invalid_args");
				return;
			}

			LevelManager.LevelSettings settings = bot.getDBUtil().levels.getSettings(event.getGuild());
			if (settings.getExemptChannels().size() >= 40) {
				editError(event, path+".limit");
				return;
			}
			if (settings.isExemptChannel(channel.getIdLong())) {
				editError(event, path+".already", channel.getAsMention());
				return;
			}

			Set<Long> channels = new HashSet<>(settings.getExemptChannels());
			channels.add(channel.getIdLong());
			String channelIds = channels.stream().map(String::valueOf).collect(Collectors.joining(";"));

			if (!bot.getDBUtil().levels.setExemptChannels(event.getGuild().getIdLong(), channelIds)) {
				editErrorDatabase(event, "set level exempt channels");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(channel.getAsMention()))
				.build()
			);
		}
	}

	private class RemoveLevelExempt extends SlashCommand {
		public RemoveLevelExempt() {
			this.name = "remove";
			this.path = "bot.guild.setup.level_exempt.remove";
			this.options = List.of(
				new OptionData(OptionType.STRING, "channel", lu.getText(path+".channel.help"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("level_exempt", lu.getText("bot.guild.setup.level_exempt.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			GuildChannel channel = event.optMentions("channel").getChannels().get(0);
			long channelId = channel!=null ? channel.getIdLong() : event.optLong("channel");

			if (channel!=null && !channel.getGuild().equals(event.getGuild())) {
				editError(event, path+".invalid_args");
				return;
			}

			LevelManager.LevelSettings settings = bot.getDBUtil().levels.getSettings(event.getGuild());
			if (!settings.isExemptChannel(channelId)) {
				editError(event, path+".not_exempt", channel!=null ? channel.getAsMention() : String.valueOf(channelId));
				return;
			}

			Set<Long> channels = new HashSet<>(settings.getExemptChannels());
			channels.remove(channelId);
			String channelIds = channels.stream().map(String::valueOf).collect(Collectors.joining(";"));

			if (!bot.getDBUtil().levels.setExemptChannels(event.getGuild().getIdLong(), channelIds)) {
				editErrorDatabase(event, "set level exempt channels");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(channel!=null ? channel.getAsMention() : channelId))
				.build()
			);
		}
	}

	private class ClearLevelExempt extends SlashCommand {
		public ClearLevelExempt() {
			this.name = "clear";
			this.path = "bot.guild.setup.level_exempt.clear";
			this.subcommandGroup = new SubcommandGroupData("level_exempt", lu.getText("bot.guild.setup.level_exempt.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			if (!bot.getDBUtil().levels.setExemptChannels(event.getGuild().getIdLong(), null)) {
				editErrorDatabase(event, "clear level exempt channels");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done"))
				.build()
			);
		}
	}

	private class ViewLevelExempt extends SlashCommand {
		public ViewLevelExempt() {
			this.name = "view";
			this.path = "bot.guild.setup.level_exempt.view";
			this.subcommandGroup = new SubcommandGroupData("level_exempt", lu.getText("bot.guild.setup.level_exempt.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Set<Long> channelIds = bot.getDBUtil().levels.getSettings(event.getGuild()).getExemptChannels();
			if (channelIds.isEmpty()) {
				editError(event, path+".empty");
				return;
			}

			StringBuilder response = new StringBuilder();
			channelIds.forEach(id -> response.append("\n> <#%s> `%<s`".formatted(id)));

			editEmbed(event, bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".title"))
				.setDescription(response.toString())
				.build()
			);
		}
	}

}

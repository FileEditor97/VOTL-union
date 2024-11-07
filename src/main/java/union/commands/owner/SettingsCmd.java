package union.commands.owner;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.file.SettingsManager;
import union.utils.message.MessageUtil;

import java.awt.*;
import java.util.List;

public class SettingsCmd extends CommandBase {

	public SettingsCmd() {
		this.name = "settings";
		this.path = "bot.owner.settings";
		this.children = new SlashCommand[]{
			new Database(), new BotWhitelist(), new GameServer(),
			new Server(), new View()
		};
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Database extends SlashCommand {
		public Database() {
			this.name = "database";
			this.path = "bot.owner.settings.database";
			this.options = List.of(
				new OptionData(OptionType.BOOLEAN, "player", lu.getText(path+".player.help")),
				new OptionData(OptionType.BOOLEAN, "verify", lu.getText(path+".verify.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			StringBuilder builder = new StringBuilder("Database settings changed\n");
			if (event.hasOption("player")) {
				boolean enabled = event.optBoolean("player");
				bot.getSettings().setDbPlayerEnabled(enabled);
				builder.append("> Player database enabled: ").append(enabled ? Constants.SUCCESS : Constants.FAILURE).append("\n");
			}
			if (event.hasOption("verify")) {
				boolean enabled = event.optBoolean("verify");
				bot.getSettings().setDbVerifyEnabled(enabled);
				builder.append("> Verify database enabled: ").append(enabled ? Constants.SUCCESS : Constants.FAILURE).append("\n");
			}
			editMsg(event, builder.toString());
		}
	}

	private class BotWhitelist extends SlashCommand {
		public BotWhitelist() {
			this.name = "whitelist";
			this.path = "bot.owner.settings.whitelist";
			this.options = List.of(
				new OptionData(OptionType.BOOLEAN, "add", lu.getText(path+".add.help"), true),
				new OptionData(OptionType.STRING, "bot_id", lu.getText(path+".bot_id.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			long id;
			try {
				id = event.optLong("bot_id");
			} catch (NumberFormatException ex) {
				editErrorUnknown(event, ex.getMessage());
				return;
			}
			if (event.optBoolean("add")) {
				bot.getSettings().addBotWhitelisted(id);
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_add").formatted(id))
					.build()
				);
			} else {
				bot.getSettings().removeBotWhitelisted(id);
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_remove").formatted(id))
					.build()
				);
			}
		}
	}

	private class GameServer extends SlashCommand {
		public GameServer() {
			this.name = "gameserver";
			this.path = "bot.owner.settings.gameserver";
			this.options = List.of(
				new OptionData(OptionType.BOOLEAN, "add", lu.getText(path+".add.help"), true),
				new OptionData(OptionType.STRING, "db_name", lu.getText(path+".db_name.help"), true),
				new OptionData(OptionType.STRING, "title", lu.getText(path+".title.help")),
				new OptionData(OptionType.STRING, "color", lu.getText(path+".color.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			String dbName = event.optString("db_name");

			if (event.optBoolean("add")) {
				String title = event.optString("title");
				if (title == null || title.isBlank()) {
					editError(event, path+".no_title");
					return;
				}
				Color color = MessageUtil.getColor(event.optString("color"));
				if (color == null) {
					editError(event, path+".no_color");
					return;
				}

				bot.getSettings().addDatabase(dbName, new SettingsManager.GameServerInfo(title, 0xFFFFFF&color.getRGB()));
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_add")
						.formatted(dbName, title, 0xFFFFFF&color.getRGB()))
					.build()
				);
			} else {
				bot.getSettings().removeDatabase(dbName);
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_remove").formatted(dbName))
					.build()
				);
			}
		}
	}

	private class Server extends SlashCommand {
		public Server() {
			this.name = "server";
			this.path = "bot.owner.settings.server";
			this.options = List.of(
				new OptionData(OptionType.BOOLEAN, "add", lu.getText(path+".add.help"), true),
				new OptionData(OptionType.STRING, "server_id", lu.getText(path+".server_id.help"), true),
				new OptionData(OptionType.STRING, "dbs", lu.getText(path+".dbs.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			long guildId;
			try {
				guildId = event.optLong("server_id");
			} catch (NumberFormatException ex) {
				editErrorUnknown(event, ex.getMessage());
				return;
			}

			if (event.optBoolean("add")) {
				if (bot.JDA.getGuildById(guildId) == null) {
					editError(event, path+".guild_not_found", "Provided: "+guildId);
					return;
				}
				String dbString = event.optString("dbs");
				if (dbString == null || dbString.isBlank()) {
					editError(event, path+".no_dbs");
					return;
				}
				List<String> dbs = List.of(dbString.split(";"));
				bot.getSettings().addServer(guildId, dbs);
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_add")
						.formatted(guildId, String.join(", ", dbs)))
					.build()
				);
			} else {
				bot.getSettings().removeServer(guildId);
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_remove").formatted(guildId))
					.build()
				);
			}
		}
	}

	private class View extends SlashCommand {
		public View() {
			this.name = "view";
			this.path = "bot.owner.settings.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			SettingsManager settings = bot.getSettings();

			StringBuilder builder = new StringBuilder("### Database settings\n")
				.append("Verify database enabled: ")
				.append(settings.isDbVerifyDisabled()?Constants.FAILURE:Constants.SUCCESS).append("\n")
				.append("Player database disabled: ")
				.append(settings.isDbPlayerDisabled()?Constants.FAILURE:Constants.SUCCESS).append("\n")
				.append("\nWhitelisted bots:\n> ");
			settings.getBotWhitelist().forEach(id -> builder.append(id).append(", "));
			builder.append("\n\nDatabases:");
			settings.getGameServers().forEach((name, info) -> builder.append("\n> `%s`: '%s' (`#%06X`)"
				.formatted(name, info.getTitle(), info.getColor())
			));
			builder.append("\n\nServers:");
			settings.getServers().forEach((id, dbs) -> builder.append("\n> `%s`: %s"
				.formatted(id, String.join(", ", dbs))
			));

			event.getHook().editOriginal(builder.toString()).queue();
		}
	}
}

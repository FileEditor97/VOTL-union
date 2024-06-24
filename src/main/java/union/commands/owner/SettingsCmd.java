package union.commands.owner;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import union.App;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import java.util.List;

public class SettingsCmd extends CommandBase {

	public SettingsCmd(App bot) {
		super(bot);
		this.name = "settings";
		this.path = "bot.owner.settings";
		this.children = new SlashCommand[]{new Database(bot), new BotWhitelist(bot)};
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Database extends SlashCommand {
		public Database(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "database";
			this.path = "bot.owner.settings.database";
			this.options = List.of(
				new OptionData(OptionType.BOOLEAN, "player", lu.getText(path+".player.help")),
				new OptionData(OptionType.BOOLEAN, "verify", lu.getText(path+".verify.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
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
			createReply(event, builder.toString());
		}
	}

	private class BotWhitelist extends SlashCommand {
		public BotWhitelist(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "whitelist";
			this.path = "bot.owner.settings.whitelist";
			this.options = List.of(
				new OptionData(OptionType.BOOLEAN, "add", lu.getText(path+".add.help"), true),
				new OptionData(OptionType.STRING, "bot_id", lu.getText(path+".bot_id.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			long id;
			try {
				id = event.optLong("bot_id");
			} catch (NumberFormatException ex) {
				createError(event, "errors.unknown", ex.getMessage());
				return;
			}
			if (event.optBoolean("add")) {
				bot.getSettings().addBotWhitelisted(id);
				createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_add").formatted(id))
					.build()
				);
			} else {
				bot.getSettings().removeBotWhitelisted(id);
				createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_remove").formatted(id))
					.build()
				);
			}
		}
	}
}

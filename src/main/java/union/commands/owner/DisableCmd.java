package union.commands.owner;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import union.App;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.constants.CmdCategory;

import java.util.List;

public class DisableCmd extends CommandBase {

	public DisableCmd(App bot) {
		super(bot);
		this.name = "disable";
		this.path = "bot.owner.disable";
		this.children = new SlashCommand[]{new Database(bot)};
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
			this.name = "bot.owner.disable.database";
			this.options = List.of(
				new OptionData(OptionType.BOOLEAN, "player", lu.getText(path+".player.help")),
				new OptionData(OptionType.BOOLEAN, "verify", lu.getText(path+".verify.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			StringBuilder builder = new StringBuilder("Database settings changed\n");
			if (event.hasOption("player")) {
				boolean disabled = event.optBoolean("player");
				bot.getDBUtil().unionPlayers.setDisabled(disabled);
				builder.append("> Player database disabled: ").append(disabled?"Yes":"No").append("\n");
			}
			if (event.hasOption("verify")) {
				boolean disabled = event.optBoolean("verify");
				bot.getDBUtil().unionVerify.setDisabled(disabled);
				builder.append("> Verify database disabled: ").append(disabled?"Yes":"No").append("\n");
			}
			event.reply(builder.toString()).setEphemeral(true).queue();
		}
	}
}

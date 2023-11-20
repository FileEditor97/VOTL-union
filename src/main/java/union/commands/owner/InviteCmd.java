package union.commands.owner;

import union.App;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.constants.CmdCategory;

public class InviteCmd extends CommandBase {

	public InviteCmd(App bot) {
		super(bot);
		this.name = "invite";
		this.path = "bot.owner.invite";
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		createReply(event, lu.getText(event, path+".value")
			.replace("{bot_invite}", bot.getFileManager().getString("config", "bot-invite"))
		);
	}
}

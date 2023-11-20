package union.commands.verification;

import java.util.List;

import union.App;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.entities.User;

public class VerifyCheckCmd extends CommandBase {
	
	public VerifyCheckCmd(App bot) {
		super(bot);
		this.name = "vfcheck";
		this.path = "bot.verification.vfcheck";
		this.children = new SlashCommand[]{new Enable(bot), new Disable(bot), new Forced(bot)};
		this.module = CmdModule.VERIFICATION;
		this.category = CmdCategory.VERIFICATION;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Enable extends SlashCommand {

		public Enable(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "enable";
			this.path = "bot.verification.vfcheck.enable";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			if (bot.getDBUtil().verify.getVerifyRole(event.getGuild().getId()) == null) {
				createError(event, path+".no_role");
				return;
			}

			bot.getDBUtil().verify.enableCheck(event.getGuild().getId());
			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done"))
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}

	}

	private class Disable extends SlashCommand {

		public Disable(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "disable";
			this.path = "bot.verification.vfcheck.disable";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			bot.getDBUtil().verify.disableCheck(event.getGuild().getId());
			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done"))
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}
		
	}

	private class Forced extends SlashCommand {

		public Forced(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "forced";
			this.path = "bot.verification.vfcheck.forced";
			this.accessLevel = CmdAccessLevel.MOD;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			List<String> list = bot.getDBUtil().verifyCache.getForcedUsers();
			if (list.isEmpty()) {
				createReply(event, lu.getText(event, path+".empty"));
				return;
			}

			StringBuffer buffer = new StringBuffer("Forced users: \n");
			list.forEach(
				userId -> buffer.append(String.format("%s `%s`\n", User.fromId(userId).getAsMention(), userId))
			);
			createReply(event, buffer.toString());
		}
		
	}

}

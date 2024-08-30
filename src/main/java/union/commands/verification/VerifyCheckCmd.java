package union.commands.verification;

import java.util.List;

import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class VerifyCheckCmd extends CommandBase {
	
	public VerifyCheckCmd() {
		this.name = "vfcheck";
		this.path = "bot.verification.vfcheck";
		this.children = new SlashCommand[]{new Enable(), new Disable(), new Forced(), new Time()};
		this.module = CmdModule.VERIFICATION;
		this.category = CmdCategory.VERIFICATION;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Enable extends SlashCommand {
		public Enable() {
			this.name = "enable";
			this.path = "bot.verification.vfcheck.enable";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			if (bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId() == null) {
				createError(event, path+".no_role");
				return;
			}

			bot.getDBUtil().verifySettings.setCheckState(event.getGuild().getIdLong(), true);
			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done"))
				.build()
			);
		}
	}

	private class Disable extends SlashCommand {
		public Disable() {
			this.name = "disable";
			this.path = "bot.verification.vfcheck.disable";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			bot.getDBUtil().verifySettings.setCheckState(event.getGuild().getIdLong(), false);
			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done"))
				.build()
			);
		}
	}

	private class Forced extends SlashCommand {
		public Forced() {
			this.name = "forced";
			this.path = "bot.verification.vfcheck.forced";
			this.accessLevel = CmdAccessLevel.MOD;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			List<Long> list = bot.getDBUtil().verifyCache.getForcedUsers();
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

	private class Time extends SlashCommand {
		public Time() {
			this.name = "playtime";
			this.path = "bot.verification.vfcheck.playtime";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "hours", lu.getText(path+".hours.help"), true)
					.setRequiredRange(-1, 10)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			if (bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId() == null) {
				createError(event, path+".no_role");
				return;
			}

			int hours = event.optInteger("hours");
			bot.getDBUtil().verifySettings.setRequiredPlaytime(event.getGuild().getIdLong(), hours);
			if (hours == -1)
				createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_disabled"))
					.build()
				);
			else if (hours == 0)
				createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_once"))
					.build()
				);
			else
				createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_hours").formatted(hours))
					.build()
				);
		}
	}

}

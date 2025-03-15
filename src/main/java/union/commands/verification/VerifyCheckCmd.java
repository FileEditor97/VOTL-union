package union.commands.verification;

import java.sql.SQLException;
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
			event.deferReply().queue();
			if (bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId() == null) {
				editError(event, path+".no_role");
				return;
			}

			try {
				bot.getDBUtil().verifySettings.setCheckState(event.getGuild().getIdLong(), true);
			} catch (SQLException e) {
				editErrorDatabase(event, e, "vfcheck enable");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
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
			event.deferReply().queue();

			try {
				bot.getDBUtil().verifySettings.setCheckState(event.getGuild().getIdLong(), false);
			} catch (SQLException e) {
				editErrorDatabase(event, e, "vfcheck disable");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
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
			event.deferReply().queue();
			List<Long> list = bot.getDBUtil().verifyCache.getForcedUsers();
			if (list.isEmpty()) {
				editMsg(event, lu.getText(event, path+".empty"));
				return;
			}

			StringBuffer buffer = new StringBuffer("Forced users: \n");
			list.forEach(
				userId -> buffer.append(String.format("%s `%s`\n", User.fromId(userId).getAsMention(), userId))
			);
			editMsg(event, buffer.toString());
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
			event.deferReply().queue();
			if (bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId() == null) {
				editError(event, path+".no_role");
				return;
			}

			int hours = event.optInteger("hours");
			try {
				bot.getDBUtil().verifySettings.setRequiredPlaytime(event.getGuild().getIdLong(), hours);
			} catch (SQLException e) {
				editErrorDatabase(event, e, "vfcheck playtime");
				return;
			}
			if (hours == -1)
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_disabled"))
					.build()
				);
			else if (hours == 0)
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_once"))
					.build()
				);
			else
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_hours").formatted(hours))
					.build()
				);
		}
	}

}

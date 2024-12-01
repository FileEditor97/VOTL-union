package union.commands.verification;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.SteamUtil;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

public class CommentCmd extends CommandBase {
	public CommentCmd() {
		this.name = "comment";
		this.path = "bot.verification.comment";
		this.children = new SlashCommand[]{
			new Add(), new Delete(), new Clear()
		};
		this.category = CmdCategory.VERIFICATION;
		this.module = CmdModule.VERIFICATION;
		this.accessLevel = CmdAccessLevel.MOD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Add extends SlashCommand {
		public Add() {
			this.name = "add";
			this.path = "bot.verification.comment.add";
			this.options = List.of(
				new OptionData(OptionType.STRING, "steam", lu.getText(path+".steam.help"), true).setMaxLength(30),
				new OptionData(OptionType.STRING, "text", lu.getText(path+".text.help"), true).setMaxLength(400)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			String input = event.optString("steam");
			long steam64;
			if (Pattern.matches("^STEAM_[0-5]:[01]:\\d+$", input)) {
				steam64 = SteamUtil.convertSteamIDtoSteam64(input);
			} else {
				try {
					steam64 = Long.parseLong(input);
				} catch (NumberFormatException ex) {
					editErrorOther(event, ex.getMessage());
					return;
				}
			}
			String steamId;
			try {
				steamId = SteamUtil.convertSteam64toSteamID(steam64);
			} catch (NumberFormatException ex) {
				editErrorOther(event, "Incorrect SteamID provided\nInput: `%s`".formatted(steam64));
				return;
			}

			// Remove markdown and new line
			String text = MarkdownSanitizer.sanitize(event.optString("text")).replaceAll("\n", "");

			final Instant timestamp = Instant.now();
			final long authorId = event.getUser().getIdLong();

			// Add to db
			if (!bot.getDBUtil().comments.add(steam64, authorId, timestamp, text)) {
				editErrorOther(event, "Failed to save comment.");
				return;
			}
			// Reply
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(steamId, text))
				.build());
		}
	}

	private class Delete extends SlashCommand {
		public Delete() {
			this.name = "delete";
			this.path = "bot.verification.comment.delete";
			this.options = List.of(
				new OptionData(OptionType.STRING, "steam", lu.getText(path+".steam.help"), true).setMaxLength(30)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			String input = event.optString("steam");
			long steam64;
			if (Pattern.matches("^STEAM_[0-5]:[01]:\\d+$", input)) {
				steam64 = SteamUtil.convertSteamIDtoSteam64(input);
			} else {
				try {
					steam64 = Long.parseLong(input);
				} catch (NumberFormatException ex) {
					editErrorOther(event, ex.getMessage());
					return;
				}
			}
			String steamId;
			try {
				steamId = SteamUtil.convertSteam64toSteamID(steam64);
			} catch (NumberFormatException ex) {
				editErrorOther(event, "Incorrect SteamID provided\nInput: `%s`".formatted(steam64));
				return;
			}

			final long authorId = event.getUser().getIdLong();

			// Remove from DB
			if (!bot.getDBUtil().comments.removeComment(steam64, authorId)) {
				editErrorOther(event, "Failed to delete comment.");
				return;
			}
			// Reply
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(steamId))
				.build());
		}
	}

	private class Clear extends SlashCommand {
		public Clear() {
			this.name = "clear";
			this.path = "bot.verification.comment.clear";
			this.options = List.of(
				new OptionData(OptionType.STRING, "steam", lu.getText(path+".steam.help"), true).setMaxLength(30)
			);
			this.accessLevel = CmdAccessLevel.OPERATOR;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			String input = event.optString("steam");
			long steam64;
			if (Pattern.matches("^STEAM_[0-5]:[01]:\\d+$", input)) {
				steam64 = SteamUtil.convertSteamIDtoSteam64(input);
			} else {
				try {
					steam64 = Long.parseLong(input);
				} catch (NumberFormatException ex) {
					editErrorOther(event, ex.getMessage());
					return;
				}
			}
			String steamId;
			try {
				steamId = SteamUtil.convertSteam64toSteamID(steam64);
			} catch (NumberFormatException ex) {
				editErrorOther(event, "Incorrect SteamID provided\nInput: `%s`".formatted(steam64));
				return;
			}

			// Remove from DB
			if (!bot.getDBUtil().comments.removePlayer(steam64)) {
				editErrorOther(event, "Failed to clear comments.");
				return;
			}
			// Reply
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(steamId))
				.build());
		}
	}
}

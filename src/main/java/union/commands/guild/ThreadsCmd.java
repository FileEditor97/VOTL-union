package union.commands.guild;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import java.sql.SQLException;
import java.util.List;

public class ThreadsCmd extends CommandBase {

	public ThreadsCmd() {
		this.name = "threads";
		this.path = "bot.guild.threads";
		this.category = CmdCategory.GUILD;
		this.children = new SlashCommand[]{new Add(), new Remove(), new View()};
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Add extends SlashCommand {
		public Add() {
			this.name = "add";
			this.path = "bot.guild.threads.add";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
						.setChannelTypes(ChannelType.FORUM, ChannelType.TEXT)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			GuildChannel channel = event.optGuildChannel("channel");
			if (channel == null) {
				editError(event, path+".no_channel");
				return;
			}
			if (bot.getDBUtil().threadControl.exist(channel.getIdLong())) {
				editError(event, path+".exists");
				return;
			}

			try {
				bot.getDBUtil().threadControl.add(event.getGuild().getIdLong(), channel.getIdLong());
			} catch (SQLException e) {
				editErrorDatabase(event, e, "threads add channel");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(channel.getAsMention()))
				.build());
		}
	}

	private class Remove extends SlashCommand {
		public Remove() {
			this.name = "remove";
			this.path = "bot.guild.threads.remove";
			this.options = List.of(
				new OptionData(OptionType.STRING, "channel", lu.getText(path + ".channel.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			long channelId;
			try {
				channelId = Long.parseLong(event.optString("channel"));
			} catch (NumberFormatException e) {
				editError(event, path+".no_channel", e.getMessage());
				return;
			}
			Long guildId = bot.getDBUtil().threadControl.getGuildId(channelId);
			if (guildId == null || !guildId.equals(event.getGuild().getIdLong())) {
				editError(event, path+".not_found");
				return;
			}

			try {
				bot.getDBUtil().threadControl.remove(channelId);
			} catch (SQLException e) {
				editErrorDatabase(event, e, "threads remove channel");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(channelId))
				.build());
		}
	}

	private class View extends SlashCommand {
		public View() {
			this.name = "view";
			this.path = "bot.guild.threads.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".title"));

			List<Long> channelIds = bot.getDBUtil().threadControl.getChannelIds(event.getGuild().getIdLong());
			if (channelIds.isEmpty()) {
				editEmbed(event, builder
					.setDescription(lu.getText(event, path+".none"))
					.build()
				);
				return;
			}

			channelIds.forEach(id -> builder.appendDescription("<#%s>\n".formatted(id)));

			editEmbed(event, builder.build());
		}
	}

}

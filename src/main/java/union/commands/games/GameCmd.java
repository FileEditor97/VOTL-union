package union.commands.games;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import union.App;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import java.util.List;

public class GameCmd extends CommandBase {

	public GameCmd(App bot) {
		super(bot);
		this.name = "game";
		this.path = "bot.games.game";
		this.children = new SlashCommand[]{new Add(bot), new Remove(bot), new ViewChannels(bot), new Clear(bot)};
		this.category = CmdCategory.GAMES;
		this.module = CmdModule.GAMES;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Add extends SlashCommand {
		public Add(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "add";
			this.path = "bot.games.game.add";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT),
				new OptionData(OptionType.INTEGER, "max_strikes", lu.getText(path+".max_strikes.help"))
					.setRequiredRange(1,6)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			GuildChannel channel = event.optGuildChannel("channel");
			if (bot.getDBUtil().games.getMaxStrikes(channel.getIdLong()) != null) {
				createError(event, path+".already", "Channel: %s".formatted(channel.getAsMention()));
				return;
			}
			int maxStrikes = event.optInteger("max_strikes", 3);

			bot.getDBUtil().games.addChannel(event.getGuild().getIdLong(), channel.getIdLong(), maxStrikes);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(channel.getAsMention(), maxStrikes))
				.build());
		}
	}

	private class Remove extends SlashCommand {
		public Remove(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "remove";
			this.path = "bot.games.game.remove";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			GuildChannel channel = event.optGuildChannel("channel");
			if (bot.getDBUtil().games.getMaxStrikes(channel.getIdLong()) == null) {
				createError(event, path+".not_found", "Channel: %s".formatted(channel.getAsMention()));
				return;
			}

			bot.getDBUtil().games.removeChannel(channel.getIdLong());

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(channel.getAsMention()))
				.build());
		}
	}

	private class ViewChannels extends SlashCommand {
		public ViewChannels(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "view-channels";
			this.path = "bot.games.game.view-channels";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			List<Long> channels = bot.getDBUtil().games.getChannels(event.getGuild().getIdLong());
			if (channels.isEmpty()) {
				createReplyEmbed(event, bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getText(event, path+".none"))
					.build()
				);
				return;
			}

			StringBuilder builder = new StringBuilder();
			for (Long channelId : channels) {
				builder.append("<#").append(channelId).append(">\n");
			}

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setTitle(lu.getText(event, path+".embed_title"))
				.setDescription(builder.toString())
				.build()
			);
		}
	}

	private class Clear extends SlashCommand {
		public Clear(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "clear";
			this.path = "bot.games.game.clear";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			GuildChannel channel = event.optGuildChannel("channel");
			if (bot.getDBUtil().games.getMaxStrikes(channel.getIdLong()) == null) {
				createError(event, path+".not_found", "Channel: %s".formatted(channel.getAsMention()));
				return;
			}
			User user = event.optUser("user");
			if (user == null) {
				createError(event, path+".no_user");
				return;
			}

			bot.getDBUtil().games.clearStrikes(channel.getIdLong(), user.getIdLong());

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(user.getAsMention(), channel.getAsMention()))
				.build());
		}
	}

}

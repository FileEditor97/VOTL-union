package union.commands.guild;

import java.awt.Color;
import java.net.URL;
import java.util.List;
import java.util.Objects;

import union.App;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.command.SlashCommand;
import union.objects.command.SlashCommandEvent;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class SetupCmd extends CommandBase {

	public SetupCmd(App bot) {
		super(bot);
		this.name = "setup";
		this.path = "bot.guild.setup";
		this.children = new SlashCommand[]{new Main(bot), new PanelColor(bot), new AppealLink(bot)};
		this.category = CmdCategory.GUILD;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Main extends SlashCommand {
		
		public Main(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "main";
			this.path = "bot.guild.setup.main";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();

			if (bot.getDBUtil().guild.add(guildId)) {
				createReplyEmbed(event, 
					bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getText(event, path+".done"))
						.setColor(Constants.COLOR_SUCCESS)
						.build()
				);
				bot.getLogger().info(String.format("Added server through setup '%s' (%s) to DB.", guild.getName(), guildId));
			} else {
				createReplyEmbed(event, 
					bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getText(event, path+".exists"))
						.setColor(Constants.COLOR_WARNING)
						.build()
				);
			}
		}

	}

	private class PanelColor extends SlashCommand {

		public PanelColor(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "color";
			this.path = "bot.guild.setup.color";
			this.options = List.of(
				new OptionData(OptionType.STRING, "color", lu.getText(path+".color.help"), true).setRequiredLength(5, 11)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String guildId = event.getGuild().getId();
			String text = event.optString("color");

			Color color = bot.getMessageUtil().getColor(text);
			if (color == null) {
				createError(event, path+".no_color");
				return;
			}
			bot.getDBUtil().guild.setColor(guildId, color.getRGB() & 0xFFFFFF);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{color}", "#"+Integer.toHexString(color.getRGB() & 0xFFFFFF)))
				.setColor(color)
				.build());
		}

	}

	private class AppealLink extends SlashCommand {

		public AppealLink(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "appeal";
			this.path = "bot.guild.setup.appeal";
			this.options = List.of(
				new OptionData(OptionType.STRING, "link", lu.getText(path+".link.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String guildId = event.getGuild().getId();
			String text = event.optString("link");

			if (!isValidURL(text)) {
				createError(event, path+".not_valid", "Received unvalid URL: `%s`".formatted(text));
				return;
			}

			bot.getDBUtil().guild.setAppealLink(guildId, text);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{link}", text))
				.build());
		}

	}

	private static boolean isValidURL(String urlString) {
		try {
			URL url = new URL(urlString);
			url.toURI();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

}

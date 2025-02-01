package union.commands.level;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdModule;
import union.objects.ExpType;
import union.objects.constants.CmdCategory;
import union.utils.database.managers.LevelManager;

import java.util.List;

public class LeaderboardCmd extends CommandBase {
	public LeaderboardCmd() {
		this.name = "leaderboard";
		this.path = "bot.level.leaderboard";
		this.category = CmdCategory.LEVELS;
		this.module = CmdModule.LEVELS;
		this.options = List.of(
			new OptionData(OptionType.INTEGER, "type", lu.getText(path+".type.help"))
				.addChoice("Text", 1)
				.addChoice("Voice", 2)
		);
		this.cooldown = 30;
		this.cooldownScope = CooldownScope.USER;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		long authorId = event.getUser().getIdLong();

		ExpType expType = ExpType.values()[event.optInteger("type", 0)];
		int limit = expType.equals(ExpType.TOTAL) ? 10 : 20;
		LevelManager.TopInfo top = bot.getDBUtil().levels.getServerTop(event.getGuild().getIdLong(), expType, limit);

		EmbedBuilder embed = bot.getEmbedUtil().getEmbed(event)
			.setAuthor(lu.getText(event, path+".embed_title"), null, event.getGuild().getIconUrl());

		// Text top
		if (!expType.equals(ExpType.VOICE)) {
			String title = lu.getText(event, path+".top_text").formatted(limit);
			if (top.getTextTop().isEmpty()) {
				embed.addField(title, lu.getText(event, path+".empty"), true);
			} else {
				StringBuilder builder = new StringBuilder();
				top.getTextTop().forEach((place, user) -> {
					if (user.userId() == authorId)
						builder.append("\n**#%s | <@!%s> XP: `%s`**".formatted(place, user.userId(), user.exp()));
					else
						builder.append("\n#%s | <@!%s> XP: `%s`".formatted(place, user.userId(), user.exp()));
				});
				embed.addField(title, builder.toString(), true);
			}
		}

		if (!expType.equals(ExpType.TEXT)) {
			String title = lu.getText(event, path+".top_voice").formatted(limit);
			if (top.getVoiceTop().isEmpty()) {
				embed.addField(title, lu.getText(event, path+".empty"), true);
			} else {
				StringBuilder builder = new StringBuilder();
				top.getVoiceTop().forEach((place, user) -> {
					if (user.userId() == authorId)
						builder.append("\n**#%s | <@!%s> XP: `%s`**".formatted(place, user.userId(), user.exp()));
					else
						builder.append("\n#%s | <@!%s> XP: `%s`".formatted(place, user.userId(), user.exp()));
				});
				embed.addField(title, builder.toString(), true);
			}
		}

		editEmbed(event, embed.build());
	}
}

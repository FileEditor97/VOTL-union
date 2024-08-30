package union.commands.other;

import java.util.List;

import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class StatusCmd extends CommandBase {

	public StatusCmd() {
		this.name = "status";
		this.path = "bot.other.status";
		this.options = List.of(
			new OptionData(OptionType.BOOLEAN, "show", lu.getText(path+".show.help"))
		);
		this.category = CmdCategory.OTHER;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		DiscordLocale userLocale = event.getUserLocale();
		MessageEmbed embed = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setAuthor(event.getJDA().getSelfUser().getName(), event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.addField(
				lu.getLocalized(userLocale, "bot.other.status.embed.stats_title"),
				String.join(
					"\n",
					lu.getLocalized(userLocale, "bot.other.status.embed.stats.guilds").replace("{value}", String.valueOf(event.getJDA().getGuilds().size())),
					lu.getLocalized(userLocale, "bot.other.status.embed.stats.shard")
						.replace("{this}", String.valueOf(event.getJDA().getShardInfo().getShardId() + 1))
						.replace("{all}", String.valueOf(event.getJDA().getShardInfo().getShardTotal()))
				),
				false
			)
			.addField(lu.getLocalized(userLocale, "bot.other.status.embed.shard_title"),
				String.join(
					"\n",
					lu.getLocalized(userLocale, "bot.other.status.embed.shard.users").replace("{value}", String.valueOf(event.getJDA().getUsers().size())),
					lu.getLocalized(userLocale, "bot.other.status.embed.shard.guilds").replace("{value}", String.valueOf(event.getJDA().getGuilds().size()))
				),
				true
			)
			.addField("",
				String.join(
					"\n",
					lu.getLocalized(userLocale, "bot.other.status.embed.shard.text_channels").replace("{value}", String.valueOf(event.getJDA().getTextChannels().size())),
					lu.getLocalized(userLocale, "bot.other.status.embed.shard.voice_channels").replace("{value}", String.valueOf(event.getJDA().getVoiceChannels().size()))
				),
				true
			)
			.setFooter(lu.getLocalized(userLocale, "bot.other.status.embed.last_restart"))
			.setTimestamp(event.getClient().getStartTime())
			.build();
		
		createReplyEmbed(event, event.isFromGuild() && !event.optBoolean("show", false), embed);
	}

}

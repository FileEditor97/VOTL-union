package union.commands.other;

import java.util.List;

import union.App;
import union.commands.CommandBase;
import union.objects.command.SlashCommandEvent;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.objects.constants.Links;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.jagrosh.jdautilities.commons.JDAUtilitiesInfo;

public class AboutCmd extends CommandBase {

	public AboutCmd(App bot) {
		super(bot);
		this.name = "about";
		this.path = "bot.other.about";
		this.options = List.of(
			new OptionData(OptionType.BOOLEAN, "show", lu.getText(path+".show.help"))
		);
		this.category = CmdCategory.OTHER;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		DiscordLocale userLocale = event.getUserLocale();
		EmbedBuilder builder = null;

		if (event.isFromGuild()) {
			builder = bot.getEmbedUtil().getEmbed(event);
		} else {
			builder = bot.getEmbedUtil().getEmbed();
		}

		builder.setAuthor(event.getJDA().getSelfUser().getName(), event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.addField(
				lu.getLocalized(userLocale, "bot.other.about.embed.about_title")
					.replace("{name}", "union bot"),
				lu.getLocalized(userLocale, "bot.other.about.embed.about_value")
					.replace("{developer_name}", Constants.DEVELOPER_TAG)
					.replace("{developer_id}", Constants.DEVELOPER_ID),
				false
			)
			.addField(
				lu.getLocalized(userLocale, "bot.other.about.embed.commands_title"),
				lu.getLocalized(userLocale, "bot.other.about.embed.commands_value"),
				false
			)
			.addField(
				lu.getLocalized(userLocale, "bot.other.about.embed.bot_info.title"),
				String.join(
					"\n",
					lu.getLocalized(userLocale, "bot.other.about.embed.bot_info.bot_version").replace("{bot_version}", bot.VERSION),
					lu.getLocalized(userLocale, "bot.other.about.embed.bot_info.library")
						.replace("{jda_version}", JDAInfo.VERSION_MAJOR+"."+JDAInfo.VERSION_MINOR+"."+JDAInfo.VERSION_REVISION+"-"+JDAInfo.VERSION_CLASSIFIER)
						.replace("{jda_github}", JDAInfo.GITHUB)
						.replace("{chewtils_version}", JDAUtilitiesInfo.VERSION_MAJOR+"."+JDAUtilitiesInfo.VERSION_MINOR)
						.replace("{chewtils_github}", Links.CHEWTILS_GITHUB)
				),
				false
			)
			.addField(
				lu.getLocalized(userLocale, "bot.other.about.embed.links.title"),
				String.join(
					"\n",
					lu.getLocalized(userLocale, "bot.other.about.embed.links.discord").replace("{guild_invite}", Links.DISCORD),
					lu.getLocalized(userLocale, "bot.other.about.embed.links.github").replace("{github_url}", Links.GITHUB),
					lu.getLocalized(userLocale, "bot.other.about.embed.links.terms").replace("{terms_url}", Links.TERMS),
					lu.getLocalized(userLocale, "bot.other.about.embed.links.privacy").replace("{privacy_url}", Links.PRIVACY)
				),
				true
			)
			.addField(
				lu.getLocalized(userLocale, "bot.other.about.embed.links.unionteams_title"),
				String.join(
					"\n",
					lu.getLocalized(userLocale, "bot.other.about.embed.links.unionteams_website").replace("{unionteams}", Links.UNIONTEAMS)
				),
				true
			);
		
		createReplyEmbed(event, event.isFromGuild() ? !event.optBoolean("show", false) : false, builder.build());
	}

}

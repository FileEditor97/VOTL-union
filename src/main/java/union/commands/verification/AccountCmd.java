package union.commands.verification;

import java.util.List;
import java.util.regex.Pattern;

import union.App;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.command.SlashCommandEvent;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class AccountCmd extends CommandBase {
	
	public AccountCmd(App bot) {
		super(bot);
		this.name = "account";
		this.path = "bot.verification.account";
		this.options = List.of(
			new OptionData(OptionType.STRING, "steamid", lu.getText(path+".steamid.help")),
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"))
		);
		this.category = CmdCategory.VERIFICATION;
		this.module = CmdModule.VERIFICATION;
		this.accessLevel = CmdAccessLevel.MOD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		User optUser = event.optUser("user");
		if (optUser != null) {
			String userId = optUser.getId();
			String steam64 = bot.getDBUtil().verifyRequest.getSteam64(userId);
			if (steam64 == null) {
				createError(event, path+".not_found_steam", "Received: "+userId);
				return;
			}

			replyAccount(event, steam64, optUser);
			return;
		}

		String id = event.optString("steamid");
		if (id != null) {
			boolean matches = Pattern.matches("^STEAM_[0-5]:[01]:\\d+$", id);
			if (matches) {
				id = bot.getSteamUtil().convertSteamIDtoSteam64(id);
			}
			String discordId = bot.getDBUtil().verifyRequest.getDiscordId(id);
			if (discordId == null) {
				createError(event, path+".not_found_discord", "Received: "+id);
				return;
			}

			String steam64 = id;
			event.getJDA().retrieveUserById(discordId).queue(
				user -> {
					// create embed
					replyAccount(event, steam64, user);
				},
				failed -> {
					createError(event, path+".not_found_user", "User ID: "+discordId);
				});
			return;
		}

		createError(event, path+".no_options");
	}

	private void replyAccount(SlashCommandEvent event, final String steam64, User user) {
		String profileUrl = "https://steamcommunity.com/profiles/" + steam64;
		String avatarUrl = "https://avatars.cloudflare.steamstatic.com/" + bot.getDBUtil().verifyRequest.getSteamAvatarUrl(steam64) + "_full.jpg";
		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setFooter("ID: "+user.getId(), user.getEffectiveAvatarUrl())
			.setTitle(bot.getDBUtil().verifyRequest.getSteamName(steam64), profileUrl)
			.setThumbnail(avatarUrl)
			.addField(lu.getUserText(event, "bot.verification.account.field_steam"), bot.getSteamUtil().convertSteam64toSteamID(steam64), true)
			.addField(lu.getUserText(event, "bot.verification.account.field_steam"), steam64, true)
			.addField(lu.getUserText(event, path+".field_discord"), user.getAsMention(), false);
		
		event.replyEmbeds(builder.build()).queue();
	}
}

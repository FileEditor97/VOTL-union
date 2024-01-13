package union.commands.verification;

import java.util.List;
import java.util.regex.Pattern;

import union.App;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.database.managers.UnionPlayerManager.PlayerInfo;

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
			new OptionData(OptionType.STRING, "steamid", lu.getText(path+".steamid.help")).setMaxLength(30),
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"))
		);
		this.category = CmdCategory.VERIFICATION;
		this.module = CmdModule.VERIFICATION;
		this.accessLevel = CmdAccessLevel.HELPER;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		if (event.hasOption("user")) {
			User optUser = event.optUser("user");
			String userId = optUser.getId();
			
			String steam64 = bot.getDBUtil().verifyCache.getSteam64(userId);
			if (steam64 == null) {
				editError(event, path+".not_found_steam", "Received: "+userId);
				return;
			}

			replyAccountFull(event, steam64, optUser);
		} else if (event.hasOption("steamid")) {
			String id = event.optString("steamid");

			if (Pattern.matches("^STEAM_[0-5]:[01]:\\d+$", id)) {
				id = bot.getSteamUtil().convertSteamIDtoSteam64(id);
			}

			String discordId = bot.getDBUtil().verifyCache.getDiscordId(id);
			if (discordId == null) {
				replyAccountSteam(event, id);
			} else {
				String steam64 = id;
				event.getJDA().retrieveUserById(discordId).queue(
					user -> {
						// create embed
						replyAccountFull(event, steam64, user);
					},
					failed -> {
						editError(event, path+".not_found_user", "User ID: "+discordId);
					}
				);
			}
		} else {
			editError(event, path+".no_options");
		}
	}

	private void replyAccountFull(SlashCommandEvent event, final String steam64, User user) {
		String steamId;
		try {
			steamId = bot.getSteamUtil().convertSteam64toSteamID(steam64);
		} catch (NumberFormatException ex) {
			editError(event, "errors.unknown", "Incorrect SteamID provided\nInput: `%s`".formatted(steam64));
			return;
		}
		PlayerInfo playerInfo = bot.getDBUtil().unionPlayers.getPlayerInfo(event.getGuild().getId(), steamId);
		String profileUrl = "https://steamcommunity.com/profiles/" + steam64;
		String avatarUrl = "https://avatars.cloudflare.steamstatic.com/" + bot.getDBUtil().unionVerify.getSteamAvatarUrl(steam64) + "_full.jpg";
		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setFooter("ID: "+user.getId(), user.getEffectiveAvatarUrl())
			.setTitle(bot.getDBUtil().unionVerify.getSteamName(steam64), profileUrl)
			.setThumbnail(avatarUrl)
			.addField("Steam", steamId, true)
			.addField("Links", "> [UnionTeams](https://unionteams.ru/player/%s)\n> [SteamRep](https://steamrep.com/profiles/%s)".formatted(steam64, steam64), true)
			.addField(lu.getUserText(event, path+".field_rank"), "`%s`".formatted(playerInfo.getRank()), true)
			.addField(lu.getUserText(event, path+".field_playtime"), "%s %s".formatted(playerInfo.getPlayTime(), lu.getUserText(event, "misc.time.hours")), true)
			.addField(lu.getUserText(event, path+".field_discord"), user.getAsMention(), false);
		
		editHookEmbed(event, builder.build());
	}

	private void replyAccountSteam(SlashCommandEvent event, final String steam64) {
		String steamId;
		try {
			steamId = bot.getSteamUtil().convertSteam64toSteamID(steam64);
		} catch (NumberFormatException ex) {
			editError(event, "errors.unknown", "Incorrect SteamID provided\nInput: `%s`".formatted(steam64));
			return;
		}
		PlayerInfo playerInfo = bot.getDBUtil().unionPlayers.getPlayerInfo(event.getGuild().getId(), steamId);
		String profileUrl = "https://steamcommunity.com/profiles/" + steam64;
		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setTitle(bot.getDBUtil().unionVerify.getSteamName(steam64), profileUrl)
			.addField("Steam", steamId, true)
			.addField("Links", "> [UnionTeams](https://unionteams.ru/player/"+steam64+")", true)
			.addField(lu.getUserText(event, path+".field_rank"), "`%s`".formatted(playerInfo.getRank()), true)
			.addField(lu.getUserText(event, path+".field_playtime"), "%s %s".formatted(playerInfo.getPlayTime(), lu.getUserText(event, "misc.time.hours")), true)
			.addField(lu.getUserText(event, path+".field_discord"), lu.getText(event, path+".not_found_discord"), false);
		
		editHookEmbed(event, builder.build());
	}

}

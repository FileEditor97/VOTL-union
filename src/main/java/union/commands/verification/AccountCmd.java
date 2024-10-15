package union.commands.verification;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import net.dv8tion.jda.internal.utils.tuple.Pair;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.SteamUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class AccountCmd extends CommandBase {
	
	public AccountCmd() {
		this.name = "account";
		this.path = "bot.verification.account";
		this.options = List.of(
			new OptionData(OptionType.STRING, "steamid", lu.getText(path+".steamid.help")).setMaxLength(30),
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"))
		);
		this.category = CmdCategory.VERIFICATION;
		this.module = CmdModule.VERIFICATION;
		this.accessLevel = CmdAccessLevel.HELPER;
		this.cooldown = 10;
		this.cooldownScope = CooldownScope.USER;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		if (event.hasOption("user")) {
			User optUser = event.optUser("user");
			long userId = optUser.getIdLong();
			
			Long steam64 = bot.getDBUtil().verifyCache.getSteam64(userId);
			if (steam64 == null || steam64 == 0L) {
				editError(event, path+".not_found_steam", "Received: "+userId);
				return;
			}

			replyAccountFull(event, steam64, optUser);
		} else if (event.hasOption("steamid")) {
			String input = event.optString("steamid");

			long steam64;
			if (Pattern.matches("^STEAM_[0-5]:[01]:\\d+$", input)) {
				steam64 = SteamUtil.convertSteamIDtoSteam64(input);
			} else {
				try {
					steam64 = Long.parseLong(input);
				} catch (NumberFormatException ex) {
					editError(event, "errors.error", ex.getMessage());
					return;
				}
			}

			Long discordId = bot.getDBUtil().verifyCache.getDiscordId(steam64);
			if (discordId == null) {
				replyAccountSteam(event, steam64);
			} else {
				Long steam64copy = steam64;
				event.getJDA().retrieveUserById(discordId).queue(
					user -> replyAccountFull(event, steam64copy, user),
					failed -> editError(event, path+".not_found_user", "User ID: "+discordId)
				);
			}
		} else {
			editError(event, path+".no_options");
		}
	}

	private void replyAccountFull(SlashCommandEvent event, final Long steam64, User user) {
		String steamId;
		try {
			steamId = SteamUtil.convertSteam64toSteamID(steam64);
		} catch (NumberFormatException ex) {
			editError(event, "errors.error", "Incorrect SteamID provided\nInput: `%s`".formatted(steam64));
			return;
		}

		String profileUrl = "https://steamcommunity.com/profiles/" + steam64;
		Pair<String, String> profileInfo = bot.getDBUtil().unionVerify.getSteamInfo(steam64.toString());
		String profileName = Optional.ofNullable(profileInfo)
			.map(Pair::getLeft)
			.orElse("*Not found*");
		String avatarUrl = Optional.ofNullable(profileInfo)
			.map(Pair::getRight)
			.map("https://avatars.cloudflare.steamstatic.com/%s_full.jpg"::formatted)
			.orElse(null);
		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setFooter("ID: "+user.getId(), user.getEffectiveAvatarUrl())
			.setTitle(profileName, profileUrl)
			.setThumbnail(avatarUrl)
			.addField("Steam", steamId, true)
			.addField("Links", "> [UnionTeams](https://unionteams.ru/player/%s)\n> [SteamRep](https://steamrep.com/profiles/%<s)".formatted(steam64), true)
			.addField(lu.getText(event, path+".field_discord"), user.getAsMention(), true);
		
		bot.getDBUtil().unionPlayers.getPlayerInfo(event.getGuild().getIdLong(), steamId).forEach(playerInfo -> {
			String value = playerInfo.exists()
				? lu.getText(event, "bot.verification.account.field_info").formatted(playerInfo.getRank(), playerInfo.getPlayTime())
				: lu.getText(event, "bot.verification.account.no_data");
			builder.addField(playerInfo.getServerTitle(), value, false);
		});
		
		editEmbed(event, builder.build());
	}

	private void replyAccountSteam(SlashCommandEvent event, final Long steam64) {
		String steamId;
		try {
			steamId = SteamUtil.convertSteam64toSteamID(steam64);
		} catch (NumberFormatException ex) {
			editError(event, "errors.error", "Incorrect SteamID provided\nInput: `%s`".formatted(steam64));
			return;
		}

		String profileUrl = "https://steamcommunity.com/profiles/" + steam64;
		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setTitle(bot.getDBUtil().unionVerify.getSteamName(steam64.toString()), profileUrl)
			.addField("Steam", steamId, true)
			.addField("Links", "> [UnionTeams](https://unionteams.ru/player/%s)\n> [SteamRep](https://steamrep.com/profiles/%<s)".formatted(steam64), true)
			.addField(lu.getText(event, path+".field_discord"), lu.getText(event, path+".not_found_discord"), true);
		
		bot.getDBUtil().unionPlayers.getPlayerInfo(event.getGuild().getIdLong(), steamId).forEach(playerInfo -> {
			String value = playerInfo.exists()
				? lu.getText(event, "bot.verification.account.field_info").formatted(playerInfo.getRank(), playerInfo.getPlayTime())
				: lu.getText(event, "bot.verification.account.no_data");
			builder.addField(playerInfo.getServerTitle(), value, false);
		});
		
		editEmbed(event, builder.build());
	}

}

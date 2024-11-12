package union.commands.verification;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.jetbrains.annotations.Nullable;
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
import union.utils.database.managers.BanlistManager;
import union.utils.database.managers.UnionPlayerManager;

public class AccountCmd extends CommandBase {
	
	public AccountCmd() {
		this.name = "account";
		this.path = "bot.verification.account";
		this.options = List.of(
			new OptionData(OptionType.STRING, "info", lu.getText(path+".info.help"))
				.addChoice("Ranks (Normal)", "ranks")
				.addChoice("Checks (+Banlists)", "bans"),
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

			replyAccount(event, steam64, optUser);
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
				replyAccount(event, steam64, null);
			} else {
				Long steam64copy = steam64;
				event.getJDA().retrieveUserById(discordId).queue(
					user -> replyAccount(event, steam64copy, user),
					failed -> editError(event, path+".not_found_user", "User ID: "+discordId)
				);
			}
		} else {
			editError(event, path+".no_options");
		}
	}

	private void replyAccount(final SlashCommandEvent event, final Long steam64, @Nullable final User user) {
		if (event.optString("info", "default").equals("bans")) {
			replyAccountBans(event, steam64, user);
			return;
		}
		String steamId;
		try {
			steamId = SteamUtil.convertSteam64toSteamID(steam64);
		} catch (NumberFormatException ex) {
			editErrorOther(event, "Incorrect SteamID provided\nInput: `%s`".formatted(steam64));
			return;
		}

		String profileUrl = "https://steamcommunity.com/profiles/" + steam64;
		Pair<String, String> profileInfo = bot.getDBUtil().unionVerify.getSteamInfo(steam64);
		String profileName = Optional.ofNullable(profileInfo)
			.map(Pair::getLeft)
			.orElse("*Not found*");
		String avatarUrl = Optional.ofNullable(profileInfo)
			.map(Pair::getRight)
			.map("https://avatars.cloudflare.steamstatic.com/%s_full.jpg"::formatted)
			.orElse(null);

		String links = "> [UnionTeams](https://unionteams.ru/player/%s)".formatted(steam64);
		Integer fxUserId = bot.getDBUtil().unionVerify.getUserId(steam64);
		if (fxUserId != null) {
			links += "\n> [- Forum -](https://forum.unionteams.ru/members/%s)".formatted(fxUserId);
		}

		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setTitle(profileName, profileUrl)
			.setThumbnail(avatarUrl)
			.addField("Steam", steamId, true)
			.addField("Links", links, true);
		if (user == null) {
			builder.addField(lu.getText(event, path+".field_discord"), lu.getText(event, path+".not_found_discord"), true);
		} else {
			builder.addField(lu.getText(event, path+".field_discord"), user.getAsMention(), true)
				.setFooter("ID: "+user.getId(), user.getEffectiveAvatarUrl());
		}

		List<UnionPlayerManager.PlayerInfo> list = bot.getDBUtil().unionPlayers.getPlayerInfo(event.getGuild().getIdLong(), steamId);
		if (list.size() > 1) {
			list.stream().filter(UnionPlayerManager.PlayerInfo::exists).forEach(playerInfo -> {
				builder.addField(
					playerInfo.getServerInfo().getTitle(),
					lu.getText(event, "bot.verification.account.field_info").formatted(playerInfo.getRank(), playerInfo.getPlayTime()),
					false
				);
			});
		} else {
			list.forEach(playerInfo -> {
				String value = playerInfo.exists()
					? lu.getText(event, "bot.verification.account.field_info").formatted(playerInfo.getRank(), playerInfo.getPlayTime())
					: lu.getText(event, "bot.verification.account.no_data");
				builder.addField(playerInfo.getServerInfo().getTitle(), value, false);
			});
		}
		
		editEmbed(event, builder.build());
	}

	private void replyAccountBans(final SlashCommandEvent event, final Long steam64, @Nullable final User user) {
		String steamId;
		try {
			steamId = SteamUtil.convertSteam64toSteamID(steam64);
		} catch (NumberFormatException ex) {
			editError(event, "errors.error", "Incorrect SteamID provided\nInput: `%s`".formatted(steam64));
			return;
		}

		String profileUrl = "https://steamcommunity.com/profiles/" + steam64;
		Pair<String, String> profileInfo = bot.getDBUtil().unionVerify.getSteamInfo(steam64);
		String profileName = Optional.ofNullable(profileInfo)
			.map(Pair::getLeft)
			.orElse("*Not found*");
		String avatarUrl = Optional.ofNullable(profileInfo)
			.map(Pair::getRight)
			.map("https://avatars.cloudflare.steamstatic.com/%s_full.jpg"::formatted)
			.orElse(null);
		String links = ("""
			> [UnionTeams](https://unionteams.ru/player/%s)
			> [SteamRep](https://steamrep.com/profiles/%<s)
			> [SteamHistory](https://steamhistory.net/id/%<s)
			> [SteamidUK](https://steamid.uk/profile/%<s)""")
			.formatted(steam64);

		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setTitle(profileName, profileUrl)
			.setThumbnail(avatarUrl)
			.addField("Steam", "%s\n`%s`".formatted(steamId, steam64), true)
			.addField("Links", links, true);
		if (user==null) {
			builder.addField(lu.getText(event, path+".field_discord"), lu.getText(event, path+".not_found_discord"), true);
		} else {
			builder.addField(lu.getText(event, path+".field_discord"), user.getAsMention(), true)
				.setFooter("ID: "+user.getId(), user.getEffectiveAvatarUrl());
		}

		addBanlist(event, builder, steam64, steamId);

		editEmbed(event, builder.build());
	}

	private void addBanlist(SlashCommandEvent event, EmbedBuilder embedBuilder, long steam64, String steamId) {
		String links = ("""
			> [Perpheads](https://bans.perpheads.com/?search=%s)
			> [UnionRP C2](https://unionrp.info/hl2rp/bans/c2/?page=1&player=%s)
			> [FastRP](https://desk.fastrp.ru/bans/?page=1&steamid=%<s)""")
			.formatted(steam64, steamId);
		embedBuilder.addField(lu.getText(event, path+".field_banlist"), links, false);

		BanlistManager.BanlistData data = bot.getDBUtil().banlist.search(steam64);
		StringBuilder banlistBuilder = new StringBuilder();

		if (data.isAlium()) banlistBuilder.append("\n> [**")
			.append(lu.getText(event, path+".banlist_alium"))
			.append("**](https://raw.githubusercontent.com/Pika-Software/alium-ban-list/refs/heads/main/banned_user.cfg)");
		if (data.isMz()) banlistBuilder.append("\n> ").append(lu.getText(event, path+".banlist_mz"));
		Optional.ofNullable(data.getOcto()).ifPresent(map -> {
			banlistBuilder.append("\n> **")
				.append(lu.getText(event, path+".banlist_octo"))
				.append("**\n```\nReason: ")
				.append(map.getOrDefault("reason", "-"))
				.append("\nDetails: ")
				.append(map.getOrDefault("details", "-"))
				.append("\nCommand: ")
				.append(map.getOrDefault("command", "-"))
				.append("\n```");
		});
		Optional.ofNullable(data.getCustom()).ifPresent(reason -> {
			banlistBuilder.append("\n> **")
				.append(lu.getText(event, path+".banlist_custom"))
				.append("**\n```\nReason: ")
				.append(reason)
				.append("\n```");
		});

		if (!banlistBuilder.isEmpty())
			embedBuilder.addField(lu.getText(event, path+".banlist_found"), banlistBuilder.toString(), false);
	}
}

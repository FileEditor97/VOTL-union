package union.menus;

import net.dv8tion.jda.internal.utils.tuple.Pair;
import union.base.command.UserContextMenu;
import union.base.command.UserContextMenuEvent;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.Constants;
import union.utils.SteamUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import union.utils.database.managers.UnionPlayerManager;

import java.util.List;
import java.util.Optional;

public class AccountContext extends UserContextMenu {
	
	public AccountContext() {
		this.name = "account";
		this.path = "menus.account";
		this.module = CmdModule.VERIFICATION;
		this.accessLevel = CmdAccessLevel.HELPER;
	}

	@Override
	protected void execute(UserContextMenuEvent event) {
		event.deferReply(true).queue();
		User user = event.getTarget();

		long userId = user.getIdLong();
		Long steam64 = bot.getDBUtil().verifyCache.getSteam64(userId);
		if (steam64 == null || steam64 == 0L) {
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.verification.account.not_found_steam", "Received: "+userId)).queue();
			return;
		}

		String steamId = SteamUtil.convertSteam64toSteamID(steam64);
		String profileUrl = "https://steamcommunity.com/profiles/" + steam64;
		Pair<String, String> profileInfo = bot.getDBUtil().unionVerify.getSteamInfo(steam64);
		String avatarUrl = Optional.ofNullable(profileInfo)
			.map(Pair::getRight)
			.map("https://avatars.cloudflare.steamstatic.com/%s_full.jpg"::formatted)
			.orElse(null);
		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setFooter("ID: "+user.getId(), user.getEffectiveAvatarUrl())
			.setTitle(profileInfo.getLeft(), profileUrl)
			.setThumbnail(avatarUrl)
			.addField("Steam", steamId, true)
			.addField("Links", "> [UnionTeam](https://unionteams.ru/player/%s)\n> [SteamRep](https://steamrep.com/profiles/%<s)".formatted(steam64), true)
			.addField(lu.getText(event, "bot.verification.account.field_discord"), user.getAsMention(), true);

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
		
		event.getHook().editOriginalEmbeds(builder.build()).queue();
	}
}

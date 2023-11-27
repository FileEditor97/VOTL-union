package union.menus;

import union.App;
import union.base.command.UserContextMenu;
import union.base.command.UserContextMenuEvent;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.PlayerInfo;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;

public class AccountContext extends UserContextMenu {
	
	public AccountContext(App bot) {
		this.bot = bot;
		this.lu = bot.getLocaleUtil();
		this.name = "account";
		this.path = "menus.account";
		this.module = CmdModule.VERIFICATION;
		this.accessLevel = CmdAccessLevel.HELPER;
	}

	@Override
	protected void execute(UserContextMenuEvent event) {
		event.deferReply(true).queue();
		User user = event.getTarget();

		String userId = user.getId();
		String steam64 = bot.getDBUtil().verifyCache.getSteam64(userId);
		if (steam64 == null) {
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.verification.account.not_found_steam", "Received: "+userId)).queue();
			return;
		}

		String steamId = bot.getSteamUtil().convertSteam64toSteamID(steam64);
		PlayerInfo playerInfo = bot.getDBUtil().unionPlayers.getPlayerInfo(event.getGuild().getId(), steamId);
		String profileUrl = "https://steamcommunity.com/profiles/" + steam64;
		String avatarUrl = "https://avatars.cloudflare.steamstatic.com/" + bot.getDBUtil().unionVerify.getSteamAvatarUrl(steam64) + "_full.jpg";
		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setFooter("ID: "+user.getId(), user.getEffectiveAvatarUrl())
			.setTitle(bot.getDBUtil().unionVerify.getSteamName(steam64), profileUrl)
			.setThumbnail(avatarUrl)
			.addField("Steam", steamId, true)
			.addField("Links", "> [UnionTeams](https://unionteams.ru/player/"+steam64+")", true)
			.addField(lu.getUserText(event, "bot.verification.account.field_rank"), "`%s`".formatted(playerInfo.getRank()), true)
			.addField(lu.getUserText(event, "bot.verification.account.field_playtime"), "**%s** %s".formatted(playerInfo.getPlayTime(), lu.getUserText(event, "misc.time.hours")), true)
			.addField(lu.getUserText(event, "bot.verification.account.field_discord"), user.getAsMention(), false);
		
		event.getHook().editOriginalEmbeds(builder.build()).queue();
	}
}

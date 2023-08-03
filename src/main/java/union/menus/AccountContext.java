package union.menus;

import union.App;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.command.UserContextMenu;
import union.objects.command.UserContextMenuEvent;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;

public class AccountContext extends UserContextMenu {
	
	public AccountContext(App bot) {
		this.bot = bot;
		this.lu = bot.getLocaleUtil();
		this.name = "UnionTeams Account";
		this.module = CmdModule.VERIFICATION;
		this.accessLevel = CmdAccessLevel.MOD;
	}

	@Override
	protected void execute(UserContextMenuEvent event) {
		User user = event.getTarget();

		String userId = user.getId();
		String steam64 = bot.getDBUtil().verifyRequest.getSteam64(userId);
		if (steam64 == null) {
			event.replyEmbeds(bot.getEmbedUtil().getError(event, "bot.verification.account.not_found_steam", "Received: "+userId)).setEphemeral(true).queue();
			return;
		}

		String profileUrl = "https://steamcommunity.com/profiles/" + steam64;
		String avatarUrl = "https://avatars.cloudflare.steamstatic.com/" + bot.getDBUtil().verifyRequest.getSteamAvatarUrl(steam64) + "_full.jpg";
		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setFooter("ID: "+user.getId(), user.getEffectiveAvatarUrl())
			.setTitle(bot.getDBUtil().verifyRequest.getSteamName(steam64), profileUrl)
			.setThumbnail(avatarUrl)
			.addField(lu.getUserText(event, "bot.verification.account.field_steam"), bot.getSteamUtil().convertSteam64toSteamID(steam64), false)
			.addField(lu.getUserText(event, "bot.verification.account.field_discord"), user.getAsMention(), false);
		
		event.replyEmbeds(builder.build()).setEphemeral(true).queue();
	}
}

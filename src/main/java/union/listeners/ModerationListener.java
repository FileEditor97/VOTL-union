package union.listeners;

import union.App;
import union.objects.CaseType;
import union.objects.annotation.NotNull;
import union.utils.database.DBUtil;
import union.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ModerationListener extends ListenerAdapter {

	//private final App bot;
	private final DBUtil db;

	public ModerationListener(App bot) {
		//this.bot = bot;
		this.db = bot.getDBUtil();
	}

	@Override
	public void onGuildBan(@NotNull GuildBanEvent event) {}

	@Override
    public void onGuildUnban(@NotNull GuildUnbanEvent event) {
		CaseData banData = db.cases.getMemberActive(event.getUser().getIdLong(), event.getGuild().getIdLong(), CaseType.BAN);
		if (banData != null) {
			db.cases.setInactive(banData.getCaseIdInt());
		}
	}

	@Override
	public void onGuildMemberUpdateTimeOut(@NotNull GuildMemberUpdateTimeOutEvent event) {
		if (event.getNewTimeOutEnd() == null) {
			// timeout removed by moderator
			CaseData timeoutData = db.cases.getMemberActive(event.getUser().getIdLong(), event.getGuild().getIdLong(), CaseType.MUTE);
			if (timeoutData != null) {
				db.cases.setInactive(timeoutData.getCaseIdInt());
			}
		}
	}

}

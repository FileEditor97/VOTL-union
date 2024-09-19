package union.listeners;

import union.App;
import union.objects.CaseType;
import union.objects.annotation.NotNull;
import union.objects.logs.LogType;
import union.utils.database.DBUtil;
import union.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Objects;

public class ModerationListener extends ListenerAdapter {

	private final App bot;
	private final DBUtil db;

	public ModerationListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	@Override
	public void onGuildBan(@NotNull GuildBanEvent event) {
		// Log
		if (!db.getLogSettings(event.getGuild()).enabled(LogType.MODERATION)) return;
		event.getGuild().retrieveAuditLogs()
			.type(ActionType.BAN)
			.limit(1)
			.queue(list -> {
				if (list.isEmpty()) return;
				AuditLogEntry entry = list.get(0);
				if (Objects.equals(entry.getUser(), event.getJDA().getSelfUser())) return;  // Ignore self
				bot.getLogger().mod.onUserBan(entry, event.getUser());
			});
	}

	@Override
    public void onGuildUnban(@NotNull GuildUnbanEvent event) {
		CaseData banData = db.cases.getMemberActive(event.getUser().getIdLong(), event.getGuild().getIdLong(), CaseType.BAN);
		if (banData != null) {
			db.cases.setInactive(banData.getRowId());
		}
		// Log
		if (!db.getLogSettings(event.getGuild()).enabled(LogType.MODERATION)) return;
		event.getGuild().retrieveAuditLogs()
			.type(ActionType.UNBAN)
			.limit(1)
			.queue(list -> {
				if (list.isEmpty()) return;
				AuditLogEntry entry = list.get(0);
				if (Objects.equals(entry.getUser(), event.getJDA().getSelfUser())) return;  // Ignore self
				bot.getLogger().mod.onUserUnban(entry, event.getUser());
			});
	}

	@Override
	public void onGuildMemberUpdateTimeOut(@NotNull GuildMemberUpdateTimeOutEvent event) {
		if (event.getNewTimeOutEnd() == null) {
			// Timeout removed by moderator
			CaseData timeoutData = db.cases.getMemberActive(event.getUser().getIdLong(), event.getGuild().getIdLong(), CaseType.MUTE);
			if (timeoutData != null) {
				// Remove active case for time-out
				db.cases.setInactive(timeoutData.getRowId());
			}
			// Log
			if (!db.getLogSettings(event.getGuild()).enabled(LogType.MODERATION)) return;
			event.getGuild().retrieveAuditLogs()
				.type(ActionType.MEMBER_UPDATE)
				.limit(1)
				.queue(list -> {
					if (list.isEmpty()) return;
					AuditLogEntry entry = list.get(0);
					if (Objects.equals(entry.getUser(), event.getJDA().getSelfUser())) return;  // Ignore self
					if (entry.getChangeByKey(AuditLogKey.MEMBER_TIME_OUT) == null) return; // Not timeout
					bot.getLogger().mod.onUserTimeoutRemoved(entry, event.getUser());
				});
		} else {
			// Timeout updated or set
			if (!db.getLogSettings(event.getGuild()).enabled(LogType.MODERATION)) return;
			event.getGuild().retrieveAuditLogs()
				.type(ActionType.MEMBER_UPDATE)
				.limit(1)
				.queue(list -> {
					if (list.isEmpty()) return;
					AuditLogEntry entry = list.get(0);
					if (Objects.equals(entry.getUser(), event.getJDA().getSelfUser())) return;  // Ignore self
					if (entry.getChangeByKey(AuditLogKey.MEMBER_TIME_OUT) == null) return; // Not timeout
					bot.getLogger().mod.onUserTimeoutUpdated(entry, event.getUser(), event.getNewTimeOutEnd());
				});
		}
	}

}

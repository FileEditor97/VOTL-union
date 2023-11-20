package union.helper;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.UnavailableGuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GuildListener extends ListenerAdapter {

	private final Helper helper;

	public GuildListener(Helper helper) {
		this.helper = helper;
	}
	
	@Override
	public void onGuildAuditLogEntryCreate(GuildAuditLogEntryCreateEvent event) {
		if (event.getEntry().getType() == ActionType.BAN || event.getEntry().getType() == ActionType.UNBAN) {
			UserSnowflake admin = UserSnowflake.fromId(event.getEntry().getUserId());
			// Ignore actions made by both bots
			if (admin != null && (admin.equals(helper.getJDA().getSelfUser()) || admin.equals(helper.getMainJDA().getSelfUser()))) return;

			// Get master guilds IDs and send logs to them
			helper.getDBUtil().group.getGuildGroups(event.getGuild().getId()).forEach(groupId -> 
				helper.getLogListener().group.helperInformAction(groupId, event.getGuild(), event.getEntry())
			);
		}
	}

	@Override
	public void onGuildLeave(GuildLeaveEvent event) {
		helper.getDBUtil().group.getGuildGroups(event.getGuild().getId()).forEach(groupId -> 
			helper.getLogListener().group.helperInformLeave(groupId, event.getGuild(), event.getGuild().getId())
		);
	}

	@Override
	public void onUnavailableGuildLeave(UnavailableGuildLeaveEvent event) {
		helper.getDBUtil().group.getGuildGroups(event.getGuildId()).forEach(groupId -> 
			helper.getLogListener().group.helperInformLeave(groupId, null, event.getGuildId())
		);
	}
}

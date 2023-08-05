package union.helper;

import java.util.List;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GuildListener extends ListenerAdapter {

	private final Helper helper;

	public GuildListener(Helper helper) {
		this.helper = helper;
	}
	
	@Override
	public void onGuildAuditLogEntryCreate(GuildAuditLogEntryCreateEvent event) {
		if (event.getEntry().getType() == ActionType.BAN || event.getEntry().getType() == ActionType.UNBAN) {
			User admin = event.getEntry().getUser();
			// Ignore actions made by both bots
			if (admin.equals(helper.getJDA().getSelfUser()) || admin.equals(helper.getBotJDA().getSelfUser())) return;

			// Get master guilds IDs and send logs to them
			List<Integer> groupIds = helper.getDBUtil().group.getGuildGroups(event.getGuild().getId());
			if (groupIds.isEmpty()) return;

			for (Integer groupId : groupIds) {
				helper.getLogListener().onHelperInform(groupId, event.getGuild(), event.getEntry());
			}
		}
	}
}

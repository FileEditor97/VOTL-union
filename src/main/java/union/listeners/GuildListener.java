package union.listeners;

import org.jetbrains.annotations.NotNull;
import union.App;
import union.objects.logs.LogType;
import union.utils.database.DBUtil;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.sql.SQLException;

public class GuildListener extends ListenerAdapter {

	private final App bot;
	private final DBUtil db;

	public GuildListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	@Override
	public void onGuildJoin(@NotNull GuildJoinEvent event) {
		String guildId = event.getGuild().getId();
        bot.getAppLogger().info("Joined guild '{}'({})", event.getGuild().getName(), guildId);
	}

	@Override
	public void onGuildLeave(@NotNull GuildLeaveEvent event) {
		long guildIdLong = event.getGuild().getIdLong();
		bot.getAppLogger().info("Left guild '{}'({})", event.getGuild().getName(), guildIdLong);

		// Deletes every information connected to this guild from bot's DB (except ban tables)
		// May be dangerous, but provides privacy
		for (Integer groupId : db.group.getGuildGroups(guildIdLong)) {
			String groupName = db.group.getName(groupId);
			String guildName = event.getGuild().getName();
			Long masterId = db.group.getOwner(groupId);

			try {
				Guild master = event.getJDA().getGuildById(masterId);
				String masterIcon = event.getJDA().getGuildById(masterId).getIconUrl();
				bot.getLogger().sendLog(master, LogType.GROUP,
					() -> bot.getLogEmbedUtil().groupOwnerLeftEmbed(master.getLocale(), masterId, masterIcon, guildName, guildIdLong, groupId, groupName)
				);
			} catch (Exception ignored) {}
		}
		for (Integer groupId : db.group.getOwnedGroups(guildIdLong)) {
			String groupName = db.group.getName(groupId);
			String ownerIcon = event.getGuild().getIconUrl();
			for (Long memberId : db.group.getGroupMembers(groupId)) {
				try {
					Guild member = event.getJDA().getGuildById(memberId);

					bot.getLogger().sendLog(member, LogType.GROUP,
						() -> bot.getLogEmbedUtil().groupMemberDeletedEmbed(member.getLocale(), guildIdLong, ownerIcon, groupId, groupName)
					);
				} catch (Exception ignored) {}
			}
			ignoreExc(() -> db.group.clearGroup(groupId));
		}
		ignoreExc(() -> db.group.removeGuildFromGroups(guildIdLong));
		ignoreExc(() -> db.group.deleteGuildGroups(guildIdLong));

		ignoreExc(() -> db.access.removeAll(guildIdLong));
		ignoreExc(() -> db.webhook.removeAll(guildIdLong));
		ignoreExc(() -> db.verifySettings.remove(guildIdLong));
		ignoreExc(() -> db.ticketSettings.remove(guildIdLong));
		ignoreExc(() -> db.role.removeAll(guildIdLong));
		ignoreExc(() -> db.guildVoice.remove(guildIdLong));
		ignoreExc(() -> db.panels.deleteAll(guildIdLong));
		ignoreExc(() -> db.tags.deleteAll(guildIdLong));
		ignoreExc(() -> db.tempRole.removeAll(guildIdLong));
		ignoreExc(() -> db.autopunish.removeGuild(guildIdLong));
		ignoreExc(() -> db.strike.removeGuild(guildIdLong));
		ignoreExc(() -> db.logs.removeGuild(guildIdLong));
		ignoreExc(() -> db.logExemption.removeGuild(guildIdLong));
		ignoreExc(() -> db.threadControl.removeAll(guildIdLong));
		ignoreExc(() -> db.games.removeGuild(guildIdLong));
		ignoreExc(() -> db.modReport.removeGuild(guildIdLong));
		ignoreExc(() -> db.persistent.removeGuild(guildIdLong));
		ignoreExc(() -> db.levels.remove(guildIdLong));
		ignoreExc(() -> db.levelRoles.removeGuild(guildIdLong));

		ignoreExc(() -> db.guildSettings.remove(guildIdLong));

		bot.getAppLogger().info("Automatically removed guild '{}'({}) from db.", event.getGuild().getName(), guildIdLong);
	}

	private void ignoreExc(RunnableExc runnable) {
		try {
			runnable.run();
		} catch (SQLException ignored) {}
	}

	@FunctionalInterface public interface RunnableExc { void run() throws SQLException; }

}

package union.listeners;

import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

import union.App;
import union.objects.CaseType;
import union.objects.LogChannels;
import union.objects.annotation.NotNull;
import union.utils.database.DBUtil;
import union.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

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
		bot.getLogger().info("Joined guild '"+event.getGuild().getName()+"'("+guildId+")");
	}

	@Override
	public void onGuildLeave(@NotNull GuildLeaveEvent event) {
		String guildId = event.getGuild().getId();
		long guildIdLong = event.getGuild().getIdLong();
		bot.getLogger().info("Left guild '%s'(%s)".formatted(event.getGuild().getName(), guildId));

		// Deletes every information connected to this guild from bot's DB (except ban tables)
		// May be dangerous, but provides privacy
		for (Integer groupId : db.group.getGuildGroups(guildIdLong)) {
			String groupName = db.group.getName(groupId);
			String guildName = event.getGuild().getName();
			Long masterId = db.group.getOwner(groupId);

			try {
				Guild master = event.getJDA().getGuildById(masterId);
				String masterIcon = event.getJDA().getGuildById(masterId).getIconUrl();
				bot.getGuildLogger().sendMessageEmbed(master, LogChannels.GROUPS,
					() -> bot.getLogUtil().groupOwnerLeftEmbed(master.getLocale(), masterId, masterIcon, guildName, guildIdLong, groupId, groupName)
				);
			} catch (Exception ex) {}
		}
		for (Integer groupId : db.group.getOwnedGroups(guildIdLong)) {
			String groupName = db.group.getName(groupId);
			String ownerIcon = event.getGuild().getIconUrl();
			for (Long memberId : db.group.getGroupMembers(groupId)) {
				try {
					Guild member = event.getJDA().getGuildById(memberId);
					
					bot.getGuildLogger().sendMessageEmbed(member, LogChannels.GROUPS,
						() -> bot.getLogUtil().groupMemberDeletedEmbed(member.getLocale(), guildIdLong, ownerIcon, groupId, groupName)
					);
				} catch (Exception ex) {}
			}
			db.group.clearGroup(groupId);
		}
		db.group.removeGuildFromGroups(guildIdLong);
		db.group.deleteGuildGroups(guildIdLong);

		db.access.removeAll(guildId);
		db.webhook.removeAll(guildIdLong);
		db.verifySettings.remove(guildIdLong);
		db.ticketSettings.remove(guildId);
		db.role.removeAll(guildId);
		db.guildVoice.remove(guildIdLong);
		db.panels.deleteAll(guildId);
		db.tags.deleteAll(guildId);
		db.tempRole.removeAll(guildId);
		db.autopunish.removeGuild(guildIdLong);
		db.strike.removeGuild(guildIdLong);
		db.logs.removeGuild(guildIdLong);
		
		db.guildSettings.remove(guildIdLong);

		bot.getLogger().info("Automatically removed guild '%s'(%s) from db.".formatted(event.getGuild().getName(), guildId));
	}

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
			CaseData banData = db.cases.getMemberActive(event.getUser().getIdLong(), event.getGuild().getIdLong(), CaseType.MUTE);
			db.cases.setInactive(banData.getCaseIdInt());
		}
	}

	@Override
	public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
		// Checks cache on local DB, if user is verified, gives out the role
		long userId = event.getUser().getIdLong();
		
		if (db.verifyCache.isVerified(userId)) {
			Guild guild = event.getGuild();
			// Check if user is blacklisted
			List<Integer> groupIds = new ArrayList<Integer>();
			groupIds.addAll(db.group.getOwnedGroups(guild.getIdLong()));
			groupIds.addAll(db.group.getGuildGroups(guild.getIdLong()));
			Long cachedSteam64 = db.verifyCache.getSteam64(userId);
			for (int groupId : groupIds) {
				// if user is blacklisted in group either by discordID or Steam64
				// and joined server is not appeal server - do not add verify role
				if (db.blacklist.inGroupUser(groupId, userId) && db.group.getAppealGuildId(groupId)!=guild.getIdLong()) return;
				if (cachedSteam64!=null && db.blacklist.inGroupSteam64(groupId, cachedSteam64) && db.group.getAppealGuildId(groupId)!=guild.getIdLong()) return;
			}

			Long roleId = db.getVerifySettings(guild).getRoleId();
			if (roleId == null) return;
			Role role = guild.getRoleById(roleId);
			if (role == null) return;

			guild.addRoleToMember(event.getUser(), role).reason(cachedSteam64 == null ? "Autocheck: Forced" : "Autocheck: Account linked - "+cachedSteam64).queue(success -> {
				bot.getLogListener().verify.onVerified(event.getUser(), cachedSteam64, guild);
			});
		}
	}
	
	@Override
	public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
		// When user leaves guild, check if there are any records in DB that would be better to remove.
		// This does not consider clearing User DB, when bot leaves guild.
		String guildId = event.getGuild().getId();
		String userId = event.getUser().getId();

		if (db.access.getUserLevel(guildId, userId) != null) {
			db.access.removeUser(guildId, userId);
		}
		db.user.remove(event.getUser().getIdLong());
		if (db.ticketSettings.getAutocloseLeft(guildId)) {
			db.ticket.getOpenedChannel(userId, guildId).stream().forEach(channelId -> {
				db.ticket.closeTicket(Instant.now(), channelId, "Ticket's author left the server");
				GuildChannel channel = event.getGuild().getGuildChannelById(channelId);
				if (channel != null) channel.delete().reason("Author left").queue();
			});
		}
	}
}

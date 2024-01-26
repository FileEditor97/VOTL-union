package union.listeners;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

import union.App;
import union.objects.CaseType;
import union.objects.CmdAccessLevel;
import union.objects.LogChannels;
import union.utils.database.DBUtil;
import union.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GuildListener extends ListenerAdapter {

	private final App bot;
	private final DBUtil db;

	public GuildListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	@Override
	public void onGuildJoin(@Nonnull GuildJoinEvent event) {
		String guildId = event.getGuild().getId();
		bot.getLogger().info("Joined guild '"+event.getGuild().getName()+"'("+guildId+")");
	}

	@Override
	public void onGuildLeave(@Nonnull GuildLeaveEvent event) {
		String guildId = event.getGuild().getId();

		// Deletes every information connected to this guild from bot's DB (except ban tables)
		// May be dangerous, but provides privacy
		for (Integer groupId : db.group.getGuildGroups(guildId)) {
			String groupName = db.group.getName(groupId);
			String guildName = event.getGuild().getName();
			String masterId = db.group.getMaster(groupId);
			String masterIcon = event.getJDA().getGuildById(masterId).getIconUrl();

			String masterChannelId = db.guild.getLogChannel(LogChannels.GROUPS, masterId);
			if (masterChannelId != null) {
				TextChannel channel = event.getJDA().getTextChannelById(masterChannelId);
				if (channel != null) {
					try {
						MessageEmbed masterEmbed = bot.getLogUtil().groupOwnerLeftEmbed(channel.getGuild().getLocale(), masterId, masterIcon, guildName, guildId, groupId, groupName);
						channel.sendMessageEmbeds(masterEmbed).queue();
					} catch (InsufficientPermissionException ex) {}
				}
			}
		}
		for (Integer groupId : db.group.getOwnedGroups(guildId)) {
			String groupName = db.group.getName(groupId);
			for (String gid : db.group.getGroupGuildIds(groupId)) {
				String channelId = db.guild.getLogChannel(LogChannels.GROUPS, gid);
				if (channelId == null) {
					continue;
				}
				TextChannel channel = event.getJDA().getTextChannelById(channelId);
				if (channel == null) {
					continue;
				}

				try {
					MessageEmbed embed = bot.getLogUtil().groupMemberDeletedEmbed(channel.getGuild().getLocale(), guildId, event.getGuild().getIconUrl(), groupId, groupName);
					channel.sendMessageEmbeds(embed).queue();
				} catch (InsufficientPermissionException ex) {
					continue;
				}
			}
			db.group.clearGroup(groupId);
		}
		db.group.removeFromGroups(guildId);
		db.group.deleteAll(guildId);

		db.access.removeAll(guildId);
		db.module.removeAll(guildId);
		db.webhook.removeAll(guildId);
		db.verify.remove(guildId);
		db.ticketSettings.remove(guildId);
		db.role.removeAll(guildId);
		db.guildVoice.remove(guildId);
		db.panels.deleteAll(guildId);
		db.tags.deleteAll(guildId);
		db.tempRole.removeAll(guildId);
		Long guildIdLong = event.getGuild().getIdLong();
		db.autopunish.removeGuild(guildIdLong);
		db.strike.removeGuild(guildIdLong);
		
		db.guild.remove(guildId);

		bot.getLogger().info("Automatically removed guild '"+event.getGuild().getName()+"'("+guildId+") from db.");
		bot.getLogger().info("Left guild '"+event.getGuild().getName()+"'("+guildId+")");
	}

	@Override
	public void onGuildUnban(@Nonnull GuildUnbanEvent event) {
		CaseData banData = db.cases.getMemberActive(event.getUser().getIdLong(), event.getGuild().getIdLong(), CaseType.BAN);
		if (banData != null) {
			db.cases.setInactive(banData.getCaseIdInt());
		}
	}
	
	@Override
	public void onGuildMemberUpdateTimeOut(@Nonnull GuildMemberUpdateTimeOutEvent event) {
		if (event.getNewTimeOutEnd() == null) {
			// timeout removed by moderator
			CaseData banData = db.cases.getMemberActive(event.getUser().getIdLong(), event.getGuild().getIdLong(), CaseType.MUTE);
			db.cases.setInactive(banData.getCaseIdInt());
		}
	}

	@Override
	public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
		// Checks cache on local DB, if user is verified, gives out the role
		Guild guild = event.getGuild();
		long userId = event.getUser().getIdLong();
		
		if (db.verifyCache.isVerified(userId)) {
			// Check if user is blacklisted
			List<Integer> groupIds = new ArrayList<Integer>();
			groupIds.addAll(db.group.getOwnedGroups(event.getGuild().getId()));
			groupIds.addAll(db.group.getGuildGroups(event.getGuild().getId()));
			for (int groupId : groupIds) {
				if (db.blacklist.inGroupUser(groupId, userId)) return;
			}

			String roleId = db.verify.getVerifyRole(guild.getId());
			if (roleId == null) return;
			Role role = guild.getRoleById(roleId);
			if (role == null) return;

			Long cachedSteam64 = db.verifyCache.getSteam64(userId);
			guild.addRoleToMember(event.getUser(), role).reason(cachedSteam64 == null ? "Autocheck: Forced" : "Autocheck: Account linked - "+cachedSteam64).queue(success -> {
				bot.getLogListener().verify.onVerified(event.getUser(), cachedSteam64, guild);
			});
		}
	}
	
	@Override
	public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent event) {
		// When user leaves guild, check if there are any records in DB that would be better to remove.
		// This does not consider clearing User DB, when bot leaves guild.
		String guildId = event.getGuild().getId();
		String userId = event.getUser().getId();

		if (db.access.getUserLevel(guildId, userId).isHigherThan(CmdAccessLevel.ALL)) {
			db.access.removeUser(guildId, userId);
		}
		db.user.remove(userId);
	}
}

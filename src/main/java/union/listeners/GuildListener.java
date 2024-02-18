package union.listeners;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

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
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
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
		long guildIdLong = event.getGuild().getIdLong();
		bot.getLogger().info("Left guild '%s'(%s)".formatted(event.getGuild().getName(), guildId));

		// Deletes every information connected to this guild from bot's DB (except ban tables)
		// May be dangerous, but provides privacy
		for (Integer groupId : db.group.getGuildGroups(guildIdLong)) {
			String groupName = db.group.getName(groupId);
			String guildName = event.getGuild().getName();
			Long masterId = db.group.getOwner(groupId);
			String masterIcon = event.getJDA().getGuildById(masterId).getIconUrl();

			String masterChannelId = db.guild.getLogChannel(LogChannels.GROUPS, String.valueOf(masterId));
			if (masterChannelId != null) {
				TextChannel channel = event.getJDA().getTextChannelById(masterChannelId);
				if (channel != null) {
					try {
						MessageEmbed masterEmbed = bot.getLogUtil().groupOwnerLeftEmbed(channel.getGuild().getLocale(), masterId, masterIcon, guildName, guildIdLong, groupId, groupName);
						channel.sendMessageEmbeds(masterEmbed).queue();
					} catch (InsufficientPermissionException ex) {}
				}
			}
		}
		for (Integer groupId : db.group.getOwnedGroups(guildIdLong)) {
			String groupName = db.group.getName(groupId);
			for (Long memberId : db.group.getGroupMembers(groupId)) {
				String channelId = db.guild.getLogChannel(LogChannels.GROUPS, memberId.toString());
				if (channelId == null) {
					continue;
				}
				TextChannel channel = event.getJDA().getTextChannelById(channelId);
				if (channel == null) {
					continue;
				}

				try {
					MessageEmbed embed = bot.getLogUtil().groupMemberDeletedEmbed(channel.getGuild().getLocale(), guildIdLong, event.getGuild().getIconUrl(), groupId, groupName);
					channel.sendMessageEmbeds(embed).queue();
				} catch (InsufficientPermissionException ex) {
					continue;
				}
			}
			db.group.clearGroup(groupId);
		}
		db.group.removeGuildFromGroups(guildIdLong);
		db.group.deleteGuildGroups(guildIdLong);

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
		db.autopunish.removeGuild(guildIdLong);
		db.strike.removeGuild(guildIdLong);
		
		db.guild.remove(guildId);

		bot.getLogger().info("Automatically removed guild '%s'(%s) from db.".formatted(event.getGuild().getName(), guildId));
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

			String roleId = db.verify.getVerifyRole(guild.getId());
			if (roleId == null) return;
			Role role = guild.getRoleById(roleId);
			if (role == null) return;

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
		if (db.ticketSettings.getAutocloseLeft(guildId)) {
			db.ticket.getOpenedChannel(userId, guildId).stream().forEach(channelId -> {
				db.ticket.closeTicket(Instant.now(), channelId, "Ticket's author left the server");
				GuildChannel channel = event.getGuild().getGuildChannelById(channelId);
				if (channel != null) channel.delete().reason("Author left").queue();
			});
		}
	}
}

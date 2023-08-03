package union.listeners;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import union.App;
import union.objects.command.SlashCommandEvent;
import union.utils.LogUtil;
import union.utils.database.DBUtil;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Guild.Ban;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

public class LogListener {
	
	private final App bot;
	private final DBUtil db;
	private final LogUtil logUtil;

	public LogListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
		this.logUtil = bot.getLogUtil();
	}

	// Moderation actions
	public void onBan(SlashCommandEvent event, User target, Member moderator, Integer banId) {
		String guildId = Objects.requireNonNull(event.getGuild()).getId();

		String channelId = db.guild.getModLogChannel(guildId);
		if (channelId == null) {
			return;
		}
		TextChannel channel = event.getJDA().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		Map<String, Object> ban = db.ban.getInfo(banId);
		if (ban.isEmpty() || ban == null) {
			bot.getLogger().warn("That is not supposed to happen... Ban ID: %s", banId);
			return;
		}

		try {
			MessageEmbed embed = logUtil.getBanEmbed(event.getGuildLocale(), ban, target.getAvatarUrl());
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException ex) {}
	}
	
	public void onSyncBan(SlashCommandEvent event, Guild guild, User target, String reason) {
		String guildId = Objects.requireNonNull(guild).getId();

		String channelId = db.guild.getModLogChannel(guildId);
		if (channelId == null) {
			return;
		}
		TextChannel channel = event.getJDA().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		try {
			MessageEmbed embed = logUtil.getSyncBanEmbed(guild.getLocale(), event.getGuild(), event.getUser(), target, reason);
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException ex) {
			return;
		}
	}

	public void onUnban(SlashCommandEvent event, Member moderator, Ban banData, String reason) {
		String guildId = Objects.requireNonNull(event.getGuild()).getId();

		String channelId = db.guild.getModLogChannel(guildId);
		if (channelId == null) {
			return;
		}
		TextChannel channel = event.getJDA().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		try {
			MessageEmbed embed = logUtil.getUnbanEmbed(event.getGuildLocale(), banData, moderator, reason);
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException ex) {
			return;
		}
	}

	public void onSyncUnban(SlashCommandEvent event, Guild guild, User target, String banReason, String reason) {
		String guildId = Objects.requireNonNull(guild).getId();

		String channelId = db.guild.getModLogChannel(guildId);
		if (channelId == null) {
			return;
		}
		TextChannel channel = event.getJDA().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		try {
			MessageEmbed embed = logUtil.getSyncUnbanEmbed(guild.getLocale(), event.getGuild(), event.getUser(), target, banReason, reason);
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException ex) {
			return;
		}
	}

	public void onAutoUnban(Map<String, Object> banMap, Integer banId, Guild guild) {
		String channelId = db.guild.getModLogChannel(guild.getId());
		if (channelId == null) {
			return;
		}
		TextChannel channel = bot.JDA.getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		try {
			MessageEmbed embed = logUtil.getAutoUnbanEmbed(guild.getLocale(), banMap);
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException ex) {
			return;
		}
	}

	public void onChangeReason(SlashCommandEvent event, Integer banId, String oldReason, String newReason) {
		String guildId = Objects.requireNonNull(event.getGuild()).getId();

		String channelId = db.guild.getModLogChannel(guildId);
		if (channelId == null) {
			return;
		}
		TextChannel channel = event.getJDA().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		Map<String, Object> ban = db.ban.getInfo(banId);
		if (ban.isEmpty() || ban == null) {
			bot.getLogger().warn("That is not supposed to happen... Ban ID: %s", banId);
			return;
		}

		try {
			MessageEmbed embed = logUtil.getReasonChangeEmbed(event.getGuildLocale(), banId, ban.get("userTag").toString(), ban.get("userId").toString(), event.getUser().getId(), oldReason, oldReason);
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException ex) {
			return;
		}
	}

	public void onChangeDuration(SlashCommandEvent event, Integer banId, Instant timeStart, Duration oldDuration, String newTime) {
		String guildId = Objects.requireNonNull(event.getGuild()).getId();

		String channelId = db.guild.getModLogChannel(guildId);
		if (channelId == null) {
			return;
		}
		TextChannel channel = event.getJDA().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		Map<String, Object> ban = db.ban.getInfo(banId);
		if (ban.isEmpty() || ban == null) {
			bot.getLogger().warn("That is not supposed to happen... Ban ID: %s", banId);
			return;
		}

		try {
			MessageEmbed embed = logUtil.getDurationChangeEmbed(event.getGuildLocale(), banId, ban.get("userTag").toString(), ban.get("userId").toString(), event.getUser().getId(), timeStart, oldDuration, newTime);
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException ex) {
			return;
		}
	}

	public void onKick(SlashCommandEvent event, User target, User moderator, String reason) {
		String guildId = Objects.requireNonNull(event.getGuild()).getId();

		String channelId = db.guild.getModLogChannel(guildId);
		if (channelId == null) {
			return;
		}
		TextChannel channel = event.getJDA().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		try {
			MessageEmbed embed = logUtil.getKickEmbed(event.getGuildLocale(), target.getName(), target.getId(), moderator.getName(), moderator.getId(), reason, target.getAvatarUrl(), true);
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException ex) {
			return;
		}
	}

	public void onSyncKick(SlashCommandEvent event, Guild guild, User target, String reason) {
		String guildId = Objects.requireNonNull(guild).getId();

		String channelId = db.guild.getModLogChannel(guildId);
		if (channelId == null) {
			return;
		}
		TextChannel channel = event.getJDA().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		try {
			MessageEmbed embed = logUtil.getSyncKickEmbed(guild.getLocale(), guild, event.getUser(), target, reason);
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException ex) {
			return;
		}
	}

	public void helperOnSyncBan(Integer groupId, Guild master, User target, String reason, Integer success, Integer max) {
		String channelId = db.guild.getModLogChannel(master.getId());
		if (channelId == null) {
			return;
		}
		TextChannel channel = master.getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		try {
			MessageEmbed embed = logUtil.getHelperBanEmbed(master.getLocale(), groupId, target, reason, success, max);
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException ex) {
			return;
		}
	}

	public void helperOnSyncUnban(Integer groupId, Guild master, User target, String reason, Integer success, Integer max) {
		String channelId = db.guild.getModLogChannel(master.getId());
		if (channelId == null) {
			return;
		}
		TextChannel channel = master.getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		try {
			MessageEmbed embed = logUtil.getHelperUnbanEmbed(master.getLocale(), groupId, target, reason, success, max);
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException ex) {
			return;
		}
	}

	public void helperOnSyncKick(Integer groupId, Guild master, User target, String reason, Integer success, Integer max) {
		String channelId = db.guild.getModLogChannel(master.getId());
		if (channelId == null) {
			return;
		}
		TextChannel channel = master.getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		try {
			MessageEmbed embed = logUtil.getHelperKickEmbed(master.getLocale(), groupId, target, reason, success, max);
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException ex) {
			return;
		}
	}

	// Group settings
	public void onGroupCreation(SlashCommandEvent event, Integer groupId, String name) {
		String masterId = event.getGuild().getId();

		String channelId = db.guild.getGroupLogChannel(masterId);
		if (channelId == null) {
			return;
		}
		TextChannel channel = event.getJDA().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		try {
			MessageEmbed embed = logUtil.getGroupCreationEmbed(event.getGuildLocale(), event.getMember().getAsMention(), masterId, event.getGuild().getIconUrl(), groupId, name);
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException ex) {
			return;
		}
	}

	public void onGroupDeletion(SlashCommandEvent event, Integer groupId, String name) {
		String masterId = event.getGuild().getId();
		String masterIcon = event.getGuild().getIconUrl();

		// For each group guild (except master) remove if from group DB and send log to log channel
		List<String> guildIds = db.group.getGroupGuildIds(groupId);
		for (String guildId : guildIds) {
			db.group.remove(groupId, guildId);
			String channelId = db.guild.getGroupLogChannel(guildId);
			if (channelId == null) {
				continue;
			}
			TextChannel channel = event.getJDA().getTextChannelById(channelId);
			if (channel == null) {
				continue;
			}

			try {
				MessageEmbed embed = logUtil.getGroupMemberDeletedEmbed(channel.getGuild().getLocale(), masterId, masterIcon, groupId, name);
				channel.sendMessageEmbeds(embed).queue();
			} catch (InsufficientPermissionException ex) {
				continue;
			}
		}

		// Master log
		String channelId = db.guild.getGroupLogChannel(masterId);
		if (channelId != null) {
			TextChannel channel = event.getJDA().getTextChannelById(channelId);
			if (channel != null) {
				try {
					MessageEmbed embed = logUtil.getGroupOwnerDeletedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), masterId, masterIcon, groupId, name);
					channel.sendMessageEmbeds(embed).queue();
				} catch (InsufficientPermissionException ex) {}
			}
		}
	}

	public void onGroupAdded(SlashCommandEvent event, Integer groupId, String name, String targetId, String targetName) {
		String ownerId = event.getGuild().getId();
		String ownerIcon = event.getGuild().getIconUrl();

		String ownerChannelId = db.guild.getGroupLogChannel(ownerId);
		if (ownerChannelId != null) {
			TextChannel channel = event.getJDA().getTextChannelById(ownerChannelId);
			if (channel != null) {
				try {
					MessageEmbed embed = logUtil.getGroupOwnerAddedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, targetName, targetId, groupId, name);
					channel.sendMessageEmbeds(embed).queue();
				} catch (InsufficientPermissionException ex) {}
			}
		}

		// Send log to added server's log channel
		String channelId = db.guild.getGroupLogChannel(targetId);
		if (channelId != null) {
			TextChannel channel = event.getJDA().getTextChannelById(channelId);
			if (channel != null) {
				try {
					MessageEmbed embed = logUtil.getGroupMemberAddedEmbed(event.getGuildLocale(), ownerId, ownerIcon, groupId, name);
					channel.sendMessageEmbeds(embed).queue();
				} catch (InsufficientPermissionException ex) {}
			}
		}
	}

	public void onGroupJoined(SlashCommandEvent event, Integer groupId, String name) {
		String guildId = event.getGuild().getId();
		String guildName = event.getGuild().getName();
		String masterId = db.group.getMaster(groupId);
		String masterIcon = event.getJDA().getGuildById(masterId).getIconUrl();

		String channelId = db.guild.getGroupLogChannel(guildId);
		
		if (channelId != null) {
			TextChannel channel = event.getJDA().getTextChannelById(channelId);
			if (channel != null) {
				try {
					MessageEmbed embed = logUtil.getGroupMemberJoinEmbed(event.getGuildLocale(), event.getMember().getAsMention(), masterId, masterIcon, groupId, name);
					channel.sendMessageEmbeds(embed).queue();
				} catch (InsufficientPermissionException ex) {}
			}
		}

		// Send log to group's master log channel
		String masterChannelId = db.guild.getGroupLogChannel(masterId);
		if (masterChannelId != null) {
			TextChannel channel = event.getJDA().getTextChannelById(masterChannelId);
			if (channel != null) {
				try {
					MessageEmbed masterEmbed = logUtil.getGroupOwnerJoinEmbed(channel.getGuild().getLocale(), masterId, masterIcon, guildName, guildId, groupId, name);
					channel.sendMessageEmbeds(masterEmbed).queue();
				} catch (InsufficientPermissionException ex) {}
			}
		}
	}

	public void onGroupLeave(SlashCommandEvent event, Integer groupId, String name) {
		String guildId = event.getGuild().getId();
		String guildName = event.getGuild().getName();
		String masterId = db.group.getMaster(groupId);
		String masterIcon = event.getJDA().getGuildById(masterId).getIconUrl();

		String channelId = db.guild.getGroupLogChannel(guildId);
		if (channelId != null) {
			TextChannel channel = event.getJDA().getTextChannelById(channelId);
			if (channel != null) {
				try {
					MessageEmbed embed = logUtil.getGroupMemberLeaveEmbed(event.getGuildLocale(), event.getMember().getAsMention(), masterId, masterIcon, groupId, name);
					channel.sendMessageEmbeds(embed).queue();
				} catch (InsufficientPermissionException ex) {}
			}
		}

		// Send log to group's master log channel
		String masterChannelId = db.guild.getGroupLogChannel(masterId);
		if (masterChannelId != null) {
			TextChannel channel = event.getJDA().getTextChannelById(masterChannelId);
			if (channel != null) {
				try {
					MessageEmbed masterEmbed = logUtil.getGroupOwnerLeaveEmbed(channel.getGuild().getLocale(), masterId, masterIcon, guildName, guildId, groupId, name);
					channel.sendMessageEmbeds(masterEmbed).queue();
				} catch (InsufficientPermissionException ex) {}
			}
		}
	}

	public void onGroupRemove(SlashCommandEvent event, Guild target, Integer groupId, String name) {
		String targetId = target.getId();
		String targetName = target.getName();
		String masterId = event.getGuild().getId();
		String masterIcon = event.getGuild().getIconUrl();

		String channelId = db.guild.getGroupLogChannel(targetId);
		if (channelId != null) {
			TextChannel channel = event.getJDA().getTextChannelById(channelId);
			if (channel != null) {
				try {
					MessageEmbed embed = logUtil.getGroupMemberLeaveEmbed(channel.getGuild().getLocale(), "Forced, by group Master", masterId, masterIcon, groupId, name);
					channel.sendMessageEmbeds(embed).queue();
				} catch (InsufficientPermissionException ex) {}
			}
		}

		// Send log to group's master log channel
		String masterChannelId = db.guild.getGroupLogChannel(masterId);
		if (masterChannelId != null) {
			TextChannel channel = event.getJDA().getTextChannelById(masterChannelId);
			if (channel != null) {
				try {
					MessageEmbed embed = logUtil.getGroupOwnerRemoveEmbed(event.getGuildLocale(), event.getMember().getAsMention(), masterId, masterIcon, targetName, targetId, groupId, name);
					channel.sendMessageEmbeds(embed).queue();
				} catch (InsufficientPermissionException ex) {}
			}
		}
	}

	public void onGroupRename(SlashCommandEvent event, String oldName, Integer groupId, String newName) {
		String masterId = event.getGuild().getId();
		String masterIcon = event.getGuild().getIconUrl();

		// Send log to each group guild
		List<String> guildIds = db.group.getGroupGuildIds(groupId);
		for (String guildId : guildIds) {
			String channelId = db.guild.getGroupLogChannel(guildId);
			if (channelId == null) {
				continue;
			}
			TextChannel channel = event.getJDA().getTextChannelById(channelId);
			if (channel == null) {
				continue;
			}

			try {
				MessageEmbed embed = logUtil.getGroupMemberRenamedEmbed(channel.getGuild().getLocale(), masterId, masterIcon, groupId, oldName, newName);
				channel.sendMessageEmbeds(embed).queue();
			} catch (InsufficientPermissionException ex) {
				continue;
			}
		}

		// Master log
		String channelId = db.guild.getGroupLogChannel(masterId);
		if (channelId != null) {
			TextChannel channel = event.getJDA().getTextChannelById(channelId);
			if (channel != null) {
				try {
					MessageEmbed embed = logUtil.getGroupOwnerRenamedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), masterId, masterIcon, groupId, oldName, newName);
					channel.sendMessageEmbeds(embed).queue();
				} catch (InsufficientPermissionException ex) {}
			}
		}
	}

	// Verification
	public void onVerified(User user, String steam64, Guild guild) {
		String guildId = Objects.requireNonNull(guild).getId();

		String channelId = db.guild.getVerifyLogChannel(guildId);
		if (channelId == null) {
			return;
		}
		TextChannel channel = guild.getJDA().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		try {
			MessageEmbed embed = logUtil.getVerifiedEmbed(guild.getLocale(), user.getName(), user.getId(), user.getEffectiveAvatarUrl(), (steam64 == null ? null : db.verifyRequest.getSteamName(steam64)), steam64);
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException ex) {}
	}

	public void onUnverified(User user, String steam64, Guild guild, String reason) {
		String guildId = Objects.requireNonNull(guild).getId();

		String channelId = db.guild.getVerifyLogChannel(guildId);
		if (channelId == null) {
			return;
		}
		TextChannel channel = guild.getJDA().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		try {
			MessageEmbed embed = logUtil.getUnverifiedEmbed(guild.getLocale(), user.getName(), user.getId(), user.getEffectiveAvatarUrl(), (steam64 == null ? null : db.verifyRequest.getSteamName(steam64)), steam64, reason);
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException ex) {}
	}

	// Ticketing
	public void onRolesApproved(Member member, Member admin, Guild guild, List<Role> roles, String ticketId) {
		String guildId = Objects.requireNonNull(guild).getId();

		String channelId = db.guild.getTicketLogChannel(guildId);
		if (channelId == null) {
			return;
		}
		TextChannel channel = guild.getJDA().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		try {
			MessageEmbed embed = logUtil.getRolesApprovedEmbed(guild.getLocale(), ticketId, member.getAsMention(), member.getId(), roles.stream().map(role -> role.getAsMention()).collect(Collectors.joining(" ")), admin.getAsMention());
			channel.sendMessageEmbeds(embed).setAllowedMentions(Collections.emptyList()).queue();
		} catch (InsufficientPermissionException ex) {}
	}

	public void onTicketClose(Member admin, Guild guild, Channel threadChannel, String ticketId) {
		String guildId = Objects.requireNonNull(guild).getId();

		String channelId = db.guild.getTicketLogChannel(guildId);
		if (channelId == null) {
			return;
		}
		TextChannel channel = guild.getJDA().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		try {
			MessageEmbed embed = logUtil.getTicketClosedEmbed(guild.getLocale(), ticketId, threadChannel.getId(), admin.getAsMention());
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException ex) {}
	}
}

package union.listeners;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import union.App;
import union.base.command.SlashCommandEvent;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.LogChannels;
import union.objects.annotation.Nullable;
import union.objects.constants.Constants;
import union.utils.LogUtil;
import union.utils.database.DBUtil;
import union.utils.database.managers.CaseManager.CaseData;
import union.utils.message.SteamUtil;

import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.utils.FileUpload;

public class LogListener {
	
	private final App bot;
	private final DBUtil db;
	private final LogUtil logUtil;

	public final Moderation mod = new Moderation();
	public final Roles role = new Roles();
	public final Groups group = new Groups();
	public final Verification verify = new Verification();
	public final Tickets ticket = new Tickets();
	public final Server server = new Server();

	public LogListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
		this.logUtil = bot.getLogUtil();
	}

	private TextChannel getLogChannel(LogChannels type, Guild guild) {
		return Optional.ofNullable(db.guild.getLogChannel(type, guild.getId())).map(guild::getTextChannelById).orElse(null);
	}

	private TextChannel getLogChannel(LogChannels type, long guildId) {
		return Optional.ofNullable(db.guild.getLogChannel(type, String.valueOf(guildId))).map(bot.JDA::getTextChannelById).orElse(null);
	}

	private void sendLog(TextChannel channel, MessageEmbed embed) {
		try {
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException | IllegalArgumentException ex) {
			return;
		}
	}

	private void sendLog(LogChannels type, Guild guild, MessageEmbed embed) {
		TextChannel channel = getLogChannel(type, guild);
		if (channel == null) return;

		try {
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException | IllegalArgumentException ex) {
			return;
		}
	}

	// Moderation actions
	public class Moderation {
		private final LogChannels type = LogChannels.MODERATION;

		public void onNewCase(Guild guild, User target, CaseData caseData) {
			onNewCase(guild, target, caseData, null);
		}
		
		public void onNewCase(Guild guild, User target, CaseData caseData, String oldReason) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, guild);
			if (channel == null) return;

			if (caseData == null) {
				bot.getLogger().warn("Unknown case provided with interaction");
				return;
			}

			MessageEmbed embed = null;
			switch (caseData.getCaseType()) {
				case BAN:
					embed = logUtil.banEmbed(guild.getLocale(), caseData, target.getAvatarUrl());
					break;
				case UNBAN:
					embed = logUtil.unbanEmbed(guild.getLocale(), caseData, oldReason);
					break;
				case MUTE:
					embed = logUtil.muteEmbed(guild.getLocale(), caseData, target.getAvatarUrl());
					break;
				case UNMUTE:
					embed = logUtil.unmuteEmbed(guild.getLocale(), caseData, target.getAvatarUrl(), oldReason);
					break;
				case KICK:
					embed = logUtil.kickEmbed(guild.getLocale(), caseData, target.getAvatarUrl());
					break;
				case STRIKE_1:
				case STRIKE_2:
				case STRIKE_3:
					embed = logUtil.strikeEmbed(guild.getLocale(), caseData, target.getAvatarUrl());
					break;
				case BLACKLIST:
					embed = null;
					break;
				default:
					break;
			}
			if (embed!=null) sendLog(channel, embed);
		}

		public void onStrikesCleared(IReplyCallback event, User target) {
			sendLog(type, event.getGuild(), logUtil.strikesClearedEmbed(event.getGuild().getLocale(), target.getName(), target.getIdLong(),
				event.getUser().getIdLong()));
		}

		public void onStrikeDeleted(IReplyCallback event, User target, int caseId, int deletedAmount, int maxAmount) {
			sendLog(type, event.getGuild(), logUtil.strikeDeletedEmbed(event.getGuild().getLocale(), target.getName(), target.getIdLong(),
				event.getUser().getIdLong(), caseId, deletedAmount, maxAmount));
		}

		public void onAutoUnban(CaseData caseData, Guild guild) {
			sendLog(type, guild, logUtil.autoUnbanEmbed(guild.getLocale(), caseData));
		}

		public void onChangeReason(SlashCommandEvent event, CaseData caseData, Member moderator, String newReason) {
			if (caseData == null) {
				bot.getLogger().warn("Unknown case provided with interaction ", event.getName());
				return;
			}

			sendLog(type, event.getGuild(), logUtil.reasonChangedEmbed(event.getGuildLocale(), caseData, moderator.getIdLong(), newReason));
		}

		public void onChangeDuration(SlashCommandEvent event, CaseData caseData, Member moderator, String newTime) {
			if (caseData == null) {
				bot.getLogger().warn("Unknown case provided with interaction ", event.getName());
				return;
			}

			sendLog(type, event.getGuild(), logUtil.durationChangedEmbed(event.getGuildLocale(), caseData, moderator.getIdLong(), newTime));
		}

		public void onHelperSyncBan(int groupId, Guild guild, User target, String reason, int success, int max) {
			sendLog(type, guild, logUtil.helperBanEmbed(guild.getLocale(), groupId, target, reason, success, max));
		}

		public void onHelperSyncUnban(int groupId, Guild guild, User target, String reason, int success, int max) {
			sendLog(type, guild, logUtil.helperUnbanEmbed(guild.getLocale(), groupId, target, reason, success, max));
		}

		public void onHelperSyncKick(int groupId, Guild guild, User target, String reason, int success, int max) {
			sendLog(type, guild, logUtil.helperKickEmbed(guild.getLocale(), groupId, target, reason, success, max));
		}

		public void onBlacklistAdded(User mod, User target, Long steam64, List<Integer> groupIds) {
			for (int groupId : groupIds) {
				final String groupInfo = "%s (#%d)".formatted(bot.getDBUtil().group.getName(groupId), groupId);
				Guild master = bot.JDA.getGuildById(bot.getDBUtil().group.getOwner(groupId));
				sendLog(type, master, logUtil.blacklistAddedEmbed(master.getLocale(), mod, target, steam64 == null ? "none" : SteamUtil.convertSteam64toSteamID(steam64), groupInfo));
			}
		}

		public void onBlacklistRemoved(User mod, User target, Long steam64, int groupId) {
			final String groupInfo = "%s (#%d)".formatted(bot.getDBUtil().group.getName(groupId), groupId);
			Guild master = bot.JDA.getGuildById(bot.getDBUtil().group.getOwner(groupId));
			sendLog(type, master, logUtil.blacklistRemovedEmbed(master.getLocale(), mod, target, steam64 == null ? "none" : SteamUtil.convertSteam64toSteamID(steam64), groupInfo));
		}
	}

	// Roles actions
	public class Roles {
		private final LogChannels type = LogChannels.ROLES;

		public void onApproved(Member member, Member admin, Guild guild, List<Role> roles, String ticketId) {
			sendLog(type, guild, logUtil.rolesApprovedEmbed(guild.getLocale(), ticketId, member.getAsMention(), member.getId(), roles.stream().map(role -> role.getAsMention()).collect(Collectors.joining(" ")), admin.getAsMention()));
		}

		public void onCheckRank(Guild guild, User admin, Role role, String rankName) {
			sendLog(type, guild, logUtil.checkRankEmbed(guild.getLocale(), admin.getId(), role.getId(), rankName));
		}

		public void onRoleAdded(Guild guild, User mod, User target, Role role) {
			sendLog(type, guild, logUtil.roleAddedEmbed(guild.getLocale(), mod.getId(), target.getId(), target.getAvatarUrl(), role.getId()));
		}

		public void onRoleRemoved(Guild guild, User mod, User target, Role role) {
			sendLog(type, guild, logUtil.roleRemovedEmbed(guild.getLocale(), mod.getId(), target.getId(), target.getAvatarUrl(), role.getId()));
		}

		public void onRoleRemovedAll(Guild guild, User mod, Role role) {
			sendLog(type, guild, logUtil.roleRemovedAllEmbed(guild.getLocale(), mod.getId(), role.getId()));
		}

		public void onTempRoleAdded(Guild guild, User mod, User target, Role role, Duration duration) {
			sendLog(type, guild, logUtil.tempRoleAddedEmbed(guild.getLocale(), mod, target, role, duration));
		}

		public void onTempRoleRemoved(Guild guild, User mod, User target, Role role) {
			sendLog(type, guild, logUtil.tempRoleRemovedEmbed(guild.getLocale(), mod, target, role));
		}

		public void onTempRoleUpdated(Guild guild, User mod, User target, Role role, Instant until) {
			sendLog(type, guild, logUtil.tempRoleUpdatedEmbed(guild.getLocale(), mod, target, role, until));
		}

		public void onTempRoleAutoRemoved(Guild guild, String targetId, Role role) {
			sendLog(type, guild, logUtil.tempRoleAutoRemovedEmbed(guild.getLocale(), targetId, role));
		}

		public void onRoleCheckChildGuild(Guild guild, User admin, Role role, Guild targetGuild) {
			sendLog(type, guild, logUtil.checkRoleChildGuild(guild.getLocale(), admin.getId(), role.getId(), targetGuild.getName(), targetGuild.getId()));
		}
	}

	// Group actions
	public class Groups {
		private final LogChannels type = LogChannels.GROUPS;

		public void onCreation(SlashCommandEvent event, Integer groupId, String name) {
			sendLog(type, event.getGuild(), logUtil.groupCreatedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), event.getGuild().getIdLong(), event.getGuild().getIconUrl(), groupId, name));
		}

		public void onDeletion(SlashCommandEvent event, Integer groupId, String name) {
			long ownerId = event.getGuild().getIdLong();
			String ownerIcon = event.getGuild().getIconUrl();

			// For each group guild (except master) remove if from group DB and send log to log channel
			List<Long> memberIds = db.group.getGroupMembers(groupId);
			for (Long memberId : memberIds) {
				db.group.remove(groupId, memberId);

				TextChannel channel = getLogChannel(LogChannels.GROUPS, memberId);
				if (channel == null) continue;

				sendLog(channel, logUtil.groupMemberDeletedEmbed(channel.getGuild().getLocale(), ownerId, ownerIcon, groupId, name));
			}

			// Master log
			sendLog(type, event.getGuild(), logUtil.groupOwnerDeletedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, groupId, name));
		}

		public void onGuildAdded(SlashCommandEvent event, Integer groupId, String name, long targetId, String targetName) {
			long ownerId = event.getGuild().getIdLong();
			String ownerIcon = event.getGuild().getIconUrl();

			// Send log to added server
			TextChannel channel = getLogChannel(LogChannels.GROUPS, targetId);
			if (channel != null) {
				sendLog(channel, logUtil.groupMemberAddedEmbed(event.getGuildLocale(), ownerId, ownerIcon, groupId, name));
			}

			// Master log
			sendLog(type, event.getGuild(), logUtil.groupOwnerAddedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, targetName, targetId, groupId, name));
		}

		public void onGuildJoined(SlashCommandEvent event, Integer groupId, String name) {
			long ownerId = db.group.getOwner(groupId);
			String ownerIcon = event.getJDA().getGuildById(ownerId).getIconUrl();

			// Send log to added server
			sendLog(type, event.getGuild(), logUtil.groupMemberJoinedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, groupId, name));

			// Master log
			TextChannel channel = getLogChannel(LogChannels.GROUPS, ownerId);
			if (channel == null) return;

			sendLog(channel, logUtil.groupOwnerJoinedEmbed(channel.getGuild().getLocale(), ownerId, ownerIcon, event.getGuild().getName(), event.getGuild().getIdLong(), groupId, name));
		}

		public void onGuildLeft(SlashCommandEvent event, Integer groupId, String name) {
			long ownerId = db.group.getOwner(groupId);
			String ownerIcon = event.getJDA().getGuildById(ownerId).getIconUrl();

			// Send log to removed server
			sendLog(type, event.getGuild(), logUtil.groupMemberLeftEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, groupId, name));

			// Master log
			TextChannel channel = getLogChannel(LogChannels.GROUPS, ownerId);
			if (channel == null) return;

			sendLog(channel, logUtil.groupOwnerLeftEmbed(channel.getGuild().getLocale(), ownerId, ownerIcon, event.getGuild().getName(), event.getGuild().getIdLong(), groupId, name));
		}

		public void onGuildRemoved(SlashCommandEvent event, Guild target, Integer groupId, String name) {
			long ownerId = event.getGuild().getIdLong();
			String ownerIcon = event.getGuild().getIconUrl();

			// Send log to removed server
			sendLog(type, target, logUtil.groupMemberLeftEmbed(target.getLocale(), "Forced, by group Master", ownerId, ownerIcon, groupId, name));

			// Master log
			sendLog(type, event.getGuild(), logUtil.groupOwnerRemovedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, target.getName(), target.getIdLong(), groupId, name));
		}

		public void onRenamed(SlashCommandEvent event, String oldName, Integer groupId, String newName) {
			long ownerId = event.getGuild().getIdLong();
			String ownerIcon = event.getGuild().getIconUrl();

			// Send log to each group guild
			List<Long> memberIds = db.group.getGroupMembers(groupId);
			for (Long memberId : memberIds) {
				db.group.remove(groupId, memberId);

				TextChannel channel = getLogChannel(LogChannels.GROUPS, memberId);
				if (channel == null) continue;

				sendLog(channel, logUtil.groupMemberRenamedEmbed(channel.getGuild().getLocale(), ownerId, ownerIcon, groupId, oldName, newName));
			}

			// Master log
			sendLog(type, event.getGuild(), logUtil.groupOwnerRenamedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, groupId, oldName, newName));
		}

		public void helperInformAction(int groupId, Guild target, AuditLogEntry auditLogEntry) {
			Guild master = Optional.ofNullable(db.group.getOwner(groupId)).map(bot.JDA::getGuildById).orElse(null);
			if (master == null) return;

			sendLog(type, master, logUtil.auditLogEmbed(master.getLocale(), groupId, target, auditLogEntry));
		}

		public void helperInformLeave(int groupId, @Nullable Guild guild, String guildId) {
			Guild master = Optional.ofNullable(db.group.getOwner(groupId)).map(bot.JDA::getGuildById).orElse(null);
			if (master == null) return;

			sendLog(type, master, logUtil.botLeftEmbed(master.getLocale(), groupId, guild, guildId));
		}

		public void helperAlertTriggered(int groupId, Guild targetGuild, Member targetMember, String actionTaken, String reason) {
			Guild master = Optional.ofNullable(db.group.getOwner(groupId)).map(bot.JDA::getGuildById).orElse(null);
			if (master == null) return;

			TextChannel channel = getLogChannel(LogChannels.GROUPS, master);
			if (channel == null) return;

			try {
				channel.sendMessage("||"+Constants.DEVELOPER_TAG+"||").addEmbeds(logUtil.alertEmbed(master.getLocale(), groupId, targetGuild, targetMember, actionTaken, reason)).queue();
			} catch (InsufficientPermissionException | IllegalArgumentException ex) {
				// ignore
			}
		}
	}

	// Verification actions
	public class Verification {
		private final LogChannels type = LogChannels.VERIFICATION;

		public void onVerified(User user, Long steam64, Guild guild) {
			sendLog(type, guild, logUtil.verifiedEmbed(guild.getLocale(), user.getName(), user.getId(), user.getEffectiveAvatarUrl(), (steam64 == null ? null : db.unionVerify.getSteamName(steam64.toString())), steam64));
		}

		public void onUnverified(User user, Long steam64, Guild guild, String reason) {
			sendLog(type, guild, logUtil.unverifiedEmbed(guild.getLocale(), user.getName(), user.getId(), user.getEffectiveAvatarUrl(), (steam64 == null ? null : db.unionVerify.getSteamName(steam64.toString())), steam64, reason));
		}

		public void onVerifiedAttempt(User user, Long steam64, Guild guild, int groupId) {
			sendLog(type, guild, logUtil.verifyAttempt(guild.getLocale(), user.getName(), user.getId(), user.getEffectiveAvatarUrl(), steam64, groupId));
		}
	}

	// Tickets actions
	public class Tickets {
		private final LogChannels type = LogChannels.TICKETS;

		public void onCreate(Guild guild, GuildMessageChannel messageChannel, User author) {
			sendLog(type, guild, logUtil.ticketCreatedEmbed(guild.getLocale(), messageChannel, author));
		}

		public void onClose(Guild guild, GuildMessageChannel messageChannel, User userClosed, String authorId, FileUpload file) {
			TextChannel channel = getLogChannel(type, guild);
			if (channel == null) return;

			try {
				channel.sendMessageEmbeds(
					logUtil.ticketClosedEmbed(guild.getLocale(), messageChannel, userClosed, authorId, db.ticket.getClaimer(messageChannel.getId()))
				).addFiles(file).queue();
			} catch (InsufficientPermissionException | IllegalArgumentException ex) {
				return;
			}
		}
	}

	// Server actions
	public class Server {
		private final LogChannels type = LogChannels.SERVER;

		public void onAccessAdded(Guild guild, User mod, @Nullable User userTarget, @Nullable Role roleTarget, CmdAccessLevel level) {
			sendLog(type, guild, logUtil.accessAdded(guild.getLocale(), mod, userTarget, roleTarget, level.getName()));
		}

		public void onAccessRemoved(Guild guild, User mod, @Nullable User userTarget, @Nullable Role roleTarget, CmdAccessLevel level) {
			sendLog(type, guild, logUtil.accessRemoved(guild.getLocale(), mod, userTarget, roleTarget, level.getName()));
		}

		public void onModuleEnabled(Guild guild, User mod, CmdModule module) {
			sendLog(type, guild, logUtil.moduleEnabled(guild.getLocale(), mod, module));
		}

		public void onModuleDisabled(Guild guild, User mod, CmdModule module) {
			sendLog(type, guild, logUtil.moduleDisabled(guild.getLocale(), mod, module));
		}
	}

}

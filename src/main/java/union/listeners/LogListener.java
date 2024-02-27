package union.listeners;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;

import union.App;
import union.base.command.SlashCommandEvent;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.LogChannels;
import union.objects.constants.Constants;
import union.utils.LogUtil;
import union.utils.database.DBUtil;
import union.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
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

	private TextChannel getLogChannel(LogChannels type, Long guildId) {
		return Optional.ofNullable(db.guild.getLogChannel(type, guildId.toString())).map(bot.JDA::getTextChannelById).orElse(null);
	}

	private void sendLog(TextChannel channel, MessageEmbed embed) {
		try {
			channel.sendMessageEmbeds(embed).queue();
		} catch (InsufficientPermissionException | IllegalArgumentException ex) {
			return;
		}
	}

	// Moderation actions
	public class Moderation {
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

		public void onSyncBan(SlashCommandEvent event, Guild guild, User target, String reason) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, event.getGuild());
			if (channel == null) return;

			sendLog(channel, logUtil.syncBanEmbed(guild.getLocale(), event.getGuild(), event.getUser(), target, reason));
		}

		public void onSyncUnban(SlashCommandEvent event, Guild guild, User target, String banReason, String reason) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, event.getGuild());
			if (channel == null) return;

			sendLog(channel, logUtil.syncUnbanEmbed(guild.getLocale(), event.getGuild(), event.getUser(), target, banReason, reason));
		}

		public void onAutoUnban(CaseData caseData, Guild guild) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.autoUnbanEmbed(guild.getLocale(), caseData));
		}

		public void onSyncKick(SlashCommandEvent event, Guild guild, User target, String reason) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, event.getGuild());
			if (channel == null) return;

			sendLog(channel, logUtil.syncKickEmbed(guild.getLocale(), guild, event.getUser(), target, reason));
		}

		public void onChangeReason(SlashCommandEvent event, CaseData caseData, Member moderator, String newReason) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, event.getGuild());
			if (channel == null) return;

			if (caseData == null) {
				bot.getLogger().warn("Unknown case provided with interaction ", event.getName());
				return;
			}

			sendLog(channel, logUtil.reasonChangedEmbed(event.getGuildLocale(), caseData, moderator.getIdLong(), newReason));
		}

		public void onChangeDuration(SlashCommandEvent event, CaseData caseData, Member moderator, String newTime) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, event.getGuild());
			if (channel == null) return;

			if (caseData == null) {
				bot.getLogger().warn("Unknown case provided with interaction ", event.getName());
				return;
			}

			sendLog(channel, logUtil.durationChangedEmbed(event.getGuildLocale(), caseData, moderator.getIdLong(), newTime));
		}

		public void onHelperSyncBan(Integer groupId, Guild master, User target, String reason, Integer success, Integer max) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, master);
			if (channel == null) return;

			sendLog(channel, logUtil.helperBanEmbed(master.getLocale(), groupId, target, reason, success, max));
		}

		public void onHelperSyncUnban(Integer groupId, Guild master, User target, String reason, Integer success, Integer max) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, master);
			if (channel == null) return;

			sendLog(channel, logUtil.helperUnbanEmbed(master.getLocale(), groupId, target, reason, success, max));
		}

		public void onHelperSyncKick(Integer groupId, Guild master, User target, String reason, Integer success, Integer max) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, master);
			if (channel == null) return;

			sendLog(channel, logUtil.helperKickEmbed(master.getLocale(), groupId, target, reason, success, max));
		}
	}

	// Roles actions
	public class Roles {
		public void onApproved(Member member, Member admin, Guild guild, List<Role> roles, String ticketId) {
			TextChannel channel = getLogChannel(LogChannels.ROLES, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.rolesApprovedEmbed(guild.getLocale(), ticketId, member.getAsMention(), member.getId(), roles.stream().map(role -> role.getAsMention()).collect(Collectors.joining(" ")), admin.getAsMention()));
		}

		public void onCheckRank(Guild guild, User admin, Role role, String rankName) {
			TextChannel channel = getLogChannel(LogChannels.ROLES, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.checkRankEmbed(guild.getLocale(), admin.getId(), role.getId(), rankName));
		}

		public void onRoleAdded(Guild guild, User mod, User target, Role role) {
			TextChannel channel = getLogChannel(LogChannels.ROLES, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.roleAddedEmbed(guild.getLocale(), mod.getId(), target.getId(), target.getAvatarUrl(), role.getId()));
		}

		public void onRoleRemoved(Guild guild, User mod, User target, Role role) {
			TextChannel channel = getLogChannel(LogChannels.ROLES, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.roleRemovedEmbed(guild.getLocale(), mod.getId(), target.getId(), target.getAvatarUrl(), role.getId()));
		}

		public void onRoleRemovedAll(Guild guild, User mod, Role role) {
			TextChannel channel = getLogChannel(LogChannels.ROLES, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.roleRemovedAllEmbed(guild.getLocale(), mod.getId(), role.getId()));
		}

		public void onTempRoleAdded(Guild guild, User mod, User target, Role role, Duration duration) {
			TextChannel channel = getLogChannel(LogChannels.ROLES, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.tempRoleAddedEmbed(guild.getLocale(), mod, target, role, duration));
		}

		public void onTempRoleRemoved(Guild guild, User mod, User target, Role role) {
			TextChannel channel = getLogChannel(LogChannels.ROLES, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.tempRoleRemovedEmbed(guild.getLocale(), mod, target, role));
		}

		public void onTempRoleUpdated(Guild guild, User mod, User target, Role role, Instant until) {
			TextChannel channel = getLogChannel(LogChannels.ROLES, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.tempRoleUpdatedEmbed(guild.getLocale(), mod, target, role, until));
		}

		public void onTempRoleAutoRemoved(Guild guild, String targetId, Role role) {
			TextChannel channel = getLogChannel(LogChannels.ROLES, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.tempRoleAutoRemovedEmbed(guild.getLocale(), targetId, role));
		}

		public void onRoleCheckChildGuild(Guild guild, User admin, Role role, Guild targetGuild) {
			TextChannel channel = getLogChannel(LogChannels.ROLES, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.checkRoleChildGuild(guild.getLocale(), admin.getId(), role.getId(), targetGuild.getName(), targetGuild.getId()));
		}
	}

	// Group actions
	public class Groups {
		public void onCreation(SlashCommandEvent event, Integer groupId, String name) {
			TextChannel channel = getLogChannel(LogChannels.GROUPS, event.getGuild());
			if (channel == null) return;

			sendLog(channel, logUtil.groupCreatedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), event.getGuild().getIdLong(), event.getGuild().getIconUrl(), groupId, name));
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
			TextChannel channel = getLogChannel(LogChannels.GROUPS, event.getGuild());
			if (channel == null) return;

			sendLog(channel, logUtil.groupOwnerDeletedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, groupId, name));
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
			channel = getLogChannel(LogChannels.GROUPS, event.getGuild());
			if (channel == null) return;

			sendLog(channel, logUtil.groupOwnerAddedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, targetName, targetId, groupId, name));
		}

		public void onGuildJoined(SlashCommandEvent event, Integer groupId, String name) {
			long ownerId = db.group.getOwner(groupId);
			String ownerIcon = event.getJDA().getGuildById(ownerId).getIconUrl();

			// Send log to added server
			TextChannel channel = getLogChannel(LogChannels.GROUPS, event.getGuild());
			if (channel != null) {
				sendLog(channel, logUtil.groupMemberJoinedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, groupId, name));
			}

			// Master log
			channel = getLogChannel(LogChannels.GROUPS, ownerId);
			if (channel == null) return;

			sendLog(channel, logUtil.groupOwnerJoinedEmbed(channel.getGuild().getLocale(), ownerId, ownerIcon, event.getGuild().getName(), event.getGuild().getIdLong(), groupId, name));
		}

		public void onGuildLeft(SlashCommandEvent event, Integer groupId, String name) {
			long ownerId = db.group.getOwner(groupId);
			String ownerIcon = event.getJDA().getGuildById(ownerId).getIconUrl();

			// Send log to removed server
			TextChannel channel = getLogChannel(LogChannels.GROUPS, event.getGuild());
			if (channel != null) {
				sendLog(channel, logUtil.groupMemberLeftEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, groupId, name));
			}

			// Master log
			channel = getLogChannel(LogChannels.GROUPS, ownerId);
			if (channel == null) return;

			sendLog(channel, logUtil.groupOwnerLeftEmbed(channel.getGuild().getLocale(), ownerId, ownerIcon, event.getGuild().getName(), event.getGuild().getIdLong(), groupId, name));
		}

		public void onGuildRemoved(SlashCommandEvent event, Guild target, Integer groupId, String name) {
			long ownerId = event.getGuild().getIdLong();
			String ownerIcon = event.getGuild().getIconUrl();

			// Send log to removed server
			TextChannel channel = getLogChannel(LogChannels.GROUPS, target);
			if (channel != null) {
				sendLog(channel, logUtil.groupMemberLeftEmbed(channel.getGuild().getLocale(), "Forced, by group Master", ownerId, ownerIcon, groupId, name));
			}

			// Master log
			channel = getLogChannel(LogChannels.GROUPS, event.getGuild());
			if (channel == null) return;

			sendLog(channel, logUtil.groupOwnerRemovedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, target.getName(), target.getIdLong(), groupId, name));
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
			TextChannel channel = getLogChannel(LogChannels.GROUPS, event.getGuild());
			if (channel == null) return;

			sendLog(channel, logUtil.groupOwnerRenamedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, groupId, oldName, newName));
		}

		public void helperInformAction(int groupId, Guild target, AuditLogEntry auditLogEntry) {
			Guild master = Optional.ofNullable(db.group.getOwner(groupId)).map(bot.JDA::getGuildById).orElse(null);
			if (master == null) return;

			TextChannel channel = getLogChannel(LogChannels.GROUPS, master);
			if (channel == null) return;

			sendLog(channel, logUtil.auditLogEmbed(master.getLocale(), groupId, target, auditLogEntry));
		}

		public void helperInformLeave(int groupId, @Nullable Guild guild, String guildId) {
			Guild master = Optional.ofNullable(db.group.getOwner(groupId)).map(bot.JDA::getGuildById).orElse(null);
			if (master == null) return;

			TextChannel channel = getLogChannel(LogChannels.GROUPS, master);
			if (channel == null) return;

			sendLog(channel, logUtil.botLeftEmbed(master.getLocale(), groupId, guild, guildId));
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
		public void onVerified(User user, Long steam64, Guild guild) {
			TextChannel channel = getLogChannel(LogChannels.VERIFICATION, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.verifiedEmbed(guild.getLocale(), user.getName(), user.getId(), user.getEffectiveAvatarUrl(), (steam64 == null ? null : db.unionVerify.getSteamName(steam64.toString())), steam64));
		}

		public void onUnverified(User user, Long steam64, Guild guild, String reason) {
			TextChannel channel = getLogChannel(LogChannels.VERIFICATION, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.unverifiedEmbed(guild.getLocale(), user.getName(), user.getId(), user.getEffectiveAvatarUrl(), (steam64 == null ? null : db.unionVerify.getSteamName(steam64.toString())), steam64, reason));
		}
	}

	// Tickets actions
	public class Tickets {
		public void onCreate(Guild guild, GuildMessageChannel messageChannel, User author) {
			TextChannel channel = getLogChannel(LogChannels.TICKETS, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.ticketCreatedEmbed(guild.getLocale(), messageChannel, author));
		}

		public void onClose(Guild guild, GuildMessageChannel messageChannel, User userClosed, String authorId, FileUpload file) {
			TextChannel channel = getLogChannel(LogChannels.TICKETS, guild);
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
		public void onAccessAdded(Guild guild, User mod, @Nullable User userTarget, @Nullable Role roleTarget, CmdAccessLevel level) {
			TextChannel channel = getLogChannel(LogChannels.SERVER, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.accessAdded(guild.getLocale(), mod, userTarget, roleTarget, level.getName()));
		}

		public void onAccessRemoved(Guild guild, User mod, @Nullable User userTarget, @Nullable Role roleTarget, CmdAccessLevel level) {
			TextChannel channel = getLogChannel(LogChannels.SERVER, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.accessRemoved(guild.getLocale(), mod, userTarget, roleTarget, level.getName()));
		}

		public void onModuleEnabled(Guild guild, User mod, CmdModule module) {
			TextChannel channel = getLogChannel(LogChannels.SERVER, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.moduleEnabled(guild.getLocale(), mod, module));
		}

		public void onModuleDisabled(Guild guild, User mod, CmdModule module) {
			TextChannel channel = getLogChannel(LogChannels.SERVER, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.moduleDisabled(guild.getLocale(), mod, module));
		}
	}

}

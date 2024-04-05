package union.utils.logs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import union.App;
import union.base.command.SlashCommandEvent;
import union.listeners.MessageListener.MessageData;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.annotation.NotNull;
import union.objects.annotation.Nullable;
import union.objects.constants.Constants;
import union.objects.logs.LogType;
import union.utils.SteamUtil;
import union.utils.database.DBUtil;
import union.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.requests.IncomingWebhookClientImpl;

public class LoggingUtil {
	
	private final App bot;
	private final DBUtil db;
	private final LogEmbedUtil logUtil;

	public final ModerationLogs mod =	new ModerationLogs();
	public final RoleLogs role =		new RoleLogs();
	public final GroupLogs group =		new GroupLogs();
	public final VerificationLogs verify = new VerificationLogs();
	public final TicketLogs ticket =	new TicketLogs();
	public final ServerLogs server =	new ServerLogs();
	public final ChannelLogs channel =	new ChannelLogs();
	public final MemberLogs member =	new MemberLogs();
	public final MessageLogs message =	new MessageLogs();
	public final VoiceLogs voice =		new VoiceLogs();

	public LoggingUtil(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
		this.logUtil = bot.getLogEmbedUtil();
	}

	private IncomingWebhookClientImpl getWebhookClient(LogType type, Guild guild) {
		return bot.getGuildLogger().getWebhookClient(guild, type);
	}

	private void sendLog(Guild guild, LogType type, Supplier<MessageEmbed> embedSupplier) {
		bot.getGuildLogger().sendMessageEmbed(guild, type, embedSupplier);
	}

	private void sendLog(@NotNull IncomingWebhookClientImpl webhookClient, MessageEmbed embed) {
		webhookClient.sendMessageEmbeds(embed).queue();
	}

	// Moderation actions
	public class ModerationLogs {
		private final LogType type = LogType.MODERATION;

		public void onNewCase(Guild guild, User target, CaseData caseData) {
			onNewCase(guild, target, caseData, null);
		}
		
		public void onNewCase(Guild guild, User target, @NotNull CaseData caseData, String oldReason) {
			IncomingWebhookClientImpl client = getWebhookClient(type, guild);
			if (client == null) return;

			if (caseData == null) {
				bot.getAppLogger().warn("Unknown case provided with interaction");
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
			if (embed!=null) sendLog(client, embed);
		}

		public void onStrikesCleared(IReplyCallback event, User target) {
			sendLog(event.getGuild(), type, () -> logUtil.strikesClearedEmbed(event.getGuild().getLocale(), target.getName(), target.getIdLong(),
				event.getUser().getIdLong()));
		}

		public void onStrikeDeleted(IReplyCallback event, User target, int caseId, int deletedAmount, int maxAmount) {
			sendLog(event.getGuild(), type, () -> logUtil.strikeDeletedEmbed(event.getGuild().getLocale(), target.getName(), target.getIdLong(),
				event.getUser().getIdLong(), caseId, deletedAmount, maxAmount));
		}

		public void onAutoUnban(CaseData caseData, Guild guild) {
			sendLog(guild, type, () -> logUtil.autoUnbanEmbed(guild.getLocale(), caseData));
		}

		public void onChangeReason(SlashCommandEvent event, CaseData caseData, Member moderator, String newReason) {
			if (caseData == null) {
				bot.getAppLogger().warn("Unknown case provided with interaction ", event.getName());
				return;
			}

			sendLog(event.getGuild(), type, () -> logUtil.reasonChangedEmbed(event.getGuildLocale(), caseData, moderator.getIdLong(), newReason));
		}

		public void onChangeDuration(SlashCommandEvent event, CaseData caseData, Member moderator, String newTime) {
			if (caseData == null) {
				bot.getAppLogger().warn("Unknown case provided with interaction ", event.getName());
				return;
			}

			sendLog(event.getGuild(), type, () -> logUtil.durationChangedEmbed(event.getGuildLocale(), caseData, moderator.getIdLong(), newTime));
		}

		public void onHelperSyncBan(int groupId, Guild guild, User target, String reason, int success, int max) {
			sendLog(guild, type, () -> logUtil.helperBanEmbed(guild.getLocale(), groupId, target, reason, success, max));
		}

		public void onHelperSyncUnban(int groupId, Guild guild, User target, String reason, int success, int max) {
			sendLog(guild, type, () -> logUtil.helperUnbanEmbed(guild.getLocale(), groupId, target, reason, success, max));
		}

		public void onHelperSyncKick(int groupId, Guild guild, User target, String reason, int success, int max) {
			sendLog(guild, type, () -> logUtil.helperKickEmbed(guild.getLocale(), groupId, target, reason, success, max));
		}

		public void onBlacklistAdded(User mod, User target, Long steam64, List<Integer> groupIds) {
			for (int groupId : groupIds) {
				final String groupInfo = "%s (#%d)".formatted(bot.getDBUtil().group.getName(groupId), groupId);
				Guild master = bot.JDA.getGuildById(bot.getDBUtil().group.getOwner(groupId));
				sendLog(master, type, () -> logUtil.blacklistAddedEmbed(master.getLocale(), mod, target,
					steam64 == null ? "none" : SteamUtil.convertSteam64toSteamID(steam64), groupInfo));
			}
		}

		public void onBlacklistRemoved(User mod, User target, Long steam64, int groupId) {
			final String groupInfo = "%s (#%d)".formatted(bot.getDBUtil().group.getName(groupId), groupId);
			Guild master = bot.JDA.getGuildById(bot.getDBUtil().group.getOwner(groupId));
			sendLog(master, type, () -> logUtil.blacklistRemovedEmbed(master.getLocale(), mod, target,
				steam64 == null ? "none" : SteamUtil.convertSteam64toSteamID(steam64), groupInfo));
		}

		public void onUserBan(AuditLogEntry entry, User target) {
			final Guild guild = entry.getGuild();
			final long modId = entry.getUserIdLong();

			sendLog(guild, type, () -> logUtil.userBanEmbed(guild.getLocale(), target, entry.getReason(), modId));
		}

		public void onUserUnban(AuditLogEntry entry, User target) {
			final Guild guild = entry.getGuild();
			final long modId = entry.getUserIdLong();

			sendLog(guild, type, () -> logUtil.userUnbanEmbed(guild.getLocale(), target, entry.getReason(), modId));
		}
	}

	// Roles actions
	public class RoleLogs {
		private final LogType type = LogType.ROLE;

		public void onApproved(Member member, Member admin, Guild guild, List<Role> roles, String ticketId) {
			sendLog(guild, type, () -> logUtil.rolesApprovedEmbed(guild.getLocale(), ticketId, member.getIdLong(),
				roles.stream().map(role -> role.getAsMention()).collect(Collectors.joining(" ")), admin.getIdLong()));
		}

		public void onCheckRank(Guild guild, User admin, Role role, String rankName) {
			sendLog(guild, type, () -> logUtil.checkRankEmbed(guild.getLocale(), admin.getIdLong(), role.getIdLong(), rankName));
		}

		public void onRoleAdded(Guild guild, User mod, User target, Role role) {
			sendLog(guild, type, () -> logUtil.roleAddedEmbed(guild.getLocale(), mod.getIdLong(), target.getIdLong(), target.getAvatarUrl(), role.getIdLong()));
		}

		public void onRoleRemoved(Guild guild, User mod, User target, Role role) {
			sendLog(guild, type, () -> logUtil.roleRemovedEmbed(guild.getLocale(), mod.getIdLong(), target.getIdLong(), target.getAvatarUrl(), role.getIdLong()));
		}

		public void onRoleRemovedAll(Guild guild, User mod, Role role) {
			sendLog(guild, type, () -> logUtil.roleRemovedAllEmbed(guild.getLocale(), mod.getIdLong(), role.getIdLong()));
		}

		public void onTempRoleAdded(Guild guild, User mod, User target, Role role, Duration duration) {
			sendLog(guild, type, () -> logUtil.tempRoleAddedEmbed(guild.getLocale(), mod, target, role, duration));
		}

		public void onTempRoleRemoved(Guild guild, User mod, User target, Role role) {
			sendLog(guild, type, () -> logUtil.tempRoleRemovedEmbed(guild.getLocale(), mod, target, role));
		}

		public void onTempRoleUpdated(Guild guild, User mod, User target, Role role, Instant until) {
			sendLog(guild, type, () -> logUtil.tempRoleUpdatedEmbed(guild.getLocale(), mod, target, role, until));
		}

		public void onTempRoleAutoRemoved(Guild guild, Long targetId, Role role) {
			sendLog(guild, type, () -> logUtil.tempRoleAutoRemovedEmbed(guild.getLocale(), targetId, role));
		}

		public void onRoleCheckChildGuild(Guild guild, User admin, Role role, Guild targetGuild) {
			sendLog(guild, type, () -> logUtil.checkRoleChildGuild(guild.getLocale(), admin.getIdLong(), role.getIdLong(), targetGuild.getName(), targetGuild.getIdLong()));
		}

		public void onRoleCreate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();
			final String name = entry.getChangeByKey(AuditLogKey.ROLE_NAME).getNewValue();

			sendLog(guild, type, () -> logUtil.roleCreated(guild.getLocale(), id, name, entry.getChanges().values(), entry.getUserIdLong(), entry.getReason()));
		}

		public void onRoleDelete(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();
			final String name = entry.getChangeByKey(AuditLogKey.ROLE_NAME).getOldValue();

			sendLog(guild, type, () -> logUtil.roleDeleted(guild.getLocale(), id, name, entry.getChanges().values(), entry.getUserIdLong(), entry.getReason()));
		}

		public void onRoleUpdate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();
			final String name = guild.getRoleById(id).getName();

			sendLog(guild, type, () -> logUtil.roleUpdate(guild.getLocale(), id, name, entry.getChanges().values(), entry.getUserIdLong()));
		}
	}

	// Group actions
	public class GroupLogs {
		private final LogType type = LogType.GROUP;

		public void onCreation(SlashCommandEvent event, Integer groupId, String name) {
			sendLog(event.getGuild(), type, () -> logUtil.groupCreatedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), event.getGuild().getIdLong(),
				event.getGuild().getIconUrl(), groupId, name));
		}

		public void onDeletion(SlashCommandEvent event, Integer groupId, String name) {
			long ownerId = event.getGuild().getIdLong();
			String ownerIcon = event.getGuild().getIconUrl();

			// For each group guild (except master) remove if from group DB and send log to log channel
			List<Long> memberIds = db.group.getGroupMembers(groupId);
			for (Long memberId : memberIds) {
				db.group.remove(groupId, memberId);
				Guild membed = bot.JDA.getGuildById(memberId);

				sendLog(membed, type, () -> logUtil.groupMemberDeletedEmbed(membed.getLocale(), ownerId, ownerIcon, groupId, name));
			}

			// Master log
			sendLog(event.getGuild(), type, () -> logUtil.groupOwnerDeletedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, groupId, name));
		}

		public void onGuildAdded(SlashCommandEvent event, Integer groupId, String name, long targetId, String targetName) {
			long ownerId = event.getGuild().getIdLong();
			String ownerIcon = event.getGuild().getIconUrl();

			// Send log to added server
			Guild member = bot.JDA.getGuildById(targetId);
			sendLog(member, type, () -> logUtil.groupMemberAddedEmbed(member.getLocale(), ownerId, ownerIcon, groupId, name));

			// Master log
			sendLog(event.getGuild(), type, () -> logUtil.groupOwnerAddedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, targetName, targetId, groupId, name));
		}

		public void onGuildJoined(SlashCommandEvent event, Integer groupId, String name) {
			long ownerId = db.group.getOwner(groupId);
			Guild owner = bot.JDA.getGuildById(ownerId);
			String ownerIcon = owner.getIconUrl();

			// Send log to added server
			sendLog(event.getGuild(), type, () -> logUtil.groupMemberJoinedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, groupId, name));

			// Master log
			sendLog(owner, type, () -> logUtil.groupOwnerJoinedEmbed(owner.getLocale(), ownerId, ownerIcon, event.getGuild().getName(), event.getGuild().getIdLong(), groupId, name));
		}

		public void onGuildLeft(SlashCommandEvent event, Integer groupId, String name) {
			long ownerId = db.group.getOwner(groupId);
			Guild owner = bot.JDA.getGuildById(ownerId);
			String ownerIcon = owner.getIconUrl();

			// Send log to removed server
			sendLog(event.getGuild(), type, () -> logUtil.groupMemberLeftEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, groupId, name));

			// Master log
			sendLog(owner, type, () -> logUtil.groupOwnerLeftEmbed(owner.getLocale(), ownerId, ownerIcon, event.getGuild().getName(), event.getGuild().getIdLong(), groupId, name));
		}

		public void onGuildRemoved(SlashCommandEvent event, Guild target, Integer groupId, String name) {
			long ownerId = event.getGuild().getIdLong();
			String ownerIcon = event.getGuild().getIconUrl();

			// Send log to removed server
			sendLog(target, type, () -> logUtil.groupMemberLeftEmbed(target.getLocale(), "Forced, by group Master", ownerId, ownerIcon, groupId, name));

			// Master log
			sendLog(event.getGuild(), type, () -> logUtil.groupOwnerRemovedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, target.getName(), target.getIdLong(), groupId, name));
		}

		public void onRenamed(SlashCommandEvent event, String oldName, Integer groupId, String newName) {
			long ownerId = event.getGuild().getIdLong();
			String ownerIcon = event.getGuild().getIconUrl();

			// Send log to each group guild
			List<Long> memberIds = db.group.getGroupMembers(groupId);
			for (Long memberId : memberIds) {
				db.group.remove(groupId, memberId);
				Guild member = bot.JDA.getGuildById(memberId);

				sendLog(member, type, () -> logUtil.groupMemberRenamedEmbed(member.getLocale(), ownerId, ownerIcon, groupId, oldName, newName));
			}

			// Master log
			sendLog(event.getGuild(), type, () -> logUtil.groupOwnerRenamedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), ownerId, ownerIcon, groupId, oldName, newName));
		}

		public void helperInformAction(int groupId, Guild target, AuditLogEntry auditLogEntry) {
			Guild master = Optional.ofNullable(db.group.getOwner(groupId)).map(bot.JDA::getGuildById).orElse(null);
			if (master == null) return;

			sendLog(master, type, () -> logUtil.auditLogEmbed(master.getLocale(), groupId, target, auditLogEntry));
		}

		public void helperInformLeave(int groupId, @Nullable Guild guild, String guildId) {
			Guild master = Optional.ofNullable(db.group.getOwner(groupId)).map(bot.JDA::getGuildById).orElse(null);
			if (master == null) return;

			sendLog(master, type, () -> logUtil.botLeftEmbed(master.getLocale(), groupId, guild, guildId));
		}

		public void helperAlertTriggered(int groupId, Guild targetGuild, Member targetMember, String actionTaken, String reason) {
			Guild master = Optional.ofNullable(db.group.getOwner(groupId)).map(bot.JDA::getGuildById).orElse(null);
			if (master == null) return;

			IncomingWebhookClientImpl client = getWebhookClient(type, master);
			if (client == null) return;
			try {
				client.sendMessage("||"+Constants.DEVELOPER_TAG+"||").addEmbeds(logUtil.alertEmbed(master.getLocale(), groupId, targetGuild, targetMember, actionTaken, reason)).queue();
			} catch (Exception ex) {}
		}
	}

	// Verification actions
	public class VerificationLogs {
		private final LogType type = LogType.VERIFICATION;

		public void onVerified(User user, Long steam64, Guild guild) {
			sendLog(guild, type, () -> logUtil.verifiedEmbed(guild.getLocale(), user.getName(), user.getIdLong(), user.getEffectiveAvatarUrl(),
				(steam64 == null ? null : db.unionVerify.getSteamName(steam64.toString())), steam64)
			);
		}

		public void onUnverified(User user, Long steam64, Guild guild, String reason) {
			sendLog(guild, type, () -> logUtil.unverifiedEmbed(guild.getLocale(), user.getName(), user.getIdLong(), user.getEffectiveAvatarUrl(),
				(steam64 == null ? null : db.unionVerify.getSteamName(steam64.toString())), steam64, reason)
			);
		}

		public void onVerifiedAttempt(User user, Long steam64, Guild guild, String reason) {
			sendLog(guild, type, () -> logUtil.verifyAttempt(guild.getLocale(), user.getName(), user.getIdLong(), user.getEffectiveAvatarUrl(),
				steam64, reason)
			);
		}
	}

	// Tickets actions
	public class TicketLogs {
		private final LogType type = LogType.TICKET;

		public void onCreate(Guild guild, GuildMessageChannel messageChannel, User author) {
			sendLog(guild, type, () -> logUtil.ticketCreatedEmbed(guild.getLocale(), messageChannel, author));
		}

		public void onClose(Guild guild, GuildMessageChannel messageChannel, User userClosed, String authorId, FileUpload file) {
			IncomingWebhookClientImpl client = getWebhookClient(type, guild);
			if (client == null) return;
			try {
				client.sendMessageEmbeds(
					logUtil.ticketClosedEmbed(guild.getLocale(), messageChannel, userClosed, authorId, db.ticket.getClaimer(messageChannel.getId()))
				).addFiles(file).queue();
			} catch (Exception ex) {}
		}

		public void onClose(Guild guild, GuildMessageChannel messageChannel, User userClosed, String authorId) {
			sendLog(guild, type, () -> logUtil.ticketClosedEmbed(guild.getLocale(), messageChannel, userClosed, authorId, db.ticket.getClaimer(messageChannel.getId())));
		}
	}

	// Server actions
	public class ServerLogs {
		private final LogType type = LogType.SERVER;

		public void onAccessAdded(Guild guild, User mod, @Nullable User userTarget, @Nullable Role roleTarget, CmdAccessLevel level) {
			sendLog(guild, type, () -> logUtil.accessAdded(guild.getLocale(), mod, userTarget, roleTarget, level.getName()));
		}

		public void onAccessRemoved(Guild guild, User mod, @Nullable User userTarget, @Nullable Role roleTarget, CmdAccessLevel level) {
			sendLog(guild, type, () -> logUtil.accessRemoved(guild.getLocale(), mod, userTarget, roleTarget, level.getName()));
		}

		public void onModuleEnabled(Guild guild, User mod, CmdModule module) {
			sendLog(guild, type, () -> logUtil.moduleEnabled(guild.getLocale(), mod, module));
		}

		public void onModuleDisabled(Guild guild, User mod, CmdModule module) {
			sendLog(guild, type, () -> logUtil.moduleDisabled(guild.getLocale(), mod, module));
		}

		public void onGuildUpdate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = guild.getIdLong();
			final String name = guild.getName();

			sendLog(guild, type, () -> logUtil.guildUpdate(guild.getLocale(), id, name, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onEmojiCreate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			sendLog(guild, type, () -> logUtil.emojiCreate(guild.getLocale(), id, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onEmojiUpdate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			sendLog(guild, type, () -> logUtil.emojiUpdate(guild.getLocale(), id, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onEmojiDelete(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			sendLog(guild, type, () -> logUtil.emojiDelete(guild.getLocale(), id, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onStickerCreate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			sendLog(guild, type, () -> logUtil.stickerCreate(guild.getLocale(), id, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onStickerUpdate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			sendLog(guild, type, () -> logUtil.stickerUpdate(guild.getLocale(), id, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onStickerDelete(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			sendLog(guild, type, () -> logUtil.stickerDelete(guild.getLocale(), id, entry.getChanges().values(), entry.getUserIdLong()));
		}

	}

	// Channel actions
	public class ChannelLogs {
		private final LogType type = LogType.CHANNEL;

		public void onChannelCreate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();
			final String name = entry.getChangeByKey(AuditLogKey.CHANNEL_NAME).getNewValue();

			sendLog(guild, type, () -> logUtil.channelCreated(guild.getLocale(), id, name, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onChannelDelete(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();
			final String name = entry.getChangeByKey(AuditLogKey.CHANNEL_NAME).getOldValue();

			sendLog(guild, type, () -> logUtil.channelDeleted(guild.getLocale(), id, name, entry.getChanges().values(), entry.getUserIdLong(), entry.getReason()));
		}

		public void onChannelUpdate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();
			final String name = guild.getGuildChannelById(id).getName();

			sendLog(guild, type, () -> logUtil.channelUpdate(guild.getLocale(), id, name, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onOverrideCreate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			sendLog(guild, type, () -> logUtil.overrideCreate(guild.getLocale(), id, entry, entry.getUserIdLong()));
		}

		public void onOverrideUpdate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			sendLog(guild, type, () -> logUtil.overrideUpdate(guild.getLocale(), id, entry, entry.getUserIdLong(), guild.getId()));
		}

		public void onOverrideDelete(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			sendLog(guild, type, () -> logUtil.overrideDelete(guild.getLocale(), id, entry, entry.getUserIdLong()));
		}
	}

	// Member actions
	public class MemberLogs {
		private final LogType type = LogType.MEMBER;

		public void onNickChange(Member target, String oldNick, String newNick) {
			final Guild guild = target.getGuild();

			sendLog(guild, type, () -> logUtil.memberNickUpdate(guild.getLocale(), target.getUser(), oldNick, newNick));
		}

		public void onRoleChange(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			sendLog(guild, type, () -> logUtil.rolesChange(guild.getLocale(), id, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onJoined(Member member) {
			final Guild guild = member.getGuild();

			sendLog(guild, type, () -> logUtil.memberJoin(guild.getLocale(), member));
		}

		public void onLeft(Guild guild, Member cachedMember, User user) {
			sendLog(guild, type, () -> logUtil.memberLeave(guild.getLocale(), cachedMember, user, cachedMember!=null ? cachedMember.getRoles() : List.of()));
		}
	}

	// Voice actions
	public class VoiceLogs {
		private final LogType type = LogType.VOICE;

		public void onVoiceMute(Member target, boolean isMuted, Long modId) {
			final Guild guild = target.getGuild();

			sendLog(guild, type, () -> logUtil.voiceMute(guild.getLocale(), target.getIdLong(), target.getUser().getName(), target.getEffectiveAvatarUrl(), isMuted, modId));
		}

		public void onVoiceDeafen(Member target, boolean isDeafen, Long modId) {
			final Guild guild = target.getGuild();

			sendLog(guild, type, () -> logUtil.voiceDeafen(guild.getLocale(), target.getIdLong(), target.getUser().getName(), target.getEffectiveAvatarUrl(), isDeafen, modId));
		}
	}

	// Message actions
	public class MessageLogs {
		private final LogType type = LogType.MESSAGE;

		public void onMessageUpdate(Member author, GuildChannel channel, long messageId, MessageData oldData, MessageData newData) {
			final Guild guild = author.getGuild();

			sendLog(guild, type, () -> logUtil.messageUpdate(guild.getLocale(), author, channel.getIdLong(), messageId, oldData, newData));
		}

		public void onMessageDelete(GuildChannel channel, long messageId, MessageData data, Long modId) {
			final Guild guild = channel.getGuild();
			if ((data == null || data.isEmpty()) && modId == null) return;

			sendLog(guild, type, () -> logUtil.messageDelete(guild.getLocale(), channel.getIdLong(), messageId, data, modId));
		}

		public void onMessageBulkDelete(GuildChannel channel, String count, List<MessageData> messages, Long modId) {
			final Guild guild = channel.getGuild();
			IncomingWebhookClientImpl client = getWebhookClient(type, guild);
			if (client == null) return;

			if (!messages.isEmpty()) {
				FileUpload fileUpload = uploadContent(messages, channel.getIdLong());
				if (fileUpload != null) {
					client.sendMessageEmbeds(logUtil.messageBulkDelete(guild.getLocale(), channel.getIdLong(), count, modId))
						.addFiles(uploadContent(messages, channel.getIdLong()))
						.queue();
					return;
				}	
			}
			client.sendMessageEmbeds(logUtil.messageBulkDelete(guild.getLocale(), channel.getIdLong(), count, modId)).queue();				
		}

		private FileUpload uploadContent(List<MessageData> messages, long channelId) {
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				baos.write("Channel ID: %s\n\n".formatted(channelId).getBytes());
				for (MessageData data : messages) {
					if (data.isEmpty()) continue;
					baos.write("[%s (%s)]:\n".formatted(data.getAuthorName(), data.getAuthorId()).getBytes());
					if (data.getAttachment() != null)
						baos.write("[Attachement: %s]\n".formatted(data.getAttachment().getFileName()).getBytes());
					baos.write(data.getContent().getBytes());
					baos.write("\n\n-------===-------\n\n".getBytes());
				}
				return FileUpload.fromData(new ByteArrayInputStream(baos.toByteArray()), channelId+"_"+Instant.now().toEpochMilli()+".txt");
			} catch (IOException ex) {
				bot.getAppLogger().error("Error at bulk deleted messages content upload.", ex);
				return null;
			}
			
		}
	}
}

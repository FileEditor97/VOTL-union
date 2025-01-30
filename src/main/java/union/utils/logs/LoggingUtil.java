package union.utils.logs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.utils.AttachmentProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import union.App;
import union.base.command.SlashCommandEvent;
import union.listeners.MessageListener.MessageData;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.Constants;
import union.objects.logs.LogType;
import union.utils.CaseProofUtil;
import union.utils.SteamUtil;
import union.utils.database.DBUtil;
import union.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.requests.IncomingWebhookClientImpl;
import union.utils.encoding.EncodingUtil;

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
	public final LevelLogs level =		new LevelLogs();

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

	private CompletableFuture<String> submitLog(@NotNull IncomingWebhookClientImpl webhookClient, MessageEmbed embed) {
		return webhookClient.sendMessageEmbeds(embed).submit()
			.exceptionally(ex -> null)
			.thenApply(msg -> msg==null ? null : msg.getJumpUrl());
	}

	private CompletableFuture<String> submitLog(@NotNull IncomingWebhookClientImpl webhookClient, MessageEmbed embed, CaseProofUtil.ProofData proofData) {
		try (final InputStream is = new AttachmentProxy(proofData.proxyUrl).download().join()) {
			return webhookClient.sendMessageEmbeds(embed).addFiles(FileUpload.fromData(is.readAllBytes(), proofData.fileName)).submit()
				.exceptionally(ex -> null)
				.thenApply(msg -> msg==null ? null : msg.getJumpUrl());
		} catch (IOException e) {
			bot.getAppLogger().error("Exception at log submission.", e);
			return submitLog(webhookClient, embed);
		}
	}

	// Panic webhook
	private void panic(MessageEmbed embed, Guild guild) {
		String webhookUrl = bot.getSettings().getPanicWebhook();
		if (webhookUrl == null) return;
		WebhookClient<Message> client = WebhookClient.createClient(bot.JDA, webhookUrl);
		client.sendMessage(guild.getName()).setEmbeds(embed).queue(null, failure -> {
			bot.getAppLogger().error("Panic webhook failure.", failure);
		});
	}


	// Moderation actions
	@SuppressWarnings("LoggingSimilarMessage")
	public class ModerationLogs {
		private final LogType type = LogType.MODERATION;

		public CompletableFuture<String> onNewCase(Guild guild, User target, CaseData caseData) {
			return onNewCase(guild, target, caseData, null, null);
		}

		public CompletableFuture<String> onNewCase(Guild guild, User target, CaseData caseData, String optionalData) {
			return onNewCase(guild, target, caseData, null, optionalData);
		}

		public CompletableFuture<String> onNewCase(Guild guild, User target, CaseData caseData, CaseProofUtil.ProofData proofData) {
			return onNewCase(guild, target, caseData, proofData, null);
		}
		
		public CompletableFuture<String> onNewCase(Guild guild, User target, @NotNull CaseData caseData, @Nullable CaseProofUtil.ProofData proofData, String optionalData) {
			IncomingWebhookClientImpl client = getWebhookClient(type, guild);
			if (client == null) return CompletableFuture.completedFuture(null);

			String proofFileName = proofData==null ? null : proofData.setFileName(caseData.getLocalIdInt());
			MessageEmbed embed = switch (caseData.getCaseType()) {
				case BAN ->
					logUtil.banEmbed(guild.getLocale(), caseData, target.getEffectiveAvatarUrl(), proofFileName);
				case UNBAN ->
					logUtil.unbanEmbed(guild.getLocale(), caseData, optionalData);
				case MUTE ->
					logUtil.muteEmbed(guild.getLocale(), caseData, target.getEffectiveAvatarUrl(), proofFileName);
				case UNMUTE ->
					logUtil.unmuteEmbed(guild.getLocale(), caseData, target.getEffectiveAvatarUrl(), optionalData);
				case KICK ->
					logUtil.kickEmbed(guild.getLocale(), caseData, target.getEffectiveAvatarUrl(), proofFileName);
				case STRIKE_1, STRIKE_2, STRIKE_3 ->
					logUtil.strikeEmbed(guild.getLocale(), caseData, target.getEffectiveAvatarUrl(), proofFileName);
				case GAME_STRIKE ->
					logUtil.gameStrikeEmbed(guild.getLocale(), caseData, target.getEffectiveAvatarUrl(), proofFileName, optionalData);
			};
			if (embed == null) return CompletableFuture.completedFuture(null);

			return proofData==null ? submitLog(client, embed) : submitLog(client, embed, proofData);
		}

		public void onStrikesCleared(IReplyCallback event, User target) {
			sendLog(event.getGuild(), type, () -> logUtil.strikesClearedEmbed(event.getGuild().getLocale(), target.getName(), target.getIdLong(),
				event.getUser().getIdLong()));
		}

		public void onStrikeDeleted(IReplyCallback event, User target, int caseLocalId, int deletedAmount, int maxAmount) {
			sendLog(event.getGuild(), type, () -> logUtil.strikeDeletedEmbed(event.getGuild().getLocale(), target.getName(), target.getIdLong(),
				event.getUser().getIdLong(), caseLocalId, deletedAmount, maxAmount));
		}

		public void onAutoUnban(CaseData caseData, Guild guild) {
			sendLog(guild, type, () -> logUtil.autoUnbanEmbed(guild.getLocale(), caseData));
		}

		public void onChangeReason(SlashCommandEvent event, CaseData caseData, Member moderator, String newReason) {
			if (caseData == null) {
				bot.getAppLogger().warn("Unknown case provided with interaction {}", event.getName());
				return;
			}

			sendLog(event.getGuild(), type, () -> logUtil.reasonChangedEmbed(event.getGuildLocale(), caseData, moderator.getIdLong(), newReason));
		}

		public void onChangeDuration(SlashCommandEvent event, CaseData caseData, Member moderator, String newTime) {
			if (caseData == null) {
				bot.getAppLogger().warn("Unknown case provided with interaction {}", event.getName());
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
				if (target != null)
					sendLog(master, type, () -> logUtil.blacklistAddedEmbed(master.getLocale(), mod, target,
						steam64 == null ? "none" : SteamUtil.convertSteam64toSteamID(steam64), groupInfo));
				else
					sendLog(master, type, () -> logUtil.blacklistAddedEmbed(master.getLocale(), mod,
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

		public void onUserKick(AuditLogEntry entry, User target) {
			final Guild guild = entry.getGuild();
			final long modId = entry.getUserIdLong();

			sendLog(guild, type, () -> logUtil.userKickEmbed(guild.getLocale(), target, entry.getReason(), modId));
		}

		public void onUserTimeoutUpdated(AuditLogEntry entry, User target, OffsetDateTime until) {
			final Guild guild = entry.getGuild();
			final long modId = entry.getUserIdLong();

			sendLog(guild, type, () -> logUtil.userTimeoutUpdateEmbed(guild.getLocale(), target, entry.getReason(), modId, until));
		}

		public void onUserTimeoutRemoved(AuditLogEntry entry, User target) {
			final Guild guild = entry.getGuild();
			final long modId = entry.getUserIdLong();

			sendLog(guild, type, () -> logUtil.userTimeoutRemoveEmbed(guild.getLocale(), target, entry.getReason(), modId));
		}

		public void onMessagePurge(User mod, User target, int msgCount, GuildChannel channel) {
			final Guild guild = channel.getGuild();

			sendLog(guild, type, () -> logUtil.messagePurge(guild.getLocale(), mod, target, msgCount, channel));
		}
	}

	// Roles actions
	public class RoleLogs {
		private final LogType type = LogType.ROLE;

		public void onApproved(Member member, Member admin, Guild guild, List<Role> roles, String ticketId) {
			sendLog(guild, type, () -> logUtil.rolesApprovedEmbed(guild.getLocale(), ticketId, member.getIdLong(),
				roles.stream().map(Role::getAsMention).collect(Collectors.joining(" ")), admin.getIdLong()));
		}

		public void onCheckRank(Guild guild, User admin, Role role, String rankName) {
			sendLog(guild, type, () -> logUtil.checkRankEmbed(guild.getLocale(), admin.getIdLong(), role.getIdLong(), rankName));
		}

		public void onRoleAdded(Guild guild, User mod, User target, Role role) {
			sendLog(guild, type, () -> logUtil.roleAddedEmbed(guild.getLocale(), mod.getIdLong(), target.getIdLong(), target.getEffectiveAvatarUrl(), role.getAsMention()));
		}

		public void onRolesAdded(Guild guild, User mod, User target, String rolesAdded) {
			sendLog(guild, type, () -> logUtil.rolesAddedEmbed(guild.getLocale(), mod.getIdLong(), target.getIdLong(), target.getEffectiveAvatarUrl(), rolesAdded));
		}

		public void onRoleRemoved(Guild guild, User mod, User target, Role role) {
			sendLog(guild, type, () -> logUtil.roleRemovedEmbed(guild.getLocale(), mod.getIdLong(), target.getIdLong(), target.getEffectiveAvatarUrl(), role.getAsMention()));
		}

		public void onRolesRemoved(Guild guild, User mod, User target, String rolesRemoved) {
			sendLog(guild, type, () -> logUtil.rolesRemovedEmbed(guild.getLocale(), mod.getIdLong(), target.getIdLong(), target.getEffectiveAvatarUrl(), rolesRemoved));
		}

		public void onRoleRemovedAll(Guild guild, User mod, Role role) {
			sendLog(guild, type, () -> logUtil.roleRemovedAllEmbed(guild.getLocale(), mod.getIdLong(), role.getIdLong()));
		}

		public void onRolesModified(Guild guild, User mod, User target, String rolesModified) {
			sendLog(guild, type, () -> logUtil.rolesModifiedEmbed(guild.getLocale(), mod.getIdLong(), target.getIdLong(), target.getEffectiveAvatarUrl(), rolesModified));
		}

		public void onTempRoleAdded(Guild guild, User mod, User target, Role role, Duration duration, boolean deleteAfter) {
			sendLog(guild, type, () -> logUtil.tempRoleAddedEmbed(guild.getLocale(), mod, target, role, duration, deleteAfter));
		}

		public void onTempRoleAdded(Guild guild, User mod, User target, long roleId, Duration duration, boolean deleteAfter) {
			sendLog(guild, type, () -> logUtil.tempRoleAddedEmbed(guild.getLocale(), mod, target, roleId, duration, deleteAfter));
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
				Guild member = bot.JDA.getGuildById(memberId);

				sendLog(member, type, () -> logUtil.groupMemberDeletedEmbed(member.getLocale(), ownerId, ownerIcon, groupId, name));
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
			// Try panic
			panic(logUtil.botLeftEmbed(bot.getLocaleUtil().defaultLocale, groupId, guild, guildId), guild);
			// Log
			Guild master = Optional.ofNullable(db.group.getOwner(groupId)).map(bot.JDA::getGuildById).orElse(null);
			if (master == null) return;

			IncomingWebhookClientImpl client = getWebhookClient(type, master);
			if (client == null) return;
			try {
				String ping = Optional.ofNullable(db.getGuildSettings(master).getAnticrashPing()).orElse("|| <@"+Constants.DEVELOPER_ID+"> ||");
				client.sendMessage(ping).addEmbeds(logUtil.botLeftEmbed(master.getLocale(), groupId, guild, guildId)).queue();
			} catch (Exception ignored) {}
		}

		public void helperAlertTriggered(int groupId, Guild targetGuild, Member targetMember, String actionTaken, String reason) {
			// Try panic
			panic(logUtil.alertEmbed(bot.getLocaleUtil().defaultLocale, groupId, targetGuild, targetMember, actionTaken, reason), targetGuild);
			// Log
			Guild master = Optional.ofNullable(db.group.getOwner(groupId)).map(bot.JDA::getGuildById).orElse(null);
			if (master == null) return;

			IncomingWebhookClientImpl client = getWebhookClient(type, master);
			if (client == null) return;
			try {
				String ping = Optional.ofNullable(db.getGuildSettings(master).getAnticrashPing()).orElse("|| <@"+Constants.DEVELOPER_ID+"> ||");
				client.sendMessage(ping).addEmbeds(logUtil.alertEmbed(master.getLocale(), groupId, targetGuild, targetMember, actionTaken, reason)).queue();
			} catch (Exception ignored) {}
		}

		public void helperInformVerify(int groupId, Guild targetGuild, User targetUser, String actionTaken) {
			Guild master = Optional.ofNullable(db.group.getOwner(groupId)).map(bot.JDA::getGuildById).orElse(null);
			if (master == null) return;

			if (targetUser.isBot())
				sendLog(master, type, () -> logUtil.informBotVerify(master.getLocale(), groupId, targetGuild, targetUser, actionTaken));
			else
				sendLog(master, type, () -> logUtil.informUserVerify(master.getLocale(), groupId, targetGuild, targetUser, actionTaken));
		}

		public void helperInformBadUser(int groupId, Guild targetGuild, User targetUser) {
			// Try panic
			panic(logUtil.informBadUser(bot.getLocaleUtil().defaultLocale, groupId, targetGuild, targetUser), targetGuild);
			// Log
			Guild master = Optional.ofNullable(db.group.getOwner(groupId)).map(bot.JDA::getGuildById).orElse(null);
			if (master == null) return;

			sendLog(master, type, () -> logUtil.informBadUser(master.getLocale(), groupId, targetGuild, targetUser));
		}
	}

	// Verification actions
	public class VerificationLogs {
		private final LogType type = LogType.VERIFICATION;

		public void onVerified(User user, Long steam64, Guild guild) {
			sendLog(guild, type, () -> logUtil.verifiedEmbed(guild.getLocale(), user.getName(), user.getIdLong(), user.getEffectiveAvatarUrl(),
				(steam64 == null ? null : db.unionVerify.getSteamName(steam64)), steam64)
			);
		}

		public void onUnverified(User user, Long steam64, Guild guild, String reason) {
			sendLog(guild, type, () -> logUtil.unverifiedEmbed(guild.getLocale(), user.getName(), user.getIdLong(), user.getEffectiveAvatarUrl(),
				(steam64 == null ? null : db.unionVerify.getSteamName(steam64)), steam64, reason)
			);
		}

		public void onVerifyAttempted(User user, Long steam64, Guild guild, String reason) {
			sendLog(guild, type, () -> logUtil.verifyAttempt(guild.getLocale(), user.getName(), user.getIdLong(), user.getEffectiveAvatarUrl(),
				steam64, reason)
			);
		}

		public void onVerifyBlacklisted(User user, Long steam64, Guild guild, String reason) {
			// Try panic
			panic(logUtil.verifyAttempt(bot.getLocaleUtil().defaultLocale, user.getName(), user.getIdLong(), user.getEffectiveAvatarUrl(),
				steam64, reason), guild);
			// Log
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

		public void onClose(Guild guild, GuildMessageChannel messageChannel, User userClosed, long authorId, FileUpload file) {
			if (file == null) onClose(guild, messageChannel, userClosed, authorId);

			IncomingWebhookClientImpl client = getWebhookClient(type, guild);
			if (client == null) return;
			try {
				client.sendMessageEmbeds(
					logUtil.ticketClosedEmbed(guild.getLocale(), messageChannel, userClosed, authorId, db.ticket.getClaimer(messageChannel.getIdLong()))
				).addFiles(file).queue();
			} catch (Exception ignored) {}
		}

		public void onClose(Guild guild, GuildMessageChannel messageChannel, User userClosed, long authorId) {
			sendLog(guild, type, () -> logUtil.ticketClosedEmbed(guild.getLocale(), messageChannel, userClosed, authorId, db.ticket.getClaimer(messageChannel.getIdLong())));
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
			if (oldData == null || newData == null) return;

			final Guild guild = channel.getGuild();
			IncomingWebhookClientImpl client = getWebhookClient(type, guild);
			if (client == null) return;

			MessageEmbed embed = logUtil.messageUpdate(guild.getLocale(), author, channel.getIdLong(), messageId, oldData, newData);
			if (embed == null) return;
			FileUpload fileUpload = uploadContentUpdate(oldData, newData, messageId);
			if (fileUpload != null) {
				client.sendMessageEmbeds(embed)
					.addFiles(fileUpload)
					.queue();
			} else {
				client.sendMessageEmbeds(embed).queue();
			}
		}

		public void onMessageDelete(GuildChannel channel, long messageId, MessageData data, Long modId) {
			final Guild guild = channel.getGuild();
			IncomingWebhookClientImpl client = getWebhookClient(type, guild);
			if (client == null) return;

			if ((data == null || data.isEmpty()) && modId == null) return;

			FileUpload fileUpload = uploadContent(data, messageId);
			if (fileUpload != null) {
				client.sendMessageEmbeds(logUtil.messageDelete(guild.getLocale(), channel.getIdLong(), messageId, data, modId))
					.addFiles(fileUpload)
					.queue();
				return;
			}
			client.sendMessageEmbeds(logUtil.messageDelete(guild.getLocale(), channel.getIdLong(), messageId, data, modId)).queue();
		}

		public void onMessageBulkDelete(GuildChannel channel, String count, List<MessageData> messages, Long modId) {
			final Guild guild = channel.getGuild();
			IncomingWebhookClientImpl client = getWebhookClient(type, guild);
			if (client == null) return;

			if (!messages.isEmpty()) {
				FileUpload fileUpload = uploadContentBulk(messages, channel.getIdLong());
				if (fileUpload != null) {
					client.sendMessageEmbeds(logUtil.messageBulkDelete(guild.getLocale(), channel.getIdLong(), count, modId))
						.addFiles(fileUpload)
						.queue();
					return;
				}
			}
			client.sendMessageEmbeds(logUtil.messageBulkDelete(guild.getLocale(), channel.getIdLong(), count, modId)).queue();			
		}

		private FileUpload uploadContentUpdate(MessageData oldData, MessageData newData, long messageId) {
			if (oldData.getContent().isBlank() || newData.getContent().isBlank()) return null;
			if (newData.getContent().equals(oldData.getContent())) return null;
			if (newData.getContent().length()+oldData.getContent().length() < 1000) return null;
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				baos.write("[%s (%s)]\n".formatted(newData.getAuthorName(), newData.getAuthorId()).getBytes());

				baos.write("------- OLD MESSAGE\n\n".getBytes());
				baos.write(oldData.getContent().getBytes(StandardCharsets.UTF_8));
				baos.write("\n\n------- NEW MESSAGE\n\n".getBytes());
				baos.write(newData.getContent().getBytes(StandardCharsets.UTF_8));

				return FileUpload.fromData(baos.toByteArray(), EncodingUtil.encodeMessage(messageId, Instant.now().getEpochSecond()));
			} catch (IOException ex) {
				bot.getAppLogger().error("Error at updated message content upload.", ex);
				return null;
			}
		}

		private FileUpload uploadContent(MessageData data, long messageId) {
			if (data == null || data.isEmpty()) return null;
			if (data.getContent().length() < 1000) return null;
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				baos.write("[%s (%s)]\n".formatted(data.getAuthorName(), data.getAuthorId()).getBytes());

				baos.write("------- CONTENT\n\n".getBytes());
				baos.write(data.getContent().getBytes(StandardCharsets.UTF_8));

				return FileUpload.fromData(baos.toByteArray(), EncodingUtil.encodeMessage(messageId, Instant.now().getEpochSecond()));
			} catch (IOException ex) {
				bot.getAppLogger().error("Error at deleted message content upload.", ex);
				return null;
			}
		}

		private FileUpload uploadContentBulk(List<MessageData> messages, long channelId) {
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				baos.write("Channel ID: %s\n\n".formatted(channelId).getBytes());
				int cached = 0;
				for (MessageData data : messages) {
					if (data.isEmpty()) continue;
					cached++;
					baos.write("[%s (%s)]:\n".formatted(data.getAuthorName(), data.getAuthorId()).getBytes());
					if (data.getAttachment() != null)
						baos.write("[Attachment: %s]\n".formatted(data.getAttachment().getFileName()).getBytes(StandardCharsets.UTF_8));
					baos.write(data.getContent().getBytes(StandardCharsets.UTF_8));
					baos.write("\n\n-------===-------\n\n".getBytes());
				}
				if (cached == 0) return null;
				return FileUpload.fromData(baos.toByteArray(), EncodingUtil.encodeMessage(channelId, Instant.now().getEpochSecond()));
			} catch (IOException ex) {
				bot.getAppLogger().error("Error at bulk deleted messages content upload.", ex);
				return null;
			}
		}
	}

	public class LevelLogs {
		private final LogType type = LogType.LEVEL;

		public void onLevelUp(Member target, long level) {
			final Guild guild = target.getGuild();

			IncomingWebhookClientImpl client = getWebhookClient(type, guild);
			if (client == null) return;
			try {
				client.sendMessageEmbeds(logUtil.levelUp(guild.getLocale(), target, level)).setAllowedMentions(List.of()).queue();
			} catch (Exception ignored) {}
		}
	}
}

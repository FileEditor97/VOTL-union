package union.listeners;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;

import union.App;
import union.base.command.SlashCommandEvent;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.LogChannels;
import union.utils.LogUtil;
import union.utils.database.DBUtil;

import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Guild.Ban;
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

	private TextChannel getLogChannel(LogChannels type, String guildId) {
		return Optional.ofNullable(db.guild.getLogChannel(type, guildId)).map(bot.JDA::getTextChannelById).orElse(null);
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
		public void onBan(SlashCommandEvent event, User target, Member moderator, Integer banId) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, event.getGuild());
			if (channel == null) return;

			Map<String, Object> ban = db.ban.getInfo(banId);
			if (ban.isEmpty() || ban == null) {
				bot.getLogger().warn("That is not supposed to happen... Ban ID: %s", banId);
				return;
			}

			sendLog(channel, logUtil.banEmbed(event.getGuildLocale(), ban, target.getAvatarUrl()));
		}

		public void onSyncBan(SlashCommandEvent event, Guild guild, User target, String reason) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, event.getGuild());
			if (channel == null) return;

			sendLog(channel, logUtil.syncBanEmbed(guild.getLocale(), event.getGuild(), event.getUser(), target, reason));
		}

		public void onUnban(SlashCommandEvent event, Member moderator, Ban banData, String reason) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, event.getGuild());
			if (channel == null) return;

			sendLog(channel, logUtil.unbanEmbed(event.getGuildLocale(), banData, moderator, reason));
		}

		public void onSyncUnban(SlashCommandEvent event, Guild guild, User target, String banReason, String reason) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, event.getGuild());
			if (channel == null) return;

			sendLog(channel, logUtil.syncUnbanEmbed(guild.getLocale(), event.getGuild(), event.getUser(), target, banReason, reason));
		}

		public void onAutoUnban(Map<String, Object> banMap, Integer banId, Guild guild) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.autoUnbanEmbed(guild.getLocale(), banMap));
		}

		public void onKick(SlashCommandEvent event, User target, User moderator, String reason) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, event.getGuild());
			if (channel == null) return;

			sendLog(channel, logUtil.kickEmbed(event.getGuildLocale(), target.getName(), target.getId(), moderator.getName(), moderator.getId(), reason, target.getAvatarUrl(), true));
		}

		public void onSyncKick(SlashCommandEvent event, Guild guild, User target, String reason) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, event.getGuild());
			if (channel == null) return;

			sendLog(channel, logUtil.syncKickEmbed(guild.getLocale(), guild, event.getUser(), target, reason));
		}

		public void onChangeReason(SlashCommandEvent event, Integer banId, String oldReason, String newReason) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, event.getGuild());
			if (channel == null) return;

			Map<String, Object> ban = db.ban.getInfo(banId);
			if (ban.isEmpty() || ban == null) {
				bot.getLogger().warn("That is not supposed to happen... Ban ID: %s", banId);
				return;
			}

			sendLog(channel, logUtil.reasonChangedEmbed(event.getGuildLocale(), banId, ban.get("userTag").toString(), ban.get("userId").toString(), event.getUser().getId(), oldReason, oldReason));
		}

		public void onChangeDuration(SlashCommandEvent event, Integer banId, Instant timeStart, Duration oldDuration, String newTime) {
			TextChannel channel = getLogChannel(LogChannels.MODERATION, event.getGuild());
			if (channel == null) return;

			Map<String, Object> ban = db.ban.getInfo(banId);
			if (ban.isEmpty() || ban == null) {
				bot.getLogger().warn("That is not supposed to happen... Ban ID: %s", banId);
				return;
			}

			sendLog(channel, logUtil.durationChangedEmbed(event.getGuildLocale(), banId, ban.get("userTag").toString(), ban.get("userId").toString(), event.getUser().getId(), timeStart, oldDuration, newTime));
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

			sendLog(channel, logUtil.groupCreatedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), event.getGuild().getId(), event.getGuild().getIconUrl(), groupId, name));
		}

		public void onDeletion(SlashCommandEvent event, Integer groupId, String name) {
			String masterId = event.getGuild().getId();
			String masterIcon = event.getGuild().getIconUrl();

			// For each group guild (except master) remove if from group DB and send log to log channel
			List<String> guildIds = db.group.getGroupGuildIds(groupId);
			for (String guildId : guildIds) {
				db.group.remove(groupId, guildId);

				TextChannel channel = getLogChannel(LogChannels.GROUPS, guildId);
				if (channel == null) continue;

				sendLog(channel, logUtil.groupMemberDeletedEmbed(channel.getGuild().getLocale(), masterId, masterIcon, groupId, name));
			}

			// Master log
			TextChannel channel = getLogChannel(LogChannels.GROUPS, event.getGuild());
			if (channel == null) return;

			sendLog(channel, logUtil.groupOwnerDeletedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), masterId, masterIcon, groupId, name));
		}

		public void onGuildAdded(SlashCommandEvent event, Integer groupId, String name, String targetId, String targetName) {
			String ownerId = event.getGuild().getId();
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
			String masterId = db.group.getMaster(groupId);
			String masterIcon = event.getJDA().getGuildById(masterId).getIconUrl();

			// Send log to added server
			TextChannel channel = getLogChannel(LogChannels.GROUPS, event.getGuild());
			if (channel != null) {
				sendLog(channel, logUtil.groupMemberJoinedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), masterId, masterIcon, groupId, name));
			}

			// Master log
			channel = getLogChannel(LogChannels.GROUPS, masterId);
			if (channel == null) return;

			sendLog(channel, logUtil.groupOwnerJoinedEmbed(channel.getGuild().getLocale(), masterId, masterIcon, event.getGuild().getName(), event.getGuild().getId(), groupId, name));
		}

		public void onGuildLeft(SlashCommandEvent event, Integer groupId, String name) {
			String masterId = db.group.getMaster(groupId);
			String masterIcon = event.getJDA().getGuildById(masterId).getIconUrl();

			// Send log to removed server
			TextChannel channel = getLogChannel(LogChannels.GROUPS, event.getGuild());
			if (channel != null) {
				sendLog(channel, logUtil.groupMemberLeftEmbed(event.getGuildLocale(), event.getMember().getAsMention(), masterId, masterIcon, groupId, name));
			}

			// Master log
			channel = getLogChannel(LogChannels.GROUPS, masterId);
			if (channel == null) return;

			sendLog(channel, logUtil.groupOwnerLeftEmbed(channel.getGuild().getLocale(), masterId, masterIcon, event.getGuild().getName(), event.getGuild().getId(), groupId, name));
		}

		public void onGuildRemoved(SlashCommandEvent event, Guild target, Integer groupId, String name) {
			String masterId = event.getGuild().getId();
			String masterIcon = event.getGuild().getIconUrl();

			// Send log to removed server
			TextChannel channel = getLogChannel(LogChannels.GROUPS, target);
			if (channel != null) {
				sendLog(channel, logUtil.groupMemberLeftEmbed(channel.getGuild().getLocale(), "Forced, by group Master", masterId, masterIcon, groupId, name));
			}

			// Master log
			channel = getLogChannel(LogChannels.GROUPS, event.getGuild());
			if (channel == null) return;

			sendLog(channel, logUtil.groupOwnerRemovedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), masterId, masterIcon, target.getName(), target.getId(), groupId, name));
		}

		public void onRenamed(SlashCommandEvent event, String oldName, Integer groupId, String newName) {
			String masterId = event.getGuild().getId();
			String masterIcon = event.getGuild().getIconUrl();

			// Send log to each group guild
			List<String> guildIds = db.group.getGroupGuildIds(groupId);
			for (String guildId : guildIds) {
				db.group.remove(groupId, guildId);

				TextChannel channel = getLogChannel(LogChannels.GROUPS, guildId);
				if (channel == null) continue;

				sendLog(channel, logUtil.groupMemberRenamedEmbed(channel.getGuild().getLocale(), masterId, masterIcon, groupId, oldName, newName));
			}

			// Master log
			TextChannel channel = getLogChannel(LogChannels.GROUPS, event.getGuild());
			if (channel == null) return;

			sendLog(channel, logUtil.groupOwnerRenamedEmbed(event.getGuildLocale(), event.getMember().getAsMention(), masterId, masterIcon, groupId, oldName, newName));
		}

		public void helperInformAction(Integer groupId, Guild target, AuditLogEntry auditLogEntry) {
			Guild master = Optional.ofNullable(db.group.getMaster(groupId)).map(bot.JDA::getGuildById).orElse(null);
			if (master == null) return;

			TextChannel channel = getLogChannel(LogChannels.GROUPS, master);
			if (channel == null) return;

			sendLog(channel, logUtil.auditLogEmbed(master.getLocale(), groupId, target, auditLogEntry));
		}

		public void helperInformLeave(Integer groupId, @Nullable Guild guild, String guildId) {
			Guild master = Optional.ofNullable(db.group.getMaster(groupId)).map(bot.JDA::getGuildById).orElse(null);
			if (master == null) return;

			TextChannel channel = getLogChannel(LogChannels.GROUPS, master);
			if (channel == null) return;

			sendLog(channel, logUtil.botLeftEmbed(master.getLocale(), groupId, guild, guildId));
		}
	}

	// Verification actions
	public class Verification {
		public void onVerified(User user, String steam64, Guild guild) {
			TextChannel channel = getLogChannel(LogChannels.VERIFICATION, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.verifiedEmbed(guild.getLocale(), user.getName(), user.getId(), user.getEffectiveAvatarUrl(), (steam64 == null ? null : db.unionVerify.getSteamName(steam64)), steam64));
		}

		public void onUnverified(User user, String steam64, Guild guild, String reason) {
			TextChannel channel = getLogChannel(LogChannels.VERIFICATION, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.unverifiedEmbed(guild.getLocale(), user.getName(), user.getId(), user.getEffectiveAvatarUrl(), (steam64 == null ? null : db.unionVerify.getSteamName(steam64)), steam64, reason));
		}
	}

	// Tickets actions
	public class Tickets {
		public void onCreate(Guild guild, GuildMessageChannel messageChannel, User author) {
			TextChannel channel = getLogChannel(LogChannels.TICKETS, guild);
			if (channel == null) return;

			sendLog(channel, logUtil.ticketCreatedEmbed(guild.getLocale(), channel, author));
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

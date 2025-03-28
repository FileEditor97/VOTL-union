package union.listeners;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;
import union.App;
import union.objects.CaseType;
import union.objects.logs.LogType;
import union.utils.database.DBUtil;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import union.utils.database.managers.CaseManager;

public class MemberListener extends ListenerAdapter {

	private final App bot;
	private final DBUtil db;

	public MemberListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}
	
	@Override
	public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
		// Log
		if (db.getLogSettings(event.getGuild()).enabled(LogType.MEMBER)) {
			bot.getLogger().member.onJoined(event.getMember());
		}

		long userId = event.getUser().getIdLong();
		Guild guild = event.getGuild();
		long guildId = guild.getIdLong();
		// Check for persistent role
		try {
			List<Role> roles = new ArrayList<>();
			for (Long roleId : db.persistent.getUserRoles(guildId, userId)) {
				Role role = guild.getRoleById(roleId);
				if (role == null) {
					// Role is deleted
					db.persistent.removeRole(guildId, roleId);
					continue;
				}
				roles.add(role);
			}
			if (!roles.isEmpty()) {
				List<Role> newRoles = new ArrayList<>(event.getMember().getRoles());
				newRoles.addAll(roles);
				guild.modifyMemberRoles(event.getMember(), newRoles).queueAfter(3, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER));
			}
		} catch (Exception e) {
			bot.getAppLogger().warn("Failed to assign persistent roles for {} @ {}\n{}", userId, guildId, e.getMessage());
		}

		// Check for active mute - then give timeout
		CaseManager.CaseData caseData = db.cases.getMemberActive(userId, guildId, CaseType.MUTE);
		if (caseData != null) {
			Instant timeEnd = caseData.getTimeEnd();
			if (timeEnd != null && timeEnd.isAfter(Instant.now())) {
				event.getMember().timeoutUntil(timeEnd)
					.reason("Active mute (#%s): %s".formatted(caseData.getLocalId(), caseData.getReason()))
					.queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER));
			}
		}


		// Checks cache on local DB, if user is verified, gives out the role
		if (db.verifyCache.isVerified(userId)) {
			Long cachedSteam64 = db.verifyCache.getSteam64(userId);
			// Check if user is blacklisted
			if (!Objects.equals(bot.getSettings().getAppealGuildId(), guildId)) {
				List<Integer> groupIds = new ArrayList<>();
				groupIds.addAll(db.group.getOwnedGroups(guildId));
				groupIds.addAll(db.group.getGuildGroups(guildId));

				for (int groupId : groupIds) {
					// if user is blacklisted in group either by discordID or Steam64
					// and joined server is not appeal server - do not add verify role
					if (db.blacklist.inGroupUser(groupId, userId) || (cachedSteam64!=null && db.blacklist.inGroupSteam64(groupId, cachedSteam64))) return;
				}
			}

			// Set roles
			Role verifyRole = Optional.ofNullable(db.getVerifySettings(guild).getRoleId())
				.map(guild::getRoleById)
				.orElse(null);
			if (verifyRole == null) return;

			Set<Long> additionalRoles = db.getVerifySettings(guild).getAdditionalRoles();
			if (additionalRoles.isEmpty()) {
				guild.addRoleToMember(event.getUser(), verifyRole)
					.reason(cachedSteam64 == null ? "Autocheck: Forced" : "Autocheck: Account linked - "+cachedSteam64)
					.queue(success -> bot.getLogger().verify.onVerified(event.getUser(), cachedSteam64, guild));
			} else {
				List<Role> finalRoles = new ArrayList<>(event.getMember().getRoles());
				// add verify role
				finalRoles.add(verifyRole);
				// add each additional role
				additionalRoles.stream()
					.map(guild::getRoleById)
					.filter(Objects::nonNull)
					.forEach(finalRoles::add);
				// modify
				guild.modifyMemberRoles(event.getMember(), finalRoles)
					.reason(cachedSteam64 == null ? "Autocheck: Forced" : "Autocheck: Account linked - "+cachedSteam64)
					.queue(success -> bot.getLogger().verify.onVerified(event.getUser(), cachedSteam64, guild));
			}
		}

	}
	
	@Override
	public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
		// Log
		if (db.getLogSettings(event.getGuild()).enabled(LogType.MEMBER)) {
			event.getGuild().retrieveAuditLogs()
				.type(ActionType.KICK)
				.limit(1)
				.queue(list -> {
					if (!list.isEmpty()) {
						AuditLogEntry entry = list.get(0);
						if (!Objects.equals(entry.getUser(), event.getJDA().getSelfUser()) && entry.getTargetIdLong() == event.getUser().getIdLong()
							&& entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(15))) {
							bot.getLogger().mod.onUserKick(entry, event.getUser());
						}
					}
					bot.getLogger().member.onLeft(event.getGuild(), event.getMember(), event.getUser());
				},
				failure -> {
					bot.getAppLogger().warn("Unable to retrieve audit log for member kick.", failure);
					bot.getLogger().member.onLeft(event.getGuild(), event.getMember(), event.getUser());
				});
		}

		long guildId = event.getGuild().getIdLong();
		long userId = event.getUser().getIdLong();
		// Add persistent roles
		try {
			List<Role> roles = event.getMember().getRoles();
			if (!roles.isEmpty()) {
				List<Long> persistentRoleIds = db.persistent.getRoles(guildId);
				if (!persistentRoleIds.isEmpty()) {
					List<Long> common = new ArrayList<>(roles.stream().map(Role::getIdLong).toList());
					common.retainAll(persistentRoleIds);
					if (!common.isEmpty()) {
						db.persistent.addUser(guildId, userId, common);
					}
				}
			}
		} catch (Exception e) {
			bot.getAppLogger().warn("Failed to save persistent roles for {} @ {}\n{}", userId, guildId, e.getMessage());
		}
		// When user leaves guild, check if there are any records in DB that would be better to remove.
		// This does not consider clearing User DB, when bot leaves guild.
		try {
			db.access.removeUser(guildId, userId);
			db.user.remove(event.getUser().getIdLong());
		} catch (SQLException ignored) {}

		if (db.getTicketSettings(event.getGuild()).autocloseLeftEnabled()) {
			db.ticket.getOpenedChannel(userId, guildId).forEach(channelId -> {
				try {
					db.ticket.closeTicket(Instant.now(), channelId, "Ticket's author left the server");
				} catch (SQLException ignored) {}
				GuildChannel channel = event.getGuild().getGuildChannelById(channelId);
				if (channel != null) channel.delete().reason("Author left").queue();
			});
		}
	}

	@Override
	public void onGuildMemberUpdateNickname(@NotNull GuildMemberUpdateNicknameEvent event) {
		if (db.getLogSettings(event.getGuild()).enabled(LogType.MEMBER)) {
			bot.getLogger().member.onNickChange(event.getMember(), event.getOldValue(), event.getNewValue());
		}
	}

}
